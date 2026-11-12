package com.postpole.gharkhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GharKhataDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun provider(name: String = "Milkman", archived: Boolean = false, sortOrder: Int = 0) = ProviderEntity(
        name = name,
        type = ProviderType.MILK,
        billingMode = BillingMode.PER_UNIT,
        unitLabel = "L",
        startEpochDay = 20_000,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = 1,
    )

    private suspend fun newProvider(name: String = "Milkman") = db.providerDao().upsert(provider(name))

    @Test
    fun rateOn_picksLatestRateNotAfterDay_andIgnoresLaterRate() = runBlocking {
        val id = newProvider()
        val dao = db.rateChangeDao()
        dao.insert(RateChangeEntity(providerId = id, effectiveFromEpochDay = 100, amountPaise = 5000))
        dao.insert(RateChangeEntity(providerId = id, effectiveFromEpochDay = 200, amountPaise = 6000))
        dao.insert(RateChangeEntity(providerId = id, effectiveFromEpochDay = 300, amountPaise = 7000))

        assertNull(dao.rateOn(id, 99))
        assertEquals(5000L, dao.rateOn(id, 100))
        assertEquals(5000L, dao.rateOn(id, 199))
        assertEquals(6000L, dao.rateOn(id, 250))
        assertEquals(7000L, dao.rateOn(id, 1000))
    }

    @Test
    fun deletingProvider_cascadesToRatesMarksAndSettlements() = runBlocking {
        val id = newProvider()
        val other = newProvider("Other")
        db.rateChangeDao().insert(RateChangeEntity(providerId = id, effectiveFromEpochDay = 1, amountPaise = 1))
        db.dayMarkDao().upsert(DayMarkEntity(id, 10, DayStatus.ABSENT, updatedAt = 1))
        db.monthSettlementDao().upsert(MonthSettlementEntity(id, 202611, 100, 100, 5))
        db.dayMarkDao().upsert(DayMarkEntity(other, 10, DayStatus.ABSENT, updatedAt = 1))

        db.providerDao().delete(db.providerDao().getById(id)!!)

        assertEquals(emptyList<RateChangeEntity>(), db.rateChangeDao().getAll())
        assertEquals(listOf(other), db.dayMarkDao().getAll().map { it.providerId })
        assertEquals(emptyList<MonthSettlementEntity>(), db.monthSettlementDao().getAll())
    }

    @Test
    fun updatingProvider_keepsItsChildren() = runBlocking {
        val id = newProvider()
        db.dayMarkDao().upsert(DayMarkEntity(id, 10, DayStatus.ABSENT, updatedAt = 1))
        db.providerDao().upsert(db.providerDao().getById(id)!!.copy(name = "Renamed"))
        assertEquals("Renamed", db.providerDao().getById(id)!!.name)
        assertEquals(1, db.dayMarkDao().getAll().size)
    }

    @Test
    fun observeForDay_returnsOnlyThatDate() = runBlocking {
        val a = newProvider("A")
        val b = newProvider("B")
        val dao = db.dayMarkDao()
        dao.upsert(DayMarkEntity(a, 10, DayStatus.ABSENT, updatedAt = 1))
        dao.upsert(DayMarkEntity(b, 10, DayStatus.EXTRA, qty = 2.0, updatedAt = 1))
        dao.upsert(DayMarkEntity(a, 11, DayStatus.HALF, updatedAt = 1))

        assertEquals(setOf(a, b), dao.observeForDay(10).first().map { it.providerId }.toSet())
        assertEquals(listOf(DayStatus.HALF), dao.observeForDay(11).first().map { it.status })
        assertEquals(emptyList<DayMarkEntity>(), dao.observeForDay(12).first())
        assertEquals(listOf(10L, 11L), dao.observeRange(a, 10, 11).first().map { it.epochDay })
        assertEquals(listOf(11L), dao.observeRange(a, 11, 20).first().map { it.epochDay })
    }

    @Test
    fun upsertMark_overwrites_andClearRestoresNoMark() = runBlocking {
        val id = newProvider()
        val dao = db.dayMarkDao()
        dao.upsert(DayMarkEntity(id, 10, DayStatus.ABSENT, updatedAt = 1))
        dao.upsert(DayMarkEntity(id, 10, DayStatus.PRESENT, qty = 2.5, note = "x", updatedAt = 2))

        val marks = dao.observeForDay(10).first()
        assertEquals(1, marks.size)
        assertEquals(DayStatus.PRESENT, marks.single().status)
        assertEquals(2.5, marks.single().qty!!, 0.0)

        dao.clear(id, 10)
        assertEquals(emptyList<DayMarkEntity>(), dao.observeForDay(10).first())
    }

    @Test
    fun observeActive_hidesArchived_ordersBySortOrderThenName_andCounts() = runBlocking {
        val dao = db.providerDao()
        dao.upsert(provider("Zed", sortOrder = 0))
        dao.upsert(provider("Amy", sortOrder = 1))
        dao.upsert(provider("Bob", sortOrder = 0))
        val gone = dao.upsert(provider("Gone"))

        assertEquals(4, dao.countActive().first())
        dao.setArchived(gone, true)

        assertEquals(listOf("Bob", "Zed", "Amy"), dao.observeActive().first().map { it.name })
        assertEquals(4, dao.observeAll().first().size)
        assertEquals(3, dao.countActive().first())
    }

    @Test
    fun settlements_upsertGetDelete() = runBlocking {
        val id = newProvider()
        val dao = db.monthSettlementDao()
        dao.upsert(MonthSettlementEntity(id, 202611, 1000, 500, 7))
        dao.upsert(MonthSettlementEntity(id, 202611, 1000, 1000, 8, "done"))

        assertEquals(1000L, dao.get(id, 202611)!!.paidPaise)
        assertEquals(1, dao.observeFor(id).first().size)
        dao.delete(id, 202611)
        assertNull(dao.get(id, 202611))
    }

    @Test
    fun providerWithRates_loadsRateHistory() = runBlocking {
        val id = newProvider()
        db.rateChangeDao().insert(RateChangeEntity(providerId = id, effectiveFromEpochDay = 1, amountPaise = 10))
        db.rateChangeDao().insert(RateChangeEntity(providerId = id, effectiveFromEpochDay = 2, amountPaise = 20))
        val loaded = db.providerDao().observeActiveWithRates().first().single()
        assertEquals(2, loaded.rates.size)
    }
}
