// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.remoteplay

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/**
 * True when the device has a hardware HEVC decoder for 1080p60. HEVC gives a
 * better picture than H.264 at the same Remote Play bitrate; software decoders
 * are excluded because they add latency and heat.
 */
internal fun hasHardwareHevc1080p60(): Boolean =
    runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder &&
                info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) } &&
                info.isHardwareDecoder() &&
                info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                    .videoCapabilities
                    ?.areSizeAndRateSupported(1920, 1080, 60.0) == true
        }
    }.getOrDefault(false)

private fun MediaCodecInfo.isHardwareDecoder(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isHardwareAccelerated && !isSoftwareOnly
    } else {
        val lower = name.lowercase()
        !lower.startsWith("omx.google.") && !lower.startsWith("c2.android.")
    }
