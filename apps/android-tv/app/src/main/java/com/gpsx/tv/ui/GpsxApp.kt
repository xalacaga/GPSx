package com.gpsx.tv.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.gpsx.tv.BuildConfig
import com.gpsx.tv.R
import com.gpsx.tv.remoteplay.RemotePlayEngine
import com.gpsx.tv.remoteplay.ConnectedGameController
import com.gpsx.tv.remoteplay.GameControllerMonitor
import com.gpsx.tv.remoteplay.RemotePlayPhase
import com.gpsx.tv.remoteplay.RemotePlaySnapshot
import com.gpsx.tv.remoteplay.RemotePlaySurfaceController
import com.gpsx.tv.remoteplay.cloud.CloudCategory
import com.gpsx.tv.remoteplay.cloud.CloudGame
import com.gpsx.tv.remoteplay.cloud.CloudPlayController
import com.gpsx.tv.remoteplay.cloud.CloudSnapshot
import com.gpsx.tv.remoteplay.cloud.LocalTokenServer
import com.gpsx.tv.remoteplay.cloud.BLURRED_ART_WIDTH
import com.gpsx.tv.remoteplay.cloud.CloudArtCache
import com.gpsx.tv.remoteplay.cloud.THUMBNAIL_ART_WIDTH
import com.gpsx.tv.remoteplay.cloud.startLocalSignIn
import com.gpsx.tv.remoteplay.cloud.PsnRelayLogin
import com.metallic.chiaki.lib.StreamMetrics
import com.gpsx.tv.diagnostics.LiveLogServer
import com.gpsx.tv.diagnostics.StoredReport
import com.gpsx.tv.diagnostics.reportUploadUrl
import com.gpsx.tv.retro.RetroGame
import com.gpsx.tv.retro.RetroLibrary
import com.gpsx.tv.retro.RetroPhase
import com.gpsx.tv.retro.RetroSession
import com.gpsx.tv.retro.RetroSnapshot
import com.gpsx.tv.update.AppUpdater
import com.gpsx.tv.update.ReleaseManifest
import com.gpsx.tv.update.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val PanelShape = RoundedCornerShape(3.dp)

internal data class ResponsiveLayout(
    val compact: Boolean,
    val short: Boolean,
)

internal val LocalResponsiveLayout = staticCompositionLocalOf { ResponsiveLayout(compact = false, short = false) }

@Composable
fun GpsxApp() {
    val context = LocalContext.current
    val engine = remember(context) { RemotePlayEngine(context.applicationContext) }
    val retroSession = remember(context) { RetroSession(context.applicationContext) }
    DisposableEffect(engine, retroSession) {
        onDispose {
            retroSession.close()
            engine.close()
        }
    }
    GpsxApp(engine, retroSession)
}

@Composable
fun GpsxApp(engine: RemotePlayEngine, retroSession: RetroSession) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(AppUiState()) }
    var remote by remember { mutableStateOf(engine.snapshot()) }
    var controllers by remember { mutableStateOf(emptyList<ConnectedGameController>()) }
    val firstLegalFocusRequester = remember { FocusRequester() }
    val controllerMonitor = remember(context) { GameControllerMonitor(context.applicationContext) }
    val cloud = remember(engine) { CloudPlayController(context.applicationContext, engine) }
    var cloudState by remember { mutableStateOf(cloud.snapshot()) }
    val retro = remember(context) { RetroLibrary(context.applicationContext) }
    var retroState by remember { mutableStateOf(retro.snapshot()) }

    DisposableEffect(retro) {
        retro.setListener { retroState = it }
        onDispose(retro::close)
    }
    var retroGame by remember { mutableStateOf(retroSession.snapshot()) }
    DisposableEffect(retroSession) {
        retroSession.setListener { retroGame = it }
        onDispose { retroSession.setListener(null) }
    }
    val retroScreenVisible = retroGame.phase != RetroPhase.IDLE

    DisposableEffect(cloud) {
        cloud.setListener { cloudState = it }
        onDispose(cloud::close)
    }

    DisposableEffect(controllerMonitor) {
        controllerMonitor.start { controllers = it }
        onDispose(controllerMonitor::close)
    }

    DisposableEffect(engine) {
        engine.setListener { remote = it }
        onDispose { engine.setListener(null) }
    }

    LaunchedEffect(remote.phase) {
        state = state.copy(sessionPhase = remote.phase.toSessionPhase())
    }

    LaunchedEffect(remote.phase, remote.cloudTitle) {
        if (remote.cloudTitle != null && remote.phase == RemotePlayPhase.CONNECTING && state.destination == Destination.CLOUD) {
            state = state.copy(destination = Destination.SESSION)
        }
        // Leaving a cloud game (Back, the L1+R1+Options chord or the server ending it) returns to the
        // catalog; a failure stays on the session screen so its reason can be read.
        if (remote.cloudTitle != null && remote.phase == RemotePlayPhase.STOPPED && state.destination == Destination.SESSION) {
            state = state.copy(destination = Destination.CLOUD)
        }
    }

    fun dispatch(action: UiAction) {
        when (action) {
            UiAction.StartDemo -> {
                state = state.copy(destination = Destination.SESSION)
                if (remote.phase in setOf(RemotePlayPhase.READY, RemotePlayPhase.STOPPED, RemotePlayPhase.FAILED, RemotePlayPhase.WAKING)) {
                    engine.start(state.quality)
                }
            }
            UiAction.StopDemo -> engine.stop()
            else -> state = reduce(state, action)
        }
    }

    val fullscreenStream =
        state.destination == Destination.SESSION && remote.phase == RemotePlayPhase.STREAMING

    StreamDisplayMode(active = fullscreenStream || retroGame.phase == RetroPhase.PLAYING)

    BackHandler(enabled = fullscreenStream) {
        dispatch(UiAction.StopDemo)
    }

    BackHandler(enabled = !fullscreenStream && state.destination != Destination.HOME) {
        dispatch(UiAction.Navigate(Destination.HOME))
    }

    // A cloud launch (server reservation, stream connection, first picture) takes tens of seconds
    // with nothing else on screen, so a launch screen covers it until a frame has been rendered.
    var firstFrameRendered by remember { mutableStateOf(false) }
    LaunchedEffect(remote.phase, remote.cloudTitle) {
        firstFrameRendered = false
        if (remote.cloudTitle == null || remote.phase != RemotePlayPhase.STREAMING) return@LaunchedEffect
        val deadline = System.nanoTime() + FIRST_FRAME_WAIT_NANOS
        while (System.nanoTime() < deadline && (engine.metrics()?.videoFramesRendered ?: 0L) == 0L) {
            delay(200)
        }
        firstFrameRendered = true
    }
    val cloudStreamStarting =
        remote.cloudTitle != null && state.destination == Destination.SESSION &&
            (remote.phase == RemotePlayPhase.CONNECTING || (remote.phase == RemotePlayPhase.STREAMING && !firstFrameRendered))
    val cloudLaunchVisible = cloudState.provisioning || cloudStreamStarting
    fun cancelCloudLaunch() {
        if (cloudState.provisioning) {
            cloud.cancelProvisioning()
        } else {
            engine.stop()
            dispatch(UiAction.Navigate(Destination.CLOUD))
        }
    }

    BackHandler(enabled = cloudLaunchVisible, onBack = ::cancelCloudLaunch)

    // Controller shoulder buttons switch tabs (L1 left, R1 right) outside a stream; during a stream
    // MainActivity hands them to the game before they reach the UI.
    var tabFocusRequest by remember { mutableStateOf(0) }
    fun switchTab(step: Int): Boolean {
        if (fullscreenStream || cloudLaunchVisible) return false
        val tabs = Destination.entries
        val next = tabs.getOrNull(tabs.indexOf(state.destination) + step) ?: return true
        dispatch(UiAction.Navigate(next))
        tabFocusRequest++
        return true
    }

    BoxWithConstraints(
        modifier =
            Modifier.fillMaxSize().background(Ink).onPreviewKeyEvent { event ->
                when {
                    event.type != KeyEventType.KeyDown -> false
                    event.key == Key.ButtonL1 -> switchTab(-1)
                    event.key == Key.ButtonR1 -> switchTab(1)
                    else -> false
                }
            },
    ) {
        val responsive = ResponsiveLayout(compact = maxWidth < 1100.dp, short = maxHeight < 650.dp)
        CompositionLocalProvider(
            LocalContentColor provides SoftWhite,
            LocalResponsiveLayout provides responsive,
        ) {
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    state.destination,
                    firstLegalFocusRequester,
                    controllerCount = controllers.size,
                    visible = !fullscreenStream && !retroScreenVisible,
                    tabFocusRequest = tabFocusRequest,
                    onNavigate = { dispatch(UiAction.Navigate(it)) },
                )
                Box(Modifier.fillMaxSize()) {
                    key(state.destination) {
                        when (state.destination) {
                            Destination.HOME -> HomeScreen(remote, controllers, cloudState, retroState, ::dispatch)
                            Destination.CONSOLES -> ConsolesScreen(remote, retroState, retro, retroSession, ::dispatch)
                            Destination.SESSION -> SessionScreen(state, remote, engine, ::dispatch)
                            Destination.CLOUD -> CloudScreen(cloudState, cloud)
                            Destination.CONTROLLERS ->
                                ControllersScreen(controllers) {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                }
                            Destination.DIAGNOSTICS -> DiagnosticsScreen(engine)
                            Destination.SETTINGS -> SettingsScreen(state, engine, ::dispatch)
                            Destination.ABOUT -> AboutScreen(firstLegalFocusRequester)
                        }
                    }
                }
            }
            if (retroScreenVisible) {
                RetroPlayScreen(retroGame, retroSession)
            }
            if (cloudLaunchVisible) {
                val step =
                    when {
                        cloudState.provisioning -> 0
                        remote.phase == RemotePlayPhase.CONNECTING -> 1
                        else -> 2
                    }
                CloudLaunchScreen(
                    title = if (cloudState.provisioning) cloudState.launchingTitle else remote.cloudTitle.orEmpty(),
                    detail =
                        when (step) {
                            0 -> cloudState.detail
                            1 -> remote.detail
                            else -> "Réception de la première image…"
                        },
                    step = step,
                    onCancel = ::cancelCloudLaunch,
                    artUrl = cloudState.launchArtUrl,
                )
            }
        }
    }
}

