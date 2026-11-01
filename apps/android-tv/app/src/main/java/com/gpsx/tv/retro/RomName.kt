// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

/** Display name and region read from a No-Intro style file name, e.g. "Aladdin (France).zip". */
data class RomName(val title: String, val region: String)

object RomNames {
    private val TAG = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val TRAILING_ARTICLE = Regex("""^(.*), (The|A|An|Le|La|Les|L')(\s+-\s+.*)?$""")
    private val REGIONS =
        mapOf(
            "france" to "FR", "europe" to "EU", "usa" to "US", "japan" to "JP", "world" to "Monde",
            "germany" to "DE", "spain" to "ES", "italy" to "IT", "uk" to "UK", "australia" to "AU",
        )

    /**
     * "Adventures of Batman & Robin, The (Europe).zip" → title "The Adventures of Batman & Robin",
     * region "EU". Tags in brackets or parentheses are dropped from the title.
     */
    fun parse(fileName: String): RomName {
        val base = fileName.substringAfterLast('/').let { name -> name.substringBeforeLast('.', name) }
        val firstTag = Regex("""[(\[]([^)\]]*)[)\]]""").find(base)?.groupValues?.get(1).orEmpty()
        val region =
            firstTag.split(',').map(String::trim).firstNotNullOfOrNull { REGIONS[it.lowercase()] }.orEmpty()
        var title = TAG.replace(base, "").replace('_', ' ').trim()
        TRAILING_ARTICLE.matchEntire(title)?.let { match ->
            val article = match.groupValues[2]
            val separator = if (article.endsWith("'")) "" else " "
            title = article + separator + match.groupValues[1] + match.groupValues[3]
        }
        return RomName(title.ifEmpty { base }, region)
    }

    /** "# Combat #" → "Combat": Recalbox genre folders are framed with hashes. */
    fun folderGenre(folder: String): String = folder.trim().trim('#').trim()
}
