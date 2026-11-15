package com.postpole.gharkhata.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.postpole.gharkhata.billing.BillingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns ads: SDK init (only after UMP allows it), the entitlement / kill-switch gate and the single
 * after-bill interstitial with its caps. Banners read [bannersActive]; screens on the never-on list
 * simply never ask for one.
 *
 * Entitlement: no ads when Pro (`remove_ads` joins in gk-02).
 */
@Singleton
class AdsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val consent: ConsentManager,
    private val configSource: AdsConfigSource,
    billing: BillingRepository,
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val sdkReady = MutableStateFlow(false)
    private var initStarted = false
    private var started = false

    /** SDK is initialised, UMP allows requests, the kill switch is off and the user isn't Pro. */
    private val adsActive: StateFlow<Boolean> = combine(
        sdkReady,
        consent.canRequestAds,
        configSource.config,
        billing.isProStream,
    ) { ready, canRequest, config, isPro -> ready && canRequest && config.adsEnabled && !isPro }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val bannersActive: StateFlow<Boolean> = combine(adsActive, configSource.config) { active, config ->
        active && config.bannersAllowed
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val interstitialActive: StateFlow<Boolean> = combine(adsActive, configSource.config) { active, config ->
        active && config.interstitialsAllowed
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val capsLock = Mutex()
    private var sessionNumber = 0
    private var shownThisSession = 0
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var showing = false

    init {
        scope.launch {
            // Session counter survives restarts: session 1 is the first launch after install.
            sessionNumber = (dataStore.data.first()[SESSION_COUNT] ?: 0) + 1
            dataStore.edit { it[SESSION_COUNT] = sessionNumber }
        }
        scope.launch {
            // Keep the SDK's content rating in step with Remote Config. Never set child-directed treatment.
            // Nothing touches the Mobile Ads SDK until UMP has allowed ad requests (see initializeSdk).
            configSource.config.collect { if (initStarted) applyRequestConfiguration(it) }
        }
        scope.launch {
            // Withdrawing consent or turning the kill switch on drops any preloaded interstitial.
            interstitialActive.collect { if (!it) interstitial = null }
        }
    }

    /** Call from MainActivity once per process: UMP first, then `MobileAds.initialize` if ads can be requested. */
    fun start(activity: Activity) {
        if (started) return
        started = true
        consent.gather(activity) { }
        scope.launch {
            consent.canRequestAds.collect { if (it) initializeSdk() }
        }
        configSource.refresh()
    }

    /** Re-fetches Remote Config (kill switch) when the app returns to the foreground. */
    fun onForeground() = configSource.refresh()

    fun showPrivacyOptions(activity: Activity) = consent.showPrivacyOptions(activity)

    val privacyOptionsRequired: StateFlow<Boolean> get() = consent.privacyOptionsRequired

    private fun initializeSdk() {
        if (initStarted) return
        initStarted = true
        applyRequestConfiguration(configSource.config.value)
        scope.launch {
            withContext(Dispatchers.IO) {
                MobileAds.initialize(context) { sdkReady.value = true }
            }
        }
    }

    private fun applyRequestConfiguration(config: AdsConfig) {
        val rating = when (config.maxContentRating.uppercase()) {
            "G" -> RequestConfiguration.MAX_AD_CONTENT_RATING_G
            "T" -> RequestConfiguration.MAX_AD_CONTENT_RATING_T
            "MA" -> RequestConfiguration.MAX_AD_CONTENT_RATING_MA
            else -> RequestConfiguration.MAX_AD_CONTENT_RATING_PG
        }
        MobileAds.setRequestConfiguration(
            MobileAds.getRequestConfiguration().toBuilder().setMaxAdContentRating(rating).build(),
        )
    }

    // ---- Interstitial (after leaving Bill preview) ----

    /** Warms an interstitial when the bill is opened, if one could be shown afterwards. */
    fun preloadInterstitial() {
        scope.launch {
            if (!interstitialActive.value || interstitial != null || loading || showing) return@launch
            if (!InterstitialPolicy.allows(configSource.config.value, history(), System.currentTimeMillis())) return@launch
            loading = true
            InterstitialAd.load(
                context,
                AdUnits.interstitialAfterBill(),
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        loading = false
                        interstitial = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loading = false
                        interstitial = null
                    }
                },
            )
        }
    }

    /**
     * Shows the loaded interstitial if every guard passes: ads active, [owner] resumed, caps ok, nothing
     * already showing. The caller has already waited for Bill preview to be popped and a settle delay.
     */
    suspend fun showInterstitialIfAllowed(activity: Activity, owner: LifecycleOwner): Boolean =
        capsLock.withLock {
            val ad = interstitial
            if (ad == null || showing || !interstitialActive.value) return false
            if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
            val now = System.currentTimeMillis()
            if (!InterstitialPolicy.allows(configSource.config.value, history(), now)) return false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() = finished()

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "Interstitial failed to show: ${error.code}")
                    finished()
                }

                private fun finished() {
                    showing = false
                    interstitial = null
                }
            }
            showing = true
            interstitial = null
            record(now)
            ad.show(activity)
            true
        }

    private suspend fun history(): InterstitialHistory {
        val prefs = dataStore.data.first()
        val today = LocalDate.now().toEpochDay()
        val shownToday = if (prefs[SHOWN_DAY] == today) prefs[SHOWN_TODAY] ?: 0 else 0
        return InterstitialHistory(
            sessionNumber = sessionNumber,
            installTimeMs = installTimeMs(),
            shownThisSession = shownThisSession,
            shownToday = shownToday,
            lastShownMs = prefs[LAST_SHOWN_MS],
        )
    }

    private suspend fun record(nowMs: Long) {
        shownThisSession++
        val today = LocalDate.now().toEpochDay()
        dataStore.edit {
            val count = if (it[SHOWN_DAY] == today) it[SHOWN_TODAY] ?: 0 else 0
            it[SHOWN_DAY] = today
            it[SHOWN_TODAY] = count + 1
            it[LAST_SHOWN_MS] = nowMs
        }
    }

    private fun installTimeMs(): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }.getOrDefault(System.currentTimeMillis())

    private companion object {
        const val TAG = "AdsManager"
        val SESSION_COUNT = intPreferencesKey("ads_session_count")
        val SHOWN_DAY = longPreferencesKey("ads_interstitial_day")
        val SHOWN_TODAY = intPreferencesKey("ads_interstitial_day_count")
        val LAST_SHOWN_MS = longPreferencesKey("ads_interstitial_last_ms")
    }
}