/** Catalog art for [url] at [width] pixels, from the shared cache; null while loading or on failure. */
@Composable
private fun rememberCloudArt(url: String, width: Int): ImageBitmap? {
    val art by produceState(CloudArtCache.cached(url, width)?.asImageBitmap(), url, width) {
        if (value == null && url.isNotEmpty()) value = CloudArtCache.load(url, width)?.asImageBitmap()
    }
    return art
}

/**
 * Catalog art shrunk to a few dozen pixels and stretched back (a blur on every API level: Compose's
 * blur modifier needs API 31, the GP520 runs API 30); fades in and crossfades when [url] changes.
 */
@Composable
private fun BlurredArtBackground(url: String, strength: Float, modifier: Modifier = Modifier) {
    Crossfade(targetState = url, animationSpec = tween(600), label = "art-background", modifier = modifier) { current ->
        val art = rememberCloudArt(current, BLURRED_ART_WIDTH)
        val alpha by animateFloatAsState(if (art != null) strength else 0f, tween(600), label = "art-background-alpha")
        if (art != null) {
            Image(
                art,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
            )
        }
    }
}

/** Home greeting by local hour: "Bonjour." from 5 h to 17 h 59, "Bonsoir." otherwise. */
internal fun greetingFor(hour: Int): String = if (hour in 5..17) "Bonjour." else "Bonsoir."

private const val FIRST_FRAME_WAIT_NANOS = 15_000_000_000L

private val CLOUD_LAUNCH_STEPS = listOf("Réservation du serveur", "Connexion au stream", "Première image")

/**
 * Full-screen wait while a cloud game starts. It sits above the session screen, whose Surface
 * stays alive underneath so the decoder exists before the first frame arrives.
 */
@Composable
internal fun CloudLaunchScreen(title: String, detail: String, step: Int, onCancel: () -> Unit, artUrl: String = "") {
    val cancelFocus = remember { FocusRequester() }
    val startedAt = remember { System.nanoTime() }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        runCatching { cancelFocus.requestFocus() }
        while (true) {
            elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000L
            delay(500)
        }
    }
    val spin = rememberInfiniteTransition(label = "cloud-launch")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1_100, easing = LinearEasing)), label = "cloud-launch-angle")
    Box(
        Modifier.fillMaxSize().background(Ink)
            // Only Cancel is focusable here: keep the D-pad from reaching the screens underneath.
            .onPreviewKeyEvent { it.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight) }
            .testTag("cloud-launch"),
        contentAlignment = Alignment.Center,
    ) {
        // The game's catalog art, blurred under a dark veil.
        if (artUrl.isNotEmpty()) {
            BlurredArtBackground(artUrl, strength = 0.7f, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.55f)))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Canvas(Modifier.size(72.dp)) {
                drawCircle(Line, style = Stroke(6.dp.toPx()))
                drawArc(Signal, angle, 90f, useCenter = false, style = Stroke(6.dp.toPx()))
            }
            Text("LANCEMENT DU JEU CLOUD", color = Signal, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text(
                title.ifBlank { "Jeu cloud" },
                fontSize = 38.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 1100.dp).padding(horizontal = 32.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                CLOUD_LAUNCH_STEPS.forEachIndexed { index, label ->
                    Text(
                        (if (index < step) "✓ " else "${index + 1}. ") + label,
                        color = when {
                            index == step -> SoftWhite
                            index < step -> Go
                            else -> Muted
                        },
                        fontSize = 14.sp,
                        fontWeight = if (index == step) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            Text(detail, color = Chalk, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(720.dp))
            Text("Patientez : $elapsedSeconds s", color = Muted, fontSize = 13.sp)
            FocusButton("Annuler", modifier = Modifier.focusRequester(cancelFocus).testTag("cloud-launch-cancel"), onClick = onCancel)
        }
    }
}

/** Horizontal arcade bar: wordmark, one tab per destination, live pad count. Hidden while streaming. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TopBar(
    selected: Destination,
    firstLegalFocusRequester: FocusRequester,
    controllerCount: Int,
    visible: Boolean = true,
    /** Incremented to put focus on the selected tab (after an L1/R1 tab switch). */
    tabFocusRequest: Int = 0,
    onNavigate: (Destination) -> Unit,
) {
    val responsive = LocalResponsiveLayout.current
    val homeFocusRequester = remember { FocusRequester() }
    val tabRequesters = remember { Destination.entries.associateWith { FocusRequester() } }
    // Kept in composition while streaming so focus is not reset to Home when the bar comes back.
    LaunchedEffect(homeFocusRequester) {
        homeFocusRequester.requestFocus()
    }
    LaunchedEffect(tabFocusRequest) {
        if (tabFocusRequest > 0 && visible) runCatching { tabRequesters.getValue(selected).requestFocus() }
    }
    if (!visible) return
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = if (responsive.compact) 24.dp else 44.dp,
                vertical = if (responsive.short) 8.dp else 14.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The orange G is a quiet nod to Gaby; never spelled out in the UI (owner request).
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Signal)) { append("G") }
                    append("PSx")
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Box(Modifier.padding(start = 3.dp, bottom = 10.dp).size(7.dp).background(Signal))
            Spacer(Modifier.width(if (responsive.compact) 18.dp else 36.dp))
            // Coming up from a screen lands on the active tab, not on whichever tab is nearest.
            Row(
                Modifier.weight(1f).focusProperties { enter = { tabRequesters.getValue(selected) } }.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Destination.entries.forEach { destination ->
                    NavItem(
                        destination = destination,
                        selected = destination == selected,
                        onClick = { onNavigate(destination) },
                        modifier =
                            when (destination) {
                                Destination.HOME -> Modifier.focusRequester(homeFocusRequester)
                                Destination.ABOUT -> Modifier.focusProperties { down = firstLegalFocusRequester }
                                else -> Modifier
                            }.focusRequester(tabRequesters.getValue(destination)),
                    )
                }
            }
            Text(
                if (controllerCount == 0) "○ aucune manette" else "● $controllerCount manette${if (controllerCount > 1) "s" else ""}",
                color = if (controllerCount == 0) Muted else SoftWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

@Composable
private fun NavItem(destination: Destination, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .onFocusChanged { focused = it.isFocused }
                .semantics { this.selected = selected }
                .clickable(role = Role.Tab, onClick = onClick)
                .background(if (focused) Signal else Color.Transparent)
                .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(destination.labelRes),
            color = when {
                focused -> Ink
                selected -> SoftWhite
                else -> Muted
            },
            fontSize = 13.sp,
            fontWeight = if (selected || focused) FontWeight.Black else FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.height(3.dp).width(if (selected) 18.dp else 0.dp).background(if (focused) Ink else Signal))
    }
}

