package com.postpole.gharkhata.ui.settings

import com.postpole.gharkhata.analytics.Analytics
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoveAdsPaywallViewLoggerTest {
    private class Recording : Analytics {
        val events = mutableListOf<String>()

        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name
        }

        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) = Unit
    }

    @Test fun logsOncePerVisit() {
        val a = Recording()
        val logger = RemoveAdsPaywallViewLogger(a)
        logger.onOfferShown()
        logger.onOfferShown()
        assertEquals(listOf("remove_ads_paywall_view"), a.events)
    }

    @Test fun newVisit_logsAgain() {
        val a = Recording()
        RemoveAdsPaywallViewLogger(a).onOfferShown()
        RemoveAdsPaywallViewLogger(a).onOfferShown()
        assertEquals(2, a.events.size)
    }
}
