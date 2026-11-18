package com.postpole.gharkhata.ads

import com.postpole.gharkhata.ui.Routes
import com.postpole.gharkhata.ui.bannerPlacementFor
import com.postpole.gharkhata.ui.isInterstitialDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsPolicyTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private val today = 100L
    private val settings = AdsSettings()
    private val seasoned = InterstitialHistory(sessionNumber = 5)

    private fun allowed(
        s: AdsSettings = settings,
        h: InterstitialHistory = seasoned,
        installAge: Long = 10 * day,
        at: Long = now,
    ) = InterstitialPolicy.allowed(s, h, installAge, at, today)

    @Test fun launchDefaultsMatchPlan() {
        val d = AdsSettings.DEFAULT
        assertTrue(d.adsEnabled && d.bannerEnabled && d.interstitialEnabled)
        assertFalse(d.rewardedEnabled || d.nativeEnabled || d.appOpenEnabled)
        assertTrue(d.interstitialBlockFirstSession)
        assertEquals(3, d.interstitialMinInstallDays)
        assertEquals(480, d.interstitialMinIntervalSec)
        assertEquals(1, d.interstitialMaxPerSession)
        assertEquals(3, d.interstitialMaxPerDay)
        assertEquals("PG", d.maxContentRating)
        assertEquals(1, d.rewardedDailyLimit)
        assertEquals(13, AdsSettings.defaultsMap().size)
    }

    @Test fun allowedWhenAllCapsPass() = assertTrue(allowed())

    @Test fun blockedInFirstSession() = assertFalse(allowed(h = InterstitialHistory(sessionNumber = 1)))

    @Test fun blockedInFirstThreeDays() {
        assertFalse(allowed(installAge = 3 * day - 1))
        assertTrue(allowed(installAge = 3 * day))
    }

    @Test fun maxOnePerSession() = assertFalse(allowed(h = seasoned.copy(shownThisSession = 1)))

    @Test fun maxThreePerDay() {
        assertFalse(allowed(h = seasoned.copy(day = today, shownOnDay = 3)))
        // Yesterday's count does not carry over.
        assertTrue(allowed(h = seasoned.copy(day = today - 1, shownOnDay = 3)))
    }

    @Test fun minEightMinutesApart() {
        val h = seasoned.copy(lastShownMs = now - 479_000)
        assertFalse(allowed(h = h))
        assertTrue(allowed(h = seasoned.copy(lastShownMs = now - 480_000)))
    }

    @Test fun killSwitchAndFlagsBlock() {
        assertFalse(allowed(s = settings.copy(adsEnabled = false)))
        assertFalse(allowed(s = settings.copy(interstitialEnabled = false)))
    }

    @Test fun recordShownUpdatesCounters() {
        val h = seasoned.recordShown(now, today).recordShown(now, today)
        assertEquals(2, h.shownThisSession)
        assertEquals(2, h.shownToday(today))
        assertEquals(0, h.shownToday(today + 1))
    }

    @Test fun debugUsesOnlyDemoUnits() {
        BannerPlacement.entries.forEach { assertEquals("ca-app-pub-3940256099942544/9214589741", AdUnits.banner(it, true)) }
        assertEquals("ca-app-pub-3940256099942544/1033173712", AdUnits.interstitialAfterBill(true))
    }

    @Test fun releaseUsesProductionUnits() {
        assertEquals("ca-app-pub-2007361247232604/6249514775", AdUnits.banner(BannerPlacement.TODAY, false))
        assertEquals("ca-app-pub-2007361247232604/7287264274", AdUnits.banner(BannerPlacement.PROVIDERS, false))
        assertEquals("ca-app-pub-2007361247232604/1300440426", AdUnits.banner(BannerPlacement.MONTH, false))
        assertEquals("ca-app-pub-2007361247232604/2921302990", AdUnits.banner(BannerPlacement.YEAR, false))
        assertEquals("ca-app-pub-2007361247232604/5355894640", AdUnits.interstitialAfterBill(false))
    }

    @Test fun bannersOnlyOnBrowseScreens() {
        assertEquals(BannerPlacement.TODAY, bannerPlacementFor(Routes.TODAY))
        assertEquals(BannerPlacement.PROVIDERS, bannerPlacementFor(Routes.PROVIDERS))
        assertEquals(BannerPlacement.MONTH, bannerPlacementFor(Routes.PROVIDER_MONTH))
        assertEquals(BannerPlacement.YEAR, bannerPlacementFor(Routes.YEAR))
        listOf(Routes.PROVIDER_EDIT, Routes.BILL, Routes.SETTINGS, null).forEach { assertNull(bannerPlacementFor(it)) }
    }

    @Test fun interstitialDestinations() {
        assertTrue(isInterstitialDestination(Routes.TODAY))
        assertTrue(isInterstitialDestination(Routes.PROVIDERS))
        assertTrue(isInterstitialDestination(Routes.PROVIDER_MONTH))
        listOf(Routes.YEAR, Routes.SETTINGS, Routes.BILL, Routes.PROVIDER_EDIT).forEach {
            assertFalse(isInterstitialDestination(it))
        }
    }
}