/** Screen header: one big tight title, a short signal rule and a single muted line. */
@Composable
internal fun ScreenFrame(
    @Suppress("UNUSED_PARAMETER") eyebrow: String,
    title: String,
    subtitle: String,
    fullscreen: Boolean = false,
    content: @Composable () -> Unit,
) {
    val responsive = LocalResponsiveLayout.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = if (fullscreen) 0.dp else if (responsive.compact) 24.dp else 44.dp,
                end = if (fullscreen) 0.dp else if (responsive.compact) 24.dp else 44.dp,
                top = if (fullscreen) 0.dp else if (responsive.short) 14.dp else 28.dp,
                bottom = if (fullscreen) 0.dp else if (responsive.short) 18.dp else 34.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (responsive.short) 12.dp else 18.dp),
    ) {
        item {
            if (!fullscreen) {
                Text(
                    title,
                    fontSize = if (responsive.compact) 38.sp else 50.sp,
                    lineHeight = if (responsive.compact) 40.sp else 52.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.5).sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(28.dp).height(3.dp).background(Signal))
                    Spacer(Modifier.width(12.dp))
                    Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 2)
                }
            }
        }
        item { content() }
    }
}

@Composable
private fun HomeScreen(
    remote: RemotePlaySnapshot,
    controllers: List<ConnectedGameController>,
    cloudState: CloudSnapshot,
    retroState: RetroSnapshot,
    dispatch: (UiAction) -> Unit,
) {
    val responsive = LocalResponsiveLayout.current
    ScreenFrame(
        eyebrow = "",
        title = greetingFor(remember { java.time.LocalTime.now().hour }),
        subtitle = "PS5 en Remote Play, jeux cloud PlayStation Plus et consoles rétro de votre NAS.",
    ) {
        val tileHeight = if (responsive.short) 92.dp else 118.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlayTile(
                console = remote.consoleName ?: "PS5",
                status = remote.phase.homeLabel(),
                statusColor = remote.phase.homeColor(),
                modifier = Modifier.weight(1.6f).height(tileHeight * 3 + 24.dp),
            ) { dispatch(UiAction.StartDemo) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ArcadeTile(
                    title = "Cloud",
                    detail =
                        when {
                            !cloudState.signedIn -> "Connecter le compte PSN"
                            cloudState.games.isEmpty() -> "Catalogue PlayStation Plus"
                            else -> "${cloudState.games.count(CloudGame::playable)} jeux jouables"
                        },
                    modifier = Modifier.fillMaxWidth().height(tileHeight),
                ) { dispatch(UiAction.Navigate(Destination.CLOUD)) }
                ArcadeTile(
                    title = "Rétro",
                    detail =
                        when {
                            retroState.signInRequired -> "Connecter le NAS"
                            retroState.shelves.isEmpty() && retroState.scanning -> "Lecture du NAS…"
                            retroState.shelves.isEmpty() -> "Consoles rétro du NAS"
                            else -> "${retroState.shelves.size} consoles · ${retroState.shelves.sumOf { it.games.size }} jeux"
                        },
                    modifier = Modifier.fillMaxWidth().height(tileHeight),
                ) { dispatch(UiAction.Navigate(Destination.CONSOLES)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ArcadeTile(
                        title = "Manettes",
                        detail =
                            when (controllers.size) {
                                0 -> "Aucune connectée"
                                1 -> controllers.first().name
                                else -> "${controllers.size} connectées"
                            },
                        modifier = Modifier.weight(1f).height(tileHeight),
                        small = true,
                    ) { dispatch(UiAction.Navigate(Destination.CONTROLLERS)) }
                    ArcadeTile(
                        title = "Réglages",
                        detail = "Image, son, mises à jour",
                        modifier = Modifier.weight(1f).height(tileHeight),
                        small = true,
                    ) { dispatch(UiAction.Navigate(Destination.SETTINGS)) }
                }
            }
        }
    }
}

