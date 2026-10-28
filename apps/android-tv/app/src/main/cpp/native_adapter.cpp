// SPDX-License-Identifier: AGPL-3.0-only

#include <aaudio/AAudio.h>
#include <android/api-level.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

#include <array>
#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <dlfcn.h>
#include <memory>
#include <mutex>
#include <new>
#include <thread>
#include <unordered_map>

namespace {

enum class Status : jint {
    kOk = 0,
    kClosed = 1,
    kStaleGeneration = 2,
    kInvalidSurface = 3,
    kCodecError = 4,
    kAudioError = 5,
};

constexpr int32_t kAudioChannels = 2;
constexpr int32_t kAudioSampleRate = 48'000;
constexpr size_t kPcmCapacitySamples = 8'192;  // 85.3 ms, stereo s16.
constexpr int64_t kAudioStopWaitNanos = 100'000'000;
constexpr int kAudioStopWaitAttempts = 5;
static_assert((kPcmCapacitySamples & (kPcmCapacitySamples - 1U)) == 0U);
static_assert(std::atomic<uint32_t>::is_always_lock_free);
std::atomic<uint32_t> live_workers{0};
std::atomic<uint32_t> live_windows{0};

class PcmRing final {
public:
    int32_t read(int16_t* output, int32_t frames) noexcept {
        const uint32_t requested = static_cast<uint32_t>(frames * kAudioChannels);
        const uint32_t read = read_index_.load(std::memory_order_relaxed);
        const uint32_t write = write_index_.load(std::memory_order_acquire);
        const uint32_t available = write - read;
        const uint32_t copied = available < requested ? available : requested;

        for (uint32_t i = 0; i < copied; ++i) {
            output[i] = samples_[(read + i) & (samples_.size() - 1U)];
        }
        if (copied < requested) {
            std::memset(output + copied, 0, (requested - copied) * sizeof(int16_t));
            underruns_.fetch_add(1, std::memory_order_relaxed);
        }
        read_index_.store(read + copied, std::memory_order_release);
        return frames;
    }

