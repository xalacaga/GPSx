package com.gpsx.tv.ui

import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gpsx.tv.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeTextLegalTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyLegalNoticeIsReachableAtOnePointThreeFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.3f),
            ) {
                GpsxTheme {
                    GpsxApp()
                }
            }
        }

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(7) { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)

        composeRule.onNodeWithText("À propos de GPSx").assertIsDisplayed()
        composeRule.onNodeWithText("Informations légales et source exacte de cette version.").assertIsDisplayed()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNodeWithText("Copyright © 2026 contributeurs GPSx").assertIsDisplayed()
        composeRule.onNodeWithText(
            "GPSx est un logiciel libre sous GNU AGPL-3.0-only avec permission additionnelle OpenSSL.",
        ).assertIsDisplayed()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNodeWithText("GPSx est fourni sans aucune garantie, dans les limites permises par la loi.").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Vous pouvez redistribuer et modifier GPSx selon les conditions de sa licence.",
        ).assertIsDisplayed()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNodeWithText("Code source correspondant").assertIsDisplayed()
        composeRule.onNodeWithText(BuildConfig.SOURCE_REPOSITORY_URL).assertIsDisplayed()
        assertFullyWithinWindow(BuildConfig.SOURCE_REPOSITORY_URL)

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNodeWithText("Révision installée").assertIsDisplayed()
        composeRule.onNodeWithText(BuildConfig.SOURCE_REVISION).assertIsDisplayed()
        composeRule.onNode(
            hasText("Révision installée") and hasText(BuildConfig.SOURCE_REVISION),
        ).assertIsFocused()
        assertFullyWithinWindow(BuildConfig.SOURCE_REVISION)
    }

    private fun assertFullyWithinWindow(text: String) {
        val bounds = composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        val displayMetrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        assertTrue("$text starts outside the window: $bounds", bounds.left >= 0f && bounds.top >= 0f)
        assertTrue(
            "$text ends outside the window: $bounds",
            bounds.right <= displayMetrics.widthPixels && bounds.bottom <= displayMetrics.heightPixels,
        )
    }
}