/** The one big call to action: fills with the signal colour when focused. */
@Composable
private fun PlayTile(console: String, status: String, statusColor: Color, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val ink = if (focused) Ink else SoftWhite
    Column(
        modifier
            .background(if (focused) Signal else Panel)
            .then(if (focused) Modifier else Modifier.border(1.dp, Line))
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(28.dp),
    ) {
        Text("▶", color = if (focused) Ink else Signal, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        Text("Jouer", color = ink, fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black, letterSpacing = (-2.5).sp)
        Spacer(Modifier.height(6.dp))
        Text(console, color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(if (focused) Ink else statusColor))
            Spacer(Modifier.width(8.dp))
            Text(status, color = if (focused) Ink else Chalk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ArcadeTile(title: String, detail: String, modifier: Modifier, small: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .background(if (focused) PanelRaised else Panel)
            .border(if (focused) 3.dp else 1.dp, if (focused) Signal else Line)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = if (small) 20.sp else 26.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp, maxLines = 1)
            Text(detail, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!small) Text("→", color = if (focused) Signal else Line, fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

private fun RemotePlayPhase.homeLabel(): String =
    when (this) {
        RemotePlayPhase.NEEDS_REGISTRATION -> "à associer"
        RemotePlayPhase.REGISTERING -> "association…"
        RemotePlayPhase.READY -> "prête"
        RemotePlayPhase.CONNECTING -> "connexion…"
        RemotePlayPhase.STREAMING -> "en jeu"
        RemotePlayPhase.LOGIN_PIN_REQUIRED -> "PIN demandé"
        RemotePlayPhase.WAKING -> "réveil…"
        RemotePlayPhase.STOPPING -> "arrêt…"
        RemotePlayPhase.STOPPED -> "prête"
        RemotePlayPhase.FAILED -> "échec, réessayer"
    }

private fun RemotePlayPhase.homeColor(): Color =
    when (this) {
        RemotePlayPhase.READY, RemotePlayPhase.STOPPED, RemotePlayPhase.STREAMING -> Go
        RemotePlayPhase.FAILED -> Danger
        RemotePlayPhase.NEEDS_REGISTRATION -> Muted
        else -> Amber
    }

@Composable
private fun ConsolesScreen(
    remote: RemotePlaySnapshot,
    retroState: RetroSnapshot,
    retro: RetroLibrary,
    retroSession: RetroSession,
    dispatch: (UiAction) -> Unit,
) {
    // A retro console opens its game list in place; Back returns to the consoles with focus on it.
    var openFolder by remember { mutableStateOf<String?>(null) }
    var lastFolder by remember { mutableStateOf<String?>(null) }
    val shelf = retroState.shelves.firstOrNull { it.folder == openFolder }
    if (shelf != null) {
        RetroGamesScreen(
            shelf,
            retro,
            retroState,
            onPlay = { retroSession.play(it, retro) },
        ) { openFolder = null }
        return
    }
    ScreenFrame("", "Vos consoles", "La PS5 associée à GPSx et les consoles rétro de votre NAS.") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val name = remote.consoleName
            ConsoleCard(
                name ?: "Aucune PS5 associée",
                remote.discoveredHost?.let { "Réseau local · $it" } ?: "Associer depuis Session",
                remote.phase.homeLabel().uppercase(),
                remote.phase.homeColor(),
                Modifier.fillMaxWidth(),
            ) {
                dispatch(if (name == null) UiAction.Navigate(Destination.SESSION) else UiAction.StartDemo)
            }
            RetroConsolesPanel(retroState, retro, focusFolder = lastFolder) {
                lastFolder = it.folder
                openFolder = it.folder
            }
        }
    }
}

@Composable
private fun SessionScreen(
    state: AppUiState,
    remote: RemotePlaySnapshot,
    engine: RemotePlayEngine,
    dispatch: (UiAction) -> Unit,
) {
    val responsive = LocalResponsiveLayout.current
    val fullscreen = remote.phase == RemotePlayPhase.STREAMING
    var host by remember { mutableStateOf("") }
    var registrationPin by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf("") }
    var loginPin by remember { mutableStateOf("") }
    var showFullscreenHint by remember { mutableStateOf(false) }
    val discoveryFocus = remember { FocusRequester() }
    val hostFocus = remember { FocusRequester() }
    val registrationPinFocus = remember { FocusRequester() }
    val accountIdFocus = remember { FocusRequester() }
    val registerFocus = remember { FocusRequester() }

    LaunchedEffect(remote.discoveredHost) {
        remote.discoveredHost?.let { host = it }
    }

    LaunchedEffect(fullscreen) {
        showFullscreenHint = fullscreen
        if (fullscreen) {
            delay(10_000)
            showFullscreenHint = false
        }
    }

    BoxWithConstraints {
        val streamHeight = maxHeight
        ScreenFrame("Lecture", "Session PS5", remote.detail, fullscreen = fullscreen) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(if (fullscreen) streamHeight else if (responsive.short) 300.dp else if (responsive.compact) 360.dp else 430.dp)
                        .clip(if (fullscreen) RectangleShape else PanelShape)
                        .background(Color.Black),
            ) {
                RemotePlaySurface(
                    engine,
                    Modifier.fillMaxSize().testTag(if (fullscreen) "fullscreen-stream" else "session-surface"),
                )
                if (remote.phase != RemotePlayPhase.STREAMING) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (remote.phase == RemotePlayPhase.CONNECTING) "◌" else "GPSx", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(remote.detail, color = Muted, fontSize = 14.sp)
                    }
                }
                // In a game the title leaves with the Back hint after 10 s, so nothing stays over the picture.
                if (!fullscreen || showFullscreenHint) {
                    StatusPill(
                        if (fullscreen) remote.cloudTitle ?: remote.consoleName ?: "PS5" else remote.phase.label(),
                        remote.phase.accent(),
                        Modifier.align(Alignment.TopStart).padding(18.dp),
                    )
                }
                if (fullscreen && state.metricsVisible) {
                    StreamMetricsOverlay(engine, Modifier.align(Alignment.TopEnd).padding(18.dp))
                }
                if (fullscreen && showFullscreenHint) {
                    Text(
                        "Quitter : Retour, ou L1 + R1 + Options maintenus 1 s",
                        color = SoftWhite,
                        fontSize = 11.sp,
                        modifier =
                            Modifier.align(Alignment.BottomStart).padding(18.dp)
                                .clip(RoundedCornerShape(3.dp)).background(Color(0xD90C0C0D))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                } else if (!fullscreen) {
                    SessionActions(state, remote, engine, dispatch, vertical = responsive.compact, Modifier.align(Alignment.BottomStart).padding(18.dp))
                }
            }

            // Never below a running or cloud stream: focus would land on these fields when the launch
            // screen closes and scroll the picture away.
            if (remote.consoleName == null && !fullscreen && !(remote.cloudTitle != null && remote.phase in BUSY_PHASES)) {
                SectionTitle(
                    "Enregistrer GPSx sur la PS5",
                    "Association chiffrée dans le stockage privé Android ; aucun secret n’est journalisé",
                )
                RegistrationGuide()
                if (responsive.compact) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusButton(
                        if (remote.discoveryRunning) "Recherche en cours…" else "Rechercher ma PS5",
                        primary = true,
                        modifier = Modifier.focusRequester(discoveryFocus).focusProperties { down = hostFocus },
                    ) { engine.discoverPs5() }
                    Text(remote.discoveryDetail, color = Muted, fontSize = 11.sp)
                } else Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FocusButton(
                        if (remote.discoveryRunning) "Recherche en cours…" else "Rechercher ma PS5",
                        primary = true,
                        modifier = Modifier.focusRequester(discoveryFocus).focusProperties { down = hostFocus },
                    ) { engine.discoverPs5() }
                    Text(remote.discoveryDetail, color = Muted, fontSize = 11.sp)
                }
                if (responsive.compact) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledInput("Adresse PS5", host, { host = it }, Modifier.fillMaxWidth(), hostFocus, discoveryFocus, registrationPinFocus, "registration-host")
                    LabeledInput("PIN à 8 chiffres", registrationPin, { registrationPin = it.filter(Char::isDigit).take(8) }, Modifier.fillMaxWidth(), registrationPinFocus, hostFocus, accountIdFocus, "registration-pin")
                    LabeledInput("PSN Account ID (Base64 ou hex)", accountId, { accountId = it }, Modifier.fillMaxWidth(), accountIdFocus, registrationPinFocus, registerFocus, "registration-account-id")
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledInput("Adresse PS5", host, { host = it }, Modifier.weight(1f), hostFocus, discoveryFocus, registrationPinFocus, "registration-host")
                    LabeledInput("PIN à 8 chiffres", registrationPin, { registrationPin = it.filter(Char::isDigit).take(8) }, Modifier.weight(1f), registrationPinFocus, hostFocus, accountIdFocus, "registration-pin")
                    LabeledInput("PSN Account ID (Base64 ou hex)", accountId, { accountId = it }, Modifier.weight(1.4f), accountIdFocus, registrationPinFocus, registerFocus, "registration-account-id")
                }
                FocusButton(
                    "Enregistrer",
                    primary = true,
                    modifier = Modifier.focusRequester(registerFocus).focusProperties { up = accountIdFocus }.testTag("registration-submit"),
                ) { engine.register(host, registrationPin, accountId) }
            }

            if (remote.phase == RemotePlayPhase.LOGIN_PIN_REQUIRED) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    LabeledInput("PIN utilisateur PS5", loginPin, { loginPin = it.filter(Char::isDigit) }, Modifier.weight(1f))
                    FocusButton("Envoyer le PIN", primary = true) { engine.submitLoginPin(loginPin) }
                }
            }

            }
        }
    }
}

/**
 * While streaming: keep the screen on and ask the display for its game mode
 * (minimal post-processing, API 30+), which removes TV picture processing latency.
 */
@Composable
private fun StreamDisplayMode(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        val window = (view.context as? Activity)?.window
        fun apply(enabled: Boolean) {
            view.keepScreenOn = enabled
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes = window.attributes.apply { preferMinimalPostProcessing = enabled }
            }
        }
        apply(active)
        onDispose { apply(false) }
    }
}

/** Real native measurements, polled once per second while the stream is visible. */
@Composable
private fun StreamMetricsOverlay(engine: RemotePlayEngine, modifier: Modifier) {
    var metrics by remember { mutableStateOf<StreamMetrics?>(null) }
    LaunchedEffect(engine) {
        while (true) {
            metrics = engine.metrics()
            delay(1_000)
        }
    }
    val current = metrics ?: return
    Text(
        String.format(
            java.util.Locale.FRANCE,
            "%dp %s · %.1f fps · affichées %d · décodage %.0f ms · %.1f Mb/s · RTT %.0f ms · pertes %.1f %% · images perdues %d · son %s",
            current.height,
            com.gpsx.tv.diagnostics.decodedCodecLabel(current.videoDecodedCodec),
            current.fps,
            current.videoFramesRendered,
            current.videoDecodeLatencyMs,
            current.bitrateMbps,
            current.rttMs,
            current.packetLoss * 100,
            current.framesLost,
            audioLabel(current, engine.audioRouteLabel(current.audioDeviceId)),
        ),
        color = SoftWhite,
        fontSize = 11.sp,
        modifier =
            modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xD90C0C0D))
                .padding(horizontal = 12.dp, vertical = 8.dp).testTag("stream-metrics"),
    )
}

