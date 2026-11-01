// SPDX-License-Identifier: AGPL-3.0-only
//
// GPSx retro host: runs one libretro core on a dedicated native thread. Video frames go straight to
// the ANativeWindow, audio through a bounded ring to AAudio, and pad state arrives through coarse
// JNI calls on change. Frames and samples never cross JNI. See docs/RETROGAMING_PLAN.md (step R2).

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <dlfcn.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <map>
#include <sstream>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "retro_api.h"

namespace {

using namespace gpsx::retro;

constexpr const char* kTag = "GPSxRetro";

// ---- AAudio, resolved at run time (API 26+; the app's minSdk is 24) ----

struct AAudioStreamBuilder;
struct AAudioStream;
using aaudio_result_t = int32_t;
using AAudioDataCallback = int32_t (*)(AAudioStream*, void*, void*, int32_t);
constexpr int32_t kAAudioFormatI16 = 1;
constexpr int32_t kAAudioLowLatency = 12;
constexpr int32_t kAAudioShared = 1;
constexpr int32_t kAAudioContinue = 0;
constexpr int32_t kOutputRate = 48000;

struct AAudioApi {
    aaudio_result_t (*create_builder)(AAudioStreamBuilder**) = nullptr;
    void (*set_format)(AAudioStreamBuilder*, int32_t) = nullptr;
    void (*set_channels)(AAudioStreamBuilder*, int32_t) = nullptr;
    void (*set_rate)(AAudioStreamBuilder*, int32_t) = nullptr;
    void (*set_performance)(AAudioStreamBuilder*, int32_t) = nullptr;
    void (*set_sharing)(AAudioStreamBuilder*, int32_t) = nullptr;
    void (*set_callback)(AAudioStreamBuilder*, AAudioDataCallback, void*) = nullptr;
    aaudio_result_t (*open)(AAudioStreamBuilder*, AAudioStream**) = nullptr;
    aaudio_result_t (*delete_builder)(AAudioStreamBuilder*) = nullptr;
    aaudio_result_t (*start)(AAudioStream*) = nullptr;
    aaudio_result_t (*stop)(AAudioStream*) = nullptr;
    aaudio_result_t (*close)(AAudioStream*) = nullptr;
    int32_t (*get_rate)(AAudioStream*) = nullptr;

    bool load() {
        void* library = dlopen("libaaudio.so", RTLD_NOW);
        if (library == nullptr) return false;
        return resolve(library, create_builder, "AAudio_createStreamBuilder") &&
               resolve(library, set_format, "AAudioStreamBuilder_setFormat") &&
               resolve(library, set_channels, "AAudioStreamBuilder_setChannelCount") &&
               resolve(library, set_rate, "AAudioStreamBuilder_setSampleRate") &&
               resolve(library, set_performance, "AAudioStreamBuilder_setPerformanceMode") &&
               resolve(library, set_sharing, "AAudioStreamBuilder_setSharingMode") &&
               resolve(library, set_callback, "AAudioStreamBuilder_setDataCallback") &&
               resolve(library, open, "AAudioStreamBuilder_openStream") &&
               resolve(library, delete_builder, "AAudioStreamBuilder_delete") &&
               resolve(library, start, "AAudioStream_requestStart") &&
               resolve(library, stop, "AAudioStream_requestStop") &&
               resolve(library, close, "AAudioStream_close") &&
               resolve(library, get_rate, "AAudioStream_getSampleRate");
    }

    template <typename T>
    static bool resolve(void* library, T& target, const char* name) {
        target = reinterpret_cast<T>(dlsym(library, name));
        return target != nullptr;
    }
};

// ---- Bounded stereo ring: one producer (core thread), one consumer (AAudio callback) ----
//
// Depth: 4096 frames, about 85 ms at 48 kHz. Overflow drops the incoming frames, underflow plays
// silence; both are counted. The core is paced by this ring (it runs while the ring holds less than
// kTargetFill), so overflow only happens if the audio device stalls.

class StereoRing {
public:
    static constexpr size_t kFrames = 4096;

    size_t fill() const { return write_.load(std::memory_order_acquire) - read_.load(std::memory_order_acquire); }

    void push(const int16_t* frames, size_t count) {
        size_t write = write_.load(std::memory_order_relaxed);
        const size_t read = read_.load(std::memory_order_acquire);
        const size_t room = kFrames - (write - read);
        if (count > room) {
            overflow_frames_.fetch_add(count - room, std::memory_order_relaxed);
            count = room;
        }
        for (size_t i = 0; i < count; ++i, ++write) {
            const size_t slot = (write % kFrames) * 2;
            data_[slot] = frames[i * 2];
            data_[slot + 1] = frames[i * 2 + 1];
        }
        write_.store(write, std::memory_order_release);
    }

