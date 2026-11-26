package com.postpole.gharkhata.ui.month

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DialIntentTest {
    @Test fun dialUri_keepsPlusAndDigits() {
        assertEquals("tel:+919876543210", dialUri("+91 98765-43210").toString())
    }

    @Test fun dialUri_stripsBracketsAndSpaces() {
        assertEquals("tel:02223456789", dialUri("(022) 2345 6789").toString())
        assertEquals("tel:9876543210", dialUri("98.76.54-32 10").toString())
    }

    @Test fun dialUri_blankOrTooShort_returnsNull() {
        assertNull(dialUri(""))
        assertNull(dialUri("  "))
        assertNull(dialUri("-"))
        assertNull(dialUri("1"))
        assertNull(dialUri("12"))
        assertNull(dialUri("+12"))
        assertNotNull(dialUri("123"))
        assertNotNull(dialUri("1234"))
    }

    @Test fun dialUri_plusOnlyAtStart() {
        assertEquals("tel:9876", dialUri("98+76").toString())
    }

    @Test fun dialUri_longPastedString_isNotTruncated() {
        val raw = "+91 (98765) 43210 - 11111 22222 33333"
        assertEquals("tel:+" + raw.filter { it.isDigit() }, dialUri(raw).toString())
    }
}
