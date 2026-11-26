package com.postpole.gharkhata.ui.provider

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.ui.theme.GharKhataTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ReorderProvidersSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private fun p(id: Long, name: String) = ProviderEntity(
        id = id, name = name, type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT, unitLabel = "L", startEpochDay = 20_000, createdAt = 0,
    )

    private val three = listOf(p(1, "Raju"), p(2, "Sita"), p(3, "Mohan"))

    private var saved: List<Long>? = null
    private var cancelled = false

    private fun show(providers: List<ProviderEntity> = three, fontScale: Float = 1f) {
        compose.setContent {
            GharKhataTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    ReorderProvidersContent(providers, onSave = { saved = it }, onCancel = { cancelled = true })
                }
            }
        }
    }

    @Test
    fun firstRowUpDisabled_lastRowDownDisabled() {
        show()
        compose.onNodeWithContentDescription("Move Raju up").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Raju down").assertIsEnabled()
        compose.onNodeWithContentDescription("Move Mohan down").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Mohan up").assertIsEnabled()
    }

    @Test
    fun moveDown_thenSave_emitsNewOrder() {
        show()
        compose.onNodeWithContentDescription("Move Raju down").performClick()
        compose.onNodeWithText("Save order").performClick()
        assertEquals(listOf(2L, 1L, 3L), saved)
    }

    @Test
    fun moveLastRowToFirst_thenSave() {
        show()
        compose.onNodeWithContentDescription("Move Mohan up").performClick()
        compose.onNodeWithContentDescription("Move Mohan up").performClick()
        compose.onNodeWithText("Save order").performClick()
        assertEquals(listOf(3L, 1L, 2L), saved)
    }

    @Test
    fun cancel_discardsChanges() {
        show()
        compose.onNodeWithContentDescription("Move Raju down").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(true, cancelled)
        assertNull(saved)
    }

    @Test
    fun arrows_haveContentDescriptions_andAtLeast48dp() {
        show()
        for (name in listOf("Raju", "Sita", "Mohan")) {
            for (dir in listOf("up", "down")) {
                compose.onNodeWithContentDescription("Move $name $dir")
                    .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            }
        }
    }

    @Test
    fun liveRegion_announcesNewPosition() {
        show()
        compose.onNodeWithContentDescription("Move Raju down").performClick()
        val node = compose.onNodeWithText("Raju, position 2 of 3").fetchSemanticsNode()
        assertEquals(
            "Raju, position 2 of 3",
            node.config[SemanticsProperties.Text].joinToString { it.text },
        )
        assertEquals(
            androidx.compose.ui.semantics.LiveRegionMode.Polite,
            node.config[SemanticsProperties.LiveRegion],
        )
    }

    @Test
    fun fontScale2_namesWrap_buttonsVisible() {
        val long = "Sharma Ji Fresh Dairy And Milk Products Supplier Number One"
        show(listOf(p(1, long), p(2, "Sita")), fontScale = 2f)
        compose.onNodeWithText(long).assertIsDisplayed()
        compose.onNodeWithText("Save order").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }
}
