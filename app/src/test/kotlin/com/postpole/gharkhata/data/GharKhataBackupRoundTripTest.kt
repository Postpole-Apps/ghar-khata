package com.postpole.gharkhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Every field of providers, rate_changes, day_marks and month_settlements must survive a backup. */
@RunWith(RobolectricTestRunner::class)
class GharKhataBackupRoundTripTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: BackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = BackupRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun export(): String = ByteArrayOutputStream().also { runBlocking { repo.exportJson(it, now = 1) } }
        .toString(Charsets.UTF_8)

    private suspend fun import(text: String) = repo.importJson(ByteArrayInputStream(text.toByteArray()))

    @Test
    fun providerRatesMarksAndSettlement_roundTripExactly() = runBlocking {
        val maid = db.providerDao().upsert(
            ProviderEntity(
                name = "Kamla बाई",
                type = ProviderType.MAID,
                billingMode = BillingMode.MONTHLY_FIXED,
                unitLabel = "visit",
                defaultQty = 1.0,
                scheduleMask = 0b0111111,
                paidLeavesPerMonth = 2,
                startEpochDay = 19_900,
                archived = true,
                archivedOnEpochDay = 20_100,
                phone = "+91 98765 43210",
                note = "Comes after 8am",
                sortOrder = 3,
                createdAt = 1_700_000_000_000,
            ),
        )
        val milk = db.providerDao().upsert(
            ProviderEntity(
                name = "Milk",
                type = ProviderType.MILK,
                billingMode = BillingMode.PER_UNIT,
                unitLabel = "L",
                defaultQty = 1.5,
                scheduleMask = 0b1111111,
                startEpochDay = 20_000,
                createdAt = 5,
            ),
        )
        db.rateChangeDao().insert(RateChangeEntity(providerId = maid, effectiveFromEpochDay = 19_900, amountPaise = 600_000))
        db.rateChangeDao().insert(RateChangeEntity(providerId = maid, effectiveFromEpochDay = 20_050, amountPaise = 650_050))
        db.rateChangeDao().insert(RateChangeEntity(providerId = milk, effectiveFromEpochDay = 20_000, amountPaise = 6_000))
        db.dayMarkDao().upsert(DayMarkEntity(maid, 20_001, DayStatus.ABSENT, note = "sick", updatedAt = 11))
        db.dayMarkDao().upsert(DayMarkEntity(maid, 20_002, DayStatus.HALF, updatedAt = 12))
        db.dayMarkDao().upsert(DayMarkEntity(milk, 20_003, DayStatus.EXTRA, qty = 2.5, updatedAt = 13))
        db.dayMarkDao().upsert(DayMarkEntity(milk, 20_004, DayStatus.PRESENT, qty = 0.5, note = "less", updatedAt = 14))
        db.monthSettlementDao().upsert(MonthSettlementEntity(maid, 202610, 600_000, 550_000, 20_030, "advance cut"))
        db.monthSettlementDao().upsert(MonthSettlementEntity(milk, 202611, 18_000, 18_000, 20_035))

        val providers = db.providerDao().getAll()
        val rates = db.rateChangeDao().getAll()
        val marks = db.dayMarkDao().getAll()
        val settlements = db.monthSettlementDao().getAll()
        val json = export()

        db.providerDao().deleteAll() // wipe (cascades)
        assertEquals(emptyList<DayMarkEntity>(), db.dayMarkDao().getAll())
        assertEquals(2, import(json))

        assertEquals(providers, db.providerDao().getAll())
        assertEquals(rates, db.rateChangeDao().getAll())
        assertEquals(marks, db.dayMarkDao().getAll())
        assertEquals(settlements, db.monthSettlementDao().getAll())
    }

    @Test
    fun invalidFile_doesNotWipeExistingData() = runBlocking {
        val id = db.providerDao().upsert(
            ProviderEntity(
                name = "keep", type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT,
                unitLabel = "L", startEpochDay = 1, createdAt = 1,
            ),
        )
        db.dayMarkDao().upsert(DayMarkEntity(id, 5, DayStatus.ABSENT, updatedAt = 1))
        val before = db.providerDao().getAll()

        listOf("", "{}", "garbage", "{\"format\":\"postpole-backup\",\"version\":3,\"providers\":[]}").forEach { bad ->
            assertThrows(InvalidBackupException::class.java) { runBlocking { import(bad) } }
        }
        assertEquals(before, db.providerDao().getAll())
        assertEquals(1, db.dayMarkDao().getAll().size)
    }
}
