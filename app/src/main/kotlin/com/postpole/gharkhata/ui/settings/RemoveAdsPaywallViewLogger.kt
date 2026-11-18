package com.postpole.gharkhata.ui.settings

import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents

/** Logs `remove_ads_paywall_view` at most once per instance (one Settings visit). */
internal class RemoveAdsPaywallViewLogger(private val analytics: Analytics) {
    private var logged = false

    fun onOfferShown() {
        if (logged) return
        logged = true
        analytics.logEvent(AnalyticsEvents.REMOVE_ADS_PAYWALL_VIEW)
    }
}