    [[nodiscard]] uint64_t underruns() const noexcept {
        return underruns_.load(std::memory_order_relaxed);
    }

private:
    std::array<int16_t, kPcmCapacitySamples> samples_{};
    std::atomic<uint32_t> read_index_{0};
    std::atomic<uint32_t> write_index_{0};
    std::atomic<uint64_t> underruns_{0};
};

#if defined(GPSX_DEBUG_TEST_HOOKS)
struct TestAAudioBuilder final {
    AAudioStream_dataCallback data_callback = nullptr;
    void* data_user_data = nullptr;
    AAudioStream_errorCallback error_callback = nullptr;
    void* error_user_data = nullptr;
};

struct TestAAudioStream final {
    AAudioStream_errorCallback error_callback = nullptr;
    void* error_user_data = nullptr;
    std::atomic<aaudio_stream_state_t> state{AAUDIO_STREAM_STATE_OPEN};
};

TestAAudioBuilder* test_builder(AAudioStreamBuilder* builder) noexcept {
    return reinterpret_cast<TestAAudioBuilder*>(builder);
}

TestAAudioStream* test_stream(AAudioStream* stream) noexcept {
    return reinterpret_cast<TestAAudioStream*>(stream);
}

aaudio_result_t test_create_stream_builder(AAudioStreamBuilder** builder) noexcept {
    auto* created = new (std::nothrow) TestAAudioBuilder();
    *builder = reinterpret_cast<AAudioStreamBuilder*>(created);
    return created == nullptr ? AAUDIO_ERROR_NO_MEMORY : AAUDIO_OK;
}

aaudio_result_t test_builder_delete(AAudioStreamBuilder* builder) noexcept {
    delete test_builder(builder);
    return AAUDIO_OK;
}

void test_set_direction(AAudioStreamBuilder*, aaudio_direction_t) noexcept {}
void test_set_format(AAudioStreamBuilder*, aaudio_format_t) noexcept {}
void test_set_channel_count(AAudioStreamBuilder*, int32_t) noexcept {}
void test_set_sample_rate(AAudioStreamBuilder*, int32_t) noexcept {}
void test_set_performance_mode(AAudioStreamBuilder*, aaudio_performance_mode_t) noexcept {}
void test_set_sharing_mode(AAudioStreamBuilder*, aaudio_sharing_mode_t) noexcept {}

void test_set_data_callback(
    AAudioStreamBuilder* builder,
    AAudioStream_dataCallback callback,
    void* user_data) noexcept {
    test_builder(builder)->data_callback = callback;
    test_builder(builder)->data_user_data = user_data;
}

void test_set_error_callback(
    AAudioStreamBuilder* builder,
    AAudioStream_errorCallback callback,
    void* user_data) noexcept {
    test_builder(builder)->error_callback = callback;
    test_builder(builder)->error_user_data = user_data;
}

aaudio_result_t test_open_stream(AAudioStreamBuilder* builder, AAudioStream** stream) noexcept {
    auto* opened = new (std::nothrow) TestAAudioStream();
    if (opened == nullptr) {
        *stream = nullptr;
        return AAUDIO_ERROR_NO_MEMORY;
    }
    opened->error_callback = test_builder(builder)->error_callback;
    opened->error_user_data = test_builder(builder)->error_user_data;
    *stream = reinterpret_cast<AAudioStream*>(opened);
    return AAUDIO_OK;
}

void test_invoke_error_callback(TestAAudioStream* stream) noexcept {
    if (stream->error_callback == nullptr) {
        return;
    }
    std::thread callback_thread([stream] {
        stream->error_callback(
            reinterpret_cast<AAudioStream*>(stream),
            stream->error_user_data,
            AAUDIO_ERROR_DISCONNECTED);
    });
    callback_thread.join();
}

aaudio_result_t test_request_start(AAudioStream* stream) noexcept {
    test_stream(stream)->state.store(AAUDIO_STREAM_STATE_STARTED, std::memory_order_release);
    return AAUDIO_OK;
}

aaudio_result_t test_request_stop(AAudioStream* stream) noexcept {
    auto* tested = test_stream(stream);
    test_invoke_error_callback(tested);
    tested->state.store(AAUDIO_STREAM_STATE_STOPPED, std::memory_order_release);
    return AAUDIO_OK;
}

aaudio_result_t test_close_stream(AAudioStream* stream) noexcept {
    auto* tested = test_stream(stream);
    test_invoke_error_callback(tested);
    tested->state.store(AAUDIO_STREAM_STATE_CLOSED, std::memory_order_release);
    delete tested;
    return AAUDIO_OK;
}

aaudio_stream_state_t test_get_state(AAudioStream* stream) noexcept {
    return test_stream(stream)->state.load(std::memory_order_acquire);
}

aaudio_result_t test_wait_for_state_change(
    AAudioStream* stream,
    aaudio_stream_state_t,
    aaudio_stream_state_t* next_state,
    int64_t) noexcept {
    *next_state = test_get_state(stream);
    return AAUDIO_OK;
}
#endif

struct AAudioApi final {
    using CreateStreamBuilder = aaudio_result_t (*)(AAudioStreamBuilder**);
    using BuilderDelete = aaudio_result_t (*)(AAudioStreamBuilder*);
    using SetDirection = void (*)(AAudioStreamBuilder*, aaudio_direction_t);
    using SetFormat = void (*)(AAudioStreamBuilder*, aaudio_format_t);
    using SetChannelCount = void (*)(AAudioStreamBuilder*, int32_t);
    using SetSampleRate = void (*)(AAudioStreamBuilder*, int32_t);
    using SetPerformanceMode = void (*)(AAudioStreamBuilder*, aaudio_performance_mode_t);
    using SetSharingMode = void (*)(AAudioStreamBuilder*, aaudio_sharing_mode_t);
    using SetDataCallback = void (*)(AAudioStreamBuilder*, AAudioStream_dataCallback, void*);
    using SetErrorCallback = void (*)(AAudioStreamBuilder*, AAudioStream_errorCallback, void*);
    using OpenStream = aaudio_result_t (*)(AAudioStreamBuilder*, AAudioStream**);
    using StreamOperation = aaudio_result_t (*)(AAudioStream*);
    using GetState = aaudio_stream_state_t (*)(AAudioStream*);
    using WaitForStateChange =
        aaudio_result_t (*)(AAudioStream*, aaudio_stream_state_t, aaudio_stream_state_t*, int64_t);

