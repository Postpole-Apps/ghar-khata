package com.postpole.gharkhata.ads

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.postpole.gharkhata.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remote Config keys for ads (ads plan section 1.1). */
object AdsKeys {
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

/** Launch defaults; also the in-app defaults registered with Remote Config. */
data class AdsSettings(
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
    companion object {
        val DEFAULT = AdsSettings()

        /** Defaults map handed to `FirebaseRemoteConfig.setDefaultsAsync`. */
        fun defaultsMap(d: AdsSettings = DEFAULT): Map<String, Any> = mapOf(
            AdsKeys.ADS_ENABLED to d.adsEnabled,
            AdsKeys.BANNER_ENABLED to d.bannerEnabled,
            AdsKeys.INTERSTITIAL_ENABLED to d.interstitialEnabled,
            AdsKeys.REWARDED_ENABLED to d.rewardedEnabled,
            AdsKeys.NATIVE_ENABLED to d.nativeEnabled,
            AdsKeys.APP_OPEN_ENABLED to d.appOpenEnabled,
            AdsKeys.INTERSTITIAL_BLOCK_FIRST_SESSION to d.interstitialBlockFirstSession,
            AdsKeys.INTERSTITIAL_MIN_INSTALL_DAYS to d.interstitialMinInstallDays.toLong(),
            AdsKeys.INTERSTITIAL_MIN_INTERVAL_SEC to d.interstitialMinIntervalSec.toLong(),
            AdsKeys.INTERSTITIAL_MAX_PER_SESSION to d.interstitialMaxPerSession.toLong(),
            AdsKeys.INTERSTITIAL_MAX_PER_DAY to d.interstitialMaxPerDay.toLong(),
            AdsKeys.MAX_CONTENT_RATING to d.maxContentRating,
            AdsKeys.REWARDED_DAILY_LIMIT to d.rewardedDailyLimit.toLong(),
        )
    }
}

/** Source of [AdsSettings]; Remote Config in the app, a fake in tests. */
interface AdsSettingsSource {
    val settings: StateFlow<AdsSettings>

    /** Fetches and activates fresh values (no-op when Firebase isn't configured). */
    fun refresh()
}

/**
 * Remote Config backed settings. Without `google-services.json` Firebase isn't initialised, so this
 * stays on the launch defaults and never touches Remote Config.
 */
class RemoteConfigAdsSettings(context: Context) : AdsSettingsSource {
    private val remote: FirebaseRemoteConfig? =
        if (!BuildConfig.ADS_SHIPPING || FirebaseApp.getApps(context).isEmpty()) {
            null
        } else {
            FirebaseRemoteConfig.getInstance().also { rc ->
                rc.setConfigSettingsAsync(
                    FirebaseRemoteConfigSettings.Builder()
                        .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0 else 3_600)
                        .build(),
                )
                rc.setDefaultsAsync(AdsSettings.defaultsMap())
            }
        }

    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<AdsSettings> = _settings.asStateFlow()

    override fun refresh() {
        val rc = remote ?: return
        rc.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                _settings.value = read()
            } else {
                Log.w(TAG, "Remote Config fetch failed", task.exception)
            }
        }
    }

    private fun read(): AdsSettings {
        val rc = remote ?: return AdsSettings.DEFAULT
        return AdsSettings(
            adsEnabled = rc.getBoolean(AdsKeys.ADS_ENABLED),
            bannerEnabled = rc.getBoolean(AdsKeys.BANNER_ENABLED),
            interstitialEnabled = rc.getBoolean(AdsKeys.INTERSTITIAL_ENABLED),
            rewardedEnabled = rc.getBoolean(AdsKeys.REWARDED_ENABLED),
            nativeEnabled = rc.getBoolean(AdsKeys.NATIVE_ENABLED),
            appOpenEnabled = rc.getBoolean(AdsKeys.APP_OPEN_ENABLED),
            interstitialBlockFirstSession = rc.getBoolean(AdsKeys.INTERSTITIAL_BLOCK_FIRST_SESSION),
            interstitialMinInstallDays = rc.getLong(AdsKeys.INTERSTITIAL_MIN_INSTALL_DAYS).toInt(),
            interstitialMinIntervalSec = rc.getLong(AdsKeys.INTERSTITIAL_MIN_INTERVAL_SEC).toInt(),
            interstitialMaxPerSession = rc.getLong(AdsKeys.INTERSTITIAL_MAX_PER_SESSION).toInt(),
            interstitialMaxPerDay = rc.getLong(AdsKeys.INTERSTITIAL_MAX_PER_DAY).toInt(),
            maxContentRating = rc.getString(AdsKeys.MAX_CONTENT_RATING).ifBlank { "PG" },
            rewardedDailyLimit = rc.getLong(AdsKeys.REWARDED_DAILY_LIMIT).toInt(),
        )
    }

    private companion object {
        const val TAG = "AdsSettings"
    }
}
