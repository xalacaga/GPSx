// SPDX-License-Identifier: AGPL-3.0-only
//
// The subset of the libretro core interface (API version 1) that the GPSx retro host uses,
// declared by GPSx from the public interface definition: struct layouts, callback signatures and
// command numbers. Cores are separate programs loaded at run time; see docs/RETROGAMING_PLAN.md.

#pragma once

#include <cstddef>
#include <cstdint>

namespace gpsx::retro {

constexpr unsigned kApiVersion = 1;
constexpr unsigned kExperimental = 0x10000;

// Environment commands answered by the host.
constexpr unsigned kEnvGetCanDupe = 3;
constexpr unsigned kEnvSetDiskControlInterface = 13;
constexpr unsigned kEnvGetVfsInterface = 45 | kExperimental;
constexpr unsigned kEnvShutdown = 7;
constexpr unsigned kEnvGetSystemDirectory = 9;
constexpr unsigned kEnvSetPixelFormat = 10;
constexpr unsigned kEnvGetVariable = 15;
constexpr unsigned kEnvGetVariableUpdate = 17;
constexpr unsigned kEnvGetLogInterface = 27;
constexpr unsigned kEnvGetCoreAssetsDirectory = 30;
constexpr unsigned kEnvGetSaveDirectory = 31;
constexpr unsigned kEnvSetSystemAvInfo = 32;
constexpr unsigned kEnvSetGeometry = 37;
constexpr unsigned kEnvGetLanguage = 39;
constexpr unsigned kEnvGetAudioVideoEnable = 47 | kExperimental;
constexpr unsigned kEnvGetInputBitmasks = 51 | kExperimental;

// Informational commands the host accepts and ignores.
constexpr unsigned kEnvSetPerformanceLevel = 8;
constexpr unsigned kEnvSetInputDescriptors = 11;
constexpr unsigned kEnvSetVariables = 16;
constexpr unsigned kEnvSetSupportNoGame = 18;
constexpr unsigned kEnvSetSubsystemInfo = 34;
constexpr unsigned kEnvSetControllerInfo = 35;
constexpr unsigned kEnvSetMemoryMaps = 36 | kExperimental;
constexpr unsigned kEnvSetSupportAchievements = 42 | kExperimental;
constexpr unsigned kEnvSetSerializationQuirks = 44;
constexpr unsigned kEnvSetContentInfoOverride = 65;

constexpr unsigned kPixel0Rgb1555 = 0;
constexpr unsigned kPixelXrgb8888 = 1;
constexpr unsigned kPixelRgb565 = 2;

constexpr unsigned kLanguageFrench = 2;

constexpr unsigned kDeviceJoypad = 1;
constexpr unsigned kDeviceAnalog = 5;
constexpr unsigned kJoypadMaskId = 256;
constexpr unsigned kAnalogIndexLeft = 0;
constexpr unsigned kAnalogIndexRight = 1;
constexpr unsigned kAnalogIdX = 0;
constexpr unsigned kAnalogIdY = 1;

constexpr unsigned kMemorySaveRam = 0;

enum LogLevel { kLogDebug = 0, kLogInfo, kLogWarn, kLogError };

struct SystemInfo {
    const char* library_name;
    const char* library_version;
    const char* valid_extensions;
    bool need_fullpath;
    bool block_extract;
};

struct GameGeometry {
    unsigned base_width;
    unsigned base_height;
    unsigned max_width;
    unsigned max_height;
    float aspect_ratio;
};

struct SystemTiming {
    double fps;
    double sample_rate;
};

struct SystemAvInfo {
    GameGeometry geometry;
    SystemTiming timing;
};

struct GameInfo {
    const char* path;
    const void* data;
    size_t size;
    const char* meta;
};

struct Variable {
    const char* key;
    const char* value;
};

using LogPrintf = void (*)(LogLevel level, const char* format, ...);

struct LogCallback {
    LogPrintf log;
};

using EnvironmentFn = bool (*)(unsigned command, void* data);
using VideoRefreshFn = void (*)(const void* data, unsigned width, unsigned height, size_t pitch);
using AudioSampleFn = void (*)(int16_t left, int16_t right);
using AudioSampleBatchFn = size_t (*)(const int16_t* data, size_t frames);
using InputPollFn = void (*)();
using InputStateFn = int16_t (*)(unsigned port, unsigned device, unsigned index, unsigned id);

// Disc changing, offered by cores whose games have several discs.
struct DiskControl {
    bool (*set_eject_state)(bool ejected);
    bool (*get_eject_state)();
    unsigned (*get_image_index)();
    bool (*set_image_index)(unsigned index);
    unsigned (*get_num_images)();
    bool (*replace_image_index)(unsigned index, const void* info);
    bool (*add_image_index)();
};

constexpr unsigned kVfsRead = 1;
constexpr unsigned kVfsWrite = 2;
constexpr unsigned kVfsUpdateExisting = 4;
constexpr int kVfsStatValid = 1;
constexpr int kVfsStatDirectory = 2;
constexpr uint32_t kVfsVersion = 3;

struct VfsFile;
struct VfsDir;

// File access the host offers the core (interface version 3), in the order the core expects.
struct VfsInterface {
    const char* (*get_path)(VfsFile*);
    VfsFile* (*open)(const char* path, unsigned mode, unsigned hints);
    int (*close)(VfsFile*);
    int64_t (*size)(VfsFile*);
    int64_t (*tell)(VfsFile*);
    int64_t (*seek)(VfsFile*, int64_t offset, int position);
    int64_t (*read)(VfsFile*, void* target, uint64_t length);
    int64_t (*write)(VfsFile*, const void* source, uint64_t length);
    int (*flush)(VfsFile*);
    int (*remove)(const char* path);
    int (*rename)(const char* from, const char* to);
    int64_t (*truncate)(VfsFile*, int64_t length);
    int (*stat)(const char* path, int32_t* size);
    int (*mkdir)(const char* path);
    VfsDir* (*opendir)(const char* path, bool hidden);
    bool (*readdir)(VfsDir*);
    const char* (*dirent_get_name)(VfsDir*);
    bool (*dirent_is_dir)(VfsDir*);
    int (*closedir)(VfsDir*);
};

struct VfsInterfaceInfo {
    uint32_t required_interface_version;
    const VfsInterface* iface;
};

// Entry points every core exports, resolved with dlsym.
struct CoreApi {
    void (*set_environment)(EnvironmentFn);
    void (*set_video_refresh)(VideoRefreshFn);
    void (*set_audio_sample)(AudioSampleFn);
    void (*set_audio_sample_batch)(AudioSampleBatchFn);
    void (*set_input_poll)(InputPollFn);
    void (*set_input_state)(InputStateFn);
    void (*init)();
    void (*deinit)();
    unsigned (*api_version)();
    void (*get_system_info)(SystemInfo*);
    void (*get_system_av_info)(SystemAvInfo*);
    void (*set_controller_port_device)(unsigned port, unsigned device);
    void (*run)();
    bool (*load_game)(const GameInfo*);
    void (*unload_game)();
    void* (*get_memory_data)(unsigned id);
    size_t (*get_memory_size)(unsigned id);
};

}  // namespace gpsx::retro