private fun audioLabel(metrics: StreamMetrics, route: String): String {
    val api = when (metrics.audioState) {
        1 -> "AAudio"
        3 -> "OpenSL ES"
        2 -> return "ÉCHEC"
        else -> return "en attente"
    }
    val playing = if (metrics.audioCallbacks > 0) "OK" else "muet"
    val reopened = if (metrics.audioReopens > 0) ", ${metrics.audioReopens} reprise(s)" else ""
    return "$playing ($api → $route$reopened)"
}

/**
 * Session buttons. The set of buttons changes with the phase; when the user acted
 * from here, focus is moved back to the first button so the D-pad never falls
 * back to the navigation rail.
 */
@Composable
private fun SessionActions(
    state: AppUiState,
    remote: RemotePlaySnapshot,
    engine: RemotePlayEngine,
    dispatch: (UiAction) -> Unit,
    vertical: Boolean,
    modifier: Modifier,
) {
    val firstAction = remember { FocusRequester() }
    var keepFocus by remember { mutableStateOf(false) }
    fun act(block: () -> Unit) {
        keepFocus = true
        block()
    }
    LaunchedEffect(remote.phase) {
        if (keepFocus) runCatching { firstAction.requestFocus() }
    }
    val waking = remote.phase == RemotePlayPhase.WAKING
    val canStart = remote.consoleName != null && remote.phase !in BUSY_PHASES
    val canStop = remote.phase in setOf(RemotePlayPhase.CONNECTING, RemotePlayPhase.STREAMING, RemotePlayPhase.LOGIN_PIN_REQUIRED)
    val buttons: @Composable () -> Unit = {
        if (canStart || waking) {
            // Start stays available while waking: it is queued behind the wake-up and connects once ready.
            FocusButton("Démarrer le stream", primary = true, modifier = Modifier.focusRequester(firstAction)) {
                act { dispatch(UiAction.StartDemo) }
            }
            FocusButton(if (waking) "Réveil en cours…" else "Réveiller la PS5", modifier = Modifier.testTag("wake-console")) {
                if (!waking) act(engine::wakeConsole)
            }
        }
        if (canStop) {
            FocusButton("Déconnecter", primary = true, modifier = if (canStart || waking) Modifier else Modifier.focusRequester(firstAction)) {
                act { dispatch(UiAction.StopDemo) }
            }
        }
        FocusButton(
            if (state.metricsVisible) "Masquer les stats" else "Afficher les stats",
            modifier = if (canStart || waking || canStop) Modifier else Modifier.focusRequester(firstAction),
        ) { dispatch(UiAction.ToggleMetrics) }
    }
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
    }
}

@Composable
private fun RemotePlaySurface(engine: RemotePlayEngine, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val surfaceView = remember(context) { SurfaceView(context).apply { isFocusable = false } }
    DisposableEffect(surfaceView, engine) {
        val controller = RemotePlaySurfaceController(surfaceView, engine)
        onDispose(controller::close)
    }
    AndroidView(factory = { surfaceView }, modifier = modifier)
}

@Composable
private fun RegistrationGuide() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
            .background(Panel).padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Où trouver les informations", color = Signal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            "1. Sur la PS5 : Paramètres → Système → Lecture à distance → Associer l’appareil. " +
                "Recopiez ici le code à 8 chiffres avant son expiration.",
            color = SoftWhite,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Text(
            "2. Le PSN Account ID est l’identifiant numérique du compte (8 octets, Base64 ou 16 caractères hexadécimaux). " +
                "Ce n’est ni votre pseudo PSN ni votre adresse e-mail. GPSx ne sait pas encore le récupérer automatiquement : " +
                "copiez-le depuis un profil Remote Play/Chiaki déjà enregistré et fiable.",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Text(
            "Ne saisissez jamais ici votre mot de passe, un cookie, un jeton PSN ou une URL de redirection.",
            color = Amber,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun LabeledInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    previousFocusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    testTag: String? = null,
    secret: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(3.dp)
    Column(modifier) {
        Text(label, color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = SoftWhite),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier =
                Modifier.fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .then(
                        if (previousFocusRequester != null || nextFocusRequester != null) {
                            Modifier.focusProperties {
                                previousFocusRequester?.let { up = it }
                                nextFocusRequester?.let { down = it }
                            }
                        } else {
                            Modifier
                        },
                    )
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> previousFocusRequester?.requestFocus() ?: false
                            Key.DirectionDown -> nextFocusRequester?.requestFocus() ?: false
                            else -> false
                        }
                    }
                    .onFocusChanged { focused = it.isFocused }
                    .clip(shape)
                    .background(Panel)
                    .border(if (focused) 3.dp else 1.dp, if (focused) Signal else PanelRaised, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ControllersScreen(
    controllers: List<ConnectedGameController>,
    onOpenBluetooth: () -> Unit,
) {
    val responsive = LocalResponsiveLayout.current
    ScreenFrame(
        "Entrées",
        stringResource(R.string.controller_screen_title),
        stringResource(R.string.controller_screen_subtitle),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (responsive.compact) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (controllers.isEmpty()) "Aucune manette détectée" else "${controllers.size} manette(s) détectée(s)",
                    color = Muted,
                    fontSize = 12.sp,
                )
                FocusButton("Ouvrir les réglages Bluetooth", primary = true, onClick = onOpenBluetooth)
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (controllers.isEmpty()) "Aucune manette détectée" else "${controllers.size} manette(s) détectée(s)",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                FocusButton("Ouvrir les réglages Bluetooth", primary = true, onClick = onOpenBluetooth)
            }
            if (responsive.compact) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { index ->
                    val controller = controllers.getOrNull(index)
                    ControllerSlot(
                        number = (index + 1).toString(),
                        name = controller?.name ?: if (index == 0) stringResource(R.string.controller_a) else stringResource(R.string.controller_b),
                        status = if (controller == null) stringResource(R.string.controller_waiting) else "CONNECTÉE",
                        detail = controller?.let { "${it.kind.label} · VID %04X · PID %04X".format(it.vendorId, it.productId) }
                            ?: stringResource(R.string.controller_merge_note),
                        accent = if (index == 0) Signal else Chalk,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(2) { index ->
                    val controller = controllers.getOrNull(index)
                    ControllerSlot(
                        number = (index + 1).toString(),
                        name = controller?.name ?: if (index == 0) stringResource(R.string.controller_a) else stringResource(R.string.controller_b),
                        status = if (controller == null) stringResource(R.string.controller_waiting) else "CONNECTÉE",
                        detail =
                            controller?.let {
                                "${it.kind.label} · VID %04X · PID %04X".format(it.vendorId, it.productId)
                            } ?: stringResource(R.string.controller_merge_note),
                        accent = if (index == 0) Signal else Chalk,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            MiniPanel("Entrées actives", "DualSense, Xbox et manettes Android standard : touches, HAT, axes et gâchettes analogiques", "ANDROID", Modifier.fillMaxWidth())
            SectionTitle("Capacités prévues", "Chaque capacité restera NON TESTÉE jusqu’à validation appropriée")
            CapabilityGrid()
        }
    }
}

@Composable
private fun ControllerSlot(
    number: String,
    name: String,
    status: String,
    detail: String,
    accent: Color,
    modifier: Modifier,
) {
    Column(modifier.background(Panel).border(1.dp, Line).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(accent), contentAlignment = Alignment.Center) {
                Text(number, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.6).sp)
            }
            StatusPill(status, accent)
        }
        Spacer(Modifier.height(16.dp))
        Text(detail, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun CapabilityGrid() {
    val responsive = LocalResponsiveLayout.current
    val capabilities =
        listOf(
            "Mapping complet" to "Standard",
            "Gâchettes analogiques" to "Prioritaire",
            "Pavé tactile" to "Prévu",
            "Gyroscope" to "Prévu",
            "Haptique" to "Avancé",
            "Gâchettes adaptatives" to "Avancé",
            "Remappage" to "Prévu",
            "Multi-manette" to "Architecture prête",
        )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        capabilities.chunked(if (responsive.compact) 2 else 4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (name, status) -> MiniPanel(name, status, "○", Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen(engine: RemotePlayEngine) {
    ScreenFrame("", stringResource(R.string.diagnostic_title), "Rapports de vos sessions, à envoyer depuis le téléphone.") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LiveLogPanel()
            TestResultsPanel(engine)
        }
    }
}

/** Starts or stops the live log served on the local network for remote debugging. */
@Composable
private fun LiveLogPanel() {
    var address by remember { mutableStateOf(LiveLogServer.Current.address) }
    var status by remember { mutableStateOf("") }
    SectionTitle("Journal en direct", "Pour le dépannage à distance sur votre réseau local · secrets retirés · arrêt auto après 30 min")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (address == null) {
            FocusButton("Démarrer le journal en direct", modifier = Modifier.testTag("live-log-start")) {
                address = LiveLogServer.Current.start(LocalTokenServer.lanAddress())
                status = if (address == null) "Aucun réseau local ou port indisponible." else ""
            }
        } else {
            FocusButton("Arrêter", modifier = Modifier.testTag("live-log-stop")) {
                LiveLogServer.Current.stop()
                address = null
            }
            Text("Adresse : http://$address", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Signal)
        }
    }
    if (status.isNotEmpty()) Text(status, color = Amber, fontSize = 11.sp)
}

@Composable
private fun UpdatePanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember(context) { AppUpdater(context) }
    var status by remember { mutableStateOf("Version installée : ${BuildConfig.VERSION_NAME}") }
    var available by remember { mutableStateOf<ReleaseManifest?>(null) }
    var busy by remember { mutableStateOf(false) }
    SectionTitle("Mettre à jour GPSx", "Depuis les releases GitHub · APK vérifié · confirmation Android requise")
    Text(status, color = Muted, fontSize = 12.sp, modifier = Modifier.testTag("update-status"))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FocusButton(if (busy) "Recherche…" else "Rechercher une mise à jour", primary = available == null, modifier = Modifier.testTag("update-check")) {
            if (busy) return@FocusButton
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { updater.check() }
                busy = false
                when (result) {
                    is UpdateCheck.Available -> {
                        available = result.manifest
                        status = "GPSx ${result.manifest.versionName} est disponible."
                    }
                    UpdateCheck.UpToDate -> {
                        available = null
                        status = "GPSx ${BuildConfig.VERSION_NAME} est à jour."
                    }
                    is UpdateCheck.Failed -> status = result.message
                }
            }
        }
        available?.let { manifest ->
            FocusButton("Installer ${manifest.versionName}", primary = true) {
                if (busy) return@FocusButton
                busy = true
                scope.launch {
                    withContext(Dispatchers.IO) {
                        updater.downloadAndInstall(manifest) { message -> scope.launch { status = message } }
                    }
                    busy = false
                }
            }
        }
    }
}

