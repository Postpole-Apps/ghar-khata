package com.postpole.gharkhata.billing

import android.app.Activity
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.analytics.AnalyticsParams
import com.postpole.gharkhata.analytics.NoOpAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns both entitlements. [isPro] (feature gates) follows only the Pro product; [adsRemoved] is true for
 * Pro OR `remove_ads`. Both are cached in DataStore for instant startup but reconciled with Play on every
 * connect, so a refund/revocation of one product never touches the other.
 */
class BillingRepository(
    private val gateway: BillingGateway,
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val maxConnectAttempts: Int = 5,
    private val initialBackoffMs: Long = 1_000,
    private val maxAcknowledgeAttempts: Int = 3,
    private val acknowledgeBackoffMs: Long = 500,
    private val log: (String) -> Unit = { Log.w("BillingRepository", it) },
    private val analytics: Analytics = NoOpAnalytics,
) {
    private enum class Source { PURCHASE_FLOW, REFRESH }

    @Volatile
    private var loadedPro: ProductResult.Loaded? = null

    /** Pro only: gates unlimited providers, bill image/PDF, CSV, reminder and year summary. */
    val isPro: StateFlow<Boolean> = dataStore.data
        .map { it[IS_PRO] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** True when the device owns `remove_ads` (says nothing about Pro). */
    val hasRemoveAds: StateFlow<Boolean> = dataStore.data
        .map { it[HAS_REMOVE_ADS] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Ads are hidden when Pro OR `remove_ads` is owned. */
    val adsRemoved: StateFlow<Boolean> = dataStore.data
        .map { it.adsRemoved() }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * The persisted ads entitlement with no initial placeholder: the first emission is the stored value,
     * so ads code never treats a paying user as free while DataStore is still loading.
     */
    val adsRemovedStoredUpdates: Flow<Boolean> = dataStore.data.map { it.adsRemoved() }.distinctUntilChanged()

    /** Reads the persisted Pro entitlement directly, so callers at startup never see [isPro]'s initial `false`. */
    suspend fun isProStored(): Boolean = dataStore.data.first()[IS_PRO] ?: false

    private fun Preferences.adsRemoved() = (this[IS_PRO] ?: false) || (this[HAS_REMOVE_ADS] ?: false)

    private val _price = MutableStateFlow<String?>(null)

    /** Localized Pro price from ProductDetails, null until loaded. */
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _removeAdsPrice = MutableStateFlow<String?>(null)

    /** Localized `remove_ads` price from ProductDetails, null until loaded (never typed in). */
    val removeAdsPrice: StateFlow<String?> = _removeAdsPrice.asStateFlow()

    private val _pending = MutableStateFlow(false)

    /** True while a Pro purchase is PENDING (not yet paid); Pro is not granted. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    private val _removeAdsPending = MutableStateFlow(false)

    /** True while a `remove_ads` purchase is PENDING. */
    val removeAdsPending: StateFlow<Boolean> = _removeAdsPending.asStateFlow()

    private val _productUnavailable = MutableStateFlow(false)

    /** True when Play answered but the Pro product/offer isn't available (so don't show "loading"). */
    val productUnavailable: StateFlow<Boolean> = _productUnavailable.asStateFlow()

    private val _removeAdsUnavailable = MutableStateFlow(false)

    /** Same as [productUnavailable] for `remove_ads`. */
    val removeAdsUnavailable: StateFlow<Boolean> = _removeAdsUnavailable.asStateFlow()

    private val _messages = Channel<BillingMessage>(Channel.BUFFERED)

    /** One-shot messages for the UI (purchase errors, launch failures). */
    val messages: Flow<BillingMessage> = _messages.receiveAsFlow()

    private val refreshLock = Mutex()
    private var started = false

    /** Call once at app start. Connects, loads product, restores purchases. */
    fun start() {
        if (started) return
        started = true
        scope.launch { gateway.updates.collect(::onUpdate) }
        scope.launch { gateway.disconnects.collect { refresh() } }
        scope.launch { refresh() }
    }

    /** Reconnects if needed, then re-queries product details and owned purchases. */
    suspend fun refresh(): Boolean = refreshLock.withLock {
        if (!connectWithRetry()) return false
        val products = gateway.queryProducts()
        applyProduct(products[BillingProduct.PRO], _price, _productUnavailable) { loadedPro = it }
        applyProduct(products[BillingProduct.REMOVE_ADS], _removeAdsPrice, _removeAdsUnavailable) {}
        val owned = gateway.queryPurchases()
        if (owned == null) {
            analytics.recordNonFatal(BillingException("Purchases query failed"))
            return false
        }
        applyPurchases(owned, revokeIfAbsent = true, source = Source.REFRESH)
        true
    }

    private fun applyProduct(
        result: ProductResult?,
        price: MutableStateFlow<String?>,
        unavailable: MutableStateFlow<Boolean>,
        onLoaded: (ProductResult.Loaded) -> Unit,
    ) {
        when (result) {
            is ProductResult.Loaded -> {
                onLoaded(result)
                price.value = result.formattedPrice
                unavailable.value = false
            }
            ProductResult.Unavailable, null -> {
                price.value = null
                unavailable.value = true
            }
            ProductResult.Failed -> analytics.recordNonFatal(BillingException("Product details query failed"))
        }
    }

    /** User-initiated restore: re-queries both products; logs no purchase-success event. */
    suspend fun restore(): Boolean = refresh()

    /** Call when the app comes to the foreground to pick up pending completions and refunds. */
    fun onForeground() {
        if (started) scope.launch { refresh() }
    }

    fun launchPurchase(activity: Activity, product: BillingProduct = BillingProduct.PRO): Boolean {
        val launched = gateway.launchPurchase(activity, product)
        if (launched) {
            // remove_ads_* events belong to gk-04; only Pro keeps its existing event.
            if (product == BillingProduct.PRO) analytics.logEvent(AnalyticsEvents.PURCHASE_STARTED)
        } else {
            analytics.recordNonFatal(BillingException("Could not launch purchase flow"))
            _messages.trySend(BillingMessage.LAUNCH_FAILED)
        }
        return launched
    }

    private suspend fun onUpdate(update: PurchaseUpdate) {
        when (update) {
            is PurchaseUpdate.Purchases ->
                applyPurchases(update.purchases, revokeIfAbsent = false, source = Source.PURCHASE_FLOW)
            PurchaseUpdate.AlreadyOwned -> refresh()
            PurchaseUpdate.Canceled -> Unit
            is PurchaseUpdate.Error -> {
                log("Purchase failed with code ${update.code}")
                analytics.recordNonFatal(BillingException("Purchase failed with code ${update.code}"))
                _messages.trySend(BillingMessage.PURCHASE_ERROR)
            }
        }
    }

    private suspend fun applyPurchases(purchases: List<OwnedPurchase>, revokeIfAbsent: Boolean, source: Source) {
        applyProductPurchases(BillingProduct.PRO, purchases, revokeIfAbsent, source, IS_PRO, _pending)
        applyProductPurchases(
            BillingProduct.REMOVE_ADS, purchases, revokeIfAbsent, source, HAS_REMOVE_ADS, _removeAdsPending,
        )
    }

    /** Each product is reconciled on its own, so revoking one never removes the other. */
    private suspend fun applyProductPurchases(
        product: BillingProduct,
        all: List<OwnedPurchase>,
        revokeIfAbsent: Boolean,
        source: Source,
        key: Preferences.Key<Boolean>,
        pending: MutableStateFlow<Boolean>,
    ) {
        val mine = all.filter { it.product == product }
        val purchased = mine.filter { it.status == PurchaseStatus.PURCHASED }
        pending.value = purchased.isEmpty() && mine.any { it.status == PurchaseStatus.PENDING }
        if (purchased.isNotEmpty()) {
            val was = dataStore.data.first()[key] ?: false
            dataStore.edit { it[key] = true }
            if (!was && product == BillingProduct.PRO) logPurchaseEvent(source)
            purchased.filter { !it.acknowledged }.forEach { acknowledgeWithRetry(it.token) }
        } else if (revokeIfAbsent) {
            dataStore.edit { it[key] = false }
        }
    }

    /** `purchase` (with value + currency) for a fresh buy, `purchase_restored` when found on refresh/restore. */
    private fun logPurchaseEvent(source: Source) {
        when (source) {
            Source.PURCHASE_FLOW -> {
                val product = loadedPro
                val params = buildMap<String, Any> {
                    if (product != null && product.priceMicros > 0 && product.currencyCode.isNotEmpty()) {
                        put(AnalyticsParams.VALUE, product.priceMicros / 1_000_000.0)
                        put(AnalyticsParams.CURRENCY, product.currencyCode)
                    }
                }
                analytics.logEvent(AnalyticsEvents.PURCHASE, params)
            }
            Source.REFRESH -> analytics.logEvent(AnalyticsEvents.PURCHASE_RESTORED)
        }
    }

    /** Play refunds unacknowledged purchases after 3 days, so retry now; the next refresh retries again. */
    private suspend fun acknowledgeWithRetry(token: String) {
        var backoff = acknowledgeBackoffMs
        repeat(maxAcknowledgeAttempts) { attempt ->
            if (gateway.acknowledge(token)) return
            log("Acknowledge failed (attempt ${attempt + 1}/$maxAcknowledgeAttempts)")
            if (attempt < maxAcknowledgeAttempts - 1) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        analytics.recordNonFatal(BillingException("Acknowledge failed after $maxAcknowledgeAttempts attempts"))
    }

    private suspend fun connectWithRetry(): Boolean {
        var backoff = initialBackoffMs
        repeat(maxConnectAttempts) { attempt ->
            if (gateway.connect()) return true
            if (attempt < maxConnectAttempts - 1) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        return false
    }

    private companion object {
        val IS_PRO = booleanPreferencesKey("is_pro")
        val HAS_REMOVE_ADS = booleanPreferencesKey("has_remove_ads")
        const val MAX_BACKOFF_MS = 15_000L
    }
}
