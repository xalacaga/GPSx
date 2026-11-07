// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

/**
 * Where the owner's Mega CD BIOS files are on the share, and the names the core asks for. Never
 * shipped with GPSx. Recalbox sets write them in capitals, and the core's file names are exact.
 */
object MegaCdBios {
    val FILES = listOf("bios_CD_E.bin", "bios_CD_J.bin", "bios_CD_U.bin")

    private val RECALBOX_SET = listOf("bios.recalbox.fullset", "bios.recalbox.fullset", "bios", "segacd")

    /** Folders to search, the console's own first: [consoleFolder], then the Recalbox BIOS set. */
    fun folders(consoleFolder: List<String>): List<List<String>> = listOf(consoleFolder, RECALBOX_SET, RECALBOX_SET.drop(1))

    /** Files of [listed] that are among [wanted], as their name on the share to the name the core asks for. */
    fun pick(listed: List<RomFile>, wanted: List<String>): Map<String, String> =
        listed.filter { !it.directory }
            .mapNotNull { file -> wanted.firstOrNull { it.equals(file.name, ignoreCase = true) }?.let { file.name to it } }
            .toMap()
}
