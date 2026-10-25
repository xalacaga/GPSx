package com.gpsx.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PanelShape = RoundedCornerShape(22.dp)

@Composable
fun GpsxApp() {
    var state by remember { mutableStateOf(AppUiState()) }
    val scope = rememberCoroutineScope()

    fun dispatch(action: UiAction) {
        state = reduce(state, action)
        if (action == UiAction.StartDemo && state.sessionPhase == SessionPhase.CONNECTING) {
            scope.launch {
                delay(750)
                state = reduce(state, UiAction.ConfirmDemoStarted)
            }
        }
    }

    BackHandler(enabled = state.destination != Destination.HOME) {
        dispatch(UiAction.Navigate(Destination.HOME))
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Ink, Color(0xFF092336), Color(0xFF07131D)),
                    ),
                ),
    ) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(state.destination, onNavigate = { dispatch(UiAction.Navigate(it)) })
            Box(Modifier.fillMaxSize().padding(start = 12.dp)) {
                when (state.destination) {
                    Destination.HOME -> HomeScreen(state, ::dispatch)
                    Destination.CONSOLES -> ConsolesScreen(::dispatch)
                    Destination.SESSION -> SessionScreen(state, ::dispatch)
                    Destination.CONTROLLERS -> ControllersScreen()
                    Destination.DIAGNOSTICS -> DiagnosticsScreen()
                    Destination.SETTINGS -> SettingsScreen(state, ::dispatch)
                }
            }
        }
    }
}

@Composable
private fun NavigationRail(selected: Destination, onNavigate: (Destination) -> Unit) {
    Column(
        modifier =
            Modifier
                .width(224.dp)
                .fillMaxHeight()
                .background(Color(0xB3081823))
                .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
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
                Text("REMOTE, REFINED", fontSize = 8.sp, color = Muted, letterSpacing = 1.6.sp)
            }
        }

        Spacer(Modifier.height(36.dp))
        Destination.entries.forEach { destination ->
            NavItem(
                destination = destination,
                selected = destination == selected,
                onClick = { onNavigate(destination) },
            )
            Spacer(Modifier.height(7.dp))
        }

        Spacer(Modifier.weight(1f))
        StatusPill("HORS LIGNE", Mint)
        Spacer(Modifier.height(10.dp))
        Text("Aucun appareil connecté", color = Muted, fontSize = 11.sp)
        Text("Prototype local · v0.1", color = Muted.copy(alpha = .65f), fontSize = 10.sp)
    }
}

@Composable
private fun NavItem(destination: Destination, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        focused -> Color(0xFF1D4657)
        selected -> Color(0xFF143344)
        else -> Color.Transparent
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(fill)
                .then(if (focused) Modifier.border(2.dp, Mint, RoundedCornerShape(14.dp)) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick)
                .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(destination.glyph, color = if (selected || focused) Mint else Muted, fontSize = 18.sp)
        Spacer(Modifier.width(13.dp))
        Text(
            destination.label,
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
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 38.dp, end = 46.dp, top = 34.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(eyebrow.uppercase(), color = Mint, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 35.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = Muted, fontSize = 14.sp)
        }
        item { content() }
    }
}

@Composable
private fun HomeScreen(state: AppUiState, dispatch: (UiAction) -> Unit) {
    ScreenFrame(
        eyebrow = "Expérience salon",
        title = "Bonsoir.",
        subtitle = "Tout ce qui compte, immédiatement accessible à la télécommande ou à la DualSense.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HeroPanel(state, dispatch)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                item { MetricCard("LATENCE LAN", "4,8 ms", "simulation", Mint) }
                item { MetricCard("FLUIDITÉ", "59,9 fps", "simulation", Sky) }
                item { MetricCard("DÉCODAGE", "8,2 ms", "simulation", Amber) }
                item { MetricCard("PERTES", "0,02 %", "simulation", Mint) }
            }
            SectionTitle("Accès rapide", "Fonctions essentielles et avancées")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(
                    listOf(
                        Triple("Connexion distante", "PSN · planifiée", "◎"),
                        Triple("Inspecteur manette", "Entrées en direct", "✦"),
                        Triple("Profils", "Utilisateurs multiples", "◉"),
                        Triple("Exporter les réglages", "JSON sans secrets", "⇧"),
                    ),
                ) { (title, subtitle, glyph) -> FeatureCard(glyph, title, subtitle) }
            }
        }
    }
}

