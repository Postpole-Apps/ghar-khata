package com.postpole.gharkhata.ui.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.analytics.AnalyticsParams
import com.postpole.gharkhata.prompt.ProPromptStore
import com.postpole.gharkhata.prompt.ProPromptTrigger
import com.postpole.gharkhata.billing.BillingRepository
import com.postpole.gharkhata.billing.BillingMessage
import com.postpole.gharkhata.billing.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val analytics: Analytics,
    private val prompts: ProPromptStore,
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
     * The Pro sheet is a sheet, not a route, so it reports its own view. Auto reasons carry
     * `trigger=auto_<trigger>`; the automatic bill sheet also logs pro_prompt_shown and uses up the
     * session's prompt; hard-gate reasons start the 48 hour auto-prompt cooldown.
     */
    fun onShown(reason: ProReason = ProReason.GENERAL) {
        val auto = reason.autoTrigger
        analytics.logEvent(
            AnalyticsEvents.PRO_SCREEN_VIEW,
            if (auto != null) mapOf(AnalyticsParams.TRIGGER to auto.autoName) else emptyMap(),
        )
        viewModelScope.launch {
            when {
                reason == ProReason.AUTO_BILL_MILESTONE -> {
                    analytics.logEvent(AnalyticsEvents.PRO_PROMPT_SHOWN, promptParams(ProPromptTrigger.BILL_MILESTONE))
                    prompts.markShown(ProPromptTrigger.BILL_MILESTONE)
                }
                reason.hardGate -> prompts.markHardGate()
            }
        }
    }

    /** Close icon, "Not now" or scrim / back. Only the automatic bill sheet counts as a prompt dismissal. */
    fun onDismissed(reason: ProReason, method: ProDismissMethod) {
        if (reason != ProReason.AUTO_BILL_MILESTONE) return
        analytics.logEvent(
            AnalyticsEvents.PRO_PROMPT_DISMISSED,
            promptParams(ProPromptTrigger.BILL_MILESTONE) + (AnalyticsParams.METHOD to method.analyticsName),
        )
        viewModelScope.launch { prompts.markDismissed(ProPromptTrigger.BILL_MILESTONE) }
    }

    fun buy(activity: Activity, reason: ProReason = ProReason.GENERAL) {
        if (reason == ProReason.AUTO_BILL_MILESTONE) {
            analytics.logEvent(AnalyticsEvents.PRO_PROMPT_CTA_TAP, promptParams(ProPromptTrigger.BILL_MILESTONE))
        }
        billing.launchPurchase(activity, Product.PRO, reason.autoTrigger?.autoName)
    }

    private fun promptParams(t: ProPromptTrigger): Map<String, Any> =
        mapOf(AnalyticsParams.TRIGGER to t.analyticsName, AnalyticsParams.SURFACE to t.surface)

    fun buyRemoveAds(activity: Activity) {
        billing.launchPurchase(activity, Product.REMOVE_ADS)
    }

    fun restore() {
        viewModelScope.launch { billing.restore() }
    }
}