@Composable
private fun TestResultsPanel(engine: RemotePlayEngine) {
    val recorder = engine.testRecorder ?: return
    var reports by remember { mutableStateOf(emptyList<StoredReport>()) }
    var shown by remember { mutableStateOf<Pair<StoredReport, String?>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(recorder) {
        reports = withContext(Dispatchers.IO) { recorder.list() }
    }
    SectionTitle(
        "Résultats de tests",
        "Chaque session stream enregistre un rapport. Choisissez-en un, scannez le QR code avec le téléphone " +
            "connecté à GitHub et appuyez sur « Submit » : le rapport arrive en ticket dans xalacaga/GPSx-diagnostics.",
    )
    Text("Version installée : GPSx ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp)
    if (reports.isEmpty()) {
        Text("Aucun rapport : lancez un stream d’au moins quelques secondes.", color = Muted, fontSize = 12.sp)
    } else {
        // Two presses: the first only arms the deletion, so a stray OK never wipes the reports.
        FocusButton(
            if (confirmDelete) "Confirmer : effacer les ${reports.size} rapports" else "Effacer tous les rapports",
            modifier = Modifier.testTag("reports-delete-all"),
        ) {
            if (!confirmDelete) {
                confirmDelete = true
            } else {
                confirmDelete = false
                shown = null
                scope.launch {
                    reports =
                        withContext(Dispatchers.IO) {
                            recorder.deleteAll()
                            recorder.list()
                        }
                }
            }
        }
    }
    shown?.let { (report, url) ->
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (url != null) {
                Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Color.White).padding(8.dp)) {
                    QrCode(url, 300.dp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(report.name.removeSuffix(".json"), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (url != null) {
                        "1. Scannez avec le téléphone (connecté à GitHub).\n2. Appuyez sur « Submit new issue »."
                    } else {
                        "Rapport trop volumineux pour un QR code."
                    },
                    color = SoftWhite,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                FocusButton("Fermer le QR code") { shown = null }
            }
        }
    }
    reports.take(8).forEach { report ->
        FocusButton(
            String.format(
                java.util.Locale.FRANCE,
                "%s · %s · %.0f s · %s fps · RTT p95 %s ms",
                report.name.removeSuffix(".json"),
                report.kind,
                report.durationSeconds,
                report.fpsP50?.let { "%.1f".format(java.util.Locale.FRANCE, it) } ?: "–",
                report.rttP95?.let { "%.0f".format(java.util.Locale.FRANCE, it) } ?: "–",
            ),
            modifier = Modifier.testTag("report-${report.name}"),
        ) {
            val json = recorder.load(report)
            shown = report to json?.let { reportUploadUrl(it, report.name) }
        }
    }
}

@Composable
private fun SettingsScreen(state: AppUiState, engine: RemotePlayEngine, dispatch: (UiAction) -> Unit) {
    var audioCompatibility by remember { mutableStateOf(engine.audioCompatibility) }
    var decoderLowLatency by remember { mutableStateOf(engine.decoderLowLatency) }
    ScreenFrame("", stringResource(R.string.settings_title), "Appliqués à la session suivante.") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingRow("Qualité vidéo", "Auto, 720p60 ou 1080p60", state.quality.label) { dispatch(UiAction.CycleQuality) }
            SettingRow(
                "Mode faible latence",
                "Options faible latence du décodeur vidéo · à couper si l’image se fige (session suivante)",
                yesNo(decoderLowLatency),
            ) {
                decoderLowLatency = !decoderLowLatency
                engine.decoderLowLatency = decoderLowLatency
            }
            SettingRow(
                "Sortie audio",
                "Auto : AAudio, OpenSL ES dès qu’une sortie Bluetooth est active · Compatibilité : OpenSL ES toujours",
                if (audioCompatibility) "COMPATIBILITÉ" else "AUTO",
            ) {
                audioCompatibility = !audioCompatibility
                engine.audioCompatibility = audioCompatibility
            }
            RetroStorageRows()
            UpdatePanel()
        }
    }
}

@Composable
private fun AboutScreen(firstLegalFocusRequester: FocusRequester) {
    ScreenFrame(
        stringResource(R.string.about_eyebrow),
        stringResource(R.string.about_title),
        stringResource(R.string.about_subtitle),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Owner's own words; keep them verbatim.
            Text("Gaby's game", color = Signal, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp)
            LegalPanel(
                stringResource(R.string.legal_copyright),
                stringResource(R.string.legal_license),
                "AGPL-3.0-only",
                Modifier.focusRequester(firstLegalFocusRequester),
            )
            LegalPanel(
                stringResource(R.string.legal_no_warranty),
                stringResource(R.string.legal_redistribution),
                "NOTICE",
            )
            LegalPanel(
                stringResource(R.string.legal_source),
                BuildConfig.SOURCE_REPOSITORY_URL,
                "CODE SOURCE",
            )
            LegalPanel(
                stringResource(R.string.legal_build),
                BuildConfig.SOURCE_REVISION,
                "RÉVISION",
            )
        }
    }
}

