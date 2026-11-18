package com.postpole.gharkhata.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.postpole.gharkhata.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google UMP wrapper. Consent is gathered first; only [canRequestAds] gates SDK init and ad requests.
 * Regions that need a message (EEA, UK, Switzerland, some US states) get the form from the messages
 * published in AdMob; elsewhere UMP reports ads can be requested straight away.
 */
class ConsentManager(context: Context) {
    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
    private var gathering = false

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** True when UMP says a privacy options entry point must be offered (Settings). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    fun canRequestAds(): Boolean = info.canRequestAds()

    /**
     * Updates consent info, shows the form if required, then calls [onDone] with [canRequestAds].
     * Also called right away when a previous session already obtained consent.
     */
    fun gather(activity: Activity, onDone: (canRequestAds: Boolean) -> Unit) {
        if (gathering) return
        gathering = true
        val params = ConsentRequestParameters.Builder().apply {
            if (BuildConfig.UMP_DEBUG_EEA) {
                setConsentDebugSettings(
                    ConsentDebugSettings.Builder(activity)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .build(),
                )
            }
        }.build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.errorCode} ${error.message}")
                    finish(onDone)
                }
            },
            { error ->
                Log.w(TAG, "Consent info update: ${error.errorCode} ${error.message}")
                finish(onDone)
            },
        )
        // A previous session may already hold valid consent: don't wait for the network.
        if (info.canRequestAds()) onDone(true)
    }

    private fun finish(onDone: (Boolean) -> Unit) {
        gathering = false
        refreshPrivacyOptionsRequired()
        onDone(info.canRequestAds())
    }

    private fun refreshPrivacyOptionsRequired() {
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    /**
     * Re-opens the UMP privacy options form (Settings). On dismissal the requirement status is
     * refreshed and [onDismissed] gets the current [canRequestAds] so callers can re-gate ads.
     */
    fun showPrivacyOptions(
        activity: Activity,
        onDismissed: (error: String?, canRequestAds: Boolean) -> Unit = { _, _ -> },
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options: ${error.errorCode} ${error.message}")
            refreshPrivacyOptionsRequired()
            onDismissed(error?.message, info.canRequestAds())
        }
    }

    /** Whether the privacy options form can currently be opened (UMP says it is required). */
    fun privacyOptionsAvailable(): Boolean =
        info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    private companion object {
        const val TAG = "ConsentManager"
    }
}
