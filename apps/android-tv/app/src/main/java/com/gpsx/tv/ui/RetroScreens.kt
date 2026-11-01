// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.gpsx.tv.retro.COMPAT_OK
import com.gpsx.tv.retro.RetroControls
import com.gpsx.tv.retro.RetroGame
import com.gpsx.tv.retro.RetroLibrary
import com.gpsx.tv.retro.RetroPhase
import com.gpsx.tv.retro.RetroSession
import com.gpsx.tv.retro.RetroSessionSnapshot
import com.gpsx.tv.retro.RetroStorage
import com.gpsx.tv.retro.RetroStorageUse
import com.gpsx.tv.retro.RetroSystem
import com.gpsx.tv.retro.RetroShelf
import com.gpsx.tv.retro.RetroSnapshot
import com.gpsx.tv.retro.ShareAddress
import com.gpsx.tv.retro.compatible
import com.gpsx.tv.retro.incompatible
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** NAS access for the retro consoles, in the general settings: share address, account, rescan. */
@Composable
internal fun RetroNasPanel(retro: RetroSnapshot, library: RetroLibrary) {
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

    SectionTitle("Consoles rétro", retro.detail.ifEmpty { ShareAddress.display(retro.shareUrl) })
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
}

/** The games of one retro console; Back closes the game sheet first, then the list. */
@Composable
internal fun RetroGamesScreen(
    shelf: RetroShelf,
    library: RetroLibrary,
    retro: RetroSnapshot,
    onPlay: (RetroGame) -> Unit,
    onBack: () -> Unit,
) {
    val responsive = LocalResponsiveLayout.current
    var hideIncompatible by remember(shelf) { mutableStateOf(true) }
    val incompatible = shelf.games.count { retro.incompatible(it) }
    val shown = if (hideIncompatible) shelf.games.filterNot { retro.incompatible(it) } else shelf.games
    val columns = if (responsive.compact) 2 else 3
    var selected by remember(shelf) { mutableStateOf<RetroGame?>(null) }
    var lastSelected by remember(shelf) { mutableStateOf<RetroGame?>(null) }
    var visibleCount by remember(shelf) { mutableStateOf(RETRO_PAGE_SIZE) }
    BackHandler(enabled = selected == null, onBack = onBack)
    BackHandler(enabled = selected != null) { selected = null }

    val game = selected
    val summary =
        buildString {
            append("${shelf.games.size} jeux · ${shelf.folder}")
            val working = shelf.games.count { retro.compatible(it) }
            if (working > 0 || incompatible > 0) append(" · $working compatibles, $incompatible incompatibles")
        }
    ScreenFrame("", "Jeux", summary) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (game != null) {
                RetroGameSheet(game, library) { onPlay(game) }
                return@Column
            }
            if (incompatible > 0) {
                FocusButton(if (hideIncompatible) "Afficher les $incompatible incompatibles" else "Masquer les incompatibles") {
                    hideIncompatible = !hideIncompatible
                }
            }
            val requesters = remember(shelf) { mutableMapOf<RetroGame, FocusRequester>() }
            // Focus lands on the game just closed, or on the first game when the list opens.
            LaunchedEffect(lastSelected) {
                (lastSelected ?: shown.firstOrNull())?.let { requesters[it]?.let { r -> runCatching { r.requestFocus() } } }
            }
            shown.take(visibleCount).chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item ->
                        val requester = requesters.getOrPut(item) { FocusRequester() }
                        RetroGameCard(item, library, retro.compatibility[RetroLibrary.compatKey(item)], Modifier.weight(1f).focusRequester(requester)) {
                            lastSelected = item
                            selected = item
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (shown.size > visibleCount) {
                FocusButton("Afficher plus (${shown.size - visibleCount} restants)") { visibleCount += RETRO_PAGE_SIZE }
            } else if (shown.isEmpty()) {
                Text("Aucun jeu reconnu dans ce dossier.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RetroGameCard(game: RetroGame, library: RetroLibrary, compat: String?, modifier: Modifier, onClick: () -> Unit) {
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
            when {
                compat == COMPAT_OK -> Text("✓ Compatible", color = Go, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                compat?.startsWith("ko") == true -> Text("✗ Incompatible", color = Danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RetroGameSheet(game: RetroGame, library: RetroLibrary, onPlay: () -> Unit) {
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
            if (!game.system.playable) Text("Pas encore d’émulateur pour la ${game.system.label}.", color = Amber, fontSize = 12.sp)
            RetroControlsPanel(game.system, Modifier.fillMaxWidth())
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

/**
 * Full-screen retro game: preparation progress, then the game's Surface at its own aspect ratio on
 * black. Back (remote) or the L1+R1+Options chord leaves; a failure stays readable until closed.
 */
@Composable
internal fun RetroPlayScreen(game: RetroSessionSnapshot, session: RetroSession) {
    BackHandler { session.stop() }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        when (game.phase) {
            RetroPhase.PLAYING ->
                AndroidView(
                    factory = { context ->
                        SurfaceView(context).apply {
                            holder.addCallback(
                                object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) = session.setSurface(holder.surface)

                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                                    override fun surfaceDestroyed(holder: SurfaceHolder) = session.setSurface(null)
                                },
                            )
                        }
                    },
                    modifier = Modifier.aspectRatio(game.aspect, matchHeightConstraintsFirst = true).testTag("retro-surface"),
                )
            RetroPhase.PREPARING, RetroPhase.FAILED, RetroPhase.IDLE -> {
                val closeFocus = remember { FocusRequester() }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(game.title, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, color = SoftWhite)
                    Text(game.detail, color = if (game.phase == RetroPhase.FAILED) Amber else Chalk, fontSize = 15.sp)
                    if (game.phase == RetroPhase.PREPARING) game.system?.let { RetroControlsPanel(it, Modifier.width(620.dp)) }
                    FocusButton(
                        if (game.phase == RetroPhase.FAILED) "Fermer" else "Annuler",
                        modifier = Modifier.focusRequester(closeFocus).testTag("retro-close"),
                    ) { session.stop() }
                }
                LaunchedEffect(game.phase) { runCatching { closeFocus.requestFocus() } }
            }
        }
        if (game.phase == RetroPhase.PLAYING && game.detail.isNotEmpty()) {
            Text(game.detail, color = Amber, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

/** Settings rows for the space the Retro section uses on the TV; each erase asks for a second press. */
@Composable
internal fun RetroStorageRows() {
    val context = LocalContext.current
    val storage = remember(context) { RetroStorage(context) }
    val scope = rememberCoroutineScope()
    var use by remember { mutableStateOf<RetroStorageUse?>(null) }
    var confirming by remember { mutableStateOf("") }
    fun refresh() = scope.launch { use = withContext(Dispatchers.IO) { storage.use() } }
    LaunchedEffect(Unit) { refresh() }
    fun erase(which: String, action: () -> Unit) {
        if (confirming != which) {
            confirming = which
            return
        }
        confirming = ""
        scope.launch {
            withContext(Dispatchers.IO) { action() }
            refresh()
        }
    }
    val current = use
    fun label(which: String, bytes: Long?) = if (confirming == which) "CONFIRMER" else "EFFACER" + (bytes?.let { " " + RetroStorage.format(it) }.orEmpty())
    SettingRow(
        "Jeux gardés sur la TV",
        current?.let { "${it.gameCount} jeux copiés depuis le NAS · espace libre sur la TV : ${RetroStorage.format(it.free)}" } ?: "Calcul…",
        label("games", current?.games),
    ) { erase("games", storage::clearGames) }
    SettingRow(
        "Jaquettes et émulateurs",
        current?.let { "Jaquettes ${RetroStorage.format(it.covers)} · émulateurs ${RetroStorage.format(it.cores)} · retéléchargés au besoin" } ?: "Calcul…",
        label("caches", current?.let { it.covers + it.cores }),
    ) { erase("caches", storage::clearCaches) }
    SettingRow(
        "Sauvegardes rétro",
        "Progression enregistrée des jeux · effacée seulement à la demande",
        label("saves", current?.saves),
    ) { erase("saves", storage::clearSaves) }
}

/** Controls diagram: each PlayStation button, drawn as a key cap, next to what it does on [system]. */
@Composable
internal fun RetroControlsPanel(system: RetroSystem, modifier: Modifier = Modifier) {
    val controls = RetroControls.forSystem(system)
    Column(
        modifier.clip(PanelShape).background(Panel).border(1.dp, Line).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("COMMANDES · ${system.label.uppercase()}", color = Signal, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        controls.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                pair.forEach { (input, action) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(30.dp).clip(if (input.symbol.length == 1) CircleShape else RoundedCornerShape(6.dp))
                                .background(PanelRaised).border(1.dp, Chalk.copy(alpha = .4f), if (input.symbol.length == 1) CircleShape else RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(input.symbol, color = SoftWhite, fontSize = if (input.symbol.length == 1) 15.sp else 11.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(action, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(input.label, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(RetroControls.QUIT_HINT, color = Muted, fontSize = 10.sp)
    }
}

/** Controls tab of a retro console's environment. */
@Composable
internal fun RetroControlsScreen(system: RetroSystem) {
    ScreenFrame("", "Commandes", "Manette PlayStation → ${system.label}") {
        RetroControlsPanel(system, Modifier.widthIn(max = 900.dp))
    }
}
