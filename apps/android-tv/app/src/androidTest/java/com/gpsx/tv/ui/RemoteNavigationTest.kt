package com.gpsx.tv.ui

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gpsx.tv.MainActivity
import com.gpsx.tv.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyPrimaryDestinationOpensWithDpadAndSelect() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val destinations =
            listOf(
                Triple(1, "Consoles", "Vos consoles"),
                Triple(2, "Session", "Session PS5"),
                Triple(3, "Manettes", "Manettes Bluetooth"),
                Triple(4, "Diagnostics", "Santé de la session"),
                Triple(5, "Paramètres", "Préférences de lecture"),
                Triple(6, "À propos", "À propos de GPSx"),
            )

        destinations.forEach { (downPresses, navigationLabel, title) ->
            repeat(10) { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP) }
            composeRule.onNode(hasText("Accueil") and hasClickAction()).assertIsFocused().assertIsSelected()
            repeat(downPresses) {
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            }
            val navigationNode = composeRule.onNode(hasText(navigationLabel) and hasClickAction())
            navigationNode.assertIsFocused()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)

            composeRule.waitUntil(timeoutMillis = 2_000) {
                composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(title).assertIsDisplayed()
            assertFullyWithinWindow(title)
            navigationNode.assertIsFocused().assertIsSelected()

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            composeRule.waitUntil(timeoutMillis = 2_000) {
                composeRule.onAllNodesWithText("Bonsoir.").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Bonsoir.").assertIsDisplayed()
            composeRule.onNode(hasText("Accueil") and hasClickAction()).assertIsSelected()
        }
    }

    @Test
    fun englishConfigurationFallsBackToFrenchResources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val englishContext = context.createConfigurationContext(configuration)

        assertEquals("Accueil", englishContext.getString(R.string.nav_home))
        assertEquals("Votre PS5, sur le meilleur écran.", englishContext.getString(R.string.hero_title))
        assertEquals("À propos de GPSx", englishContext.getString(R.string.about_title))
        assertEquals("Révision installée", englishContext.getString(R.string.legal_build))
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun registrationFieldsTraverseWithVerticalDpad() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(10) { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP) }
        repeat(2) { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithText("Session PS5").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("registration-host").performScrollTo()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        composeRule.onNodeWithTag("registration-host").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("registration-pin").assertIsFocused()
        composeRule.onNodeWithTag("registration-pin").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("registration-account-id").assertIsFocused()
        composeRule.onNodeWithTag("registration-account-id").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("registration-submit").assertIsFocused()
        composeRule.onNodeWithTag("registration-submit").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("registration-account-id").assertIsFocused()
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
