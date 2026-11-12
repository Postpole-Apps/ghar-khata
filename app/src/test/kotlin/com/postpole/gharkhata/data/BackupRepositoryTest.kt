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

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
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

    private suspend fun importText(text: String) = repo.importJson(ByteArrayInputStream(text.toByteArray()))

    private fun provider(name: String) = ProviderEntity(
        name = name,
        type = ProviderType.MILK,
        billingMode = BillingMode.PER_UNIT,
        unitLabel = "L",
        defaultQty = 1.5,
        scheduleMask = 0b1111111,
        startEpochDay = 20_000,
        phone = "98xx",
        createdAt = 5,
    )

    @Test
    fun roundTrip_restoresAllTablesExactly() = runBlocking {
        val id = db.providerDao().upsert(provider("Milk \"quoted\" ₹ 日本語\nnewline"))
        db.rateChangeDao().insert(RateChangeEntity(providerId = id, effectiveFromEpochDay = 20_000, amountPaise = 6000))
        db.dayMarkDao().upsert(DayMarkEntity(id, 20_001, DayStatus.PRESENT, qty = 2.0, updatedAt = 9))
        db.dayMarkDao().upsert(DayMarkEntity(id, 20_002, DayStatus.ABSENT, updatedAt = 9))
        db.monthSettlementDao().upsert(MonthSettlementEntity(id, 202611, 18000, 18000, 20_030, "paid"))
        val providers = db.providerDao().getAll()
        val rates = db.rateChangeDao().getAll()
        val marks = db.dayMarkDao().getAll()
        val settlements = db.monthSettlementDao().getAll()
        val json = export()

        db.providerDao().deleteAll()
        db.providerDao().upsert(provider("other data that must be replaced"))
        val count = importText(json)

        assertEquals(1, count)
        assertEquals(providers, db.providerDao().getAll())
        assertEquals(rates, db.rateChangeDao().getAll())
        assertEquals(marks, db.dayMarkDao().getAll())
        assertEquals(settlements, db.monthSettlementDao().getAll())
    }

    @Test
    fun roundTrip_emptyDatabase() = runBlocking {
        val json = export()
        db.providerDao().upsert(provider("x"))
        assertEquals(0, importText(json))
        assertEquals(emptyList<ProviderEntity>(), db.providerDao().getAll())
    }

    @Test
    fun export_isVersionedAndNamed() {
        val json = export()
        assertEquals(true, json.contains("\"format\": \"postpole-backup\""))
        assertEquals(true, json.contains("\"version\": 2"))
    }

    @Test
    fun invalidImport_leavesExistingDataUntouched() = runBlocking {
        db.providerDao().upsert(provider("keep"))
        val empty = """"rate_changes":[],"day_marks":[],"month_settlements":[]"""
        val p = """{"id":1,"name":"a","type":"MILK","billingMode":"PER_UNIT","unitLabel":"L","defaultQty":1.0,""" +
            """"scheduleMask":0,"paidLeavesPerMonth":0,"startEpochDay":1,"archived":false,"phone":null,""" +
            """"note":null,"sortOrder":0,"createdAt":1}"""
        listOf(
            "not json",
            """{"format":"other","version":2,"providers":[],$empty}""",
            """{"format":"postpole-backup","version":999,"providers":[],$empty}""",
            """{"format":"postpole-backup","version":1,"items":[]}""",
            """{"format":"postpole-backup","version":2,$empty}""",
            """{"format":"postpole-backup","version":2,"providers":[{"id":1,"name":"a"}],$empty}""",
            """{"format":"postpole-backup","version":2,"providers":[$p,$p],$empty}""",
            """{"format":"postpole-backup","version":2,"providers":[${p.replace("MILK", "NOPE")}],$empty}""",
            """{"format":"postpole-backup","version":2,"providers":[],"rate_changes":[{"id":1,"providerId":7,""" +
                """"effectiveFromEpochDay":1,"amountPaise":1}],"day_marks":[],"month_settlements":[]}""",
        ).forEach { bad ->
            assertThrows(bad, InvalidBackupException::class.java) { runBlocking { importText(bad) } }
        }
        assertEquals(listOf("keep"), db.providerDao().getAll().map { it.name })
    }

    @Test
    fun import_olderFormatV2ProviderWithoutArchivedOnEpochDay_restoresNull() = runBlocking {
        val p = """{"id":1,"name":"a","type":"MILK","billingMode":"PER_UNIT","unitLabel":"L","defaultQty":1.0,""" +
            """"scheduleMask":0,"paidLeavesPerMonth":0,"startEpochDay":1,"archived":true,"phone":null,""" +
            """"note":null,"sortOrder":0,"createdAt":1}"""
        val text = """{"format":"postpole-backup","version":2,"providers":[$p],""" +
            """"rate_changes":[],"day_marks":[],"month_settlements":[]}"""
        assertEquals(1, importText(text))
        val restored = db.providerDao().getAll().single()
        assertEquals("a", restored.name)
        assertEquals(true, restored.archived)
        assertEquals(null, restored.archivedOnEpochDay)
    }

    @Test
    fun csv_isHeaderOnlyPlaceholder() = runBlocking {
        val out = ByteArrayOutputStream()
        assertEquals(0, repo.exportItemsCsv(out))
        assertEquals("provider,date,status,qty\r\n", out.toString(Charsets.UTF_8))
    }
}
