package com.postpole.gharkhata.ads

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.postpole.gharkhata.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remote Config keys for ads (shared across Postpole apps). */
object AdsConfigKeys {
    const val ADS_ENABLED = "ads_enabled"
    const val BANNER_ENABLED = "ads_banner_enabled"
    const val INTERSTITIAL_ENABLED = "ads_interstitial_enabled"
    const val REWARDED_ENABLED = "ads_rewarded_enabled"
    const val NATIVE_ENABLED = "ads_native_enabled"
    const val APP_OPEN_ENABLED = "ads_app_open_enabled"
    const val INTERSTITIAL_BLOCK_FIRST_SESSION = "ads_interstitial_block_first_session"
    const val INTERSTITIAL_MIN_INSTALL_DAYS = "ads_interstitial_min_install_days"
    const val INTERSTITIAL_MIN_INTERVAL_SEC = "ads_interstitial_min_interval_sec"
    const val INTERSTITIAL_MAX_PER_SESSION = "ads_interstitial_max_per_session"
    const val INTERSTITIAL_MAX_PER_DAY = "ads_interstitial_max_per_day"
    const val MAX_CONTENT_RATING = "ads_max_content_rating"
    const val REWARDED_DAILY_LIMIT = "ads_rewarded_daily_limit"
}

/**
 * Launch defaults (ads plan §1.1). Rewarded, native and app-open stay off and have no UI in this app;
 * the flags are listed only so the shared keys exist.
 */
data class AdsConfig(
    val adsEnabled: Boolean = true,
    val bannerEnabled: Boolean = true,
    val interstitialEnabled: Boolean = true,
    val rewardedEnabled: Boolean = false,
    val nativeEnabled: Boolean = false,
    val appOpenEnabled: Boolean = false,
    val interstitialBlockFirstSession: Boolean = true,
    val interstitialMinInstallDays: Int = 3,
    val interstitialMinIntervalSec: Int = 480,
    val interstitialMaxPerSession: Int = 1,
    val interstitialMaxPerDay: Int = 3,
    val maxContentRating: String = "PG",
    val rewardedDailyLimit: Int = 1,
) {
    val bannersAllowed get() = adsEnabled && bannerEnabled
    val interstitialsAllowed get() = adsEnabled && interstitialEnabled

    companion object {
        /** Key/value defaults registered with Remote Config (and used as-is when Firebase is not configured). */
        val DEFAULTS: Map<String, Any> = AdsConfig().let {
            mapOf(
                AdsConfigKeys.ADS_ENABLED to it.adsEnabled,
                AdsConfigKeys.BANNER_ENABLED to it.bannerEnabled,
                AdsConfigKeys.INTERSTITIAL_ENABLED to it.interstitialEnabled,
                AdsConfigKeys.REWARDED_ENABLED to it.rewardedEnabled,
                AdsConfigKeys.NATIVE_ENABLED to it.nativeEnabled,
                AdsConfigKeys.APP_OPEN_ENABLED to it.appOpenEnabled,
                AdsConfigKeys.INTERSTITIAL_BLOCK_FIRST_SESSION to it.interstitialBlockFirstSession,
                AdsConfigKeys.INTERSTITIAL_MIN_INSTALL_DAYS to it.interstitialMinInstallDays.toLong(),
                AdsConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC to it.interstitialMinIntervalSec.toLong(),
                AdsConfigKeys.INTERSTITIAL_MAX_PER_SESSION to it.interstitialMaxPerSession.toLong(),
                AdsConfigKeys.INTERSTITIAL_MAX_PER_DAY to it.interstitialMaxPerDay.toLong(),
                AdsConfigKeys.MAX_CONTENT_RATING to it.maxContentRating,
                AdsConfigKeys.REWARDED_DAILY_LIMIT to it.rewardedDailyLimit.toLong(),
            )
        }
    }
}

/** Source of the current [AdsConfig]. */
interface AdsConfigSource {
    val config: StateFlow<AdsConfig>

    /** Fetches and activates the latest values; safe to call often (Remote Config throttles the fetches). */
    fun refresh()
}

/** Remote Config backed source. Falls back to the launch defaults when Firebase isn't configured. */
class RemoteAdsConfigSource(context: Context) : AdsConfigSource {
    private val remote: FirebaseRemoteConfig? =
        if (runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)) {
            FirebaseRemoteConfig.getInstance().apply {
                setConfigSettingsAsync(
                    remoteConfigSettings { minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3_600 },
                )
                setDefaultsAsync(AdsConfig.DEFAULTS)
            }
        } else {
            null
        }

    private val _config = MutableStateFlow(read())
    override val config: StateFlow<AdsConfig> = _config.asStateFlow()

    init {
        remote?.fetchAndActivate()?.addOnCompleteListener { _config.value = read() }
    }

    override fun refresh() {
        remote?.fetchAndActivate()?.addOnCompleteListener { _config.value = read() }
    }

    private fun read(): AdsConfig {
        val rc = remote ?: return AdsConfig()
        return runCatching {
            AdsConfig(
                adsEnabled = rc.getBoolean(AdsConfigKeys.ADS_ENABLED),
                bannerEnabled = rc.getBoolean(AdsConfigKeys.BANNER_ENABLED),
                interstitialEnabled = rc.getBoolean(AdsConfigKeys.INTERSTITIAL_ENABLED),
                rewardedEnabled = rc.getBoolean(AdsConfigKeys.REWARDED_ENABLED),
                nativeEnabled = rc.getBoolean(AdsConfigKeys.NATIVE_ENABLED),
                appOpenEnabled = rc.getBoolean(AdsConfigKeys.APP_OPEN_ENABLED),
                interstitialBlockFirstSession = rc.getBoolean(AdsConfigKeys.INTERSTITIAL_BLOCK_FIRST_SESSION),
                interstitialMinInstallDays = rc.getLong(AdsConfigKeys.INTERSTITIAL_MIN_INSTALL_DAYS).toInt(),
                interstitialMinIntervalSec = rc.getLong(AdsConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC).toInt(),
                interstitialMaxPerSession = rc.getLong(AdsConfigKeys.INTERSTITIAL_MAX_PER_SESSION).toInt(),
                interstitialMaxPerDay = rc.getLong(AdsConfigKeys.INTERSTITIAL_MAX_PER_DAY).toInt(),
                maxContentRating = rc.getString(AdsConfigKeys.MAX_CONTENT_RATING).ifBlank { "PG" },
                rewardedDailyLimit = rc.getLong(AdsConfigKeys.REWARDED_DAILY_LIMIT).toInt(),
            )
        }.getOrDefault(AdsConfig())
    }
}
