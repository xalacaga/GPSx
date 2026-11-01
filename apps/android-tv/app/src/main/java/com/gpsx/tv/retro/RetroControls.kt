// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

/** A PlayStation controller input as GPSx shows it in the controls diagram. */
enum class PadInput(val symbol: String, val label: String) {
    CROSS("✕", "Croix"),
    CIRCLE("○", "Rond"),
    SQUARE("□", "Carré"),
    TRIANGLE("△", "Triangle"),
    L1("L1", "L1"),
    R1("R1", "R1"),
    L2("L2", "L2"),
    R2("R2", "R2"),
    CREATE("≡", "Create"),
    OPTIONS("☰", "Options"),
    DPAD("✚", "Croix directionnelle / stick gauche"),
    RIGHT_STICK("◎", "Stick droit"),
}

/**
 * What each PlayStation button does on a retro system, following [RetroPad.fromPs5] (buttons by
 * position) and the cores' default RetroPad layouts. Neo Geo and arcade layouts are those cores'
 * defaults; a given arcade game may use fewer buttons.
 */
object RetroControls {
    fun forSystem(system: RetroSystem): List<Pair<PadInput, String>> =
        when (system) {
            RetroSystem.SNES ->
                listOf(
                    PadInput.CROSS to "B", PadInput.CIRCLE to "A", PadInput.SQUARE to "Y", PadInput.TRIANGLE to "X",
                    PadInput.L1 to "L", PadInput.R1 to "R", PadInput.CREATE to "Select", PadInput.OPTIONS to "Start",
                    PadInput.DPAD to "Croix",
                )
            RetroSystem.NEOGEO ->
                listOf(
                    PadInput.CROSS to "A", PadInput.CIRCLE to "B", PadInput.SQUARE to "C", PadInput.TRIANGLE to "D",
                    PadInput.CREATE to "Crédit (pièce)", PadInput.OPTIONS to "Start", PadInput.DPAD to "Joystick",
                )
            RetroSystem.ARCADE ->
                listOf(
                    PadInput.CROSS to "Bouton 1", PadInput.CIRCLE to "Bouton 2", PadInput.SQUARE to "Bouton 3", PadInput.TRIANGLE to "Bouton 4",
                    PadInput.L1 to "Bouton 5", PadInput.R1 to "Bouton 6", PadInput.CREATE to "Crédit (pièce)", PadInput.OPTIONS to "Start",
                    PadInput.DPAD to "Joystick",
                )
            RetroSystem.MEGADRIVE ->
                listOf(
                    PadInput.SQUARE to "A", PadInput.CROSS to "B", PadInput.CIRCLE to "C", PadInput.OPTIONS to "Start",
                    PadInput.DPAD to "Croix",
                )
            RetroSystem.NES, RetroSystem.GB, RetroSystem.GBC ->
                listOf(
                    PadInput.CROSS to "B", PadInput.CIRCLE to "A", PadInput.CREATE to "Select", PadInput.OPTIONS to "Start",
                    PadInput.DPAD to "Croix",
                )
            RetroSystem.AMIGA ->
                listOf(
                    PadInput.CROSS to "Feu", PadInput.CIRCLE to "Bouton 2", PadInput.SQUARE to "Entrée", PadInput.TRIANGLE to "Espace",
                    PadInput.L1 to "F1", PadInput.R1 to "F2", PadInput.L2 to "Clic gauche", PadInput.R2 to "Clic droit",
                    PadInput.RIGHT_STICK to "Souris", PadInput.CREATE to "Clavier virtuel", PadInput.DPAD to "Joystick",
                )
            RetroSystem.GBA ->
                listOf(
                    PadInput.CROSS to "B", PadInput.CIRCLE to "A", PadInput.L1 to "L", PadInput.R1 to "R",
                    PadInput.CREATE to "Select", PadInput.OPTIONS to "Start", PadInput.DPAD to "Croix",
                )
        }

    private val KEYBOARD_GENRES =
        listOf("strat", "gestion", "management", "aventure", "adventure", "simulation", "rpg", "rôle", "role", "wargame", "éducatif", "educational", "point")

    /**
     * Whether [game] is probably played with keyboard and mouse: on Amiga, genres from a scraped
     * `gamelist.xml` (strategy, management, adventure, simulation, role-playing…). Without genres
     * nothing can be inferred, and the owner's own mark is the only source.
     */
    fun keyboardLikely(game: RetroGame): Boolean =
        game.system == RetroSystem.AMIGA && game.genre.lowercase().let { genre -> KEYBOARD_GENRES.any { it in genre } }

    /** Shown under every layout: leaving a game works the same as in Remote Play. */
    const val QUIT_HINT = "L1 + R1 + Options maintenus 1 s, ou Retour sur la télécommande : quitter le jeu"
}