@Composable
internal fun FocusButton(
    label: String,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Signal
        primary -> SoftWhite
        else -> Color.Transparent
    }
    val foreground = if (focused || primary) Ink else SoftWhite
    Box(
        modifier =
            modifier.background(background)
                .then(if (!focused && !primary) Modifier.border(2.dp, Line) else Modifier)
                .onFocusChanged { focused = it.isFocused }.clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}




@Composable
private fun ConsoleCard(title: String, subtitle: String, status: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.clip(PanelShape).background(if (focused) PanelRaised else Panel)
            .border(if (focused) 3.dp else 1.dp, if (focused) Signal else Line)
            .onFocusChanged { focused = it.isFocused }.clickable(role = Role.Button, onClick = onClick).padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(112.dp).height(132.dp).background(Brush.radialGradient(listOf(PanelRaised, Ink))).border(1.dp, Line),
            contentAlignment = Alignment.Center,
        ) {
            StandingConsoleArt(Modifier.width(64.dp).height(108.dp), glow = if (focused) Signal else accent)
        }
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        StatusPill(status, accent)
    }
}

@Composable
private fun MiniPanel(title: String, subtitle: String, badge: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(3.dp)).background(Panel).padding(17.dp)) {
        Text(badge, color = Signal, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun LegalPanel(title: String, subtitle: String, badge: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
            .background(if (focused) PanelRaised else Panel)
            .border(if (focused) 3.dp else 1.dp, if (focused) Signal else Line)
            .onFocusChanged { focused = it.isFocused }.semantics(mergeDescendants = true) {}
            .focusable().padding(17.dp),
    ) {
        Text(badge, color = Signal, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
    }
}


@Composable
internal fun SettingRow(title: String, detail: String, value: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .background(if (focused) PanelRaised else Panel)
            .border(if (focused) 3.dp else 1.dp, if (focused) Signal else Line)
            .onFocusChanged { focused = it.isFocused }.clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(detail, color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(16.dp))
        Text(value, color = if (focused) Signal else Chalk, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}


@Composable
internal fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(color).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .6.sp)
    }
}

@Composable
internal fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Signal))
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.6).sp)
        }
        Text(subtitle, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 18.dp))
    }
}

@Composable
private fun CloudScreen(cloudState: CloudSnapshot, cloud: CloudPlayController) {
    val responsive = LocalResponsiveLayout.current
    var focusedGame by remember { mutableStateOf<CloudGame?>(null) }
    var backgroundArt by remember { mutableStateOf("") }
    // Settle before changing the background, so sweeping across cards does not fetch every image.
    LaunchedEffect(focusedGame) {
        delay(350)
        focusedGame?.artUrl?.takeIf(String::isNotEmpty)?.let { backgroundArt = it }
    }
    Box(Modifier.fillMaxSize()) {
        if (cloudState.signedIn && backgroundArt.isNotEmpty()) {
            BlurredArtBackground(backgroundArt, strength = 0.5f, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.6f), Ink.copy(alpha = 0.9f)))))
        }
        ScreenFrame("Jeu cloud", "PlayStation Plus", cloudState.detail) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (cloudState.signedIn) {
                    CloudCatalogPanel(cloudState, cloud, columns = if (responsive.compact) 2 else 3) { focusedGame = it }
                } else {
                    PsnSignInPanel(cloud)
                }
            }
        }
    }
}

@Composable
private fun PsnSignInPanel(cloud: CloudPlayController) {
    val scope = rememberCoroutineScope()
    var localUrl by remember { mutableStateOf<String?>(null) }
    var localServer by remember { mutableStateOf<LocalTokenServer?>(null) }
    var localStatus by remember { mutableStateOf("") }
    var relay by remember { mutableStateOf<PsnRelayLogin?>(null) }
    var relayStatus by remember { mutableStateOf("") }
    var manualToken by remember { mutableStateOf("") }
    val localCancelFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val signInFocus = remember { FocusRequester() }
    var touched by remember { mutableStateOf(false) }
    // Leaving the screen stops the local server.
    DisposableEffect(localServer) {
        val server = localServer
        onDispose { server?.close() }
    }
    // Keep D-pad focus inside the panel when the sign-in buttons and a QR code swap places.
    LaunchedEffect(localUrl, relay) {
        if (!touched) return@LaunchedEffect
        runCatching {
            when {
                localUrl != null -> localCancelFocus.requestFocus()
                relay != null -> cancelFocus.requestFocus()
                else -> signInFocus.requestFocus()
            }
        }
    }
    val currentLocal = localUrl
    val current = relay
    // While a QR code is shown, only the QR and its steps stay on screen so nothing scrolls away.
    if (currentLocal == null && current == null) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Panel).padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Connexion PlayStation Network", color = Signal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                "PlayStation Plus Premium requis. Téléphone sur le même Wi-Fi que la TV, aucun serveur tiers.",
                color = SoftWhite,
                fontSize = 12.sp,
            )
            Text(
                "Jeton chiffré sur la TV, jamais journalisé. Client non officiel : à vos risques.",
                color = Amber,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    if (currentLocal != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Color.White).padding(8.dp)) {
                QrCode(currentLocal, 220.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scannez avec le téléphone", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("1. Connectez-vous sur playstation.com", color = SoftWhite, fontSize = 14.sp)
                Text("2. Ouvrez ${LocalTokenServer.SSO_COOKIE_URL.removePrefix("https://")}", color = SoftWhite, fontSize = 14.sp)
                Text("3. Copiez la valeur npsso et collez-la dans la page", color = SoftWhite, fontSize = 14.sp)
                Text(currentLocal.removePrefix("http://"), color = Muted, fontSize = 11.sp)
                Text("En attente du téléphone (10 min au plus)…", color = Chalk, fontSize = 12.sp)
                FocusButton("Annuler", modifier = Modifier.focusRequester(localCancelFocus).testTag("psn-local-cancel")) {
                    localServer?.close()
                    localServer = null
                    localUrl = null
                    localStatus = ""
                }
            }
        }
    } else if (current == null) {
        // Stacked, not side by side: DOWN from the Cloud tab must land on the local sign-in, not on the fallback.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusButton(
                "Se connecter avec le téléphone (Wi-Fi local)",
                primary = true,
                modifier = Modifier.focusRequester(signInFocus).testTag("psn-local-sign-in"),
            ) {
                touched = true
                lateinit var server: LocalTokenServer
                server =
                    LocalTokenServer(
                        onFinish = { token ->
                            scope.launch(Dispatchers.Main) {
                                if (localServer !== server) return@launch
                                localServer = null
                                localUrl = null
                                if (token != null) {
                                    localStatus = ""
                                    cloud.signIn(token)
                                } else {
                                    localStatus = "Code expiré. Recommencez."
                                }
                            }
                        },
                    )
                val url = startLocalSignIn(server)
                if (url == null) {
                    localStatus = "Aucun réseau local trouvé : connectez la TV au Wi-Fi ou en Ethernet."
                } else {
                    localServer = server
                    localUrl = url
                    localStatus = ""
                }
            }
            FocusButton("Secours : via xbgamestream.com", modifier = Modifier.testTag("psn-relay-sign-in")) {
                touched = true
                val login = PsnRelayLogin()
                relay = login
                relayStatus = "Préparation du code…"
                scope.launch {
                    if (!withContext(Dispatchers.IO) { login.register() }) {
                        relayStatus = "Le serveur de connexion ne répond pas. Réessayez plus tard."
                        return@launch
                    }
                    relayStatus = "Scannez le QR code et connectez-vous sur le téléphone. En attente…"
                    // Poll for up to 10 minutes; leaving the screen cancels the coroutine.
                    repeat(200) {
                        delay(3_000)
                        if (relay !== login) return@launch
                        val token = withContext(Dispatchers.IO) { login.poll() }
                        if (token != null) {
                            relay = null
                            relayStatus = ""
                            cloud.signIn(token)
                            return@launch
                        }
                    }
                    relayStatus = "Code expiré. Recommencez."
                }
            }
        }
        if (localStatus.isNotEmpty()) Text(localStatus, color = Amber, fontSize = 11.sp)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Color.White).padding(8.dp)) {
                QrCode(current.loginUrl, 220.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Code : ${current.code}", fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text(current.loginUrl.removePrefix("https://"), color = Muted, fontSize = 11.sp)
                Text(relayStatus, color = SoftWhite, fontSize = 13.sp)
                Text(
                    "Secours : xbgamestream.com (équipe Pylux) reçoit votre jeton PSN pour le transmettre à la TV.",
                    color = Amber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                FocusButton("Annuler", modifier = Modifier.focusRequester(cancelFocus)) {
                    relay = null
                    relayStatus = ""
                }
            }
        }
    }
    if (currentLocal != null || current != null) return
    if (relayStatus.isNotEmpty()) Text(relayStatus, color = Amber, fontSize = 11.sp)
    SectionTitle(
        "Saisie du jeton NPSSO (secours)",
        "Copiez la valeur npsso de ca.account.sony.com/api/v1/ssocookie et collez-la ici.",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        LabeledInput("Jeton NPSSO", manualToken, { manualToken = it.take(512) }, Modifier.weight(1f), testTag = "npsso-input")
        FocusButton("Valider") {
            if (cloud.signIn(manualToken)) manualToken = ""
        }
    }
}

