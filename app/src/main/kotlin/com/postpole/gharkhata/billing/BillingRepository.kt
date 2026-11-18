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
 * Owns the Pro and Remove-ads entitlements. Both are cached in DataStore for instant startup but are
 * reconciled with Play on every connect, so refunds/revocations remove access. Each product is
 * tracked independently: losing one never touches the other.
 *
 * Feature gates use [isPro] only. Ads use [adsRemoved] (Pro OR Remove ads).
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

    @Volatile
    private var loadedRemoveAds: ProductResult.Loaded? = null

    /** Pro product owned. Gates every Pro feature; Remove ads does NOT affect it. */
    val isPro: StateFlow<Boolean> = dataStore.data
        .map { it[IS_PRO] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Remove-ads product owned (Pro does not set this). */
    val hasRemoveAds: StateFlow<Boolean> = dataStore.data
        .map { it[HAS_REMOVE_ADS] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** True when ads must be hidden: Pro OR Remove ads. */
    val adsRemoved: StateFlow<Boolean> = dataStore.data
        .map { adsRemoved(it) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * The persisted ads-removed entitlement (Pro OR Remove ads) with no initial placeholder: the first
     * emission is the stored value, so ads code never treats a buyer as free while DataStore is loading.
     */
    val adsRemovedStoredUpdates: Flow<Boolean> = dataStore.data.map { adsRemoved(it) }.distinctUntilChanged()

    /** Reads the persisted entitlement directly, so callers at startup never see [isPro]'s initial `false`. */
    suspend fun isProStored(): Boolean = dataStore.data.first()[IS_PRO] ?: false

    private val _price = MutableStateFlow<String?>(null)

    /** Localized Pro price from ProductDetails, null until loaded. */
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _removeAdsPrice = MutableStateFlow<String?>(null)

    /** Localized Remove ads price from ProductDetails, null until loaded. Never hard-coded. */
    val removeAdsPrice: StateFlow<String?> = _removeAdsPrice.asStateFlow()

    private val _pending = MutableStateFlow(false)

    /** True while a Pro purchase is PENDING (not yet paid); Pro is not granted. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    private val _removeAdsPending = MutableStateFlow(false)

    /** True while a Remove ads purchase is PENDING; ads stay on until it completes. */
    val removeAdsPending: StateFlow<Boolean> = _removeAdsPending.asStateFlow()

    private val _productUnavailable = MutableStateFlow(false)

    /** True when Play answered but the Pro product/offer isn't available (so don't show "loading"). */
    val productUnavailable: StateFlow<Boolean> = _productUnavailable.asStateFlow()

    private val _removeAdsUnavailable = MutableStateFlow(false)

    /** Same as [productUnavailable], for the Remove ads product (e.g. the SKU isn't created yet). */
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
        applyProduct(products[Product.PRO] ?: ProductResult.Failed, Product.PRO)
        applyProduct(products[Product.REMOVE_ADS] ?: ProductResult.Failed, Product.REMOVE_ADS)
        val owned = gateway.queryPurchases()
        if (owned == null) {
            analytics.recordNonFatal(BillingException("Purchases query failed"))
            return false
        }
        applyPurchases(owned, revokeIfAbsent = true, source = Source.REFRESH)
        true
    }

    private fun applyProduct(result: ProductResult, product: Product) {
        val pro = product == Product.PRO
        val price = if (pro) _price else _removeAdsPrice
        val unavailable = if (pro) _productUnavailable else _removeAdsUnavailable
        when (result) {
            is ProductResult.Loaded -> {
                if (pro) loadedPro = result else loadedRemoveAds = result
                price.value = result.formattedPrice
                unavailable.value = false
            }
            ProductResult.Unavailable -> {
                price.value = null
                unavailable.value = true
            }
            ProductResult.Failed ->
                analytics.recordNonFatal(BillingException("Product details query failed ($product)"))
        }
    }

    /** User-initiated restore: queries both products and re-applies whichever are owned. */
    suspend fun restore(): Boolean = refresh()

    /** Call when the app comes to the foreground to pick up pending completions and refunds. */
    fun onForeground() {
        if (started) scope.launch { refresh() }
    }

    fun launchPurchase(activity: Activity, product: Product = Product.PRO): Boolean {
        val launched = gateway.launchPurchase(activity, product)
        if (launched) {
            analytics.logEvent(AnalyticsEvents.PURCHASE_STARTED)
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
        for (product in Product.entries) {
            val mine = purchases.filter { it.product == product }
            // A purchase-flow update only speaks for the products it contains; only a full Play
            // query (refresh/restore) may revoke, and it revokes each product on its own.
            if (mine.isEmpty() && !revokeIfAbsent) continue
            val purchased = mine.filter { it.status == PurchaseStatus.PURCHASED }
            val pending = purchased.isEmpty() && mine.any { it.status == PurchaseStatus.PENDING }
            when (product) {
                Product.PRO -> _pending.value = pending
                Product.REMOVE_ADS -> _removeAdsPending.value = pending
            }
            val key = if (product == Product.PRO) IS_PRO else HAS_REMOVE_ADS
            if (purchased.isNotEmpty()) {
                val was = dataStore.data.first()[key] ?: false
                dataStore.edit { it[key] = true }
                if (!was) logPurchaseEvent(source, product)
                purchased.filter { !it.acknowledged }.forEach { acknowledgeWithRetry(it.token) }
            } else if (revokeIfAbsent) {
                dataStore.edit { it[key] = false }
            }
        }
    }

    /** `purchase` (with value + currency) for a fresh buy, `purchase_restored` when found on refresh/restore. */
    private fun logPurchaseEvent(source: Source, product: Product) {
        when (source) {
            Source.PURCHASE_FLOW -> {
                val loaded = if (product == Product.PRO) loadedPro else loadedRemoveAds
                val params = buildMap<String, Any> {
                    if (loaded != null && loaded.priceMicros > 0 && loaded.currencyCode.isNotEmpty()) {
                        put(AnalyticsParams.VALUE, loaded.priceMicros / 1_000_000.0)
                        put(AnalyticsParams.CURRENCY, loaded.currencyCode)
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

        fun adsRemoved(prefs: Preferences): Boolean = (prefs[IS_PRO] ?: false) || (prefs[HAS_REMOVE_ADS] ?: false)

        const val MAX_BACKOFF_MS = 15_000L
    }
}