    // Real-time side: fixed-size reads and atomics only.
    void pop(int16_t* out, size_t count) {
        size_t read = read_.load(std::memory_order_relaxed);
        const size_t available = write_.load(std::memory_order_acquire) - read;
        const size_t taken = std::min(count, available);
        for (size_t i = 0; i < taken; ++i, ++read) {
            const size_t slot = (read % kFrames) * 2;
            out[i * 2] = data_[slot];
            out[i * 2 + 1] = data_[slot + 1];
        }
        read_.store(read, std::memory_order_release);
        if (taken < count) {
            std::memset(out + taken * 2, 0, (count - taken) * 2 * sizeof(int16_t));
            if (available_once_.load(std::memory_order_relaxed)) underruns_.fetch_add(1, std::memory_order_relaxed);
        } else {
            available_once_.store(true, std::memory_order_relaxed);
        }
    }

    uint64_t underruns() const { return underruns_.load(std::memory_order_relaxed); }
    uint64_t overflow_frames() const { return overflow_frames_.load(std::memory_order_relaxed); }

private:
    int16_t data_[kFrames * 2] = {};
    std::atomic<size_t> write_{0};
    std::atomic<size_t> read_{0};
    std::atomic<bool> available_once_{false};
    std::atomic<uint64_t> underruns_{0};
    std::atomic<uint64_t> overflow_frames_{0};
};

constexpr unsigned kPorts = 4;
constexpr size_t kSlFrames = 480;  // 10 ms per OpenSL ES buffer at 48 kHz, two buffers queued
constexpr size_t kTargetFill = 2048;  // ~43 ms of buffered audio before the core runs again

// ---- The one running core ----

struct Host {
    void* library = nullptr;
    CoreApi core{};
    SystemAvInfo av{};
    unsigned pixel_format = kPixel0Rgb1555;
    std::string system_dir;
    std::string save_dir;
    std::string sram_path;
    std::vector<uint8_t> game_data;
    std::string game_path;
    // Core options chosen by GPSx (key → value); other options keep the core's defaults.
    std::map<std::string, std::string> options;
    // Default of every option the core declared (first value of "Description; v1|v2").
    std::map<std::string, std::string> option_defaults;
    // First error the core logged while loading the game (arcade cores report missing ROMs this way).
    bool loading = false;
    std::string load_error;

    std::mutex window_mutex;
    ANativeWindow* window = nullptr;
    unsigned window_width = 0;
    unsigned window_height = 0;
    int32_t window_format = 0;

    AAudioApi aaudio;
    AAudioStream* stream = nullptr;
    // OpenSL ES output, used instead of AAudio when Kotlin asks for it (Bluetooth output active or
    // the compatibility setting): FACT (GP520, 0.2.4) AAudio to a Bluetooth soundbar is not heard.
    SLObjectItf sl_engine = nullptr;
    SLObjectItf sl_mix = nullptr;
    SLObjectItf sl_player = nullptr;
    SLPlayItf sl_play = nullptr;
    SLAndroidSimpleBufferQueueItf sl_queue = nullptr;
    int16_t sl_buffers[2][kSlFrames * 2] = {};
    int sl_next = 0;
    bool audio_open = false;
    StereoRing ring;
    double resample_step = 1.0;  // core frames per output frame
    double resample_pos = 0.0;
    int16_t last_frame[2] = {0, 0};
    std::vector<int16_t> resampled;

    // One entry per player port; sticks pack x in the low 16 bits and y in the high 16 bits.
    // A press is latched until the core's next input poll, so a press released within one frame is
    // still seen for that frame: button edges are never dropped between capture and the core.
    std::atomic<uint32_t> buttons[kPorts] = {};
    std::atomic<uint32_t> latched[kPorts] = {};
    uint32_t frame_buttons[kPorts] = {};  // core thread only
    std::atomic<int32_t> left_stick[kPorts] = {};
    std::atomic<int32_t> right_stick[kPorts] = {};

