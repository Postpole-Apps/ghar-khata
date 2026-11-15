package com.postpole.gharkhata.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsPolicyTest {
    private val day = 86_400_000L
    private val now = 100 * day
    private val config = AdsConfig()

    private fun history(
        session: Int = 2,
        installAgeDays: Long = 5,
        thisSession: Int = 0,
        today: Int = 0,
        lastAgoSec: Long? = null,
    ) = InterstitialHistory(
        sessionNumber = session,
        installTimeMs = now - installAgeDays * day,
        shownThisSession = thisSession,
        shownToday = today,
        lastShownMs = lastAgoSec?.let { now - it * 1_000 },
    )

    @Test fun `eligible when every cap passes`() = assertTrue(InterstitialPolicy.allows(config, history(), now))

    @Test fun `blocked in first session`() = assertFalse(InterstitialPolicy.allows(config, history(session = 1), now))

    @Test fun `blocked in first three days`() {
        assertFalse(InterstitialPolicy.allows(config, history(installAgeDays = 2), now))
        assertTrue(InterstitialPolicy.allows(config, history(installAgeDays = 3), now))
    }

    @Test fun `max one per session`() = assertFalse(InterstitialPolicy.allows(config, history(thisSession = 1), now))

    @Test fun `max three per day`() {
        val relaxed = config.copy(interstitialMaxPerSession = 10)
        assertTrue(InterstitialPolicy.allows(relaxed, history(today = 2), now))
        assertFalse(InterstitialPolicy.allows(relaxed, history(today = 3), now))
    }

    @Test fun `min eight minutes apart`() {
        assertFalse(InterstitialPolicy.allows(config, history(lastAgoSec = 479), now))
        assertTrue(InterstitialPolicy.allows(config, history(lastAgoSec = 480), now))
    }

    @Test fun `kill switch and interstitial flag disable`() {
        assertFalse(InterstitialPolicy.allows(config.copy(adsEnabled = false), history(), now))
        assertFalse(InterstitialPolicy.allows(config.copy(interstitialEnabled = false), history(), now))
    }

    @Test fun `launch defaults match the ads plan`() {
        with(AdsConfig()) {
            assertTrue(adsEnabled && bannerEnabled && interstitialEnabled)
            assertFalse(rewardedEnabled || nativeEnabled || appOpenEnabled)
            assertTrue(interstitialBlockFirstSession)
            assertEquals(3, interstitialMinInstallDays)
            assertEquals(480, interstitialMinIntervalSec)
            assertEquals(1, interstitialMaxPerSession)
            assertEquals(3, interstitialMaxPerDay)
            assertEquals("PG", maxContentRating)
            assertEquals(1, rewardedDailyLimit)
        }
        assertEquals(13, AdsConfig.DEFAULTS.size)
        assertFalse(AdsConfig(adsEnabled = false).bannersAllowed)
    }

    @Test fun `debug uses demo units and release uses production units`() {
        BannerPlacement.entries.forEach {
            assertEquals("ca-app-pub-3940256099942544/9214589741", AdUnits.banner(it, useTestIds = true))
        }
        assertEquals("ca-app-pub-3940256099942544/1033173712", AdUnits.interstitialAfterBill(useTestIds = true))
        assertEquals("ca-app-pub-2007361247232604/6249514775", AdUnits.banner(BannerPlacement.TODAY, false))
        assertEquals("ca-app-pub-2007361247232604/7287264274", AdUnits.banner(BannerPlacement.PROVIDERS, false))
        assertEquals("ca-app-pub-2007361247232604/1300440426", AdUnits.banner(BannerPlacement.MONTH, false))
        assertEquals("ca-app-pub-2007361247232604/2921302990", AdUnits.banner(BannerPlacement.YEAR, false))
        assertEquals("ca-app-pub-2007361247232604/5355894640", AdUnits.interstitialAfterBill(false))
    }
}
