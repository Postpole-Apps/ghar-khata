package com.postpole.gharkhata.domain

/** Decides whether Today shows the quiet "save a copy" card. Pure; never carries amounts or names. */
object BackupNudge {
    const val INTERVAL_DAYS = 14L
    private const val INTERVAL_MILLIS = INTERVAL_DAYS * 24 * 60 * 60 * 1000

    fun shouldShow(
        nowMillis: Long,
        lastBackupAtMillis: Long?,
        dismissedAtMillis: Long?,
        hasProviders: Boolean,
    ): Boolean {
        if (!hasProviders) return false
        val backupDue = lastBackupAtMillis == null || nowMillis - lastBackupAtMillis >= INTERVAL_MILLIS
        val dismissDue = dismissedAtMillis == null || nowMillis - dismissedAtMillis >= INTERVAL_MILLIS
        return backupDue && dismissDue
    }
}
