package com.postpole.gharkhata.ui.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.analytics.AnalyticsEvents
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

    /** The Pro sheet is a sheet, not a route, so it reports its own view. */
    fun onShown() {
        analytics.logEvent(AnalyticsEvents.PRO_SCREEN_VIEW)
    }

    fun buy(activity: Activity) {
        billing.launchPurchase(activity, Product.PRO)
    }

    fun buyRemoveAds(activity: Activity) {
        billing.launchPurchase(activity, Product.REMOVE_ADS)
    }

    fun restore() {
        viewModelScope.launch { billing.restore() }
    }
}