    std::thread thread;
    std::atomic<bool> running{false};
    std::atomic<bool> shutdown_requested{false};
    std::atomic<uint64_t> frames_run{0};
    std::atomic<uint64_t> frames_shown{0};
};

Host* g_host = nullptr;  // libretro callbacks carry no user pointer: one core at a time
std::mutex g_host_mutex;
std::string g_last_error;

void log_core(LogLevel level, const char* format, ...) {
    va_list args;
    va_start(args, format);
    Host* host = g_host;
    if (level == kLogError && host != nullptr && host->loading && host->load_error.empty()) {
        va_list copy;
        va_copy(copy, args);
        char message[256];
        vsnprintf(message, sizeof(message), format, copy);
        va_end(copy);
        host->load_error = message;
        while (!host->load_error.empty() && (host->load_error.back() == '\n' || host->load_error.back() == '\r')) host->load_error.pop_back();
    }
    const int priority = level == kLogError ? ANDROID_LOG_ERROR : level == kLogWarn ? ANDROID_LOG_WARN : level == kLogInfo ? ANDROID_LOG_INFO : ANDROID_LOG_DEBUG;
    __android_log_vprint(priority, "GPSxRetroCore", format, args);
    va_end(args);
}

bool environment(unsigned command, void* data) {
    Host* host = g_host;
    if (host == nullptr) return false;
    switch (command) {
        case kEnvGetCanDupe:
            *static_cast<bool*>(data) = true;
            return true;
        case kEnvShutdown:
            host->shutdown_requested.store(true);
            return true;
        case kEnvGetSystemDirectory:
        case kEnvGetCoreAssetsDirectory:
            *static_cast<const char**>(data) = host->system_dir.c_str();
            return true;
        case kEnvGetSaveDirectory:
            *static_cast<const char**>(data) = host->save_dir.c_str();
            return true;
        case kEnvSetPixelFormat: {
            const unsigned format = *static_cast<const unsigned*>(data);
            if (format > kPixelRgb565) return false;
            host->pixel_format = format;
            return true;
        }
        case kEnvGetVariable: {
            auto* variable = static_cast<Variable*>(data);
            if (variable == nullptr || variable->key == nullptr) return false;
            // GPSx's choice first, else the default the core declared: cores read option values
            // through this call and some leave settings empty when it answers nothing (PUAE: model
            // and Kickstart stayed "").
            auto found = host->options.find(variable->key);
            if (found == host->options.end()) {
                found = host->option_defaults.find(variable->key);
                if (found == host->option_defaults.end()) return false;
            }
            variable->value = found->second.c_str();
            return true;
        }
        case kEnvSetVariables: {
            // Option list as "Description; value1|value2"; logged so GPSx settings can name them.
            for (auto* variable = static_cast<const Variable*>(data); variable != nullptr && variable->key != nullptr; ++variable) {
                const std::string text = variable->value != nullptr ? variable->value : "";
                __android_log_print(ANDROID_LOG_DEBUG, kTag, "core option %s = %.200s", variable->key, text.c_str());
                const size_t start = text.find("; ");
                if (start != std::string::npos) {
                    const std::string values = text.substr(start + 2);
                    host->option_defaults[variable->key] = values.substr(0, values.find('|'));
                }
            }
            return true;
        }
        case kEnvGetVariableUpdate:
            *static_cast<bool*>(data) = false;
            return true;
        case kEnvGetLogInterface:
            static_cast<LogCallback*>(data)->log = log_core;
            return true;
        case kEnvSetSystemAvInfo:
            host->av = *static_cast<const SystemAvInfo*>(data);
            return true;
        case kEnvSetGeometry:
            host->av.geometry = *static_cast<const GameGeometry*>(data);
            return true;
        case kEnvGetLanguage:
            *static_cast<unsigned*>(data) = kLanguageFrench;
            return true;
        case kEnvGetAudioVideoEnable:
            *static_cast<int*>(data) = 3;
            return true;
        case kEnvGetInputBitmasks:
            return true;
        case kEnvSetPerformanceLevel:
        case kEnvSetInputDescriptors:
        case kEnvSetSupportNoGame:
        case kEnvSetSubsystemInfo:
        case kEnvSetControllerInfo:
        case kEnvSetMemoryMaps:
        case kEnvSetSupportAchievements:
        case kEnvSetSerializationQuirks:
        case kEnvSetContentInfoOverride:
            return true;
        default:
            return false;
    }
}

uint16_t to_rgb565(uint16_t pixel_1555) {
    return static_cast<uint16_t>(((pixel_1555 & 0x7FE0) << 1) | (pixel_1555 & 0x1F) | ((pixel_1555 & 0x0200) >> 4));
}

void video_refresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    Host* host = g_host;
    if (host == nullptr || data == nullptr || width == 0 || height == 0) return;  // null data: duplicate frame
    std::lock_guard lock(host->window_mutex);
    ANativeWindow* window = host->window;
    if (window == nullptr) return;
    const bool wide = host->pixel_format == kPixelXrgb8888;
    const int32_t format = wide ? WINDOW_FORMAT_RGBX_8888 : WINDOW_FORMAT_RGB_565;
    if (width != host->window_width || height != host->window_height || format != host->window_format) {
        ANativeWindow_setBuffersGeometry(window, static_cast<int32_t>(width), static_cast<int32_t>(height), format);
        host->window_width = width;
        host->window_height = height;
        host->window_format = format;
    }
    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window, &buffer, nullptr) != 0) return;
    const unsigned rows = std::min<unsigned>(height, static_cast<unsigned>(buffer.height));
    const unsigned columns = std::min<unsigned>(width, static_cast<unsigned>(buffer.width));
    const auto* source = static_cast<const uint8_t*>(data);
    auto* target = static_cast<uint8_t*>(buffer.bits);
    for (unsigned y = 0; y < rows; ++y) {
        const uint8_t* in = source + y * pitch;
        if (wide) {
            auto* out = reinterpret_cast<uint32_t*>(target + static_cast<size_t>(y) * buffer.stride * 4);
            const auto* pixels = reinterpret_cast<const uint32_t*>(in);
            for (unsigned x = 0; x < columns; ++x) {
                const uint32_t p = pixels[x];  // 0x00RRGGBB → bytes R, G, B, X
                out[x] = 0xFF000000u | ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF);
            }
        } else {
            auto* out = reinterpret_cast<uint16_t*>(target + static_cast<size_t>(y) * buffer.stride * 2);
            const auto* pixels = reinterpret_cast<const uint16_t*>(in);
            if (host->pixel_format == kPixelRgb565) {
                std::memcpy(out, pixels, columns * 2);
            } else {
                for (unsigned x = 0; x < columns; ++x) out[x] = to_rgb565(pixels[x]);
            }
        }
    }
    ANativeWindow_unlockAndPost(window);
    host->frames_shown.fetch_add(1, std::memory_order_relaxed);
}

