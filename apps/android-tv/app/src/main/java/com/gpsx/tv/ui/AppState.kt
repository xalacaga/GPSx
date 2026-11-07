package com.gpsx.tv.ui

import androidx.annotation.StringRes
import com.gpsx.tv.R

/** A console's own space, opened from the console tiles of the home screen. */
enum class Environment { PS5, RETRO }

/**
 * Screens of the app. Those without an [environment] form the top bar (general items); the others
 * are the tabs of one console's environment, shown under its own bar with a way back to the consoles.
 */
enum class Destination(@StringRes val labelRes: Int, val environment: Environment? = null) {
    HOME(R.string.nav_home),
    SEARCH(R.string.nav_search),
    SESSION(R.string.nav_session, Environment.PS5),
    CLOUD(R.string.nav_cloud, Environment.PS5),
    PS5_SETTINGS(R.string.nav_ps5_settings, Environment.PS5),
    RETRO_GAMES(R.string.nav_retro_games, Environment.RETRO),
    RETRO_CONTROLS(R.string.nav_retro_controls, Environment.RETRO),
    CONTROLLERS(R.string.nav_controllers),
    DIAGNOSTICS(R.string.nav_diagnostics),
    SETTINGS(R.string.nav_settings),
    ABOUT(R.string.nav_about),
    ;

    companion object {
        val topLevel: List<Destination> = entries.filter { it.environment == null }

        fun tabsOf(environment: Environment): List<Destination> = entries.filter { it.environment == environment }
    }
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
    /** Share folder of the retro console whose environment is open. */
    val retroFolder: String? = null,
    val sessionPhase: SessionPhase = SessionPhase.READY,
    val quality: StreamQuality = StreamQuality.AUTO,
    val lowLatency: Boolean = true,
    val telemetryEnabled: Boolean = true,
    val microphoneEnabled: Boolean = false,
)

sealed interface UiAction {
    data class Navigate(val destination: Destination) : UiAction

    /** Opens the environment of the retro console stored in [folder] on the share. */
    data class OpenRetro(val folder: String) : UiAction

    data object StartDemo : UiAction
    data object ConfirmDemoStarted : UiAction
    data object StopDemo : UiAction
    data object ToggleLowLatency : UiAction
    data object ToggleTelemetry : UiAction
    data object ToggleMicrophone : UiAction
    data object CycleQuality : UiAction
}

/**
 * UI-only reducer for the offline prototype.
 *
 * It does not model a Remote Play session. M3 replaces its demo phase with the
 * portable Rust session state received through the coarse JNI bridge.
 */
fun reduce(state: AppUiState, action: UiAction): AppUiState =
    when (action) {
        is UiAction.Navigate -> state.copy(destination = action.destination)
        is UiAction.OpenRetro -> state.copy(destination = Destination.RETRO_GAMES, retroFolder = action.folder)
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
        UiAction.StopDemo -> {
            if (
                state.sessionPhase == SessionPhase.CONNECTING ||
                state.sessionPhase == SessionPhase.STREAMING ||
                state.sessionPhase == SessionPhase.FAILED
            ) {
                state.copy(sessionPhase = SessionPhase.STOPPED)
            } else {
                state
            }
        }
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

