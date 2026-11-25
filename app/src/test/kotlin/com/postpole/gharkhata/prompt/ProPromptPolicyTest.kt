package com.postpole.gharkhata.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ProPromptPolicyTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 100 * day
    private val settings = ProPromptSettings()

    /** History that passes every sheet rule. */
    private val sheetReady = ProPromptHistory(sessionCount = 3, installAgeMs = 2 * day, billMilestones = 2)

    /** History that passes every card rule. */
    private val cardReady = ProPromptHistory(sessionCount = 2, distinctMarkedDays = 7)

    private fun sheet(
        h: ProPromptHistory = sheetReady,
        s: ProPromptSettings = settings,
        screen: PromptScreen = PromptScreen.PROVIDER_MONTH,
        isPro: Boolean = false,
        removeAds: Boolean = false,
    ) = ProPromptPolicy.evaluate(
        ProPromptTrigger.BILL_MILESTONE, h, s, ProPromptContext(screen, now, isPro, removeAds),
    )

    private fun card(
        h: ProPromptHistory = cardReady,
        s: ProPromptSettings = settings,
        screen: PromptScreen = PromptScreen.PROVIDER_LIST,
        isPro: Boolean = false,
        removeAds: Boolean = false,
        freeLimit: Boolean = false,
    ) = ProPromptPolicy.evaluate(
        ProPromptTrigger.MARKED_DAYS_MILESTONE, h, s, ProPromptContext(screen, now, isPro, removeAds, freeLimit),
    )

    // --- both triggers -------------------------------------------------------------------------

    @Test fun eligibleBaselines() {
        assertTrue(sheet())
        assertTrue(card())
    }

    @Test fun neverInSessionOne() {
        assertFalse(sheet(sheetReady.copy(sessionCount = 1)))
        assertFalse(card(cardReady.copy(sessionCount = 1)))
        assertFalse(card(cardReady.copy(sessionCount = 0)))
    }

    @Test fun neverForProOrWhenDisabled() {
        assertFalse(sheet(isPro = true))
        assertFalse(card(isPro = true))
        assertFalse(sheet(s = settings.copy(enabled = false)))
        assertFalse(card(s = settings.copy(enabled = false)))
    }

    @Test fun maxOneAutoPromptPerSession() {
        assertFalse(sheet(sheetReady.copy(autoPromptShownThisSession = true)))
        assertFalse(card(cardReady.copy(autoPromptShownThisSession = true)))
    }

    @Test fun shownInterstitialSuppressesAutoPrompts() {
        assertFalse(sheet(sheetReady.copy(interstitialShownThisSession = true)))
        assertFalse(card(cardReady.copy(interstitialShownThisSession = true)))
    }

    @Test fun hardGateSkipsFor48Hours() {
        val hour = TimeUnit.HOURS.toMillis(1)
        assertFalse(sheet(sheetReady.copy(lastHardGateAt = now - 47 * hour)))
        assertFalse(card(cardReady.copy(lastHardGateAt = now - 1)))
        assertTrue(sheet(sheetReady.copy(lastHardGateAt = now - 48 * hour)))
        assertTrue(card(cardReady.copy(lastHardGateAt = now - 49 * hour)))
    }

    // --- bill_milestone sheet ------------------------------------------------------------------

    @Test fun sheetOnlyOnMonthScreen() {
        for (screen in PromptScreen.entries.filter { it != PromptScreen.PROVIDER_MONTH }) {
            assertFalse("sheet on $screen", sheet(screen = screen))
        }
    }

    @Test fun sheetNeedsMinSessionAndInstallAge() {
        assertFalse(sheet(sheetReady.copy(sessionCount = 2)))
        assertFalse(sheet(sheetReady.copy(installAgeMs = 2 * day - 1)))
        assertTrue(sheet(sheetReady.copy(sessionCount = 3, installAgeMs = 2 * day)))
    }

    @Test fun sheetCooldownIsSevenDays() {
        assertFalse(sheet(sheetReady.copy(lastSheetAt = now - 7 * day + 1)))
        assertTrue(sheet(sheetReady.copy(lastSheetAt = now - 7 * day)))
    }

    @Test fun threeSheetDismissalsStopSheetsForGood() {
        assertTrue(sheet(sheetReady.copy(sheetDismissals = 2)))
        assertFalse(sheet(sheetReady.copy(sheetDismissals = 3)))
        assertFalse(sheet(sheetReady.copy(sheetDismissals = 3, lastSheetAt = now - 365 * day)))
    }

    @Test fun sheetNeedsBillMilestones() {
        assertFalse(sheet(sheetReady.copy(billMilestones = 1)))
        assertTrue(sheet(sheetReady.copy(billMilestones = 2)))
    }

    @Test fun removeAdsOwnersNeverGetTheSheet() {
        assertFalse(sheet(removeAds = true))
    }

    @Test fun sheetIgnoresFreeLimitAndCardState() {
        val h = sheetReady.copy(lastCardAt = now, cardDismissals = 3, cardDismissedAt = now)
        assertTrue(sheet(h))
    }

    // --- marked_days_milestone card ------------------------------------------------------------

    @Test fun cardOnlyOnProviderList_neverToday() {
        for (screen in PromptScreen.entries.filter { it != PromptScreen.PROVIDER_LIST }) {
            assertFalse("card on $screen", card(screen = screen))
        }
        assertFalse(card(screen = PromptScreen.TODAY))
    }

    @Test fun cardHiddenWhileFreeLimitCardShows() {
        assertFalse(card(freeLimit = true))
    }

    @Test fun cardNeedsSessionTwoNotTheSheetMinimum() {
        assertTrue(card(cardReady.copy(sessionCount = 2, installAgeMs = 0)))
    }

    @Test fun cardCooldownIsThreeDays() {
        assertFalse(card(cardReady.copy(lastCardAt = now - 3 * day + 1)))
        assertTrue(card(cardReady.copy(lastCardAt = now - 3 * day)))
    }

    @Test fun cardTriggerCooldownIs14DaysAfterDismissal() {
        assertFalse(card(cardReady.copy(cardDismissedAt = now - 14 * day + 1, cardDismissals = 1)))
        assertTrue(card(cardReady.copy(cardDismissedAt = now - 14 * day, cardDismissals = 1)))
    }

    @Test fun threeCardDismissalsStopTheTrigger() {
        assertTrue(card(cardReady.copy(cardDismissals = 2)))
        assertFalse(card(cardReady.copy(cardDismissals = 3)))
    }

    @Test fun cardNeedsMarkedDays() {
        assertFalse(card(cardReady.copy(distinctMarkedDays = 6)))
        assertTrue(card(cardReady.copy(distinctMarkedDays = 7)))
    }

    @Test fun removeAdsOwnersGetTheCard() {
        assertTrue(card(removeAds = true))
    }

    @Test fun cardIgnoresSheetState() {
        assertTrue(card(cardReady.copy(lastSheetAt = now, sheetDismissals = 3)))
    }

    // --- Remote Config -------------------------------------------------------------------------

    @Test fun defaults() {
        val d = ProPromptSettings()
        assertTrue(d.enabled)
        assertEquals(3, d.minSession)
        assertEquals(7, d.markedDays)
        assertEquals(2, d.billMilestones)
    }

    @Test fun remoteConfigValuesAreClamped() {
        val low = ProPromptSettings(minSession = 0, markedDays = 1, billMilestones = 0).sanitized()
        assertEquals(2, low.minSession)
        assertEquals(5, low.markedDays)
        assertEquals(1, low.billMilestones)
        val high = ProPromptSettings(minSession = 50, markedDays = 99, billMilestones = 99).sanitized()
        assertEquals(50, high.minSession)
        assertEquals(21, high.markedDays)
        assertEquals(5, high.billMilestones)
    }

    @Test fun overridesChangeThresholds() {
        val s = settings.copy(minSession = 5, markedDays = 10, billMilestones = 4)
        assertFalse(sheet(sheetReady.copy(sessionCount = 4, billMilestones = 4), s))
        assertFalse(sheet(sheetReady.copy(sessionCount = 5, billMilestones = 3), s))
        assertTrue(sheet(sheetReady.copy(sessionCount = 5, billMilestones = 4), s))
        assertFalse(card(cardReady.copy(distinctMarkedDays = 9), s))
        assertTrue(card(cardReady.copy(distinctMarkedDays = 10), s))
    }

    @Test fun outOfRangeOverridesAreClampedBeforeEvaluating() {
        // markedDays=1 clamps to 5, billMilestones=0 clamps to 1, minSession=0 clamps to 2.
        val s = ProPromptSettings(minSession = 0, markedDays = 1, billMilestones = 0)
        assertFalse(card(cardReady.copy(distinctMarkedDays = 4), s))
        assertTrue(card(cardReady.copy(distinctMarkedDays = 5), s))
        assertFalse(sheet(sheetReady.copy(billMilestones = 0), s))
        assertTrue(sheet(sheetReady.copy(sessionCount = 2, billMilestones = 1), s))
    }

    @Test fun autoTriggerNamesAreEnumOnly() {
        assertEquals("auto_marked_days_milestone", ProPromptTrigger.MARKED_DAYS_MILESTONE.autoName)
        assertEquals("auto_bill_milestone", ProPromptTrigger.BILL_MILESTONE.autoName)
        assertEquals("card", ProPromptTrigger.MARKED_DAYS_MILESTONE.surface)
        assertEquals("sheet", ProPromptTrigger.BILL_MILESTONE.surface)
    }
}
