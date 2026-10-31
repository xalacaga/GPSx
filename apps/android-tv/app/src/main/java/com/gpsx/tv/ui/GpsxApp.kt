package com.gpsx.tv.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.gpsx.tv.remoteplay.cloud.PsnLoginView
import com.metallic.chiaki.lib.StreamMetrics
import com.gpsx.tv.diagnostics.GitHubResultUploader
import com.gpsx.tv.diagnostics.StoredReport
import com.gpsx.tv.diagnostics.UploadResult
import com.gpsx.tv.update.AppUpdater
import com.gpsx.tv.update.ReleaseManifest
import com.gpsx.tv.update.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PanelShape = RoundedCornerShape(22.dp)

private data class ResponsiveLayout(
    val compact: Boolean,
    val short: Boolean,
)

private val LocalResponsiveLayout = staticCompositionLocalOf { ResponsiveLayout(compact = false, short = false) }

@Composable
fun GpsxApp() {
    val context = LocalContext.current
    val engine = remember(context) { RemotePlayEngine(context.applicationContext) }
    DisposableEffect(engine) {
        onDispose(engine::close)
    }
    GpsxApp(engine)
}

@Composable
fun GpsxApp(engine: RemotePlayEngine) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(AppUiState()) }
    var remote by remember { mutableStateOf(engine.snapshot()) }
    var controllers by remember { mutableStateOf(emptyList<ConnectedGameController>()) }
    val firstLegalFocusRequester = remember { FocusRequester() }
    val controllerMonitor = remember(context) { GameControllerMonitor(context.applicationContext) }
    val cloud = remember(engine) { CloudPlayController(context.applicationContext, engine) }
    var cloudState by remember { mutableStateOf(cloud.snapshot()) }

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

    StreamDisplayMode(active = fullscreenStream)

    BackHandler(enabled = fullscreenStream) {
        dispatch(UiAction.StopDemo)
    }

    BackHandler(enabled = !fullscreenStream && state.destination != Destination.HOME) {
        dispatch(UiAction.Navigate(Destination.HOME))
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Ink, Color(0xFF092336), Color(0xFF07131D)),
                    ),
                ),
    ) {
        val responsive = ResponsiveLayout(compact = maxWidth < 1100.dp, short = maxHeight < 650.dp)
        CompositionLocalProvider(
            LocalContentColor provides SoftWhite,
            LocalResponsiveLayout provides responsive,
        ) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    state.destination,
                    firstLegalFocusRequester,
                    visible = !fullscreenStream,
                    onNavigate = { dispatch(UiAction.Navigate(it)) },
                )
                Box(Modifier.fillMaxSize().padding(start = if (fullscreenStream) 0.dp else 12.dp)) {
                    key(state.destination) {
                        when (state.destination) {
                            Destination.HOME -> HomeScreen(state, ::dispatch)
                            Destination.CONSOLES -> ConsolesScreen(::dispatch)
                            Destination.SESSION -> SessionScreen(state, remote, engine, ::dispatch)
                            Destination.CLOUD -> CloudScreen(cloudState, cloud)
                            Destination.CONTROLLERS ->
                                ControllersScreen(controllers) {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                }
                            Destination.DIAGNOSTICS -> DiagnosticsScreen(engine)
                            Destination.SETTINGS -> SettingsScreen(state, ::dispatch)
                            Destination.ABOUT -> AboutScreen(firstLegalFocusRequester)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationRail(
    selected: Destination,
    firstLegalFocusRequester: FocusRequester,
    visible: Boolean = true,
    onNavigate: (Destination) -> Unit,
) {
    val responsive = LocalResponsiveLayout.current
    val homeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(homeFocusRequester) {
        homeFocusRequester.requestFocus()
    }

    Column(
        modifier =
            Modifier
                .width(if (!visible) 0.dp else if (responsive.compact) 180.dp else 224.dp)
                .fillMaxHeight()
                .background(Color(0xB3081823))
                .padding(
                    horizontal = if (responsive.compact) 16.dp else 24.dp,
                    vertical = if (responsive.short) 16.dp else 28.dp,
                ),
    ) {
        if (!visible) return@Column
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Mint),
                contentAlignment = Alignment.Center,
            ) {
                Text("G", color = Ink, fontWeight = FontWeight.Black, fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("GPSx", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("REMOTE PLAY", fontSize = 7.sp, color = Muted, letterSpacing = 1.2.sp, maxLines = 1)
                Text("REPENSÉ", fontSize = 7.sp, color = Muted, letterSpacing = 1.2.sp, maxLines = 1)
            }
        }

        Spacer(Modifier.height(if (responsive.short) 18.dp else 36.dp))
        Destination.entries.forEach { destination ->
            NavItem(
                destination = destination,
                selected = destination == selected,
                onClick = { onNavigate(destination) },
                modifier =
                    when (destination) {
                        Destination.HOME -> Modifier.focusRequester(homeFocusRequester)
                        Destination.ABOUT -> Modifier.focusProperties { right = firstLegalFocusRequester }
                        else -> Modifier
                    },
            )
            Spacer(Modifier.height(if (responsive.short) 3.dp else 7.dp))
        }

        Spacer(Modifier.weight(1f))
        StatusPill(stringResource(R.string.offline_badge), Mint)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.no_hardware), color = Muted, fontSize = 11.sp)
        Text("Prototype local · v0.1", color = Muted.copy(alpha = .65f), fontSize = 10.sp)
    }
}

