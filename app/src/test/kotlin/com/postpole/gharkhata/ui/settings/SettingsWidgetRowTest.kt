package com.postpole.gharkhata.ui.settings

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
                isPro = false, themeMode = ThemeMode.SYSTEM, dynamicColor = false, currency = "INR",
                reminderMinutes = null, showWidgetPin = supported, onWidgetPin = onPin,
            )
        }
    }

    @Test
    fun rowShownAndClickable_whenSupported() {
        var pins = 0
        show(true) { pins++ }
        compose.onNodeWithText("Home-screen widget").performScrollTo().performClick()
        assertEquals(1, pins)
    }

    @Test
    fun rowHidden_whenUnsupported() {
        show(false)
        compose.onNodeWithText("Home-screen widget").assertDoesNotExist()
    }
}
