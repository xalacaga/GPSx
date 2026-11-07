package com.gpsx.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateTest {
    @Test
    fun `navigation changes only destination`() {
        val initial = AppUiState(lowLatency = false)
        val result = reduce(initial, UiAction.Navigate(Destination.DIAGNOSTICS))

        assertEquals(Destination.DIAGNOSTICS, result.destination)
        assertFalse(result.lowLatency)
        assertEquals(SessionPhase.READY, result.sessionPhase)
    }

    @Test
    fun `top bar holds general items, environments hold console tabs`() {
        assertEquals(
            listOf(Destination.HOME, Destination.SEARCH, Destination.CONTROLLERS, Destination.DIAGNOSTICS, Destination.SETTINGS, Destination.ABOUT),
            Destination.topLevel,
        )
        assertEquals(listOf(Destination.SESSION, Destination.CLOUD, Destination.PS5_SETTINGS), Destination.tabsOf(Environment.PS5))
        assertEquals(listOf(Destination.RETRO_GAMES, Destination.RETRO_CONTROLS), Destination.tabsOf(Environment.RETRO))
    }

    @Test
    fun `opening a retro console keeps its folder across its tabs`() {
        val opened = reduce(AppUiState(), UiAction.OpenRetro("neogeo"))
        val controls = reduce(opened, UiAction.Navigate(Destination.RETRO_CONTROLS))

        assertEquals(Destination.RETRO_GAMES, opened.destination)
        assertEquals("neogeo", controls.retroFolder)
        assertEquals(Destination.RETRO_CONTROLS, controls.destination)
    }

    @Test
    fun `demo follows explicit lifecycle`() {
        val connecting = reduce(AppUiState(), UiAction.StartDemo)
        val streaming = reduce(connecting, UiAction.ConfirmDemoStarted)
        val stopped = reduce(streaming, UiAction.StopDemo)

        assertEquals(SessionPhase.CONNECTING, connecting.sessionPhase)
        assertEquals(Destination.SESSION, connecting.destination)
        assertEquals(SessionPhase.STREAMING, streaming.sessionPhase)
        assertEquals(SessionPhase.STOPPED, stopped.sessionPhase)
    }

    @Test
    fun `confirmation cannot start an idle session`() {
        assertEquals(AppUiState(), reduce(AppUiState(), UiAction.ConfirmDemoStarted))
    }

    @Test
    fun `stop cannot move a ready demo to stopped`() {
        assertEquals(AppUiState(), reduce(AppUiState(), UiAction.StopDemo))
    }

    @Test
    fun `about screen is reachable through normal navigation`() {
        val result = reduce(AppUiState(), UiAction.Navigate(Destination.ABOUT))

        assertEquals(Destination.ABOUT, result.destination)
        assertEquals(SessionPhase.READY, result.sessionPhase)
    }

    @Test
    fun `quality cycles deterministically`() {
        var state = AppUiState()
        state = reduce(state, UiAction.CycleQuality)
        assertEquals(StreamQuality.HD_720, state.quality)
        state = reduce(state, UiAction.CycleQuality)
        assertEquals(StreamQuality.FULL_HD_1080, state.quality)
        state = reduce(state, UiAction.CycleQuality)
        assertEquals(StreamQuality.AUTO, state.quality)
    }

    @Test
    fun `settings toggles are independent`() {
        val initial = AppUiState()
        val result = reduce(reduce(initial, UiAction.ToggleLowLatency), UiAction.ToggleMicrophone)

        assertFalse(result.lowLatency)
        assertTrue(result.microphoneEnabled)
        assertTrue(result.telemetryEnabled)
    }

}
