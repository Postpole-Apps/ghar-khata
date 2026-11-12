package com.postpole.gharkhata.ui.provider

import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.domain.Schedule
import kotlinx.coroutines.flow.StateFlow

/** Free users may have at most this many active (non-archived) providers. */
const val FREE_ACTIVE_PROVIDER_LIMIT = 3

/** True when another active provider may be added. Pro has no cap; only the count is gated. */
fun canAddActiveProvider(activeCount: Int, isPro: Boolean): Boolean =
    isPro || activeCount < FREE_ACTIVE_PROVIDER_LIMIT

/** Source of the Pro entitlement; backed by BillingRepository in the app, faked in tests. */
interface ProStatus {
    val isPro: StateFlow<Boolean>
}

/** Defaults applied when a provider type is selected; every field stays editable. */
data class ProviderPreset(
    val billingMode: BillingMode,
    val unitLabel: String,
    val defaultQty: Double,
    val scheduleMask: Int,
    val paidLeavesPerMonth: Int = 0,
)

object ProviderPresets {
    fun forType(type: ProviderType): ProviderPreset = when (type) {
        ProviderType.MILK -> ProviderPreset(BillingMode.PER_UNIT, "L", 1.0, Schedule.ALL_DAYS)
        ProviderType.MAID -> ProviderPreset(
            BillingMode.MONTHLY_FIXED, "day", 1.0,
            Schedule.ALL_DAYS and Schedule.SUN.inv(), paidLeavesPerMonth = 2,
        )
        ProviderType.NEWSPAPER -> ProviderPreset(BillingMode.PER_UNIT, "copy", 1.0, Schedule.ALL_DAYS)
        ProviderType.WATER_CAN -> ProviderPreset(BillingMode.PER_UNIT, "can", 1.0, 0)
        ProviderType.COOK -> ProviderPreset(BillingMode.PER_VISIT, "visit", 1.0, Schedule.ALL_DAYS)
        ProviderType.DRIVER -> ProviderPreset(BillingMode.PER_VISIT, "day", 1.0, Schedule.ALL_DAYS)
        ProviderType.OTHER -> ProviderPreset(BillingMode.PER_VISIT, "visit", 1.0, 0)
    }
}
