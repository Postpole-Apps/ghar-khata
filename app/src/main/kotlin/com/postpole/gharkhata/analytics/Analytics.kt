package com.postpole.gharkhata.analytics

/** Event and parameter names. Values mirror Firebase's standard names where one exists. */
object AnalyticsEvents {
    const val APP_OPEN = "app_open"
    const val FIRST_LAUNCH = "first_launch"
    const val SCREEN_VIEW = "screen_view"
    const val PRO_SCREEN_VIEW = "pro_screen_view"
    const val PURCHASE_STARTED = "purchase_started"
    const val PURCHASE = "purchase"
    const val PURCHASE_RESTORED = "purchase_restored"
    const val BACKUP_EXPORTED = "backup_exported"
    const val BACKUP_IMPORTED = "backup_imported"
    const val PROVIDER_ADDED = "provider_added"
    const val PROVIDER_LIMIT_REACHED = "provider_limit_reached"
    const val DAY_MARKED = "day_marked"
    const val DAY_MARK_UNDONE = "day_mark_undone"
    const val BILL_SHARED = "bill_shared"
    const val SETTLEMENT_SAVED = "settlement_saved"
    const val PROVIDER_CALL_TAPPED = "provider_call_tapped"

    // Automatic Pro prompts. Enum-only params: trigger, surface, method.
    const val PRO_PROMPT_SHOWN = "pro_prompt_shown"
    const val PRO_PROMPT_DISMISSED = "pro_prompt_dismissed"
    const val PRO_PROMPT_CTA_TAP = "pro_prompt_cta_tap"

    // Remove ads (ads-era). Pro keeps its own events above; these never fire for Pro and vice versa.
    const val REMOVE_ADS_PAYWALL_VIEW = "remove_ads_paywall_view"
    const val REMOVE_ADS_PURCHASE_START = "remove_ads_purchase_start"
    const val REMOVE_ADS_PURCHASE_SUCCESS = "remove_ads_purchase_success"
    const val REMOVE_ADS_PURCHASE_FAIL = "remove_ads_purchase_fail"
    const val AD_PAYWALL_ENTRY = "ad_paywall_entry"
}

object AnalyticsParams {
    const val SCREEN_NAME = "screen_name"
    const val VALUE = "value"
    const val CURRENCY = "currency"
    const val ITEM_ID = "item_id"
    const val ITEM_COUNT = "item_count"
    const val FORMAT = "format"
    const val PROVIDER_TYPE = "provider_type"
    const val STATUS = "status"
    const val PRICE_MICROS = "price_micros"

    /** Enum-only entry point, see [AdPaywallSources]. Never free text. */
    const val SOURCE = "source"

    /** Enum-only auto Pro prompt trigger, see ProPromptTrigger. */
    const val TRIGGER = "trigger"

    /** Enum-only auto Pro prompt surface: `card` or `sheet`. */
    const val SURFACE = "surface"

    /** Enum-only dismissal method: `close`, `not_now` or `swipe`. */
    const val METHOD = "method"

    /** Enum-only failure reason, see [PurchaseFailReasons]. */
    const val REASON = "reason"
}

object AdPaywallSources {
    const val SETTINGS_ROW = "settings_row"
}

object PurchaseFailReasons {
    const val CANCELED = "canceled"
    const val LAUNCH_FAILED = "launch_failed"
    const val BILLING_ERROR = "billing_error"
    const val ALREADY_OWNED = "already_owned"
}

/**
 * App-facing analytics + crash reporting seam. Backed by Firebase when the app has a
 * google-services.json, otherwise [NoOpAnalytics]. Never put personal data in params.
 */
interface Analytics {
    /** Values may be String, Long, Int, Double or Boolean. */
    fun logEvent(name: String, params: Map<String, Any> = emptyMap())

    fun setUserProperty(name: String, value: String?)

    fun logScreenView(screenName: String)

    /** Records a handled exception (Crashlytics non-fatal). */
    fun recordNonFatal(throwable: Throwable)
}

/** Used when Firebase isn't configured: every call is safe and does nothing. */
object NoOpAnalytics : Analytics {
    override fun logEvent(name: String, params: Map<String, Any>) = Unit
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun recordNonFatal(throwable: Throwable) = Unit
}
