package com.postpole.gharkhata.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentDebugTest {
    @Test fun release_neverSetsGeographyOrDevices() {
        val c = consentDebugConfig(isDebugBuild = false, eeaRequested = true, idsCsv = "ABC,DEF")
        assertFalse(c.eeaGeography)
        assertTrue(c.testDeviceIds.isEmpty())
        assertFalse(c.isActive)
    }

    @Test fun debug_parsesCommaSeparatedIds() {
        val c = consentDebugConfig(true, true, " ABC , DEF,,ABC ")
        assertEquals(listOf("ABC", "DEF"), c.testDeviceIds)
        assertTrue(c.eeaGeography)
        assertTrue(c.isActive)
    }

    @Test fun debug_noIdsNoEea_isInactive() = assertFalse(consentDebugConfig(true, false, "").isActive)

    @Test fun debug_eeaWithoutIds_isActive() = assertTrue(consentDebugConfig(true, true, "").isActive)
}
