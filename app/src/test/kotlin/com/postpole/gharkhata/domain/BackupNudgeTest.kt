package com.postpole.gharkhata.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupNudgeTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun show(last: Long? = null, dismissed: Long? = null, providers: Boolean = true) =
        BackupNudge.shouldShow(now, last, dismissed, providers)

    @Test fun noProviders_hidden() = assertFalse(show(providers = false))
    @Test fun neverBackedUp_shown() = assertTrue(show())
    @Test fun backup13DaysAgo_hidden() = assertFalse(show(last = now - 13 * day))
    @Test fun backup14DaysAgo_shown() = assertTrue(show(last = now - 14 * day))
    @Test fun dismiss13DaysAgo_hidden() = assertFalse(show(dismissed = now - 13 * day))
    @Test fun dismiss14DaysAgo_shown() = assertTrue(show(dismissed = now - 14 * day))
    @Test fun freshBackup_resetsCue() {
        assertTrue(show(last = now - 30 * day))
        assertFalse(show(last = now))
    }
}
