package com.postpole.gharkhata.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.postpole.gharkhata.BuildConfig
import com.postpole.gharkhata.billing.BillingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single entry point for ads: UMP consent, then SDK init, then eligibility and the one interstitial.
 *
 * Flow: [start] (from Application) loads Remote Config and counts the session; [gatherConsent] (from
 * MainActivity) runs UMP and only then calls `MobileAds.initialize`. Nothing requests an ad until
 * [sdkReady], which needs `canRequestAds()`.
 */
class AdsManager(
    private val context: Context,
    private val consent: ConsentManager,
    private val config: AdsSettingsSource,
    billing: BillingRepository,
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Compile-time switch: false is the ad-free build, where this class does nothing. */
    private val adsShipping: Boolean = BuildConfig.ADS_SHIPPING,
) {
    private val initStarted = AtomicBoolean(false)
    private val started = AtomicBoolean(false)

    private val sdkReadyFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Live `canRequestAds()` result; updated after the consent flow and after Privacy options. */
    private val consentGate = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** True once UMP allowed ad requests and the Mobile Ads SDK was initialised. */
    val sdkReady: StateFlow<Boolean> = sdkReadyFlow

    /** Pro or Remove ads buyers never see ads. Emits only once the stored value is known. */
    private val entitledToAds = billing.adsRemovedStoredUpdates

    private val settings get() = config.settings.value

    /** Banners may be shown: consent given, SDK ready, kill switch on, banners on, not Pro. */
    val bannersAllowed: StateFlow<Boolean> =
        combine(sdkReadyFlow, consentGate, config.settings, entitledToAds) { ready, consented, s, pro ->
            adsShipping && ready && consented && s.adsEnabled && s.bannerEnabled && !pro
        }.stateIn(scope, SharingStarted.Eagerly, false)

    private val interstitialsAllowedFlow =
        combine(sdkReadyFlow, consentGate, config.settings, entitledToAds) { ready, consented, s, pro ->
            adsShipping && ready && consented && s.adsEnabled && s.interstitialEnabled && !pro
        }.stateIn(scope, SharingStarted.Eagerly, false)

    val privacyOptionsRequired: StateFlow<Boolean> get() = consent.privacyOptionsRequired

    @Volatile private var session = InterstitialHistory(sessionNumber = 0)
    @Volatile private var interstitial: InterstitialAd? = null
    @Volatile private var loading = false
    @Volatile private var showing = false

    /** Call once from Application.onCreate. */
    fun start() {
        if (!adsShipping || !started.compareAndSet(false, true)) return
        config.refresh()
        scope.launch {
            val prefs = dataStore.data.first()
            val number = (prefs[SESSION_COUNT] ?: 0) + 1
            dataStore.edit { it[SESSION_COUNT] = number }
            val today = LocalDate.now().toEpochDay()
            val day = prefs[SHOWN_DAY] ?: -1L
            session = InterstitialHistory(
                sessionNumber = number,
                lastShownMs = prefs[LAST_SHOWN] ?: 0L,
                day = day,
                shownOnDay = if (day == today) prefs[SHOWN_ON_DAY] ?: 0 else 0,
            )
        }
    }

    /** App returned to the foreground: pick up Remote Config changes (kill switch). */
    fun onForeground() {
        if (adsShipping) config.refresh()
    }

    /** UMP first, then init. Safe to call repeatedly (e.g. on every Activity creation). */
    fun gatherConsent(activity: Activity) {
        if (!adsShipping || initStarted.get()) return
        consent.gather(activity) { canRequest -> applyConsent(canRequest) }
    }

    private fun initializeSdk() {
        if (!initStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            // Max content rating PG; deliberately no child-directed / under-age tags.
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setMaxAdContentRating(contentRating(settings.maxContentRating))
                    .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED)
                    .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED)
                    .build(),
            )
            MobileAds.initialize(context) { sdkReadyFlow.value = true }
        }
    }

    fun showPrivacyOptions(activity: Activity, onDismissed: (String?) -> Unit = {}) =
        consent.showPrivacyOptions(activity) { error, canRequest ->
            applyConsent(canRequest)
            onDismissed(error)
        }

    /** Live consent gate: withdrawn consent stops ads now; granted consent initialises the SDK if needed. */
    private fun applyConsent(canRequest: Boolean) {
        consentGate.value = canRequest
        if (canRequest) {
            initializeSdk()
        } else {
            interstitial = null
        }
    }

    /** Preloads the interstitial (called when Bill preview opens) if one could be shown later. */
    fun preloadInterstitial(activity: Activity) {
        if (interstitial != null || loading || !interstitialsAllowedFlow.value) return
        loading = true
        InterstitialAd.load(
            activity.applicationContext,
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
                    Log.w(TAG, "Interstitial load failed: ${error.code} ${error.message}")
                }
            },
        )
    }

    /**
     * Shows the interstitial if it is loaded and every cap passes. The caller has already checked the
     * user is back on Today / Providers / Month with the Activity resumed and the settle delay elapsed.
     */
    fun showInterstitialIfAllowed(activity: Activity): Boolean {
        val ad = interstitial ?: return false
        if (showing || !interstitialsAllowedFlow.value) return false
        val now = clock()
        val installAge = now - activity.packageManager.getPackageInfo(activity.packageName, 0).firstInstallTime
        val today = LocalDate.now().toEpochDay()
        if (!InterstitialPolicy.allowed(settings, session, installAge, now, today)) return false
        showing = true
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                val shownAt = clock()
                val day = LocalDate.now().toEpochDay()
                val updated = session.recordShown(shownAt, day)
                session = updated
                scope.launch {
                    dataStore.edit {
                        it[LAST_SHOWN] = updated.lastShownMs
                        it[SHOWN_DAY] = updated.day
                        it[SHOWN_ON_DAY] = updated.shownOnDay
                    }
                }
            }

            override fun onAdDismissedFullScreenContent() {
                showing = false
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showing = false
                Log.w(TAG, "Interstitial show failed: ${error.code} ${error.message}")
            }
        }
        ad.show(activity)
        return true
    }

    private companion object {
        const val TAG = "AdsManager"
        val SESSION_COUNT = intPreferencesKey("ads_session_count")
        val LAST_SHOWN = longPreferencesKey("ads_interstitial_last_shown")
        val SHOWN_DAY = longPreferencesKey("ads_interstitial_day")
        val SHOWN_ON_DAY = intPreferencesKey("ads_interstitial_day_count")
    }
}

/** Maps the Remote Config rating to the SDK constant; anything unknown falls back to PG. */
internal fun contentRating(value: String): String = when (value.uppercase()) {
    "G" -> RequestConfiguration.MAX_AD_CONTENT_RATING_G
    "T" -> RequestConfiguration.MAX_AD_CONTENT_RATING_T
    "MA" -> RequestConfiguration.MAX_AD_CONTENT_RATING_MA
    else -> RequestConfiguration.MAX_AD_CONTENT_RATING_PG
}
