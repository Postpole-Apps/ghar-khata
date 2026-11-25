package com.postpole.gharkhata.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ProPromptPolicyTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 100 * day
    private val sheet = ProPromptTrigger.BILL_MILESTONE
    private val card = ProPromptTrigger.MARKED_DAYS_MILESTONE

    private val settings = ProPromptSettings.DEFAULT

    /** History that passes every sheet and card rule. */
    private val ready = ProPromptHistory(
        sessionCount = 3, installAgeDays = 5, billMilestones = 2, distinctMarkedDays = 7,
    )
    private val ctx = ProPromptContext(nowMs = now)

    private fun sheet(
        h: ProPromptHistory = ready, c: ProPromptContext = ctx, s: ProPromptSettings = settings,
        screen: ProPromptScreen = ProPromptScreen.PROVIDER_MONTH,
    ) = ProPromptPolicy.evaluate(sheet, screen, s, h, c)

    private fun card(
        h: ProPromptHistory = ready, c: ProPromptContext = ctx, s: ProPromptSettings = settings,
        screen: ProPromptScreen = ProPromptScreen.PROVIDER_LIST,
    ) = ProPromptPolicy.evaluate(card, screen, s, h, c)

    // --- Triggers and screens

    @Test fun bothTriggersEligibleWhenEverythingPasses() {
        assertTrue(sheet())
        assertTrue(card())
    }

    @Test fun cardNeverOnTodayOrAnyOtherScreen() {
        listOf(ProPromptScreen.TODAY, ProPromptScreen.PROVIDER_MONTH, ProPromptScreen.OTHER).forEach {
            assertFalse(card(screen = it))
        }
    }

    @Test fun sheetOnlyOnMonth() {
        listOf(ProPromptScreen.TODAY, ProPromptScreen.PROVIDER_LIST, ProPromptScreen.OTHER).forEach {
            assertFalse(sheet(screen = it))
        }
    }

    @Test fun analyticsValuesAreEnumOnly() {
        assertEquals("marked_days_milestone", card.analytics)
        assertEquals("bill_milestone", sheet.analytics)
        assertEquals("auto_marked_days_milestone", card.autoAnalytics)
        assertEquals("auto_bill_milestone", sheet.autoAnalytics)
        assertEquals("card", card.surface.analytics)
        assertEquals("sheet", sheet.surface.analytics)
    }

    // --- Global rules

    @Test fun neverForPro() {
        assertFalse(sheet(c = ctx.copy(isPro = true)))
        assertFalse(card(c = ctx.copy(isPro = true)))
    }

    @Test fun neverWhenDisabled() {
        val off = settings.copy(enabled = false)
        assertFalse(sheet(s = off))
        assertFalse(card(s = off))
    }

    @Test fun neverInSessionOne() {
        val first = ready.copy(sessionCount = 1)
        assertFalse(sheet(h = first, s = settings.copy(minSession = 1)))
        assertFalse(card(h = first))
        assertFalse(sheet(h = ready.copy(sessionCount = 0)))
    }

    @Test fun maxOneAutoPromptPerSession() {
        val shown = ctx.copy(autoPromptShownThisSession = true)
        assertFalse(sheet(c = shown))
        assertFalse(card(c = shown))
    }

    @Test fun shownInterstitialSuppressesPromptsForTheSession() {
        val ad = ctx.copy(interstitialShownThisSession = true)
        assertFalse(sheet(c = ad))
        assertFalse(card(c = ad))
    }

    @Test fun hardGateSilencesBothFor48Hours() {
        val gate = { ago: Long -> ready.copy(lastHardGateAt = now - ago) }
        val h47 = TimeUnit.HOURS.toMillis(47)
        val h48 = TimeUnit.HOURS.toMillis(48)
        assertFalse(sheet(h = gate(h47)))
        assertFalse(card(h = gate(h47)))
        assertTrue(sheet(h = gate(h48)))
        assertTrue(card(h = gate(h48)))
    }

    // --- Sheet (bill_milestone)

    @Test fun sheetNeedsMinSession_defaultThree() {
        assertFalse(sheet(h = ready.copy(sessionCount = 2)))
        assertTrue(sheet(h = ready.copy(sessionCount = 3)))
    }

    @Test fun sheetMinSessionFollowsRemoteConfigButNeverBelowTwo() {
        val five = ProPromptSettings.of(true, 5, 7, 2)
        assertFalse(sheet(h = ready.copy(sessionCount = 4), s = five))
        assertTrue(sheet(h = ready.copy(sessionCount = 5), s = five))
        val one = ProPromptSettings.of(true, 1, 7, 2)
        assertEquals(2, one.minSession)
        assertTrue(sheet(h = ready.copy(sessionCount = 2), s = one))
    }

    @Test fun sheetNeedsInstallAgeOfTwoDays() {
        assertFalse(sheet(h = ready.copy(installAgeDays = 1)))
        assertTrue(sheet(h = ready.copy(installAgeDays = 2)))
    }

    @Test fun sheetNeedsBillMilestones_defaultTwo() {
        assertFalse(sheet(h = ready.copy(billMilestones = 1)))
        assertTrue(sheet(h = ready.copy(billMilestones = 2)))
    }

    @Test fun sheetMilestonesFollowRemoteConfig() {
        val one = ProPromptSettings.of(true, 3, 7, 1)
        assertTrue(sheet(h = ready.copy(billMilestones = 1), s = one))
        val five = ProPromptSettings.of(true, 3, 7, 5)
        assertFalse(sheet(h = ready.copy(billMilestones = 4), s = five))
        assertTrue(sheet(h = ready.copy(billMilestones = 5), s = five))
    }

    @Test fun sheetCooldownIsSevenDays() {
        assertFalse(sheet(h = ready.copy(lastSheetAt = now - 7 * day + 1)))
        assertTrue(sheet(h = ready.copy(lastSheetAt = now - 7 * day)))
    }

    @Test fun threeSheetDismissalsStopSheetsForGood() {
        assertTrue(sheet(h = ready.copy(sheetDismissals = 2)))
        assertFalse(sheet(h = ready.copy(sheetDismissals = 3, lastSheetAt = now - 365 * day)))
    }

    @Test fun removeAdsOwnersNeverGetTheSheetButGetTheCard() {
        val owner = ctx.copy(hasRemoveAds = true)
        assertFalse(sheet(c = owner))
        assertTrue(card(c = owner))
    }

    @Test fun sheetIgnoresCardRules() {
        // Card dismissals and the card cooldown never block the sheet.
        val h = ready.copy(
            lastCardAt = now, cardDismissals = mapOf(card to 3), cardDismissedAt = mapOf(card to now),
        )
        assertTrue(sheet(h = h))
    }

    // --- Card (marked_days_milestone)

    @Test fun cardNeedsSessionTwo() {
        assertFalse(card(h = ready.copy(sessionCount = 1)))
        assertTrue(card(h = ready.copy(sessionCount = 2)))
    }

    @Test fun cardNeedsDistinctMarkedDays_defaultSeven() {
        assertFalse(card(h = ready.copy(distinctMarkedDays = 6)))
        assertTrue(card(h = ready.copy(distinctMarkedDays = 7)))
    }

    @Test fun cardMarkedDaysFollowRemoteConfig() {
        val five = ProPromptSettings.of(true, 3, 5, 2)
        assertTrue(card(h = ready.copy(distinctMarkedDays = 5), s = five))
        val twentyOne = ProPromptSettings.of(true, 3, 21, 2)
        assertFalse(card(h = ready.copy(distinctMarkedDays = 20), s = twentyOne))
        assertTrue(card(h = ready.copy(distinctMarkedDays = 21), s = twentyOne))
    }

    @Test fun cardCooldownIsThreeDays() {
        assertFalse(card(h = ready.copy(lastCardAt = now - 3 * day + 1)))
        assertTrue(card(h = ready.copy(lastCardAt = now - 3 * day)))
    }

    @Test fun cardWaitsFourteenDaysAfterItsDismissal() {
        val dismissed = { ago: Long -> ready.copy(cardDismissedAt = mapOf(card to now - ago), cardDismissals = mapOf(card to 1)) }
        assertFalse(card(h = dismissed(14 * day - 1)))
        assertTrue(card(h = dismissed(14 * day)))
    }

    @Test fun threeCardDismissalsStopTheTrigger() {
        assertTrue(card(h = ready.copy(cardDismissals = mapOf(card to 2))))
        assertFalse(card(h = ready.copy(cardDismissals = mapOf(card to 3))))
    }

    @Test fun cardHiddenWhileFreeLimitCardShows() {
        assertFalse(card(c = ctx.copy(freeLimitCardShowing = true)))
    }

    // --- Remote Config bounds

    @Test fun settingsAreClamped() {
        ProPromptSettings.of(true, 0, 0, 0).let {
            assertEquals(2, it.minSession); assertEquals(5, it.markedDays); assertEquals(1, it.billMilestones)
        }
        ProPromptSettings.of(true, 99, 99, 99).let {
            assertEquals(99, it.minSession); assertEquals(21, it.markedDays); assertEquals(5, it.billMilestones)
        }
    }

    @Test fun defaultsMatchTheConfirmedValues() {
        assertTrue(ProPromptSettings.DEFAULT.enabled)
        assertEquals(3, ProPromptSettings.DEFAULT.minSession)
        assertEquals(7, ProPromptSettings.DEFAULT.markedDays)
        assertEquals(2, ProPromptSettings.DEFAULT.billMilestones)
        assertEquals("pro_auto_prompt_enabled", ProPromptKeys.ENABLED)
        assertEquals("pro_auto_prompt_min_session", ProPromptKeys.MIN_SESSION)
        assertEquals("pro_prompt_marked_days", ProPromptKeys.MARKED_DAYS)
        assertEquals("pro_prompt_bill_milestones", ProPromptKeys.BILL_MILESTONES)
    }

    @Test fun policyClampsUnclampedSettingsToo() {
        // A hand-built settings object (not via `of`) still cannot go below the floors / outside the bounds.
        val raw = ProPromptSettings(enabled = true, minSession = 0, markedDays = 1, billMilestones = 0)
        assertFalse(sheet(h = ready.copy(sessionCount = 1), s = raw))
        assertFalse(card(h = ready.copy(distinctMarkedDays = 4), s = raw))
        assertTrue(card(h = ready.copy(distinctMarkedDays = 5), s = raw))
        assertTrue(sheet(h = ready.copy(billMilestones = 1), s = raw))
    }
}