@Composable
private fun NavItem(destination: Destination, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        focused -> Color(0xFF1D4657)
        selected -> Color(0xFF143344)
        else -> Color.Transparent
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(fill)
                .then(if (focused) Modifier.border(2.dp, Mint, RoundedCornerShape(14.dp)) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .semantics { this.selected = selected }
                .clickable(role = Role.Tab, onClick = onClick)
                .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(destination.labelRes),
            color = if (selected || focused) SoftWhite else Muted,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ScreenFrame(
    eyebrow: String,
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
                start = if (fullscreen) 0.dp else if (responsive.compact) 22.dp else 38.dp,
                end = if (fullscreen) 0.dp else if (responsive.compact) 24.dp else 46.dp,
                top = if (fullscreen) 0.dp else if (responsive.short) 18.dp else 34.dp,
                bottom = if (fullscreen) 0.dp else if (responsive.short) 18.dp else 34.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (responsive.short) 12.dp else 18.dp),
    ) {
        item {
            if (!fullscreen) {
                Text(eyebrow.uppercase(), color = Mint, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text(
                    title,
                    fontSize = if (responsive.compact) 29.sp else 35.sp,
                    lineHeight = if (responsive.compact) 34.sp else 40.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(5.dp))
                Text(subtitle, color = Muted, fontSize = 14.sp)
            }
        }
        item { content() }
    }
}

@Composable
private fun HomeScreen(state: AppUiState, dispatch: (UiAction) -> Unit) {
    val responsive = LocalResponsiveLayout.current
    ScreenFrame(
        eyebrow = "Expérience salon",
        title = "Bonsoir.",
        subtitle = "Tout ce qui compte, immédiatement accessible à la télécommande ou à la DualSense.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HeroPanel(state, dispatch)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetricCard("LATENCE LAN", "4,8 ms", "simulation", Mint, Modifier.weight(1f))
                MetricCard("FLUIDITÉ", "59,9 fps", "simulation", Sky, Modifier.weight(1f))
                if (!responsive.compact) {
                    MetricCard("DÉCODAGE", "8,2 ms", "simulation", Amber, Modifier.weight(1f))
                    MetricCard("PERTES", "0,02 %", "simulation", Mint, Modifier.weight(1f))
                }
                }
                if (responsive.compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MetricCard("DÉCODAGE", "8,2 ms", "simulation", Amber, Modifier.weight(1f))
                MetricCard("PERTES", "0,02 %", "simulation", Mint, Modifier.weight(1f))
                }
            }
            SectionTitle("Accès rapide", "Fonctions essentielles et avancées")
            FeatureGrid(
                listOf(
                    Triple("Connexion distante", "PSN · planifiée", "◎"),
                    Triple("Inspecteur manette", "Entrées en direct", "✦"),
                    Triple("Profils", "Utilisateurs multiples", "◉"),
                    Triple("Exporter les réglages", "JSON sans secrets", "⇧"),
                ),
            )
        }
    }
}

