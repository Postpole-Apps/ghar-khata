package com.postpole.gharkhata.billing

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

private class FakeGateway : BillingGateway {
    val updatesFlow = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 16)
    override val updates: Flow<PurchaseUpdate> = updatesFlow
    val disconnectsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val disconnects: Flow<Unit> = disconnectsFlow

    var connectFailures = 0
    var connectCalls = 0
    var queryPurchasesCalls = 0
    var product: ProductResult = ProductResult.Loaded("$4.99")
    var removeAdsProduct: ProductResult = ProductResult.Loaded("ADS-PRICE")
    var lastLaunched: Product? = null
    var owned: List<OwnedPurchase>? = emptyList()
    var acknowledgeFailures = 0
    val acknowledged = mutableListOf<String>()

    override suspend fun connect(): Boolean {
        connectCalls++
        if (connectFailures > 0) {
            connectFailures--
            return false
        }
        return true
    }

    override suspend fun queryProducts() =
        mapOf(Product.PRO to product, Product.REMOVE_ADS to removeAdsProduct)
    override suspend fun queryPurchases(): List<OwnedPurchase>? {
        queryPurchasesCalls++
        return owned
    }
    override fun launchPurchase(activity: Activity, product: Product): Boolean {
        lastLaunched = product
        return launchResult
    }
    var launchResult = true
    override suspend fun acknowledge(token: String): Boolean {
        acknowledged += token
        if (acknowledgeFailures > 0) {
            acknowledgeFailures--
            return false
        }
        return true
    }
}

