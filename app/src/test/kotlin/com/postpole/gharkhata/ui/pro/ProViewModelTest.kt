package com.postpole.gharkhata.ui.pro

import android.app.Activity
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.billing.BillingGateway
import com.postpole.gharkhata.billing.BillingRepository
import com.postpole.gharkhata.billing.OwnedPurchase
import com.postpole.gharkhata.billing.Product
import com.postpole.gharkhata.billing.ProductResult
import com.postpole.gharkhata.billing.PurchaseStatus
import com.postpole.gharkhata.billing.PurchaseUpdate
import com.postpole.gharkhata.prompt.NoOpProPromptStore
import com.postpole.gharkhata.prompt.ProPromptStore
import com.postpole.gharkhata.prompt.ProPromptTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import java.io.File

private class RecordingAnalytics : Analytics {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    override fun logEvent(name: String, params: Map<String, Any>) { events += name to params }
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun recordNonFatal(throwable: Throwable) = Unit
}

private class RecordingStore : ProPromptStore by NoOpProPromptStore {
    val shown = mutableListOf<ProPromptTrigger>()
    val dismissed = mutableListOf<ProPromptTrigger>()
    var hardGates = 0
    override suspend fun markShown(trigger: ProPromptTrigger) { shown += trigger }
    override suspend fun markDismissed(trigger: ProPromptTrigger) { dismissed += trigger }
    override suspend fun markHardGate() { hardGates++ }
}

private class StubGateway : BillingGateway {
    val updatesFlow = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 16)
    override val updates: Flow<PurchaseUpdate> = updatesFlow
    override val disconnects: Flow<Unit> = MutableSharedFlow()
    override suspend fun connect() = true
    override suspend fun queryProducts() = mapOf<Product, ProductResult>(
        Product.PRO to ProductResult.Loaded("PRICE"),
        Product.REMOVE_ADS to ProductResult.Loaded("ADS-PRICE"),
    )
    override suspend fun queryPurchases(): List<OwnedPurchase>? = emptyList()
    override fun launchPurchase(activity: Activity, product: Product) = true
    override suspend fun acknowledge(token: String) = true
}

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ProViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val analytics = RecordingAnalytics()
    private val store = RecordingStore()
    private val gateway = StubGateway()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun kotlinx.coroutines.test.TestScope.vm(): ProViewModel {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "b.preferences_pb") }
        val billing = BillingRepository(gateway, dataStore, scope, log = {}, analytics = analytics)
        billing.start()
        return ProViewModel(billing, analytics, store)
    }

    private fun dismissEvents() = analytics.events.filter { it.first == "pro_prompt_dismissed" }

    @Test fun autoSheetDismissMethodsMapToCloseNotNowSwipe() = runTest {
        val vm = vm()
        ProDismissMethod.entries.forEach { vm.onDismissed(ProReason.AUTO_BILL_MILESTONE, it) }
        assertEquals(
            listOf("close", "not_now", "swipe"),
            dismissEvents().map { it.second["method"] },
        )
        dismissEvents().forEach {
            assertEquals("bill_milestone", it.second["trigger"])
            assertEquals("sheet", it.second["surface"])
        }
        assertEquals(3, store.dismissed.size)
    }

    @Test fun methodValuesAreExactlyTheThreeEnums() {
        assertEquals(listOf("close", "not_now", "swipe"), ProDismissMethod.entries.map { it.analyticsName })
    }

    @Test fun manualModeDoesNotCountAsPromptDismissal() = runTest {
        val vm = vm()
        ProReason.entries.filter { it.autoTrigger == null }.forEach { reason ->
            ProDismissMethod.entries.forEach { vm.onDismissed(reason, it) }
        }
        assertTrue(dismissEvents().isEmpty())
        assertTrue(store.dismissed.isEmpty())
    }

    @Test fun autoSheetShownLogsPromptAndAutoTriggerOnProScreenView() = runTest {
        vm().onShown(ProReason.AUTO_BILL_MILESTONE)
        advanceUntilIdle()
        assertEquals(mapOf("trigger" to "auto_bill_milestone"), analytics.events.single { it.first == "pro_screen_view" }.second)
        assertEquals(
            mapOf("trigger" to "bill_milestone", "surface" to "sheet"),
            analytics.events.single { it.first == "pro_prompt_shown" }.second,
        )
        assertEquals(listOf(ProPromptTrigger.BILL_MILESTONE), store.shown)
    }

    @Test fun cardCtaSheetCarriesAutoTriggerWithoutExtraPromptEvents() = runTest {
        vm().onShown(ProReason.AUTO_MARKED_DAYS_MILESTONE)
        advanceUntilIdle()
        assertEquals(mapOf("trigger" to "auto_marked_days_milestone"), analytics.events.single { it.first == "pro_screen_view" }.second)
        assertTrue(analytics.events.none { it.first == "pro_prompt_shown" })
        assertTrue(store.shown.isEmpty())
    }

    @Test fun manualReasonsKeepPlainProScreenView() = runTest {
        vm().onShown(ProReason.CSV)
        advanceUntilIdle()
        assertEquals(emptyMap<String, Any>(), analytics.events.single { it.first == "pro_screen_view" }.second)
    }

    @Test fun hardGateReasonsStartCooldownOthersDoNot() = runTest {
        val vm = vm()
        ProReason.entries.forEach { vm.onShown(it) }
        advanceUntilIdle()
        assertEquals(5, store.hardGates) // PROVIDER_LIMIT, BILL_IMAGE, CSV, YEAR_SUMMARY, REMINDER
    }

    @Test fun purchaseEventsCarryAutoTrigger() = runTest {
        val vm = vm()
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        advanceUntilIdle()
        vm.buy(activity, ProReason.AUTO_BILL_MILESTONE)
        assertEquals("auto_bill_milestone", analytics.events.single { it.first == "purchase_started" }.second["trigger"])
        assertEquals(
            mapOf("trigger" to "bill_milestone", "surface" to "sheet"),
            analytics.events.single { it.first == "pro_prompt_cta_tap" }.second,
        )
        gateway.updatesFlow.emit(
            PurchaseUpdate.Purchases(listOf(OwnedPurchase(Product.PRO, "tok", PurchaseStatus.PURCHASED, true))),
        )
        advanceUntilIdle()
        assertEquals("auto_bill_milestone", analytics.events.single { it.first == "purchase" }.second["trigger"])
    }

    @Test fun manualPurchaseHasNoTrigger() = runTest {
        val vm = vm()
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        advanceUntilIdle()
        vm.buy(activity, ProReason.BILL_IMAGE)
        assertTrue("trigger" !in analytics.events.single { it.first == "purchase_started" }.second)
        assertTrue(analytics.events.none { it.first == "pro_prompt_cta_tap" })
    }
}
