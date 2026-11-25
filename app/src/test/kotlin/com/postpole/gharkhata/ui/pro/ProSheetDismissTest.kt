package com.postpole.gharkhata.ui.pro

import com.postpole.gharkhata.pro.ProDismissMethod
import com.postpole.gharkhata.pro.ProPromptTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProSheetDismissTest {
    @Test fun controlsMapToTheThreeMethods() {
        assertEquals(ProDismissMethod.CLOSE, dismissMethodFor(ProSheetExit.CLOSE_BUTTON))
        assertEquals(ProDismissMethod.NOT_NOW, dismissMethodFor(ProSheetExit.NOT_NOW_BUTTON))
        // A scrim tap and the system back press both arrive as onDismissRequest.
        assertEquals(ProDismissMethod.SWIPE, dismissMethodFor(ProSheetExit.SCRIM_OR_BACK))
    }

    @Test fun methodEnumHasOnlyTheThreeValues() {
        assertEquals(listOf("close", "not_now", "swipe"), ProDismissMethod.entries.map { it.analytics })
    }

    @Test fun mappingIsTheSameInAutoAndManualModes() {
        // The mapping does not look at the reason; manual reasons just carry no auto trigger to log.
        ProReason.entries.forEach { _ ->
            assertEquals(ProDismissMethod.CLOSE, dismissMethodFor(ProSheetExit.CLOSE_BUTTON))
            assertEquals(ProDismissMethod.NOT_NOW, dismissMethodFor(ProSheetExit.NOT_NOW_BUTTON))
            assertEquals(ProDismissMethod.SWIPE, dismissMethodFor(ProSheetExit.SCRIM_OR_BACK))
        }
    }

    @Test fun onlyTheAutoSheetIsAPrompt() {
        assertEquals(ProPromptTrigger.BILL_MILESTONE, ProReason.AUTO_BILL_MILESTONE.autoTrigger)
        ProReason.entries.filter { it != ProReason.AUTO_BILL_MILESTONE }.forEach { assertNull(it.autoTrigger) }
    }

    @Test fun hardGatesAreTheFiveLockedFeatureReasons() {
        val hard = ProReason.entries.filter { it.isHardGate }.toSet()
        assertEquals(
            setOf(ProReason.PROVIDER_LIMIT, ProReason.BILL_IMAGE, ProReason.CSV, ProReason.YEAR_SUMMARY, ProReason.REMINDER),
            hard,
        )
        assertFalse(ProReason.GENERAL.isHardGate)
        assertFalse(ProReason.AUTO_BILL_MILESTONE.isHardGate)
        assertTrue(ProReason.entries.size == 7)
    }
}
