// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.gpsx.tv.retro.RetroGame
import com.gpsx.tv.retro.RetroLibrary
import com.gpsx.tv.retro.RetroShelf
import com.gpsx.tv.retro.RetroSnapshot
import com.gpsx.tv.retro.ShareAddress
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Retro consoles found on the owner's NAS, listed in the Consoles menu under the PS5. */
@Composable
internal fun RetroConsolesPanel(
    retro: RetroSnapshot,
    library: RetroLibrary,
    focusFolder: String?,
    onOpen: (RetroShelf) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var address by remember(retro.shareUrl) { mutableStateOf(ShareAddress.display(retro.shareUrl)) }
    var user by remember(retro.user) { mutableStateOf(retro.user) }
    var password by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val addressFocus = remember { FocusRequester() }
    val userFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val editFocus = remember { FocusRequester() }
    BackHandler(enabled = editing) { editing = false }
    LaunchedEffect(retro.signInRequired) { if (retro.signInRequired) editing = true }

    SectionTitle("Rétro", retro.detail.ifEmpty { ShareAddress.display(retro.shareUrl) })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FocusButton(if (retro.scanning) "Lecture du NAS…" else "Actualiser", modifier = Modifier.testTag("retro-refresh")) { library.refresh() }
        FocusButton("Accès au NAS", modifier = Modifier.focusRequester(editFocus).testTag("retro-share-edit")) {
            invalid = false
            editing = !editing
        }
    }
    if (editing) {
        // Fields open only on demand: a focused text field raises the TV keyboard.
        Column(
            Modifier.fillMaxWidth().clip(PanelShape).background(Panel).padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LabeledInput(
                if (invalid) "Adresse illisible : \\\\serveur\\partage\\dossier" else "Dossier partagé (\\\\serveur\\partage\\dossier)",
                address,
                { address = it.take(200) },
                Modifier.fillMaxWidth(),
                focusRequester = addressFocus,
                previousFocusRequester = editFocus,
                nextFocusRequester = userFocus,
                testTag = "retro-share",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                LabeledInput(
                    "Utilisateur du NAS (vide : invité)",
                    user,
                    { user = it.take(128) },
                    Modifier.weight(1f),
                    focusRequester = userFocus,
                    previousFocusRequester = addressFocus,
                    nextFocusRequester = passwordFocus,
                    testTag = "retro-user",
                )
                LabeledInput(
                    if (retro.hasPassword) "Mot de passe (vide : inchangé)" else "Mot de passe",
                    password,
                    { password = it.take(256) },
                    Modifier.weight(1f),
                    focusRequester = passwordFocus,
                    previousFocusRequester = userFocus,
                    nextFocusRequester = saveFocus,
                    testTag = "retro-password",
                    secret = true,
                )
                FocusButton("Valider", primary = true, modifier = Modifier.focusRequester(saveFocus).testTag("retro-save")) {
                    if (library.setAccess(address, user, password)) {
                        password = ""
                        editing = false
                        runCatching { editFocus.requestFocus() }
                    } else {
                        invalid = true
                    }
                }
            }
            Text("Mot de passe chiffré sur la TV, jamais journalisé.", color = Muted, fontSize = 11.sp)
        }
    }
    val requesters = remember(retro.shelves) { retro.shelves.associate { it.folder to FocusRequester() } }
    LaunchedEffect(focusFolder, requesters) {
        focusFolder?.let { folder -> requesters[folder]?.let { runCatching { it.requestFocus() } } }
    }
    retro.shelves.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { shelf ->
                RetroConsoleCard(
                    shelf,
                    Modifier.weight(1f).focusRequester(requesters.getValue(shelf.folder)).testTag("retro-console-${shelf.system.name}"),
                ) { onOpen(shelf) }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RetroConsoleCard(shelf: RetroShelf, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val accent = if (shelf.system.playable) Signal else Chalk
    Row(
        modifier.height(150.dp).clip(PanelShape).background(if (focused) PanelRaised else Panel)
            .border(if (focused) 3.dp else 1.dp, if (focused) Signal else Line)
            .onFocusChanged { focused = it.isFocused }.clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(150.dp).height(118.dp).background(Brush.radialGradient(listOf(PanelRaised, Ink))).border(1.dp, Line),
            contentAlignment = Alignment.Center,
        ) {
            RetroConsoleArt(shelf.system, Modifier.width(132.dp).height(104.dp), glow = if (focused) Signal else accent)
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(shelf.system.label, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.6).sp, maxLines = 1)
            Text("${shelf.games.size} jeux · ${shelf.folder}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StatusPill(if (shelf.system.playable) "PRÊT" else "BIENTÔT", accent)
    }
}

/** The games of one retro console; Back closes the game sheet first, then the list. */
@Composable
internal fun RetroGamesScreen(shelf: RetroShelf, library: RetroLibrary, onBack: () -> Unit) {
    val responsive = LocalResponsiveLayout.current
    val columns = if (responsive.compact) 2 else 3
    var selected by remember(shelf) { mutableStateOf<RetroGame?>(null) }
    var lastSelected by remember(shelf) { mutableStateOf<RetroGame?>(null) }
    var visibleCount by remember(shelf) { mutableStateOf(RETRO_PAGE_SIZE) }
    var notice by remember { mutableStateOf("") }
    BackHandler(enabled = selected == null, onBack = onBack)
    BackHandler(enabled = selected != null) { selected = null }

    val game = selected
    ScreenFrame("", shelf.system.label, "${shelf.games.size} jeux · ${shelf.folder}") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (game != null) {
                RetroGameSheet(game, library, notice) {
                    notice =
                        if (game.system.playable) {
                            "Le lecteur de jeux arrive à l’étape suivante (R2)."
                        } else {
                            "Pas encore d’émulateur pour la ${game.system.label}."
                        }
                }
                return@Column
            }
            FocusButton("← Consoles", modifier = Modifier.testTag("retro-back")) { onBack() }
            val requesters = remember(shelf) { mutableMapOf<RetroGame, FocusRequester>() }
            LaunchedEffect(lastSelected) { lastSelected?.let { requesters[it]?.let { r -> runCatching { r.requestFocus() } } } }
            shelf.games.take(visibleCount).chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item ->
                        val requester = requesters.getOrPut(item) { FocusRequester() }
                        RetroGameCard(item, library, Modifier.weight(1f).focusRequester(requester)) {
                            notice = ""
                            lastSelected = item
                            selected = item
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (shelf.games.size > visibleCount) {
                FocusButton("Afficher plus (${shelf.games.size - visibleCount} restants)") { visibleCount += RETRO_PAGE_SIZE }
            } else if (shelf.games.isEmpty()) {
                Text("Aucun jeu reconnu dans ce dossier.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RetroGameCard(game: RetroGame, library: RetroLibrary, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.height(96.dp).clip(RoundedCornerShape(3.dp))
            .background(if (focused) PanelRaised else Panel)
            .border(if (focused) 3.dp else 1.dp, if (focused) Signal else Line)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(100.dp).height(74.dp).clip(RoundedCornerShape(3.dp)).background(Ink), contentAlignment = Alignment.Center) {
            val art = rememberRetroArt(library, game, THUMBNAIL_WIDTH)
            if (art != null) {
                Image(art, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Text(game.title.take(1).uppercase(), color = Line, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(game.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(game.facts(), color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RetroGameSheet(game: RetroGame, library: RetroLibrary, notice: String, onPlay: () -> Unit) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(game) { runCatching { playFocus.requestFocus() } }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.width(320.dp).height(240.dp).clip(RoundedCornerShape(3.dp)).background(Panel), contentAlignment = Alignment.Center) {
            val art = rememberRetroArt(library, game, COVER_WIDTH)
            if (art != null) {
                Image(art, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Text(game.system.label, color = Line, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(game.title, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(game.facts(), color = Chalk, fontSize = 13.sp)
            if (game.description.isNotBlank()) {
                Text(game.description.sentenceCase(), color = SoftWhite, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusButton("▶ Jouer", primary = true, modifier = Modifier.focusRequester(playFocus).testTag("retro-play"), onClick = onPlay)
            }
            if (notice.isNotEmpty()) Text(notice, color = Amber, fontSize = 12.sp)
            Text(game.path.joinToString(" / "), color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun rememberRetroArt(library: RetroLibrary, game: RetroGame, width: Int): ImageBitmap? {
    val art by produceState(library.cachedArt(game, width)?.asImageBitmap(), game, width) {
        if (value == null) {
            value = suspendCancellableCoroutine { continuation -> library.loadArt(game, width) { continuation.resume(it) } }?.asImageBitmap()
        }
    }
    return art
}

/** "1991 · Combat · 1-2 joueurs · FR", skipping what is unknown. */
internal fun RetroGame.facts(): String =
    listOfNotNull(
        year?.toString(),
        genre.takeIf(String::isNotBlank)?.sentenceCase(),
        players.takeIf(String::isNotBlank)?.let { "$it joueur" + if (it == "1") "" else "s" },
        region.takeIf(String::isNotBlank),
    ).joinToString(" · ").ifEmpty { system.label }

/** Recalbox scrapers write many descriptions in capitals; "UN TRÈS BON JEU." → "Un très bon jeu." */
internal fun String.sentenceCase(): String {
    val letters = filter(Char::isLetter)
    if (letters.isEmpty() || letters.any(Char::isLowerCase)) return this
    val lower = lowercase()
    val result = StringBuilder(lower.length)
    var capitalise = true
    lower.forEach { char ->
        result.append(if (capitalise && char.isLetter()) char.uppercaseChar() else char)
        if (char.isLetter()) capitalise = false
        if (char == '.' || char == '!' || char == '?') capitalise = true
    }
    return result.toString()
}

private const val RETRO_PAGE_SIZE = 30
private const val THUMBNAIL_WIDTH = 200
private const val COVER_WIDTH = 640