    void* library = nullptr;
    CreateStreamBuilder create_stream_builder = nullptr;
    BuilderDelete builder_delete = nullptr;
    SetDirection set_direction = nullptr;
    SetFormat set_format = nullptr;
    SetChannelCount set_channel_count = nullptr;
    SetSampleRate set_sample_rate = nullptr;
    SetPerformanceMode set_performance_mode = nullptr;
    SetSharingMode set_sharing_mode = nullptr;
    SetDataCallback set_data_callback = nullptr;
    SetErrorCallback set_error_callback = nullptr;
    OpenStream open_stream = nullptr;
    StreamOperation request_start = nullptr;
    StreamOperation request_stop = nullptr;
    StreamOperation close_stream = nullptr;
    GetState get_state = nullptr;
    WaitForStateChange wait_for_state_change = nullptr;

    AAudioApi() noexcept {
        library = dlopen("libaaudio.so", RTLD_NOW | RTLD_LOCAL);
        if (library == nullptr) {
            return;
        }
        create_stream_builder = symbol<decltype(create_stream_builder)>("AAudio_createStreamBuilder");
        builder_delete = symbol<decltype(builder_delete)>("AAudioStreamBuilder_delete");
        set_direction = symbol<decltype(set_direction)>("AAudioStreamBuilder_setDirection");
        set_format = symbol<decltype(set_format)>("AAudioStreamBuilder_setFormat");
        set_channel_count = symbol<decltype(set_channel_count)>("AAudioStreamBuilder_setChannelCount");
        set_sample_rate = symbol<decltype(set_sample_rate)>("AAudioStreamBuilder_setSampleRate");
        set_performance_mode = symbol<decltype(set_performance_mode)>("AAudioStreamBuilder_setPerformanceMode");
        set_sharing_mode = symbol<decltype(set_sharing_mode)>("AAudioStreamBuilder_setSharingMode");
        set_data_callback = symbol<decltype(set_data_callback)>("AAudioStreamBuilder_setDataCallback");
        set_error_callback = symbol<decltype(set_error_callback)>("AAudioStreamBuilder_setErrorCallback");
        open_stream = symbol<decltype(open_stream)>("AAudioStreamBuilder_openStream");
        request_start = symbol<decltype(request_start)>("AAudioStream_requestStart");
        request_stop = symbol<decltype(request_stop)>("AAudioStream_requestStop");
        close_stream = symbol<decltype(close_stream)>("AAudioStream_close");
        get_state = symbol<decltype(get_state)>("AAudioStream_getState");
        wait_for_state_change = symbol<decltype(wait_for_state_change)>("AAudioStream_waitForStateChange");
        if (!complete()) {
            dlclose(library);
            library = nullptr;
        }
    }

    ~AAudioApi() {
        if (library != nullptr) {
            dlclose(library);
        }
    }

    AAudioApi(const AAudioApi&) = delete;
    AAudioApi& operator=(const AAudioApi&) = delete;

