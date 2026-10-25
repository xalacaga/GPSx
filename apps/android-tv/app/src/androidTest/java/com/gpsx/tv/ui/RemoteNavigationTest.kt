package com.gpsx.tv.ui

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gpsx.tv.MainActivity
import com.gpsx.tv.R
import java.util.Locale
import org.junit.Assert.assertEquals
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
                Triple(2, "Session", "Session de démonstration"),
                Triple(3, "Manettes", "Deux DualSense physiques"),
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
}
