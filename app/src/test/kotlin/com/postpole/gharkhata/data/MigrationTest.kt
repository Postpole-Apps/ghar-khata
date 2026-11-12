package com.postpole.gharkhata.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_keepsRowsAndAddsNullArchivedOnEpochDay() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO providers (id, name, type, billingMode, unitLabel, defaultQty, scheduleMask, " +
                    "paidLeavesPerMonth, startEpochDay, archived, phone, note, sortOrder, createdAt) " +
                    "VALUES (7, 'Milk', 'MILK', 'PER_UNIT', 'L', 1.5, 127, 0, 20000, 1, NULL, NULL, 0, 5)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        db.query("SELECT id, name, archived, archivedOnEpochDay FROM providers").use { c ->
            assertEquals(1, c.count)
            assertTrue(c.moveToFirst())
            assertEquals(7, c.getInt(0))
            assertEquals("Milk", c.getString(1))
            assertEquals(1, c.getInt(2))
            assertTrue(c.isNull(3))
        }
        db.close()
    }

    private companion object {
        const val DB = "migration-test"
    }
}
