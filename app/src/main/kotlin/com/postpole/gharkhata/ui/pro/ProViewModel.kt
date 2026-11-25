package com.postpole.gharkhata.ui.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.billing.BillingRepository
import com.postpole.gharkhata.billing.BillingMessage
import com.postpole.gharkhata.billing.Product
import com.postpole.gharkhata.analytics.AnalyticsParams
import com.postpole.gharkhata.pro.ProDismissMethod
import com.postpole.gharkhata.pro.ProPromptTrigger
import com.postpole.gharkhata.pro.ProPrompts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val analytics: Analytics,
    private val prompts: ProPrompts,
) : ViewModel() {
    val isPro: StateFlow<Boolean> = billing.isPro
    val price: StateFlow<String?> = billing.price
    val pending: StateFlow<Boolean> = billing.pending
    val productUnavailable: StateFlow<Boolean> = billing.productUnavailable
    val messages: Flow<BillingMessage> = billing.messages

    val hasRemoveAds: StateFlow<Boolean> = billing.hasRemoveAds
    val removeAdsPrice: StateFlow<String?> = billing.removeAdsPrice
    val removeAdsPending: StateFlow<Boolean> = billing.removeAdsPending
    val removeAdsUnavailable: StateFlow<Boolean> = billing.removeAdsUnavailable

    /**
     * The Pro sheet is a sheet, not a route, so it reports its own view. [attribution] is the auto prompt it
     * came from, if any: the sheet itself (`AUTO_BILL_MILESTONE`) or the card's "See Pro".
     */
    fun onShown(reason: ProReason, attribution: ProPromptTrigger? = reason.autoTrigger) {
        analytics.logEvent(AnalyticsEvents.PRO_SCREEN_VIEW, attributionParams(attribution))
        viewModelScope.launch {
            if (reason.isHardGate) prompts.recordHardGate()
            reason.autoTrigger?.let { prompts.onShown(it) }
        }
    }

    /** Only the automatic sheet is a prompt to dismiss; a manual open or the card's sheet logs nothing here. */
    fun onDismissed(reason: ProReason, method: ProDismissMethod) {
        val trigger = reason.autoTrigger ?: return
        viewModelScope.launch { prompts.onDismissed(trigger, method) }
    }

    fun buy(activity: Activity, reason: ProReason = ProReason.GENERAL, attribution: ProPromptTrigger? = reason.autoTrigger) {
        reason.autoTrigger?.let(prompts::onCtaTap)
        billing.launchPurchase(activity, Product.PRO, attribution?.autoAnalytics)
    }

    private fun attributionParams(trigger: ProPromptTrigger?): Map<String, Any> =
        if (trigger == null) emptyMap() else mapOf(AnalyticsParams.TRIGGER to trigger.autoAnalytics)

    fun buyRemoveAds(activity: Activity) {
        billing.launchPurchase(activity, Product.REMOVE_ADS)
    }

    fun restore() {
        viewModelScope.launch { billing.restore() }
    }
}