@Composable
private fun CloudCatalogPanel(cloudState: CloudSnapshot, cloud: CloudPlayController, columns: Int, onFocusGame: (CloudGame) -> Unit) {
    var query by remember { mutableStateOf("") }
    var visibleCount by remember { mutableStateOf(CLOUD_PAGE_SIZE) }
    val matches =
        remember(cloudState.games, query) {
            val needle = query.trim()
            cloudState.games.filter { it.playable && (needle.isEmpty() || it.name.contains(needle, ignoreCase = true)) }
        }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        FocusButton(if (cloudState.loading) "Chargement…" else "Actualiser", primary = true) { cloud.refreshCatalog() }
        FocusButton("Qualité : ${cloudState.resolution.label}") { cloud.cycleResolution() }
        if (cloudState.provisioning) FocusButton("Annuler") { cloud.cancelProvisioning() }
        FocusButton("Se déconnecter") { cloud.signOut() }
    }
    if (cloudState.warning.isNotEmpty()) {
        Text(cloudState.warning, color = Amber, fontSize = 11.sp)
    }
    // The search field opens only on demand: a focused text field raises the TV keyboard, which
    // swallows the D-pad, so merely moving through this screen must never land in it.
    var searching by remember { mutableStateOf(false) }
    var searchHadFocus by remember { mutableStateOf(false) }
    val searchButtonFocus = remember { FocusRequester() }
    val searchFieldFocus = remember { FocusRequester() }
    fun closeSearch() {
        searching = false
        searchHadFocus = false
        runCatching { searchButtonFocus.requestFocus() }
    }
    BackHandler(enabled = searching, onBack = ::closeSearch)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        FocusButton(
            if (query.isBlank()) "Rechercher un jeu" else "Recherche : ${query.take(30)}",
            modifier = Modifier.focusRequester(searchButtonFocus).testTag("cloud-search-open"),
        ) { searching = true }
        if (query.isNotBlank() && !searching) {
            FocusButton("Effacer") {
                query = ""
                visibleCount = CLOUD_PAGE_SIZE
            }
        }
        if (searching) {
            LaunchedEffect(Unit) { runCatching { searchFieldFocus.requestFocus() } }
            LabeledInput(
                "Nom du jeu",
                query,
                {
                    query = it.take(80)
                    visibleCount = CLOUD_PAGE_SIZE
                },
                Modifier.weight(1f).onFocusChanged {
                    if (it.hasFocus) searchHadFocus = true else if (searchHadFocus) closeSearch()
                },
                focusRequester = searchFieldFocus,
                previousFocusRequester = searchButtonFocus,
                nextFocusRequester = searchButtonFocus,
                testTag = "cloud-search",
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        matches.take(visibleCount).chunked(columns).forEach { rowGames ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowGames.forEach { game ->
                    CloudGameCard(game, enabled = !cloudState.provisioning, modifier = Modifier.weight(1f), onFocus = { onFocusGame(game) }) { cloud.play(game) }
                }
                repeat(columns - rowGames.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (matches.size > visibleCount) {
        FocusButton("Afficher plus (${matches.size - visibleCount} restants)") { visibleCount += CLOUD_PAGE_SIZE }
    } else if (matches.isEmpty() && !cloudState.loading) {
        Text("Aucun jeu jouable trouvé.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun CloudGameCard(game: CloudGame, enabled: Boolean, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val accent = if (game.category == CloudCategory.OWNED) Signal else Chalk
    Row(
        modifier.height(104.dp).clip(RoundedCornerShape(3.dp))
            .background(if (focused) PanelRaised else Panel.copy(alpha = 0.92f))
            .border(if (focused) 3.dp else 1.dp, if (focused) Signal else Line)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 16:9 catalog art with the platform badge; the badge alone stands in while it loads or when none exists.
        Box(Modifier.width(142.dp).height(80.dp).clip(RoundedCornerShape(3.dp)).background(accent.copy(alpha = .13f))) {
            val thumbnail = rememberCloudArt(game.artUrl, THUMBNAIL_ART_WIDTH)
            if (thumbnail != null) {
                Image(thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Text(
                game.platform.uppercase().ifEmpty { "PS" },
                color = if (thumbnail != null) Ink else accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                modifier =
                    Modifier.align(if (thumbnail != null) Alignment.BottomStart else Alignment.Center)
                        .then(if (thumbnail != null) Modifier.padding(5.dp).background(accent).padding(horizontal = 5.dp, vertical = 1.dp) else Modifier),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(game.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (game.category == CloudCategory.OWNED) "Possédé" else "Catalogue Plus", color = Muted, fontSize = 10.sp)
        }
    }
}

private const val CLOUD_PAGE_SIZE = 30

private val BUSY_PHASES =
    setOf(RemotePlayPhase.WAKING, RemotePlayPhase.CONNECTING, RemotePlayPhase.STREAMING, RemotePlayPhase.STOPPING)

private fun RemotePlayPhase.toSessionPhase(): SessionPhase =
    when (this) {
        RemotePlayPhase.NEEDS_REGISTRATION,
        RemotePlayPhase.READY,
        -> SessionPhase.READY
        RemotePlayPhase.REGISTERING,
        RemotePlayPhase.WAKING,
        RemotePlayPhase.CONNECTING,
        RemotePlayPhase.STOPPING,
        -> SessionPhase.CONNECTING
        RemotePlayPhase.STREAMING,
        RemotePlayPhase.LOGIN_PIN_REQUIRED,
        -> SessionPhase.STREAMING
        RemotePlayPhase.STOPPED -> SessionPhase.STOPPED
        RemotePlayPhase.FAILED -> SessionPhase.FAILED
    }

private fun RemotePlayPhase.label(): String = homeLabel().uppercase()

private fun RemotePlayPhase.accent(): Color =
    when (this) {
        RemotePlayPhase.STREAMING,
        RemotePlayPhase.READY,
        -> Signal
        RemotePlayPhase.FAILED -> Danger
        RemotePlayPhase.NEEDS_REGISTRATION,
        RemotePlayPhase.LOGIN_PIN_REQUIRED,
        -> Amber
        else -> Chalk
    }

private fun yesNo(value: Boolean): String = if (value) "ACTIVÉ" else "DÉSACTIVÉ"