// Linear resampling from the core's rate to the device rate, on the core thread.
size_t audio_batch(const int16_t* data, size_t frames) {
    Host* host = g_host;
    if (host == nullptr || !host->audio_open) return frames;
    std::vector<int16_t>& out = host->resampled;
    out.clear();
    double position = host->resample_pos;
    const double step = host->resample_step;
    while (position < static_cast<double>(frames)) {
        const auto index = static_cast<size_t>(position);
        const double fraction = position - static_cast<double>(index);
        for (int channel = 0; channel < 2; ++channel) {
            const int16_t a = index == 0 ? host->last_frame[channel] : data[(index - 1) * 2 + channel];
            const int16_t b = data[index * 2 + channel];
            out.push_back(static_cast<int16_t>(a + (b - a) * fraction));
        }
        position += step;
    }
    host->resample_pos = position - static_cast<double>(frames);
    if (frames > 0) {
        host->last_frame[0] = data[(frames - 1) * 2];
        host->last_frame[1] = data[(frames - 1) * 2 + 1];
    }
    host->ring.push(out.data(), out.size() / 2);
    return frames;
}

void audio_sample(int16_t left, int16_t right) {
    const int16_t frame[2] = {left, right};
    audio_batch(frame, 1);
}

// Pads are sampled once per frame by the host (see sample_input), whether or not the core polls.
void input_poll() {}

void sample_input(Host* host) {
    for (unsigned port = 0; port < kPorts; ++port) {
        host->frame_buttons[port] = host->buttons[port].load(std::memory_order_relaxed) | host->latched[port].exchange(0, std::memory_order_relaxed);
    }
}

int16_t input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    Host* host = g_host;
    if (host == nullptr || port >= kPorts) return 0;
    if (device == kDeviceJoypad) {
        const uint32_t held = host->frame_buttons[port];
        if (id == kJoypadMaskId) return static_cast<int16_t>(held & 0xFFFF);
        return id < 16 && (held & (1u << id)) != 0 ? 1 : 0;
    }
    if (device == kDeviceAnalog) {
        const int32_t packed = (index == kAnalogIndexLeft ? host->left_stick[port] : host->right_stick[port]).load(std::memory_order_relaxed);
        return static_cast<int16_t>(id == kAnalogIdX ? (packed & 0xFFFF) : ((packed >> 16) & 0xFFFF));
    }
    return 0;
}

int32_t audio_callback(AAudioStream*, void* user, void* audio, int32_t frames) {
    static_cast<Host*>(user)->ring.pop(static_cast<int16_t*>(audio), static_cast<size_t>(frames));
    return kAAudioContinue;
}