    [[nodiscard]] bool complete() const noexcept {
        return create_stream_builder != nullptr && builder_delete != nullptr &&
               set_direction != nullptr && set_format != nullptr && set_channel_count != nullptr &&
               set_sample_rate != nullptr && set_performance_mode != nullptr && set_sharing_mode != nullptr &&
               set_data_callback != nullptr && set_error_callback != nullptr && open_stream != nullptr &&
               request_start != nullptr && request_stop != nullptr && close_stream != nullptr &&
               get_state != nullptr && wait_for_state_change != nullptr;
    }

#if defined(GPSX_DEBUG_TEST_HOOKS)
    void install_callback_during_stop_test_api() noexcept {
        if (library != nullptr) {
            dlclose(library);
            library = nullptr;
        }
        create_stream_builder = &test_create_stream_builder;
        builder_delete = &test_builder_delete;
        set_direction = &test_set_direction;
        set_format = &test_set_format;
        set_channel_count = &test_set_channel_count;
        set_sample_rate = &test_set_sample_rate;
        set_performance_mode = &test_set_performance_mode;
        set_sharing_mode = &test_set_sharing_mode;
        set_data_callback = &test_set_data_callback;
        set_error_callback = &test_set_error_callback;
        open_stream = &test_open_stream;
        request_start = &test_request_start;
        request_stop = &test_request_stop;
        close_stream = &test_close_stream;
        get_state = &test_get_state;
        wait_for_state_change = &test_wait_for_state_change;
    }
#endif

private:
    template <typename T>
    T symbol(const char* name) noexcept {
        return reinterpret_cast<T>(dlsym(library, name));
    }
};

ANativeWindow* acquire_window(JNIEnv* env, jobject surface) noexcept {
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (window != nullptr) {
        live_windows.fetch_add(1, std::memory_order_relaxed);
    }
    return window;
}

void release_window(ANativeWindow* window) noexcept {
    if (window != nullptr) {
        ANativeWindow_release(window);
        live_windows.fetch_sub(1, std::memory_order_relaxed);
    }
}

class Engine final {
public:
    Engine() {
        try {
            video_feeder_ = std::thread(&Engine::worker_loop, this);
            video_output_ = std::thread(&Engine::worker_loop, this);
        } catch (...) {
            {
                std::lock_guard lock(worker_mutex_);
                workers_stopping_ = true;
            }
            worker_wakeup_.notify_all();
            if (video_feeder_.joinable()) {
                video_feeder_.join();
            }
            throw;
        }
    }

    ~Engine() { shutdown(); }

    Engine(const Engine&) = delete;
    Engine& operator=(const Engine&) = delete;

    Status attach_surface(JNIEnv* env, jobject surface, int64_t generation) noexcept {
        if (surface == nullptr) {
            return Status::kInvalidSurface;
        }
        ANativeWindow* replacement = acquire_window(env, surface);
        if (replacement == nullptr) {
            return Status::kInvalidSurface;
        }

        std::lock_guard lock(control_mutex_);
        if (stopping_) {
            release_window(replacement);
            return Status::kClosed;
        }
        if (generation <= surface_generation_) {
            release_window(replacement);
            return Status::kStaleGeneration;
        }

        if (!force_codec_failure_ && codec_ != nullptr &&
            AMediaCodec_setOutputSurface(codec_, replacement) == AMEDIA_OK) {
            release_window_locked();
            window_ = replacement;
            surface_generation_ = generation;
            return Status::kOk;
        }

        destroy_codec_locked();
        if (!create_codec_locked(replacement)) {
            release_window_locked();
            surface_generation_ = generation;
            release_window(replacement);
            return Status::kCodecError;
        }
        release_window_locked();
        window_ = replacement;
        surface_generation_ = generation;
        return Status::kOk;
    }

    Status clear_surface(int64_t generation) noexcept {
        std::lock_guard lock(control_mutex_);
        if (stopping_) {
            return Status::kClosed;
        }
        if (generation != surface_generation_) {
            return Status::kStaleGeneration;
        }
        destroy_codec_locked();
        release_window_locked();
        return Status::kOk;
    }

