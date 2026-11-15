package com.postpole.gharkhata.ads

import com.postpole.gharkhata.BuildConfig

/** Where an ad can appear. Anything not listed here (edit, day-mark sheet, bill, settings, Pro...) never shows ads. */
enum class BannerPlacement { TODAY, PROVIDERS, MONTH, YEAR }

/**
 * Ad unit IDs. Debug / internal builds ([BuildConfig.ADS_USE_TEST_IDS]) only ever use Google's official
 * demo units; release builds use the production units. Never click production ads.
 */
object AdUnits {
    /** AdMob App ID; also declared in the manifest as `com.google.android.gms.ads.APPLICATION_ID`. */
    const val APP_ID = "ca-app-pub-2007361247232604~4425956356"

    // Official demo units: https://developers.google.com/admob/android/test-ads
    const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    // Production units.
    const val BANNER_TODAY = "ca-app-pub-2007361247232604/6249514775" // gk_banner_today
    const val BANNER_PROVIDERS = "ca-app-pub-2007361247232604/7287264274" // gk_banner_providers
    const val BANNER_MONTH = "ca-app-pub-2007361247232604/1300440426" // gk_banner_month
    const val BANNER_YEAR = "ca-app-pub-2007361247232604/2921302990" // gk_banner_year
    const val INTERSTITIAL_AFTER_BILL = "ca-app-pub-2007361247232604/5355894640" // gk_interstitial_after_bill

    fun banner(placement: BannerPlacement, useTestIds: Boolean = BuildConfig.ADS_USE_TEST_IDS): String =
        if (useTestIds) {
            TEST_BANNER
        } else {
            when (placement) {
                BannerPlacement.TODAY -> BANNER_TODAY
                BannerPlacement.PROVIDERS -> BANNER_PROVIDERS
                BannerPlacement.MONTH -> BANNER_MONTH
                BannerPlacement.YEAR -> BANNER_YEAR
            }
        }

    fun interstitialAfterBill(useTestIds: Boolean = BuildConfig.ADS_USE_TEST_IDS): String =
        if (useTestIds) TEST_INTERSTITIAL else INTERSTITIAL_AFTER_BILL
}