// OpenSL ES buffer-queue callback: refills the buffer just played from the ring (silence on underrun).
void sl_callback(SLAndroidSimpleBufferQueueItf queue, void* user) {
    auto* host = static_cast<Host*>(user);
    int16_t* buffer = host->sl_buffers[host->sl_next];
    host->sl_next ^= 1;
    host->ring.pop(buffer, kSlFrames);
    (*queue)->Enqueue(queue, buffer, kSlFrames * 2 * sizeof(int16_t));
}

void close_sles(Host& host) {
    if (host.sl_play != nullptr) (*host.sl_play)->SetPlayState(host.sl_play, SL_PLAYSTATE_STOPPED);
    if (host.sl_player != nullptr) (*host.sl_player)->Destroy(host.sl_player);
    if (host.sl_mix != nullptr) (*host.sl_mix)->Destroy(host.sl_mix);
    if (host.sl_engine != nullptr) (*host.sl_engine)->Destroy(host.sl_engine);
    host.sl_player = host.sl_mix = host.sl_engine = nullptr;
    host.sl_play = nullptr;
    host.sl_queue = nullptr;
}

bool open_sles(Host& host) {
    SLEngineItf engine = nullptr;
    if (slCreateEngine(&host.sl_engine, 0, nullptr, 0, nullptr, nullptr) != SL_RESULT_SUCCESS ||
        (*host.sl_engine)->Realize(host.sl_engine, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS ||
        (*host.sl_engine)->GetInterface(host.sl_engine, SL_IID_ENGINE, &engine) != SL_RESULT_SUCCESS ||
        (*engine)->CreateOutputMix(engine, &host.sl_mix, 0, nullptr, nullptr) != SL_RESULT_SUCCESS ||
        (*host.sl_mix)->Realize(host.sl_mix, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS) {
        close_sles(host);
        return false;
    }
    SLDataLocator_AndroidSimpleBufferQueue queue_locator = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};
    SLDataFormat_PCM format = {SL_DATAFORMAT_PCM, 2, SL_SAMPLINGRATE_48, SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
                               SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, SL_BYTEORDER_LITTLEENDIAN};
    SLDataSource source = {&queue_locator, &format};
    SLDataLocator_OutputMix mix_locator = {SL_DATALOCATOR_OUTPUTMIX, host.sl_mix};
    SLDataSink sink = {&mix_locator, nullptr};
    const SLInterfaceID ids[1] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE};
    const SLboolean required[1] = {SL_BOOLEAN_TRUE};
    if ((*engine)->CreateAudioPlayer(engine, &host.sl_player, &source, &sink, 1, ids, required) != SL_RESULT_SUCCESS ||
        (*host.sl_player)->Realize(host.sl_player, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS ||
        (*host.sl_player)->GetInterface(host.sl_player, SL_IID_PLAY, &host.sl_play) != SL_RESULT_SUCCESS ||
        (*host.sl_player)->GetInterface(host.sl_player, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &host.sl_queue) != SL_RESULT_SUCCESS ||
        (*host.sl_queue)->RegisterCallback(host.sl_queue, sl_callback, &host) != SL_RESULT_SUCCESS) {
        close_sles(host);
        return false;
    }
    host.resample_step = host.av.timing.sample_rate / static_cast<double>(kOutputRate);
    host.resampled.reserve(8192);
    sl_callback(host.sl_queue, &host);  // two silent buffers start the queue
    sl_callback(host.sl_queue, &host);
    return true;
}

bool open_audio(Host& host) {
    if (!host.aaudio.load()) return false;
    AAudioStreamBuilder* builder = nullptr;
    if (host.aaudio.create_builder(&builder) != 0 || builder == nullptr) return false;
    host.aaudio.set_format(builder, kAAudioFormatI16);
    host.aaudio.set_channels(builder, 2);
    host.aaudio.set_rate(builder, kOutputRate);
    host.aaudio.set_performance(builder, kAAudioLowLatency);
    host.aaudio.set_sharing(builder, kAAudioShared);
    host.aaudio.set_callback(builder, audio_callback, &host);
    const aaudio_result_t opened = host.aaudio.open(builder, &host.stream);
    host.aaudio.delete_builder(builder);
    if (opened != 0 || host.stream == nullptr) {
        host.stream = nullptr;
        return false;
    }
    const int32_t rate = host.aaudio.get_rate(host.stream);
    host.resample_step = host.av.timing.sample_rate / static_cast<double>(rate > 0 ? rate : kOutputRate);
    host.resampled.reserve(8192);
    return true;
}

void close_audio(Host& host) {
    host.audio_open = false;
    close_sles(host);
    if (host.stream == nullptr) return;
    host.aaudio.stop(host.stream);
    host.aaudio.close(host.stream);
    host.stream = nullptr;
}

template <typename T>
bool resolve_core(void* library, T& target, const char* name) {
    target = reinterpret_cast<T>(dlsym(library, name));
    if (target == nullptr) g_last_error = std::string("symbole absent : ") + name;
    return target != nullptr;
}

bool read_file(const std::string& path, std::vector<uint8_t>& out) {
    std::ifstream file(path, std::ios::binary);
    if (!file) return false;
    out.assign(std::istreambuf_iterator<char>(file), std::istreambuf_iterator<char>());
    return true;
}

void load_sram(Host& host) {
    void* memory = host.core.get_memory_data(kMemorySaveRam);
    const size_t size = host.core.get_memory_size(kMemorySaveRam);
    if (memory == nullptr || size == 0) return;
    std::vector<uint8_t> saved;
    if (read_file(host.sram_path, saved) && saved.size() == size) std::memcpy(memory, saved.data(), size);
}

void save_sram(Host& host) {
    void* memory = host.core.get_memory_data(kMemorySaveRam);
    const size_t size = host.core.get_memory_size(kMemorySaveRam);
    if (memory == nullptr || size == 0) return;
    const std::string partial = host.sram_path + ".part";
    {
        std::ofstream file(partial, std::ios::binary | std::ios::trunc);
        if (!file) return;
        file.write(static_cast<const char*>(memory), static_cast<std::streamsize>(size));
        if (!file) return;
    }
    std::rename(partial.c_str(), host.sram_path.c_str());
}

void unload(Host* host) {
    if (host->core.unload_game != nullptr) host->core.unload_game();
    if (host->core.deinit != nullptr) host->core.deinit();
    if (host->library != nullptr) dlclose(host->library);
}

// Core thread: runs a frame whenever the audio ring has room, else waits a millisecond. Without an
// audio device it paces itself on the core's frame rate.
void run_loop(Host* host) {
    using clock = std::chrono::steady_clock;
    const double fps = host->av.timing.fps > 1.0 ? host->av.timing.fps : 60.0;
    const auto frame_time = std::chrono::duration_cast<clock::duration>(std::chrono::duration<double>(1.0 / fps));
    auto next = clock::now();
    while (host->running.load() && !host->shutdown_requested.load()) {
        if (host->audio_open) {
            if (host->ring.fill() >= kTargetFill) {
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
                continue;
            }
        } else {
            std::this_thread::sleep_until(next);
            next += frame_time;
        }
        sample_input(host);
        host->core.run();
        host->frames_run.fetch_add(1, std::memory_order_relaxed);
    }
}

std::string string_from(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars != nullptr ? chars : "");
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_gpsx_tv_retro_RetroNative_nativeOpen(
    JNIEnv* env, jobject, jstring core_path, jstring game_path, jstring system_dir, jstring save_dir, jstring sram_path, jstring options, jboolean strict) {
    std::lock_guard lock(g_host_mutex);
    g_last_error.clear();
    if (g_host != nullptr) {
        g_last_error = "un jeu tourne déjà";
        return 0;
    }
    auto* host = new Host();
    host->system_dir = string_from(env, system_dir);
    host->save_dir = string_from(env, save_dir);
    host->sram_path = string_from(env, sram_path);
    host->game_path = string_from(env, game_path);
    {
        std::istringstream lines(string_from(env, options));
        std::string line;
        while (std::getline(lines, line)) {
            const size_t equals = line.find('=');
            if (equals != std::string::npos && equals > 0) host->options[line.substr(0, equals)] = line.substr(equals + 1);
        }
    }
    const std::string core = string_from(env, core_path);

    host->library = dlopen(core.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (host->library == nullptr) {
        const char* reason = dlerror();
        g_last_error = std::string("chargement du core impossible : ") + (reason != nullptr ? reason : "?");
        delete host;
        return 0;
    }
    CoreApi& api = host->core;
    void* lib = host->library;
    const bool resolved =
        resolve_core(lib, api.set_environment, "retro_set_environment") && resolve_core(lib, api.set_video_refresh, "retro_set_video_refresh") &&
        resolve_core(lib, api.set_audio_sample, "retro_set_audio_sample") &&
        resolve_core(lib, api.set_audio_sample_batch, "retro_set_audio_sample_batch") &&
        resolve_core(lib, api.set_input_poll, "retro_set_input_poll") && resolve_core(lib, api.set_input_state, "retro_set_input_state") &&
        resolve_core(lib, api.init, "retro_init") && resolve_core(lib, api.deinit, "retro_deinit") &&
        resolve_core(lib, api.api_version, "retro_api_version") && resolve_core(lib, api.get_system_info, "retro_get_system_info") &&
        resolve_core(lib, api.get_system_av_info, "retro_get_system_av_info") &&
        resolve_core(lib, api.set_controller_port_device, "retro_set_controller_port_device") && resolve_core(lib, api.run, "retro_run") &&
        resolve_core(lib, api.load_game, "retro_load_game") && resolve_core(lib, api.unload_game, "retro_unload_game") &&
        resolve_core(lib, api.get_memory_data, "retro_get_memory_data") && resolve_core(lib, api.get_memory_size, "retro_get_memory_size");
    if (!resolved || api.api_version() != kApiVersion) {
        if (resolved) g_last_error = "version d'API libretro inattendue";
        dlclose(lib);
        delete host;
        return 0;
    }

    g_host = host;
    api.set_environment(environment);
    api.init();
    api.set_video_refresh(video_refresh);
    api.set_audio_sample(audio_sample);
    api.set_audio_sample_batch(audio_batch);
    api.set_input_poll(input_poll);
    api.set_input_state(input_state);

    SystemInfo info{};
    api.get_system_info(&info);
    GameInfo game{};
    game.path = host->game_path.c_str();
    if (!info.need_fullpath) {
        if (!read_file(host->game_path, host->game_data)) {
            g_last_error = "jeu illisible";
            api.deinit();
            dlclose(lib);
            g_host = nullptr;
            delete host;
            return 0;
        }
        game.data = host->game_data.data();
        game.size = host->game_data.size();
    }
    host->loading = true;
    const bool loaded = api.load_game(&game);
    host->loading = false;
    // Strict mode (arcade sets): an error logged while loading means the romset does not match the
    // core, even when the core loads anyway to show its own error screen.
    if (loaded && strict == JNI_TRUE && !host->load_error.empty()) {
        g_last_error = "romset incompatible : " + host->load_error;
        api.unload_game();
        api.deinit();
        dlclose(lib);
        g_host = nullptr;
        delete host;
        return 0;
    }
    if (!loaded) {
        g_last_error = host->load_error.empty() ? "le core refuse ce jeu" : "le core refuse ce jeu : " + host->load_error;
        api.deinit();
        dlclose(lib);
        g_host = nullptr;
        delete host;
        return 0;
    }
    api.get_system_av_info(&host->av);
    for (unsigned port = 0; port < kPorts; ++port) api.set_controller_port_device(port, kDeviceJoypad);
    load_sram(*host);
    __android_log_print(ANDROID_LOG_INFO, kTag, "core %s %s loaded: %ux%u, %.3f fps, %.1f Hz, pixel format %u",
                        info.library_name != nullptr ? info.library_name : "?", info.library_version != nullptr ? info.library_version : "?",
                        host->av.geometry.base_width, host->av.geometry.base_height, host->av.timing.fps, host->av.timing.sample_rate,
                        host->pixel_format);
    return reinterpret_cast<jlong>(host);
}

JNIEXPORT jstring JNICALL Java_com_gpsx_tv_retro_RetroNative_nativeLastError(JNIEnv* env, jobject) {
    std::lock_guard lock(g_host_mutex);
    return env->NewStringUTF(g_last_error.c_str());
}

// [base width, base height, aspect × 1000, fps × 1000]
JNIEXPORT jintArray JNICALL Java_com_gpsx_tv_retro_RetroNative_nativeGeometry(JNIEnv* env, jobject, jlong handle) {
    auto* host = reinterpret_cast<Host*>(handle);
    const GameGeometry& g = host->av.geometry;
    const float aspect = g.aspect_ratio > 0.0f ? g.aspect_ratio : static_cast<float>(g.base_width) / static_cast<float>(std::max(1u, g.base_height));
    const jint values[4] = {static_cast<jint>(g.base_width), static_cast<jint>(g.base_height), static_cast<jint>(std::lround(aspect * 1000.0f)),
                            static_cast<jint>(std::lround(host->av.timing.fps * 1000.0))};
    jintArray result = env->NewIntArray(4);
    if (result != nullptr) env->SetIntArrayRegion(result, 0, 4, values);
    return result;
}

JNIEXPORT jboolean JNICALL Java_com_gpsx_tv_retro_RetroNative_nativeStart(JNIEnv*, jobject, jlong handle, jboolean open_sl_es) {
    auto* host = reinterpret_cast<Host*>(handle);
    if (host->running.exchange(true)) return JNI_TRUE;
    // OpenSL ES when asked, AAudio otherwise; each falls back to the other.
    const bool sles_first = open_sl_es == JNI_TRUE;
    bool sles = sles_first && open_sles(*host);
    const bool aaudio = !sles && open_audio(*host);
    if (!sles && !aaudio && !sles_first) sles = open_sles(*host);
    host->audio_open = sles || aaudio;
    __android_log_print(host->audio_open ? ANDROID_LOG_INFO : ANDROID_LOG_WARN, kTag, "audio output: %s",
                        sles ? "OpenSL ES 48 kHz" : aaudio ? "AAudio" : "none, running without sound");
    host->thread = std::thread(run_loop, host);
    if (sles) (*host->sl_play)->SetPlayState(host->sl_play, SL_PLAYSTATE_PLAYING);
    if (aaudio) host->aaudio.start(host->stream);
    return host->audio_open ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_gpsx_tv_retro_RetroNative_nativeSetSurface(JNIEnv* env, jobject, jlong handle, jobject surface) {
    auto* host = reinterpret_cast<Host*>(handle);
    ANativeWindow* replacement = surface != nullptr ? ANativeWindow_fromSurface(env, surface) : nullptr;
    std::lock_guard lock(host->window_mutex);
    if (host->window != nullptr) ANativeWindow_release(host->window);
    host->window = replacement;
    host->window_width = 0;
    host->window_height = 0;
    host->window_format = 0;
}

JNIEXPORT void JNICALL Java_com_gpsx_tv_retro_RetroNative_nativeSetInput(
    JNIEnv*, jobject, jlong handle, jint port, jint buttons, jint left_x, jint left_y, jint right_x, jint right_y) {
    auto* host = reinterpret_cast<Host*>(handle);
    if (port < 0 || static_cast<unsigned>(port) >= kPorts) return;
    host->buttons[port].store(static_cast<uint32_t>(buttons), std::memory_order_relaxed);
    host->latched[port].fetch_or(static_cast<uint32_t>(buttons), std::memory_order_relaxed);
    host->left_stick[port].store((left_x & 0xFFFF) | (left_y << 16), std::memory_order_relaxed);
    host->right_stick[port].store((right_x & 0xFFFF) | (right_y << 16), std::memory_order_relaxed);
}

// [frames run, frames shown, audio underruns, dropped audio frames, core asked to shut down]
JNIEXPORT jlongArray JNICALL Java_com_gpsx_tv_retro_RetroNative_nativeStats(JNIEnv* env, jobject, jlong handle) {
    auto* host = reinterpret_cast<Host*>(handle);
    const jlong values[5] = {static_cast<jlong>(host->frames_run.load()), static_cast<jlong>(host->frames_shown.load()),
                             static_cast<jlong>(host->ring.underruns()), static_cast<jlong>(host->ring.overflow_frames()),
                             host->shutdown_requested.load() ? 1 : 0};
    jlongArray result = env->NewLongArray(5);
    if (result != nullptr) env->SetLongArrayRegion(result, 0, 5, values);
    return result;
}

// Stops the core thread, writes the save RAM, unloads the core and frees the host.
JNIEXPORT void JNICALL Java_com_gpsx_tv_retro_RetroNative_nativeClose(JNIEnv*, jobject, jlong handle) {
    std::lock_guard lock(g_host_mutex);
    auto* host = reinterpret_cast<Host*>(handle);
    if (host == nullptr || host != g_host) return;
    host->running.store(false);
    if (host->thread.joinable()) host->thread.join();
    close_audio(*host);
    save_sram(*host);
    {
        std::lock_guard window_lock(host->window_mutex);
        if (host->window != nullptr) ANativeWindow_release(host->window);
        host->window = nullptr;
    }
    unload(host);
    g_host = nullptr;
    __android_log_print(ANDROID_LOG_INFO, kTag, "core closed after %llu frames (%llu shown, %llu audio underruns, %llu dropped audio frames)",
                        static_cast<unsigned long long>(host->frames_run.load()), static_cast<unsigned long long>(host->frames_shown.load()),
                        static_cast<unsigned long long>(host->ring.underruns()), static_cast<unsigned long long>(host->ring.overflow_frames()));
    delete host;
}

}  // extern "C"