    Status start_audio() noexcept {
        std::lock_guard lock(control_mutex_);
        if (stopping_) {
            return Status::kClosed;
        }
        if (lifecycle_test_mode_) {
            return Status::kOk;
        }
        if (audio_stream_ != nullptr) {
            return Status::kOk;
        }
        if (android_get_device_api_level() < 27 || !aaudio_.complete()) {
            return Status::kAudioError;
        }

        audio_error_.store(AAUDIO_OK, std::memory_order_relaxed);

        AAudioStreamBuilder* builder = nullptr;
        if (aaudio_.create_stream_builder(&builder) != AAUDIO_OK || builder == nullptr) {
            return Status::kAudioError;
        }
        aaudio_.set_direction(builder, AAUDIO_DIRECTION_OUTPUT);
        aaudio_.set_format(builder, AAUDIO_FORMAT_PCM_I16);
        aaudio_.set_channel_count(builder, kAudioChannels);
        aaudio_.set_sample_rate(builder, kAudioSampleRate);
        aaudio_.set_performance_mode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        aaudio_.set_sharing_mode(builder, AAUDIO_SHARING_MODE_SHARED);
        aaudio_.set_data_callback(builder, &Engine::audio_callback, this);
        aaudio_.set_error_callback(builder, &Engine::audio_error_callback, this);
        const aaudio_result_t opened = aaudio_.open_stream(builder, &audio_stream_);
        aaudio_.builder_delete(builder);
        if (opened != AAUDIO_OK || audio_stream_ == nullptr) {
            audio_stream_ = nullptr;
            return Status::kAudioError;
        }
        if (aaudio_.request_start(audio_stream_) != AAUDIO_OK) {
            stop_audio_locked();
            return Status::kAudioError;
        }
        return Status::kOk;
    }

    Status stop_audio() noexcept {
        std::lock_guard lock(control_mutex_);
        if (stopping_) {
            return Status::kClosed;
        }
        stop_audio_locked();
        return Status::kOk;
    }

    jint audio_health() const noexcept {
        if (android_get_device_api_level() < 27 || !aaudio_.complete()) {
            return 1;
        }
        return audio_error_.load(std::memory_order_relaxed) == AAUDIO_OK ? 0 : 2;
    }

#if defined(GPSX_DEBUG_TEST_HOOKS)
    void set_force_codec_failure_for_test(bool force) noexcept {
        std::lock_guard lock(control_mutex_);
        force_codec_failure_ = force;
    }

    void set_lifecycle_test_mode_for_test(bool enabled) noexcept {
        std::lock_guard lock(control_mutex_);
        lifecycle_test_mode_ = enabled;
    }

    void force_audio_disconnect_for_test() noexcept {
        audio_error_.store(AAUDIO_ERROR_DISCONNECTED, std::memory_order_relaxed);
    }

    void install_callback_during_stop_test_api() noexcept {
        std::lock_guard lock(control_mutex_);
        stop_audio_locked();
        aaudio_.install_callback_during_stop_test_api();
        audio_error_.store(AAUDIO_OK, std::memory_order_relaxed);
    }
#endif

