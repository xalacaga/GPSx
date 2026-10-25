package com.gpsx.tv.ui

enum class Destination(val label: String, val glyph: String) {
    HOME("Accueil", "⌂"),
    CONSOLES("Consoles", "▣"),
    SESSION("Lecture", "▶"),
    CONTROLLERS("Manettes", "✦"),
    DIAGNOSTICS("Diagnostics", "⌁"),
    SETTINGS("Réglages", "⚙"),
}

enum class SessionPhase {
    READY,
    CONNECTING,
    STREAMING,
    STOPPED,
    FAILED,
}

enum class StreamQuality(val label: String) {
    AUTO("Auto"),
    HD_720("720p60"),
    FULL_HD_1080("1080p60"),
}

data class AppUiState(
    val destination: Destination = Destination.HOME,
    val sessionPhase: SessionPhase = SessionPhase.READY,
    val metricsVisible: Boolean = true,
    val quality: StreamQuality = StreamQuality.AUTO,
    val lowLatency: Boolean = true,
    val telemetryEnabled: Boolean = true,
    val microphoneEnabled: Boolean = false,
)

sealed interface UiAction {
    data class Navigate(val destination: Destination) : UiAction

    data object StartDemo : UiAction
    data object ConfirmDemoStarted : UiAction
    data object StopDemo : UiAction
    data object ToggleMetrics : UiAction
    data object ToggleLowLatency : UiAction
    data object ToggleTelemetry : UiAction
    data object ToggleMicrophone : UiAction
    data object CycleQuality : UiAction
}

fun reduce(state: AppUiState, action: UiAction): AppUiState =
    when (action) {
        is UiAction.Navigate -> state.copy(destination = action.destination)
        UiAction.StartDemo -> {
            if (state.sessionPhase == SessionPhase.READY || state.sessionPhase == SessionPhase.STOPPED) {
                state.copy(sessionPhase = SessionPhase.CONNECTING, destination = Destination.SESSION)
            } else {
                state
            }
        }
        UiAction.ConfirmDemoStarted -> {
            if (state.sessionPhase == SessionPhase.CONNECTING) {
                state.copy(sessionPhase = SessionPhase.STREAMING)
            } else {
                state
            }
        }
        UiAction.StopDemo -> state.copy(sessionPhase = SessionPhase.STOPPED)
        UiAction.ToggleMetrics -> state.copy(metricsVisible = !state.metricsVisible)
        UiAction.ToggleLowLatency -> state.copy(lowLatency = !state.lowLatency)
        UiAction.ToggleTelemetry -> state.copy(telemetryEnabled = !state.telemetryEnabled)
        UiAction.ToggleMicrophone -> state.copy(microphoneEnabled = !state.microphoneEnabled)
        UiAction.CycleQuality -> {
            val next = when (state.quality) {
                StreamQuality.AUTO -> StreamQuality.HD_720
                StreamQuality.HD_720 -> StreamQuality.FULL_HD_1080
                StreamQuality.FULL_HD_1080 -> StreamQuality.AUTO
            }
            state.copy(quality = next)
        }
    }

