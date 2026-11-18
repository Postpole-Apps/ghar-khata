package com.postpole.gharkhata.ads

import com.postpole.gharkhata.BuildConfig

/** Where a banner may appear. Interstitials have their own single placement. */
enum class BannerPlacement { TODAY, PROVIDERS, MONTH, YEAR }

/**
 * Ad unit IDs. Production IDs are exactly those in the ads plan; debug / internal builds use
 * Google's official demo units (https://developers.google.com/admob/android/test-ads) and must
 * never load production units.
 */
object AdUnits {
    const val BANNER_TODAY = "ca-app-pub-2007361247232604/6249514775"
    const val BANNER_PROVIDERS = "ca-app-pub-2007361247232604/7287264274"
    const val BANNER_MONTH = "ca-app-pub-2007361247232604/1300440426"
    const val BANNER_YEAR = "ca-app-pub-2007361247232604/2921302990"
    const val INTERSTITIAL_AFTER_BILL = "ca-app-pub-2007361247232604/5355894640"

    const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    /** Listed for completeness; rewarded ads are off (`ads_rewarded_enabled=false`) and have no UI. */
    const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    fun banner(placement: BannerPlacement, useTestAds: Boolean = BuildConfig.USE_TEST_ADS): String =
        if (useTestAds) {
            TEST_BANNER
        } else {
            when (placement) {
                BannerPlacement.TODAY -> BANNER_TODAY
                BannerPlacement.PROVIDERS -> BANNER_PROVIDERS
                BannerPlacement.MONTH -> BANNER_MONTH
                BannerPlacement.YEAR -> BANNER_YEAR
            }
        }

    fun interstitialAfterBill(useTestAds: Boolean = BuildConfig.USE_TEST_ADS): String =
        if (useTestAds) TEST_INTERSTITIAL else INTERSTITIAL_AFTER_BILL
}
