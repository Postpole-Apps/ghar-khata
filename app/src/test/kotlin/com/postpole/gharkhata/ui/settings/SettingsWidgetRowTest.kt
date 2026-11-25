package com.postpole.gharkhata.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.postpole.gharkhata.data.ThemeMode
import com.postpole.gharkhata.ui.theme.GharKhataTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class SettingsWidgetRowTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(supported: Boolean, onPin: () -> Unit = {}) = compose.setContent {
        GharKhataTheme(dynamicColor = false) {
            SettingsContent(
                isPro = true, themeMode = ThemeMode.SYSTEM, dynamicColor = false, currency = "INR",
                reminderMinutes = null, showWidgetPin = supported, onWidgetPin = onPin,
            )
        }
    }

    @Test
    fun rowShownAndInvoked_whenPinSupported() {
        var pinned = 0
        show(true) { pinned++ }
        compose.onNodeWithText("Home-screen widget").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, pinned)
    }

    @Test
    fun rowHidden_whenPinUnsupported() {
        show(false)
        compose.onNodeWithText("Home-screen widget").assertDoesNotExist()
    }
}
