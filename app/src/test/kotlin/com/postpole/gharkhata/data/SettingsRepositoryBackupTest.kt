package com.postpole.gharkhata.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SettingsRepositoryBackupTest {
    private val repo = SettingsRepository(
        PreferenceDataStoreFactory.create { File.createTempFile("backup", ".preferences_pb").also { it.delete() } },
    )

    @Test
    fun timestampsDefaultToNullAndPersist() = runTest {
        assertNull(repo.lastBackupAtMillis.first())
        assertNull(repo.lastBackupNudgeDismissedAtMillis.first())
        repo.setLastBackupAtMillis(123L)
        repo.setLastBackupNudgeDismissedAtMillis(456L)
        assertEquals(123L, repo.lastBackupAtMillis.first())
        assertEquals(456L, repo.lastBackupNudgeDismissedAtMillis.first())
    }
}