    void shutdown() noexcept {
        {
            std::lock_guard lock(worker_mutex_);
            if (workers_stopping_) {
                return;
            }
            workers_stopping_ = true;
        }
        worker_wakeup_.notify_all();
        if (video_feeder_.joinable()) {
            video_feeder_.join();
        }
        if (video_output_.joinable()) {
            video_output_.join();
        }

        std::lock_guard lock(control_mutex_);
        stopping_ = true;
        destroy_codec_locked();
        release_window_locked();
        stop_audio_locked();
    }

private:
    static aaudio_data_callback_result_t audio_callback(
        AAudioStream*,
        void* user_data,
        void* audio_data,
        int32_t frames) noexcept {
        // Real-time invariant: this callback performs only fixed-size ring reads,
        // atomics and zero-fill. It never allocates, locks, logs or calls JNI.
        auto* engine = static_cast<Engine*>(user_data);
        engine->pcm_.read(static_cast<int16_t*>(audio_data), frames);
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    static void audio_error_callback(AAudioStream*, void* user_data, aaudio_result_t error) noexcept {
        auto* engine = static_cast<Engine*>(user_data);
        engine->audio_error_.store(error, std::memory_order_relaxed);
    }

    void worker_loop() noexcept {
        live_workers.fetch_add(1, std::memory_order_relaxed);
        std::unique_lock lock(worker_mutex_);
        worker_wakeup_.wait(lock, [this] { return workers_stopping_; });
        live_workers.fetch_sub(1, std::memory_order_relaxed);
    }

    bool create_codec_locked(ANativeWindow* output) noexcept {
        if (force_codec_failure_) {
            return false;
        }
        if (lifecycle_test_mode_) {
            return true;
        }
        AMediaCodec* candidate = AMediaCodec_createDecoderByType("video/avc");
        if (candidate == nullptr) {
            return false;
        }
        AMediaFormat* format = AMediaFormat_new();
        if (format == nullptr) {
            AMediaCodec_delete(candidate);
            return false;
        }
        AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, "video/avc");
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, 1280);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, 720);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, 60);
        const media_status_t configured = AMediaCodec_configure(candidate, format, output, nullptr, 0);
        AMediaFormat_delete(format);
        if (configured != AMEDIA_OK || AMediaCodec_start(candidate) != AMEDIA_OK) {
            AMediaCodec_delete(candidate);
            return false;
        }
        codec_ = candidate;
        return true;
    }

    void destroy_codec_locked() noexcept {
        if (codec_ != nullptr) {
            AMediaCodec_stop(codec_);
            AMediaCodec_delete(codec_);
            codec_ = nullptr;
        }
    }

    void release_window_locked() noexcept {
        if (window_ != nullptr) {
            release_window(window_);
            window_ = nullptr;
        }
    }

    void stop_audio_locked() noexcept {
        if (audio_stream_ != nullptr && aaudio_.complete()) {
            aaudio_.request_stop(audio_stream_);
            aaudio_stream_state_t state = aaudio_.get_state(audio_stream_);
            for (int attempt = 0; attempt < kAudioStopWaitAttempts &&
                                  state != AAUDIO_STREAM_STATE_STOPPED &&
                                  state != AAUDIO_STREAM_STATE_CLOSED;
                 ++attempt) {
                aaudio_stream_state_t next_state = state;
                const aaudio_result_t waited =
                    aaudio_.wait_for_state_change(audio_stream_, state, &next_state, kAudioStopWaitNanos);
                state = next_state;
                if (waited != AAUDIO_OK && waited != AAUDIO_ERROR_TIMEOUT) {
                    break;
                }
            }
            aaudio_.close_stream(audio_stream_);
            audio_stream_ = nullptr;
        }
    }

    std::mutex control_mutex_;
    bool stopping_ = false;
    int64_t surface_generation_ = 0;
    bool force_codec_failure_ = false;
    bool lifecycle_test_mode_ = false;
    ANativeWindow* window_ = nullptr;
    AMediaCodec* codec_ = nullptr;
    AAudioApi aaudio_;
    AAudioStream* audio_stream_ = nullptr;
    PcmRing pcm_;
    std::atomic<aaudio_result_t> audio_error_{AAUDIO_OK};

    std::mutex worker_mutex_;
    std::condition_variable worker_wakeup_;
    bool workers_stopping_ = false;
    std::thread video_feeder_;
    std::thread video_output_;
};

std::mutex registry_mutex;
std::unordered_map<int64_t, std::shared_ptr<Engine>> registry;
std::atomic<int64_t> next_handle{1};