@Composable
private fun HeroPanel(state: AppUiState, dispatch: (UiAction) -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(246.dp)
                .clip(PanelShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF123A4A), Color(0xFF102B4B), Color(0xFF321E4D)),
                    ),
                )
                .padding(28.dp),
    ) {
        Column(Modifier.weight(1f)) {
            StatusPill("MODE 100 % SIMULÉ", Amber)
            Spacer(Modifier.height(15.dp))
            Text("Votre console,\nsans friction.", fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Text(
                "Explorez toute l’expérience avant l’ouverture de la connexion matérielle.",
                color = Color(0xFFC7D8E3),
                fontSize = 13.sp,
            )
            Spacer(Modifier.weight(1f))
            FocusButton("Lancer la démo", primary = true) { dispatch(UiAction.StartDemo) }
        }
        Box(Modifier.width(330.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
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
                if (state.sessionPhase == SessionPhase.STREAMING) "DÉMO ACTIVE" else "PRÊTE",
                if (state.sessionPhase == SessionPhase.STREAMING) Mint else Sky,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun ConsolesScreen(dispatch: (UiAction) -> Unit) {
    ScreenFrame("Bibliothèque", "Vos consoles", "Découverte locale, ajout manuel, réveil et accès distant seront réunis ici.") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ConsoleCard("PS5 Salon", "Console de démonstration", "SIMULÉE", Mint, Modifier.weight(1f)) {
                    dispatch(UiAction.StartDemo)
                }
                ConsoleCard("Ajouter une console", "Découverte ou adresse manuelle", "PLANIFIÉ", Sky, Modifier.weight(1f)) {}
            }
            SectionTitle("Modes de connexion", "Parité prévue avec les clients de référence")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MiniPanel("Réseau local", "Découverte automatique et réveil", "LAN", Modifier.weight(1f))
                MiniPanel("À distance", "Connexion assistée par PSN", "WAN", Modifier.weight(1f))
                MiniPanel("Enregistrement", "PIN et stockage sécurisé", "PAIR", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SessionScreen(state: AppUiState, dispatch: (UiAction) -> Unit) {
    ScreenFrame("Lecture", "Session de démonstration", "Le rendu et les mesures ci-dessous sont synthétiques ; aucune console n’est contactée.") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier =
                    Modifier.fillMaxWidth().height(330.dp).clip(PanelShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF142D45), Color(0xFF251B40), Color(0xFF0D2630)))),
            ) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (state.sessionPhase == SessionPhase.CONNECTING) "◌" else "GPSx", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(sessionLabel(state.sessionPhase), color = Muted, fontSize = 14.sp)
                }
                StatusPill("DONNÉES SIMULÉES", Amber, Modifier.align(Alignment.TopStart).padding(18.dp))
                if (state.metricsVisible) {
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(18.dp).clip(RoundedCornerShape(14.dp))
                            .background(Color(0xCC061018)).padding(horizontal = 15.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        OverlayMetric("FPS", "59,9")
                        OverlayMetric("RTT", "4,8 ms")
                        OverlayMetric("P95", "11,3 ms")
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FocusButton(if (state.sessionPhase == SessionPhase.STREAMING) "Arrêter" else "Relancer", primary = true) {
                        if (state.sessionPhase == SessionPhase.STREAMING || state.sessionPhase == SessionPhase.CONNECTING) {
                            dispatch(UiAction.StopDemo)
                        } else {
                            dispatch(UiAction.StartDemo)
                        }
                    }
                    FocusButton(if (state.metricsVisible) "Masquer les stats" else "Afficher les stats") { dispatch(UiAction.ToggleMetrics) }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(listOf("Menu rapide", "Micro", "Ajustement image", "Reconnexion", "Mettre en veille")) {
                    FeatureCard("·", it, "Disponible au fil des jalons")
                }
            }
        }
    }
}

@Composable
private fun ControllersScreen() {
    ScreenFrame("Entrées", "Manettes", "Une architecture multi-manette, avec la DualSense comme référence prioritaire.") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ConsoleCard("DualSense", "Bluetooth · périphérique fictif", "NON CONNECTÉE", Amber, Modifier.weight(1.2f)) {}
                MiniPanel("Inspecteur en direct", "Touches, axes, HAT, VID/PID et capacités", "DEV", Modifier.weight(1f))
            }
            SectionTitle("Capacités prévues", "Chaque capacité restera NOT_TESTED jusqu’à validation appropriée")
            CapabilityGrid()
        }
    }
}

