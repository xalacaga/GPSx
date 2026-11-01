// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import java.net.URLEncoder

/**
 * Box art from the libretro thumbnail server, the one RetroArch uses. The images belong to their
 * publishers: GPSx ships none of them; the TV fetches a cover only to show the owner's own game and
 * keeps it in its cache (owner decision 2026-11-01, docs/RETROGAMING_PLAN.md).
 */
object Thumbnails {
    const val SERVER = "https://thumbnails.libretro.com/"

    /** Cover URL for [fileName] on [system], or "" when the system has no matching thumbnail set. */
    fun boxArtUrl(system: RetroSystem, fileName: String): String {
        val set = system.thumbnailSet ?: return ""
        val base = fileName.substringAfterLast('/').let { name -> name.substringBeforeLast('.', name) }
        if (base.isBlank()) return ""
        return SERVER + segment(set) + "/Named_Boxarts/" + segment(safeName(base) + ".png")
    }

    /** libretro replaces these characters with '_' in thumbnail file names. */
    internal fun safeName(name: String): String = name.map { if (it in "&*/:`<>?\\|\"") '_' else it }.joinToString("")

    private fun segment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
