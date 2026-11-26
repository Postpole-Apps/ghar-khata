package com.postpole.gharkhata.ui.provider

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.ui.theme.GharKhataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ReorderMenuItemTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 12)

    private fun row(id: Long, name: String): ProviderRow {
        val p = ProviderEntity(id = id, name = name, type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT, unitLabel = "L", startEpochDay = 20_000, createdAt = 0)
        return ProviderRow(p, 0, 6_000, ProviderListViewModel.strip(p, emptyList(), today.toEpochDay()))
    }

    private fun showWith(count: Int) {
        val state = ProviderListState(active = (1..count).map { row(it.toLong(), "P$it") }, isPro = true, loaded = true)
        compose.setContent {
            GharKhataTheme(darkTheme = false) {
                ProviderListContent(state, onOpenProvider = {}, onAddProvider = {}, onSeePro = {}, today = today)
            }
        }
        compose.onNodeWithContentDescription("More options").performClick()
    }

    @Test
    fun menuItem_disabledWithOneProvider() {
        showWith(1)
        compose.onNodeWithText("Reorder providers").assertIsNotEnabled()
    }

    @Test
    fun menuItem_enabledWithTwoProviders() {
        showWith(2)
        compose.onNodeWithText("Reorder providers").assertIsEnabled()
    }
}
