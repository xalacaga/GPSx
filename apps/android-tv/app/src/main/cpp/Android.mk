# SPDX-License-Identifier: AGPL-3.0-only
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := gpsx_android_adapter
LOCAL_SRC_FILES := native_adapter.cpp
LOCAL_LDLIBS := -landroid -lmediandk -ldl
ifeq ($(NDK_DEBUG),1)
LOCAL_CPPFLAGS += -DGPSX_DEBUG_TEST_HOOKS=1
endif
ifeq ($(GPSX_ASAN),1)
LOCAL_SANITIZE := address
endif
include $(BUILD_SHARED_LIBRARY)

# Retro host: runs one libretro core downloaded on the owner's request (docs/RETROGAMING_PLAN.md).
include $(CLEAR_VARS)
LOCAL_MODULE := gpsx_retro
LOCAL_SRC_FILES := retro_host.cpp
LOCAL_LDLIBS := -landroid -llog -ldl -lOpenSLES
ifeq ($(GPSX_ASAN),1)
LOCAL_SANITIZE := address
endif
include $(BUILD_SHARED_LIBRARY)
