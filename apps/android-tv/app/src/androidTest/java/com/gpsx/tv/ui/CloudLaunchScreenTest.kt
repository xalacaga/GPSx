package com.gpsx.tv.ui

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudLaunchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun launchScreenShowsProgressAndCancelHoldsFocus() {
        var cancels = 0
        composeRule.setContent {
            GpsxTheme {
                CloudLaunchScreen(
                    title = "Jeu de test",
                    detail = "Réservation d’un serveur pour Jeu de test…",
                    step = 0,
                    onCancel = { cancels++ },
                )
            }
        }

        composeRule.onNodeWithText("Jeu de test").assertIsDisplayed()
        composeRule.onNodeWithText("1. Réservation du serveur").assertIsDisplayed()
        composeRule.onNodeWithText("Patientez", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("cloud-launch-cancel").assertIsFocused()

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
        composeRule.onNodeWithTag("cloud-launch-cancel").assertIsFocused()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.waitForIdle()
        assertEquals(1, cancels)
    }
}