std::shared_ptr<Engine> find_engine(jlong handle) {
    // JNI receives registry IDs, never addresses. Taking a shared owner under
    // this mutex makes concurrent destroy/call races memory-safe.
    std::lock_guard lock(registry_mutex);
    const auto found = registry.find(handle);
    return found == registry.end() ? nullptr : found->second;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeCreate(JNIEnv*, jobject) {
    try {
        auto engine = std::make_shared<Engine>();
        const int64_t handle = next_handle.fetch_add(1, std::memory_order_relaxed);
        std::lock_guard lock(registry_mutex);
        registry.emplace(handle, std::move(engine));
        return handle;
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    std::shared_ptr<Engine> engine;
    {
        std::lock_guard lock(registry_mutex);
        const auto found = registry.find(handle);
        if (found == registry.end()) {
            return;
        }
        engine = std::move(found->second);
        registry.erase(found);
    }
    engine->shutdown();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeAttachSurface(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject surface,
    jlong generation) {
    const auto engine = find_engine(handle);
    return engine == nullptr ? static_cast<jint>(Status::kClosed)
                             : static_cast<jint>(engine->attach_surface(env, surface, generation));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeClearSurface(
    JNIEnv*,
    jobject,
    jlong handle,
    jlong generation) {
    const auto engine = find_engine(handle);
    return engine == nullptr ? static_cast<jint>(Status::kClosed)
                             : static_cast<jint>(engine->clear_surface(generation));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeStartAudio(JNIEnv*, jobject, jlong handle) {
    const auto engine = find_engine(handle);
    return engine == nullptr ? static_cast<jint>(Status::kClosed)
                             : static_cast<jint>(engine->start_audio());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeStopAudio(JNIEnv*, jobject, jlong handle) {
    const auto engine = find_engine(handle);
    return engine == nullptr ? static_cast<jint>(Status::kClosed)
                             : static_cast<jint>(engine->stop_audio());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeAudioHealth(JNIEnv*, jobject, jlong handle) {
    const auto engine = find_engine(handle);
    return engine == nullptr ? 3 : engine->audio_health();
}

#if defined(GPSX_DEBUG_TEST_HOOKS)
extern "C" JNIEXPORT jint JNICALL
Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeSetForceCodecFailureForTest(
    JNIEnv*,
    jobject,
    jlong handle,
    jboolean force) {
    const auto engine = find_engine(handle);
    if (engine == nullptr) {
        return static_cast<jint>(Status::kClosed);
    }
    engine->set_force_codec_failure_for_test(force == JNI_TRUE);
    return static_cast<jint>(Status::kOk);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeInstallCallbackDuringStopForTest(
    JNIEnv*,
    jobject,
    jlong handle) {
    const auto engine = find_engine(handle);
    if (engine == nullptr) {
        return static_cast<jint>(Status::kClosed);
    }
    engine->install_callback_during_stop_test_api();
    return static_cast<jint>(Status::kOk);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeSetLifecycleTestModeForTest(
    JNIEnv*,
    jobject,
    jlong handle,
    jboolean enabled) {
    const auto engine = find_engine(handle);
    if (engine == nullptr) {
        return static_cast<jint>(Status::kClosed);
    }
    engine->set_lifecycle_test_mode_for_test(enabled == JNI_TRUE);
    return static_cast<jint>(Status::kOk);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeForceAudioDisconnectForTest(
    JNIEnv*,
    jobject,
    jlong handle) {
    const auto engine = find_engine(handle);
    if (engine == nullptr) {
        return static_cast<jint>(Status::kClosed);
    }
    engine->force_audio_disconnect_for_test();
    return static_cast<jint>(Status::kOk);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeResourceCounts(JNIEnv*, jobject) {
    uint64_t engines;
    {
        std::lock_guard lock(registry_mutex);
        engines = registry.size();
    }
    const uint64_t workers = live_workers.load(std::memory_order_relaxed);
    return static_cast<jlong>((engines << 32U) | workers);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gpsx_tv_nativeadapter_NativeEngine_nativeWindowCount(JNIEnv*, jobject) {
    return static_cast<jint>(live_windows.load(std::memory_order_relaxed));
}
#endif
