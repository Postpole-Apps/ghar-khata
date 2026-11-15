package com.postpole.gharkhata.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.postpole.gharkhata.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google UMP consent. Ads may only be requested when [canRequestAds] is true, which in the EEA / UK /
 * Switzerland requires a UMP result first. Runs once per process, before `MobileAds.initialize`.
 */
@Singleton
class ConsentManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val info: ConsentInformation by lazy { UserMessagingPlatform.getConsentInformation(context) }

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** True when Settings should offer "Privacy options" (UMP says the user can change their choice). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private var started = false

    /** Requests consent info, shows the form if required, then calls [onDone] (once) with [canRequestAds]. */
    fun gather(activity: Activity, onDone: (canRequestAds: Boolean) -> Unit) {
        if (started) return
        started = true
        val params = ConsentRequestParameters.Builder().apply {
            debugSettings()?.let(::setConsentDebugSettings)
        }.build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    // A form error still leaves canRequestAds() authoritative.
                    publish()
                    onDone(info.canRequestAds())
                }
            },
            {
                publish()
                onDone(info.canRequestAds())
            },
        )
        // A previous session may already have consent: allow early init while the update is in flight.
        publish()
    }

    /** Re-opens the UMP privacy options form (Settings). */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { publish() }
    }

    private fun publish() {
        _canRequestAds.value = info.canRequestAds()
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun debugSettings(): ConsentDebugSettings? {
        val geography = when (BuildConfig.UMP_DEBUG_GEOGRAPHY.lowercase()) {
            "eea" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
            "us" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
            else -> return null
        }
        return ConsentDebugSettings.Builder(context).apply {
            setDebugGeography(geography)
            if (BuildConfig.UMP_TEST_DEVICE_ID.isNotBlank()) addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE_ID)
        }.build()
    }
}
