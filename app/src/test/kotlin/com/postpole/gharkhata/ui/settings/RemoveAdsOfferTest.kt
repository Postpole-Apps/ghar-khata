package com.postpole.gharkhata.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoveAdsOfferTest {
    private fun offer(
        isPro: Boolean = false,
        hasRemoveAds: Boolean = false,
        price: String? = null,
        pending: Boolean = false,
        unavailable: Boolean = false,
        adsShipping: Boolean = true,
    ) = removeAdsOffer(isPro, hasRemoveAds, price, pending, unavailable, adsShipping)

    @Test fun neitherOwned_showsPlayPrice() =
        assertEquals(RemoveAdsOffer.Price("Play price"), offer(price = "Play price"))

    @Test fun pro_neverPitchedRemoveAds() = assertNull(offer(isPro = true, price = "x"))

    @Test fun removeAdsOwned_notResold() = assertNull(offer(hasRemoveAds = true, price = "x"))

    @Test fun noAdsBuild_noRow() = assertNull(offer(price = "x", adsShipping = false))

    @Test fun missingProduct_showsUnavailable() = assertEquals(RemoveAdsOffer.Unavailable, offer(unavailable = true))

    @Test fun notLoadedYet_showsLoading() = assertEquals(RemoveAdsOffer.Loading, offer())

    @Test fun pending_showsPending() = assertEquals(RemoveAdsOffer.Pending, offer(price = "x", pending = true))
}