@Composable
private fun CapabilityGrid() {
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
        capabilities.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (name, status) -> MiniPanel(name, status, "○", Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen() {
    ScreenFrame("Observabilité", "Diagnostics", "Les preuves émulateur, Android et GP520 resteront toujours séparées.") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HealthCard("Réseau", "Simulation", "RTT · jitter · pertes · débit", Mint, Modifier.weight(1f))
                HealthCard("Vidéo", "Simulation", "FPS · files · rendu · erreurs", Sky, Modifier.weight(1f))
                HealthCard("Audio", "Simulation", "Route · buffer · underruns", Amber, Modifier.weight(1f))
                HealthCard("Entrées", "Simulation", "P50 · P95 · P99 · événements", Mint, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MiniPanel("Performance", "Overlay, percentiles et comparaison baseline", "P95", Modifier.weight(1f))
                MiniPanel("Journaux", "Structurés, filtrables et expurgés", "LOG", Modifier.weight(1f))
                MiniPanel("Export JSONL", "Mesures exploitables sans secret", "{ }", Modifier.weight(1f))
            }
            EvidenceBanner()
        }
    }
}

@Composable
private fun SettingsScreen(state: AppUiState, dispatch: (UiAction) -> Unit) {
    ScreenFrame("Personnalisation", "Réglages", "Des choix lisibles, avec des valeurs sûres par défaut et des options avancées à la demande.") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingRow("Qualité vidéo", "Auto, 720p60 ou 1080p60", state.quality.label) { dispatch(UiAction.CycleQuality) }
            SettingRow("Mode faible latence", "Demandé au décodeur quand il est réellement supporté", yesNo(state.lowLatency)) { dispatch(UiAction.ToggleLowLatency) }
            SettingRow("Télémétrie locale", "Mesures structurées sans données personnelles", yesNo(state.telemetryEnabled)) { dispatch(UiAction.ToggleTelemetry) }
            SettingRow("Microphone", "Désactivé par défaut", yesNo(state.microphoneEnabled)) { dispatch(UiAction.ToggleMicrophone) }
            SectionTitle("Fonctions avancées", "Prévues pour la parité complète")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniPanel("Image", "HDR · cadrage · zoom · étirement", "HDR", Modifier.weight(1f))
                MiniPanel("Audio", "Sortie · buffer · micro · réduction de bruit", "AUDIO", Modifier.weight(1f))
                MiniPanel("Profils", "Réglages locaux/distants par console", "USER", Modifier.weight(1f))
                MiniPanel("Données", "Importer · exporter · réinitialiser", "DATA", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FocusButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> SoftWhite
        primary -> Mint
        else -> Color(0xFF1A3948)
    }
    val foreground = if (focused || primary) Ink else SoftWhite
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(13.dp)).background(background)
                .then(if (focused) Modifier.border(BorderStroke(3.dp, Mint), RoundedCornerShape(13.dp)) else Modifier)
                .onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricCard(label: String, value: String, detail: String, accent: Color) {
    Column(
        Modifier.width(194.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xB3112937)).padding(18.dp),
    ) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(detail.uppercase(), color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeatureCard(glyph: String, title: String, subtitle: String) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.width(248.dp).height(86.dp).clip(RoundedCornerShape(18.dp))
            .background(if (focused) Color(0xFF1C4252) else Color(0xAA102633))
            .then(if (focused) Modifier.border(2.dp, Mint, RoundedCornerShape(18.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }.focusable().clickable { }.padding(15.dp),
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
private fun ConsoleCard(title: String, subtitle: String, status: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.clip(PanelShape).background(if (focused) Color(0xFF173B4C) else Color(0xC0102835))
            .then(if (focused) Modifier.border(2.dp, accent, PanelShape) else Modifier)
            .onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick).padding(24.dp),
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
            .onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick).padding(18.dp),
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
private fun OverlayMetric(label: String, value: String) {
    Column {
        Text(label, color = Muted, fontSize = 8.sp)
        Text(value, color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun sessionLabel(phase: SessionPhase): String =
    when (phase) {
        SessionPhase.READY -> "Prête à démarrer"
        SessionPhase.CONNECTING -> "Connexion simulée…"
        SessionPhase.STREAMING -> "Session simulée active"
        SessionPhase.STOPPED -> "Session arrêtée proprement"
        SessionPhase.FAILED -> "Échec simulé"
    }

private fun yesNo(value: Boolean): String = if (value) "ACTIVÉ" else "DÉSACTIVÉ"