@Composable
private fun HeroPanel(state: AppUiState, dispatch: (UiAction) -> Unit) {
    val responsive = LocalResponsiveLayout.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(if (responsive.compact) 220.dp else 246.dp)
                .clip(PanelShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF123A4A), Color(0xFF102B4B), Color(0xFF321E4D)),
                    ),
                )
                .padding(28.dp),
    ) {
        Column(Modifier.weight(1f)) {
            StatusPill(stringResource(R.string.offline_badge), Amber)
            Spacer(Modifier.height(15.dp))
            Text(stringResource(R.string.hero_title), fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Text(
                stringResource(R.string.hero_subtitle),
                color = Color(0xFFC7D8E3),
                fontSize = 13.sp,
            )
            Spacer(Modifier.weight(1f))
            FocusButton(stringResource(R.string.start_demo), primary = true) { dispatch(UiAction.StartDemo) }
        }
        if (!responsive.compact) Box(Modifier.width(330.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(225.dp).clip(CircleShape).background(Mint.copy(alpha = .08f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.width(172.dp).height(108.dp).clip(RoundedCornerShape(29.dp))
                        .border(2.dp, SoftWhite.copy(alpha = .75f), RoundedCornerShape(29.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("PS5", fontSize = 29.sp, fontWeight = FontWeight.Light, letterSpacing = 5.sp)
                }
            }
            StatusPill(
                if (state.sessionPhase == SessionPhase.STREAMING) {
                    stringResource(R.string.streaming)
                } else {
                    stringResource(R.string.ready)
                },
                if (state.sessionPhase == SessionPhase.STREAMING) Mint else Sky,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun ConsolesScreen(dispatch: (UiAction) -> Unit) {
    val responsive = LocalResponsiveLayout.current
    ScreenFrame("Bibliothèque", "Vos consoles", "Découverte locale, ajout manuel, réveil et accès distant seront réunis ici.") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (responsive.compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConsoleCard(
                        stringResource(R.string.console_title),
                        stringResource(R.string.console_subtitle),
                        stringResource(R.string.offline_badge),
                        Mint,
                        Modifier.fillMaxWidth(),
                    ) { dispatch(UiAction.StartDemo) }
                    ConsoleCard("Ajouter une console", "Découverte ou adresse manuelle", "PLANIFIÉ", Sky, Modifier.fillMaxWidth()) {}
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ConsoleCard(
                    stringResource(R.string.console_title),
                    stringResource(R.string.console_subtitle),
                    stringResource(R.string.offline_badge),
                    Mint,
                    Modifier.weight(1f),
                ) {
                    dispatch(UiAction.StartDemo)
                }
                ConsoleCard("Ajouter une console", "Découverte ou adresse manuelle", "PLANIFIÉ", Sky, Modifier.weight(1f)) {}
            }
            SectionTitle("Modes de connexion", "Parité prévue avec les clients de référence")
            if (responsive.compact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniPanel("Réseau local", "Découverte automatique et réveil", "LAN", Modifier.fillMaxWidth())
                    MiniPanel("À distance", "Connexion assistée par PSN", "WAN", Modifier.fillMaxWidth())
                    MiniPanel("Enregistrement", "PIN et stockage sécurisé", "PAIR", Modifier.fillMaxWidth())
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MiniPanel("Réseau local", "Découverte automatique et réveil", "LAN", Modifier.weight(1f))
                MiniPanel("À distance", "Connexion assistée par PSN", "WAN", Modifier.weight(1f))
                MiniPanel("Enregistrement", "PIN et stockage sécurisé", "PAIR", Modifier.weight(1f))
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
                StatusPill(
                    if (fullscreen) remote.cloudTitle ?: remote.consoleName ?: "PS5" else remote.phase.label(),
                    remote.phase.accent(),
                    Modifier.align(Alignment.TopStart).padding(18.dp),
                )
                if (fullscreen && state.metricsVisible) {
                    StreamMetricsOverlay(engine, Modifier.align(Alignment.TopEnd).padding(18.dp))
                }
                if (fullscreen && showFullscreenHint) {
                    Text(
                        "Retour : déconnecter",
                        color = SoftWhite,
                        fontSize = 11.sp,
                        modifier =
                            Modifier.align(Alignment.BottomStart).padding(18.dp)
                                .clip(RoundedCornerShape(10.dp)).background(Color(0x99081823))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                } else if (!fullscreen) {
                    SessionActions(state, remote, engine, dispatch, vertical = responsive.compact, Modifier.align(Alignment.BottomStart).padding(18.dp))
                }
            }

            if (remote.consoleName == null) {
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

            if (!fullscreen) FeatureGrid(
                listOf(
                    Triple("Vidéo", "MediaCodec natif · H.264 · ${state.quality.label}", "V"),
                    Triple("Audio", "Décodage et sortie natifs", "A"),
                    Triple("Manette", "Événements Android vers l’état unifié", "J1"),
                ),
                columns = if (responsive.compact) 1 else 3,
            )
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
            "%dp · %.1f fps · %.1f Mb/s · RTT %.0f ms · pertes %.1f %% · images perdues %d",
            current.height,
            current.fps,
            current.bitrateMbps,
            current.rttMs,
            current.packetLoss * 100,
            current.framesLost,
        ),
        color = SoftWhite,
        fontSize = 11.sp,
        modifier =
            modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x99081823))
                .padding(horizontal = 12.dp, vertical = 8.dp).testTag("stream-metrics"),
    )
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(Color(0xAD102835)).padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Où trouver les informations", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
private fun LabeledInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    previousFocusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    testTag: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier) {
        Text(label, color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = SoftWhite),
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
                    .background(Color(0xFF102835))
                    .border(if (focused) 3.dp else 1.dp, if (focused) Mint else Color(0xFF315263), shape)
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
                        accent = if (index == 0) Mint else Sky,
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
                        accent = if (index == 0) Mint else Sky,
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
    Column(modifier.clip(PanelShape).background(Color(0xC0102835)).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Text(number, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
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
    val responsive = LocalResponsiveLayout.current
    ScreenFrame("Observabilité", stringResource(R.string.diagnostic_title), "Les preuves émulateur, Android et GP520 resteront toujours séparées.") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TestResultsPanel(engine)
            if (responsive.compact) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HealthCard("Réseau", "Simulation", "RTT · gigue · pertes · débit", Mint, Modifier.weight(1f))
                    HealthCard("Vidéo", "Simulation", "FPS · files · rendu · erreurs", Sky, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HealthCard("Audio", "Simulation", "Route · buffer · underruns", Amber, Modifier.weight(1f))
                    HealthCard("Entrées", "Simulation", "P50 · P95 · P99 · événements", Mint, Modifier.weight(1f))
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HealthCard("Réseau", "Simulation", "RTT · gigue · pertes · débit", Mint, Modifier.weight(1f))
                HealthCard("Vidéo", "Simulation", "FPS · files · rendu · erreurs", Sky, Modifier.weight(1f))
                HealthCard("Audio", "Simulation", "Route · buffer · underruns", Amber, Modifier.weight(1f))
                HealthCard("Entrées", "Simulation", "P50 · P95 · P99 · événements", Mint, Modifier.weight(1f))
            }
            if (responsive.compact) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniPanel("Performance", "Superposition, percentiles et comparaison de référence", "P95", Modifier.fillMaxWidth())
                MiniPanel("Journaux", "Structurés, filtrables et expurgés", "JOURNAL", Modifier.fillMaxWidth())
                MiniPanel("Export JSONL", "Mesures exploitables sans secret", "{ }", Modifier.fillMaxWidth())
            } else Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MiniPanel("Performance", "Superposition, percentiles et comparaison de référence", "P95", Modifier.weight(1f))
                MiniPanel("Journaux", "Structurés, filtrables et expurgés", "JOURNAL", Modifier.weight(1f))
                MiniPanel("Export JSONL", "Mesures exploitables sans secret", "{ }", Modifier.weight(1f))
            }
            MiniPanel(
                "Coexistence Bluetooth",
                "DualSense J1 + DualSense J2 + télécommande TV + route audio, avec événements corrélés",
                "4 APPAREILS",
                Modifier.fillMaxWidth(),
            )
            MiniPanel(
                "Laboratoire de mesure",
                "Tests guidés 2/10/30/60 min · export expurgé · comparaison GitHub privée",
                "RÉFÉRENCE → CANDIDAT",
                Modifier.fillMaxWidth(),
            )
            EvidenceBanner()
        }
    }
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = engine.testRecorder ?: return
    val uploader = remember(context) { GitHubResultUploader(context) }
    var reports by remember { mutableStateOf(emptyList<StoredReport>()) }
    var hasToken by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var repository by remember { mutableStateOf(uploader.repository) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(recorder) {
        reports = withContext(Dispatchers.IO) { recorder.list() }
        hasToken = withContext(Dispatchers.IO) { uploader.hasToken() }
    }
    SectionTitle(
        "Résultats de tests",
        "Chaque session stream enregistre un rapport (mesures, appareil, Wi-Fi) sans adresse, nom ni secret",
    )
    if (reports.isEmpty()) {
        Text("Aucun rapport : lancez un stream d’au moins quelques secondes.", color = Muted, fontSize = 12.sp)
    }
    reports.take(8).forEach { report ->
        Text(
            String.format(
                java.util.Locale.FRANCE,
                "%s · %s · %.0f s · %s fps · RTT p95 %s ms%s",
                report.name.removeSuffix(".json"),
                report.kind,
                report.durationSeconds,
                report.fpsP50?.let { "%.1f".format(java.util.Locale.FRANCE, it) } ?: "–",
                report.rttP95?.let { "%.0f".format(java.util.Locale.FRANCE, it) } ?: "–",
                if (report.uploaded) " · envoyé" else "",
            ),
            color = if (report.uploaded) Muted else SoftWhite,
            fontSize = 11.sp,
        )
    }
    if (hasToken) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FocusButton(if (busy) "Envoi…" else "Envoyer les résultats sur GitHub", primary = true, modifier = Modifier.testTag("upload-results")) {
                if (busy) return@FocusButton
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { uploader.uploadPending(recorder) }
                    status =
                        when (result) {
                            is UploadResult.Sent -> if (result.count == 0) "Rien de nouveau à envoyer." else "${result.count} rapport(s) envoyé(s) vers ${uploader.repository}."
                            is UploadResult.Failed -> result.message
                        }
                    reports = withContext(Dispatchers.IO) { recorder.list() }
                    busy = false
                }
            }
            FocusButton("Supprimer le jeton") {
                uploader.clearToken()
                hasToken = false
                status = "Jeton supprimé de l’appareil."
            }
        }
    } else {
        Text(
            "Créez sur GitHub un jeton « fine-grained » limité au dépôt de diagnostics, droit Contents : lecture et écriture, " +
                "expiration 30 jours, puis collez-le ici (l’application Google TV du téléphone permet de coller).",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            LabeledInput("Dépôt (propriétaire/nom)", repository, { repository = it.take(140) }, Modifier.weight(1f), testTag = "results-repository")
            LabeledInput("Jeton GitHub", token, { token = it.take(300) }, Modifier.weight(1.4f), testTag = "github-token")
            FocusButton("Enregistrer") {
                uploader.repository = repository
                if (uploader.saveToken(token)) {
                    token = ""
                    hasToken = true
                    status = "Jeton chiffré sur l’appareil. Dépôt : ${uploader.repository}."
                } else {
                    status = "Jeton GitHub invalide."
                }
            }
        }
    }
    if (status.isNotEmpty()) Text(status, color = Amber, fontSize = 11.sp)
}

@Composable
private fun SettingsScreen(state: AppUiState, dispatch: (UiAction) -> Unit) {
    val responsive = LocalResponsiveLayout.current
    ScreenFrame("Personnalisation", stringResource(R.string.settings_title), "Des choix lisibles, avec des valeurs sûres par défaut et des options avancées à la demande.") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingRow("Qualité vidéo", "Auto, 720p60 ou 1080p60", state.quality.label) { dispatch(UiAction.CycleQuality) }
            SettingRow("Mode faible latence", "Demandé au décodeur quand il est réellement supporté", yesNo(state.lowLatency)) { dispatch(UiAction.ToggleLowLatency) }
            SettingRow("Télémétrie locale", "Mesures structurées sans données personnelles", yesNo(state.telemetryEnabled)) { dispatch(UiAction.ToggleTelemetry) }
            SettingRow("Microphone", "Désactivé par défaut", yesNo(state.microphoneEnabled)) { dispatch(UiAction.ToggleMicrophone) }
            SectionTitle("Fonctions avancées", "Prévues pour la parité complète")
            if (responsive.compact) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniPanel("Image", "HDR · cadrage · zoom · étirement", "HDR", Modifier.weight(1f))
                    MiniPanel("Audio", "Sortie · tampon · micro · réduction de bruit", "AUDIO", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniPanel("Profils", "Réglages locaux/distants par console", "PROFIL", Modifier.weight(1f))
                    MiniPanel("Données", "Importer · exporter · réinitialiser", "DONNÉES", Modifier.weight(1f))
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniPanel("Image", "HDR · cadrage · zoom · étirement", "HDR", Modifier.weight(1f))
                MiniPanel("Audio", "Sortie · tampon · micro · réduction de bruit", "AUDIO", Modifier.weight(1f))
                MiniPanel("Profils", "Réglages locaux/distants par console", "PROFIL", Modifier.weight(1f))
                MiniPanel("Données", "Importer · exporter · réinitialiser", "DONNÉES", Modifier.weight(1f))
            }
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
private fun FocusButton(
    label: String,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> SoftWhite
        primary -> Mint
        else -> Color(0xFF1A3948)
    }
    val foreground = if (focused || primary) Ink else SoftWhite
    Box(
        modifier =
            modifier.clip(RoundedCornerShape(13.dp)).background(background)
                .then(if (focused) Modifier.border(BorderStroke(3.dp, Mint), RoundedCornerShape(13.dp)) else Modifier)
                .onFocusChanged { focused = it.isFocused }.clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricCard(label: String, value: String, detail: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xB3112937)).padding(18.dp),
    ) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(detail.uppercase(), color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeatureCard(glyph: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(
        modifier.height(86.dp).clip(RoundedCornerShape(18.dp))
            .background(Color(0xAA102633)).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Mint.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
            Text(glyph, color = Mint, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontSize = 10.sp, maxLines = 2)
        }
    }
}

@Composable
private fun FeatureGrid(items: List<Triple<String, String, String>>, columns: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { (title, subtitle, glyph) ->
                    FeatureCard(glyph, title, subtitle, Modifier.weight(1f))
                }
                repeat(columns - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ConsoleCard(title: String, subtitle: String, status: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.clip(PanelShape).background(if (focused) Color(0xFF173B4C) else Color(0xC0102835))
            .then(if (focused) Modifier.border(2.dp, accent, PanelShape) else Modifier)
            .onFocusChanged { focused = it.isFocused }.clickable(role = Role.Button, onClick = onClick).padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(78.dp).clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Text("PS", color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        StatusPill(status, accent)
    }
}

@Composable
private fun MiniPanel(title: String, subtitle: String, badge: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(17.dp)).background(Color(0xAD102835)).padding(17.dp)) {
        Text(badge, color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun LegalPanel(title: String, subtitle: String, badge: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(if (focused) Color(0xFF193E4F) else Color(0xAD102835))
            .then(if (focused) Modifier.border(2.dp, Mint, RoundedCornerShape(17.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }.semantics(mergeDescendants = true) {}
            .focusable().padding(17.dp),
    ) {
        Text(badge, color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun HealthCard(title: String, status: String, detail: String, accent: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xB5102835)).padding(18.dp)) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(status.uppercase(), color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(detail, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun SettingRow(title: String, detail: String, value: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(if (focused) Color(0xFF193E4F) else Color(0xA9102835))
            .then(if (focused) Modifier.border(2.dp, Mint, RoundedCornerShape(17.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }.clickable(role = Role.Button, onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 10.sp)
        }
        Text(value, color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EvidenceBanner() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Amber.copy(alpha = .09f))
            .border(1.dp, Amber.copy(alpha = .3f), RoundedCornerShape(18.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("!", color = Amber, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(15.dp))
        Column {
            Text("Frontière de preuve", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Ces valeurs sont synthétiques. Elles ne constituent aucune preuve Android réelle ni GP520.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(text, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun CloudScreen(cloudState: CloudSnapshot, cloud: CloudPlayController) {
    val responsive = LocalResponsiveLayout.current
    ScreenFrame("Jeu cloud", "PlayStation Plus", cloudState.detail) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (cloudState.signedIn) {
                CloudCatalogPanel(cloudState, cloud, columns = if (responsive.compact) 2 else 3)
            } else {
                PsnSignInPanel(cloud)
            }
        }
    }
}

@Composable
private fun PsnSignInPanel(cloud: CloudPlayController) {
    var showWebLogin by remember { mutableStateOf(false) }
    var manualToken by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color(0xAD102835)).padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Connexion PlayStation Network", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            "Un abonnement PlayStation Plus Premium est nécessaire pour streamer le catalogue cloud. " +
                "La connexion se fait directement sur la page de Sony ; GPSx ne voit pas votre mot de passe.",
            color = SoftWhite,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Text(
            "Le jeton de session est chiffré sur l’appareil et n’est jamais journalisé. " +
                "L’usage d’un client non officiel se fait aux risques du titulaire du compte.",
            color = Amber,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    FocusButton(
        if (showWebLogin) "Fermer la page Sony" else "Essayer la page de connexion Sony",
        modifier = Modifier.testTag("psn-sign-in"),
    ) {
        showWebLogin = !showWebLogin
    }
    if (showWebLogin) {
        Box(Modifier.fillMaxWidth().height(520.dp).clip(PanelShape).background(Color.White)) {
            AndroidView(
                factory = { context ->
                    PsnLoginView(context) { token ->
                        cloud.signIn(token)
                        showWebLogin = false
                    }.apply { start() }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    SectionTitle(
        "Saisie du jeton NPSSO",
        "Sur un ordinateur ou un téléphone connecté à playstation.com, ouvrez ca.account.sony.com/api/v1/ssocookie " +
            "et collez ici la valeur npsso (ou toute la réponse). L’application Google TV du téléphone permet de coller du texte.",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        LabeledInput("Jeton NPSSO", manualToken, { manualToken = it.take(512) }, Modifier.weight(1f), testTag = "npsso-input")
        FocusButton("Valider") {
            if (cloud.signIn(manualToken)) manualToken = ""
        }
    }
}

@Composable
private fun CloudCatalogPanel(cloudState: CloudSnapshot, cloud: CloudPlayController, columns: Int) {
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
    LabeledInput(
        "Rechercher un jeu",
        query,
        {
            query = it.take(80)
            visibleCount = CLOUD_PAGE_SIZE
        },
        Modifier.fillMaxWidth(),
        testTag = "cloud-search",
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        matches.take(visibleCount).chunked(columns).forEach { rowGames ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowGames.forEach { game ->
                    CloudGameCard(game, enabled = !cloudState.provisioning, modifier = Modifier.weight(1f)) { cloud.play(game) }
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
private fun CloudGameCard(game: CloudGame, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val accent = if (game.category == CloudCategory.OWNED) Mint else Sky
    Row(
        modifier.height(86.dp).clip(RoundedCornerShape(18.dp))
            .background(if (focused) Color(0xFF173B4C) else Color(0xAA102633))
            .then(if (focused) Modifier.border(2.dp, accent, RoundedCornerShape(18.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
            Text(game.platform.uppercase().ifEmpty { "PS" }, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
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

private fun RemotePlayPhase.label(): String = name.replace('_', ' ')

private fun RemotePlayPhase.accent(): Color =
    when (this) {
        RemotePlayPhase.STREAMING,
        RemotePlayPhase.READY,
        -> Mint
        RemotePlayPhase.FAILED -> Color(0xFFFF6B6B)
        RemotePlayPhase.NEEDS_REGISTRATION,
        RemotePlayPhase.LOGIN_PIN_REQUIRED,
        -> Amber
        else -> Sky
    }

private fun yesNo(value: Boolean): String = if (value) "ACTIVÉ" else "DÉSACTIVÉ"

