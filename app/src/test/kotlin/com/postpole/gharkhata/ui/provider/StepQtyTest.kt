package com.postpole.gharkhata.ui.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class StepQtyTest {
    @Test
    fun litresStepByHalf() {
        assertEquals("1.5", stepQty("1", "L", +1))
        assertEquals("0.5", stepQty("1", "L", -1))
    }

    @Test
    fun otherUnitsStepByOne() {
        assertEquals("2", stepQty("1", "copy", +1))
        assertEquals("3", stepQty("4", "can", -1))
    }

    @Test
    fun neverReachesZeroAndToleratesBadInput() {
        assertEquals("1", stepQty("1", "copy", -1))
        assertEquals("0.5", stepQty("0.5", "L", -1))
        assertEquals("2", stepQty("", "copy", +1))
    }
}
