package com.postpole.gharkhata.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.analytics.AnalyticsParams
import com.postpole.gharkhata.billing.BillingRepository
import com.postpole.gharkhata.ui.pro.ProDismissMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Screen-facing side of the automatic Pro prompts: eligibility checks and the card's analytics. */
@HiltViewModel
class ProPromptViewModel @Inject constructor(
    private val store: ProPromptStore,
    billing: BillingRepository,
    private val analytics: Analytics,
) : ViewModel() {
    val price: StateFlow<String?> = billing.price
    val markedDays: Flow<Int> = store.distinctMarkedDays
    val autoPromptShownThisSession: Boolean get() = store.autoPromptShownThisSession

    suspend fun shouldShow(trigger: ProPromptTrigger, screen: PromptScreen, freeLimitCardVisible: Boolean = false) =
        store.shouldShow(trigger, screen, freeLimitCardVisible)

    fun onInterstitialShown() = store.markInterstitialShown()

    fun onCardShown() {
        analytics.logEvent(AnalyticsEvents.PRO_PROMPT_SHOWN, params())
        viewModelScope.launch { store.markShown(ProPromptTrigger.MARKED_DAYS_MILESTONE) }
    }

    fun onCardCtaTap() {
        analytics.logEvent(AnalyticsEvents.PRO_PROMPT_CTA_TAP, params())
    }

    /** The card has a Close icon and a "Not now" button; its dismissal method is never `swipe`. */
    fun onCardDismissed(method: ProDismissMethod) {
        analytics.logEvent(AnalyticsEvents.PRO_PROMPT_DISMISSED, params() + (AnalyticsParams.METHOD to method.analyticsName))
        viewModelScope.launch { store.markDismissed(ProPromptTrigger.MARKED_DAYS_MILESTONE) }
    }

    private fun params(): Map<String, Any> = ProPromptTrigger.MARKED_DAYS_MILESTONE.let {
        mapOf(AnalyticsParams.TRIGGER to it.analyticsName, AnalyticsParams.SURFACE to it.surface)
    }
}