@RunWith(org.robolectric.RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BillingRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val gateway = FakeGateway()
    private lateinit var dataStore: DataStore<Preferences>

    private fun TestScope.repo(): BillingRepository {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") }
        return BillingRepository(
            gateway, dataStore, scope,
            maxConnectAttempts = 3, initialBackoffMs = 10,
            maxAcknowledgeAttempts = 3, acknowledgeBackoffMs = 10,
            log = {},
        )
    }

    private val purchased = OwnedPurchase(Product.PRO, "tok", PurchaseStatus.PURCHASED, acknowledged = false)
    private val adsPurchased = OwnedPurchase(Product.REMOVE_ADS, "ads", PurchaseStatus.PURCHASED, acknowledged = false)

    @Test fun purchased_grantsProAndAcknowledges() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertEquals(listOf("tok"), gateway.acknowledged)
        assertEquals("$4.99", repo.price.value)
    }

    @Test fun alreadyAcknowledged_isNotAcknowledgedAgain() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertTrue(gateway.acknowledged.isEmpty())
    }

    @Test fun pending_doesNotGrantPro() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        gateway.updatesFlow.emit(
            PurchaseUpdate.Purchases(listOf(OwnedPurchase(Product.PRO, "tok", PurchaseStatus.PENDING, false))),
        )
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
        assertTrue(repo.pending.value)
        assertTrue(gateway.acknowledged.isEmpty())
    }

    @Test fun pending_thenPurchased_grantsPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(OwnedPurchase(Product.PRO, "tok", PurchaseStatus.PENDING, false))
        repo.start()
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
        gateway.owned = listOf(purchased)
        repo.restore()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertFalse(repo.pending.value)
    }

    @Test fun itemAlreadyOwned_restoresFromPlay() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        gateway.updatesFlow.emit(PurchaseUpdate.AlreadyOwned)
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
    }

    @Test fun refund_revokesCachedPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        gateway.owned = emptyList() // refunded / revoked
        repo.restore()
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
    }

    @Test fun failedQuery_keepsCachedPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        repo.start()
        advanceUntilIdle()
        gateway.owned = null // Play query failed: must not revoke
        repo.restore()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
    }

    @Test fun connect_retriesWithBackoff() = runTest {
        val repo = repo()
        gateway.connectFailures = 2
        gateway.owned = listOf(purchased)
        repo.start()
        advanceUntilIdle()
        assertEquals(3, gateway.connectCalls)
        assertTrue(repo.isPro.value)
    }

    @Test fun acknowledgeFailure_isRetried() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        gateway.acknowledgeFailures = 2
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertEquals(listOf("tok", "tok", "tok"), gateway.acknowledged)
    }

    @Test fun acknowledgeFailure_givesUpAfterMaxAttemptsButKeepsPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        gateway.acknowledgeFailures = 10
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertEquals(3, gateway.acknowledged.size)
    }

    @Test fun disconnect_triggersRefresh() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        val before = gateway.queryPurchasesCalls
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        gateway.disconnectsFlow.emit(Unit)
        advanceUntilIdle()
        assertEquals(before + 1, gateway.queryPurchasesCalls)
        assertTrue(repo.isPro.value)
    }

    @Test fun foreground_refreshesPurchases() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        repo.onForeground()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
    }

    @Test fun unspecifiedState_doesNotGrantPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(OwnedPurchase(Product.PRO, "tok", PurchaseStatus.UNSPECIFIED, false))
        repo.start()
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
        assertFalse(repo.pending.value)
        assertTrue(gateway.acknowledged.isEmpty())
    }

    @Test fun canceled_leavesStateUnchanged() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        gateway.updatesFlow.emit(PurchaseUpdate.Canceled)
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
        assertFalse(repo.pending.value)
        assertEquals("$4.99", repo.price.value)
    }

    @Test fun error_leavesStateUnchangedAndEmitsMessage() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        repo.start()
        advanceUntilIdle()
        gateway.updatesFlow.emit(PurchaseUpdate.Error(6))
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertFalse(repo.pending.value)
        assertEquals(BillingMessage.PURCHASE_ERROR, repo.messages.first())
    }

    @Test fun missingProduct_marksUnavailable() = runTest {
        val repo = repo()
        gateway.product = ProductResult.Unavailable
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.productUnavailable.value)
        assertEquals(null, repo.price.value)
        assertFalse(repo.isPro.value)
    }

    @Test fun failedProductQuery_isNotUnavailable() = runTest {
        val repo = repo()
        gateway.product = ProductResult.Failed
        repo.start()
        advanceUntilIdle()
        assertFalse(repo.productUnavailable.value)
        assertEquals(null, repo.price.value)
    }

    @Test fun removeAdsOnly_hidesAdsButNotPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(adsPurchased)
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.hasRemoveAds.value)
        assertTrue(repo.adsRemoved.value)
        assertFalse(repo.isPro.value)
        assertEquals(listOf("ads"), gateway.acknowledged)
        assertEquals("ADS-PRICE", repo.removeAdsPrice.value)
    }

    @Test fun proOnly_hidesAdsWithoutRemoveAds() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased)
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertTrue(repo.adsRemoved.value)
        assertFalse(repo.hasRemoveAds.value)
    }

    @Test fun neitherOwned_adsStayOn() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        assertFalse(repo.adsRemoved.value)
        assertFalse(repo.adsRemovedStoredUpdates.first())
    }

    @Test fun restore_unlocksBothProducts() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        gateway.owned = listOf(purchased.copy(acknowledged = true), adsPurchased.copy(acknowledged = true))
        repo.restore()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertTrue(repo.hasRemoveAds.value)
    }

    @Test fun refundOfOne_leavesTheOtherIntact() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased.copy(acknowledged = true), adsPurchased.copy(acknowledged = true))
        repo.start()
        advanceUntilIdle()
        gateway.owned = listOf(adsPurchased.copy(acknowledged = true)) // Pro refunded
        repo.restore()
        advanceUntilIdle()
        assertFalse(repo.isPro.value)
        assertTrue(repo.hasRemoveAds.value)
        assertTrue(repo.adsRemoved.value)
        gateway.owned = listOf(purchased.copy(acknowledged = true)) // Remove ads refunded, Pro back
        repo.restore()
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertFalse(repo.hasRemoveAds.value)
        assertTrue(repo.adsRemoved.value)
    }

    @Test fun purchaseFlowForOne_doesNotTouchTheOther() = runTest {
        val repo = repo()
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        repo.start()
        advanceUntilIdle()
        gateway.updatesFlow.emit(PurchaseUpdate.Purchases(listOf(adsPurchased)))
        advanceUntilIdle()
        assertTrue(repo.isPro.value)
        assertTrue(repo.hasRemoveAds.value)
    }

    @Test fun removeAdsPending_doesNotGrantOrAffectProPending() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        gateway.updatesFlow.emit(
            PurchaseUpdate.Purchases(listOf(OwnedPurchase(Product.REMOVE_ADS, "ads", PurchaseStatus.PENDING, false))),
        )
        advanceUntilIdle()
        assertTrue(repo.removeAdsPending.value)
        assertFalse(repo.pending.value)
        assertFalse(repo.adsRemoved.value)
    }

    @Test fun missingRemoveAdsProduct_marksOnlyThatUnavailable() = runTest {
        val repo = repo()
        gateway.removeAdsProduct = ProductResult.Unavailable
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.removeAdsUnavailable.value)
        assertEquals(null, repo.removeAdsPrice.value)
        assertFalse(repo.productUnavailable.value)
        assertEquals("$4.99", repo.price.value)
    }

    private class RecordingAnalytics : com.postpole.gharkhata.analytics.Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val nonFatals = mutableListOf<Throwable>()
        override fun logEvent(name: String, params: Map<String, Any>) { events += name to params }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) { nonFatals += throwable }
    }

    private fun TestScope.repoWith(analytics: com.postpole.gharkhata.analytics.Analytics): BillingRepository {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "a.preferences_pb") }
        return BillingRepository(
            gateway, dataStore, scope,
            maxConnectAttempts = 3, initialBackoffMs = 10,
            maxAcknowledgeAttempts = 3, acknowledgeBackoffMs = 10,
            log = {}, analytics = analytics,
        )
    }

    @Test fun purchaseFlow_logsPurchaseWithValueAndCurrency() = runTest {
        val analytics = RecordingAnalytics()
        val repo = repoWith(analytics)
        gateway.product = ProductResult.Loaded("₹499", priceMicros = 499_000_000, currencyCode = "INR")
        repo.start()
        advanceUntilIdle()
        gateway.updatesFlow.emit(PurchaseUpdate.Purchases(listOf(purchased)))
        advanceUntilIdle()
        val purchase = analytics.events.single { it.first == "purchase" }.second
        assertEquals(499.0, purchase["value"])
        assertEquals("INR", purchase["currency"])
    }

    @Test fun refreshFindingPurchase_logsRestoredNotPurchase() = runTest {
        val analytics = RecordingAnalytics()
        val repo = repoWith(analytics)
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        repo.start()
        advanceUntilIdle()
        assertEquals(listOf("purchase_restored"), analytics.events.map { it.first })
    }

    @Test fun purchaseError_isRecordedAsNonFatal() = runTest {
        val analytics = RecordingAnalytics()
        val repo = repoWith(analytics)
        repo.start()
        advanceUntilIdle()
        gateway.updatesFlow.emit(PurchaseUpdate.Error(6))
        advanceUntilIdle()
        assertTrue(analytics.nonFatals.single() is BillingException)
    }

    @Test fun connect_givesUpAfterMaxAttempts() = runTest {
        val repo = repo()
        gateway.connectFailures = 10
        repo.start()
        advanceUntilIdle()
        assertEquals(3, gateway.connectCalls)
        assertFalse(repo.isPro.value)
    }

    // ---- Remove ads vs Pro analytics split ----

    private val activity: Activity = org.robolectric.Robolectric.buildActivity(Activity::class.java).get()

    private suspend fun TestScope.started(analytics: RecordingAnalytics): BillingRepository {
        val repo = repoWith(analytics)
        gateway.product = ProductResult.Loaded("₹499", 499_000_000, "INR", "pro_id")
        gateway.removeAdsProduct = ProductResult.Loaded("₹49", 49_000_000, "INR", "remove_ads")
        repo.start()
        advanceUntilIdle()
        return repo
    }

    @Test fun removeAds_start_cancel_logsStartThenFail_notProEvents() = runTest {
        val analytics = RecordingAnalytics()
        val repo = started(analytics)
        repo.launchPurchase(activity, Product.REMOVE_ADS)
        gateway.updatesFlow.emit(PurchaseUpdate.Canceled)
        advanceUntilIdle()
        assertEquals(listOf("remove_ads_purchase_start", "remove_ads_purchase_fail"), analytics.events.map { it.first })
        val fail = analytics.events.last().second
        assertEquals("remove_ads", fail["item_id"])
        assertEquals(49_000_000L, fail["price_micros"])
        assertEquals("INR", fail["currency"])
        assertEquals("canceled", fail["reason"])
    }

    @Test fun removeAds_success_logsOnceAndNoProPurchase() = runTest {
        val analytics = RecordingAnalytics()
        val repo = started(analytics)
        repo.launchPurchase(activity, Product.REMOVE_ADS)
        gateway.updatesFlow.emit(PurchaseUpdate.Purchases(listOf(adsPurchased)))
        advanceUntilIdle()
        assertEquals(listOf("remove_ads_purchase_start", "remove_ads_purchase_success"), analytics.events.map { it.first })
        val ok = analytics.events.last().second
        assertEquals("remove_ads", ok["item_id"])
        assertEquals(49.0, ok["value"])
        assertEquals("INR", ok["currency"])
    }

    @Test fun removeAds_restoreOrRefresh_logsNoSuccess() = runTest {
        val analytics = RecordingAnalytics()
        gateway.owned = listOf(adsPurchased.copy(acknowledged = true))
        val repo = started(analytics)
        repo.restore()
        advanceUntilIdle()
        assertTrue(analytics.events.isEmpty())
    }

    @Test fun removeAds_launchFailure_andBillingError_logFail() = runTest {
        val analytics = RecordingAnalytics()
        val repo = started(analytics)
        gateway.launchResult = false
        repo.launchPurchase(activity, Product.REMOVE_ADS)
        assertEquals(listOf("remove_ads_purchase_fail"), analytics.events.map { it.first })
        assertEquals("launch_failed", analytics.events.single().second["reason"])
        analytics.events.clear()
        gateway.launchResult = true
        repo.launchPurchase(activity, Product.REMOVE_ADS)
        gateway.updatesFlow.emit(PurchaseUpdate.Error(6))
        advanceUntilIdle()
        assertEquals("billing_error", analytics.events.last().second["reason"])
    }

    @Test fun pro_purchase_keepsProEventsWithProductAndPrice() = runTest {
        val analytics = RecordingAnalytics()
        val repo = started(analytics)
        repo.launchPurchase(activity, Product.PRO)
        gateway.updatesFlow.emit(PurchaseUpdate.Purchases(listOf(purchased)))
        advanceUntilIdle()
        assertEquals(listOf("purchase_started", "purchase"), analytics.events.map { it.first })
        for ((_, p) in analytics.events) {
            assertEquals("pro_id", p["item_id"])
            assertEquals(499.0, p["value"])
            assertEquals("INR", p["currency"])
        }
    }

    @Test fun pro_cancelOrError_logsNoRemoveAdsFail() = runTest {
        val analytics = RecordingAnalytics()
        val repo = started(analytics)
        repo.launchPurchase(activity, Product.PRO)
        gateway.updatesFlow.emit(PurchaseUpdate.Canceled)
        advanceUntilIdle()
        assertEquals(listOf("purchase_started"), analytics.events.map { it.first })
    }

    @Test fun removeAds_alreadyOwned_logsFailWithAlreadyOwnedReason() = runTest {
        val analytics = RecordingAnalytics()
        val repo = started(analytics)
        repo.launchPurchase(activity, Product.REMOVE_ADS)
        gateway.updatesFlow.emit(PurchaseUpdate.AlreadyOwned)
        advanceUntilIdle()
        assertEquals(listOf("remove_ads_purchase_start", "remove_ads_purchase_fail"), analytics.events.map { it.first })
        val fail = analytics.events.last().second
        assertEquals("already_owned", fail["reason"])
        assertEquals("remove_ads", fail["item_id"])
    }

    @Test fun pro_alreadyOwned_logsNoRemoveAdsFail() = runTest {
        val analytics = RecordingAnalytics()
        val repo = started(analytics)
        repo.launchPurchase(activity, Product.PRO)
        gateway.updatesFlow.emit(PurchaseUpdate.AlreadyOwned)
        advanceUntilIdle()
        assertEquals(listOf("purchase_started"), analytics.events.map { it.first })
    }

    @Test fun pro_restore_carriesPriceValueAndCurrencyWhenKnown() = runTest {
        val analytics = RecordingAnalytics()
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        started(analytics)
        val restored = analytics.events.single { it.first == "purchase_restored" }.second
        assertEquals("pro_id", restored["item_id"])
        assertEquals(499_000_000L, restored["price_micros"])
        assertEquals(499.0, restored["value"])
        assertEquals("INR", restored["currency"])
    }

    @Test fun pro_restore_withoutKnownPrice_hasNoValueOrCurrency() = runTest {
        val analytics = RecordingAnalytics()
        gateway.owned = listOf(purchased.copy(acknowledged = true))
        gateway.product = ProductResult.Unavailable
        val repo = repoWith(analytics)
        repo.start()
        advanceUntilIdle()
        val restored = analytics.events.single { it.first == "purchase_restored" }.second
        assertFalse(restored.containsKey("value"))
        assertFalse(restored.containsKey("currency"))
        assertFalse(restored.containsKey("price_micros"))
    }
}
