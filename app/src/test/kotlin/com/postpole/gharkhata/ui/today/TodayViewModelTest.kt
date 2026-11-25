package com.postpole.gharkhata.ui.today

import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.data.SettingsRepository
import com.postpole.gharkhata.domain.Schedule
import com.postpole.gharkhata.ui.provider.FakeProStatus
import com.postpole.gharkhata.ui.provider.RecordingAnalytics
import com.postpole.gharkhata.ui.provider.TestDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val db = TestDb()
    private val analytics = RecordingAnalytics()
    private val settings = SettingsRepository(
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create {
            java.io.File.createTempFile("today", ".preferences_pb").also { it.delete() }
        },
    )
    private val start = LocalDate.of(2020, 1, 1).toEpochDay()

    /** A Sunday and the Monday after it, both safely in the past. */
    private val sunday = LocalDate.now().minusDays(8).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    private val monday = sunday.plusDays(1)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun add(
        name: String,
        mode: BillingMode,
        mask: Int,
        type: ProviderType = ProviderType.MILK,
        qty: Double = 1.0,
    ): ProviderEntity {
        val id = db.providers.upsert(
            ProviderEntity(
                name = name, type = type, billingMode = mode, unitLabel = "L", defaultQty = qty,
                scheduleMask = mask, startEpochDay = start, createdAt = 0,
            ),
        )
        return db.providers.getById(id)!!
    }

    private fun TestScope.vm(): TodayViewModel {
        val vm = TodayViewModel(db.repository, FakeProStatus(), analytics, settings)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.state.collect { } }
        return vm
    }

    private fun TodayViewModel.row(p: ProviderEntity) = state.value.rows.first { it.provider.id == p.id }

    private fun mark(p: ProviderEntity, day: LocalDate) =
        db.marks.all.value.firstOrNull { it.providerId == p.id && it.epochDay == day.toEpochDay() }

    @Test
    fun scheduledDayFilter_hidesUnscheduledUnlessMarked() = runTest {
        val maid = add("Maid", BillingMode.MONTHLY_FIXED, Schedule.ALL_DAYS and Schedule.SUN.inv(), ProviderType.MAID)
        val milk = add("Milk", BillingMode.PER_UNIT, Schedule.ALL_DAYS)
        val vm = vm()

        vm.selectDate(monday.toEpochDay())
        assertEquals(setOf(maid.id, milk.id), vm.state.value.rows.map { it.provider.id }.toSet())

        vm.selectDate(sunday.toEpochDay())
        assertEquals(listOf(milk.id), vm.state.value.rows.map { it.provider.id })
        assertEquals(listOf(maid.id), vm.state.value.extraCandidates.map { it.id })

        // Once marked on Sunday, the maid shows up there.
        vm.addExtra(maid)
        assertEquals(setOf(maid.id, milk.id), vm.state.value.rows.map { it.provider.id }.toSet())
    }

    @Test
    fun futureDatesAreIgnored() = runTest {
        val vm = vm()
        val today = vm.state.value.epochDay
        vm.selectDate(today + 1)
        assertEquals(today, vm.state.value.epochDay)
    }

    @Test
    fun nextStatus_cycles_withHalfOnlyForNonPerUnit() {
        for (mode in listOf(BillingMode.PER_VISIT, BillingMode.MONTHLY_FIXED)) {
            assertEquals(DayStatus.ABSENT, TodayViewModel.nextStatus(DayStatus.PRESENT, mode))
            assertEquals(DayStatus.HALF, TodayViewModel.nextStatus(DayStatus.ABSENT, mode))
            assertEquals(DayStatus.PRESENT, TodayViewModel.nextStatus(DayStatus.HALF, mode))
        }
        assertEquals(DayStatus.ABSENT, TodayViewModel.nextStatus(DayStatus.PRESENT, BillingMode.PER_UNIT))
        assertEquals(DayStatus.PRESENT, TodayViewModel.nextStatus(DayStatus.ABSENT, BillingMode.PER_UNIT))
    }

    @Test
    fun chipCycle_perVisit_writesThenClearsMark() = runTest {
        val maid = add("Maid", BillingMode.PER_VISIT, Schedule.ALL_DAYS, ProviderType.MAID)
        val vm = vm()
        vm.selectDate(monday.toEpochDay())

        vm.cycleStatus(vm.row(maid))
        assertEquals(DayStatus.ABSENT, mark(maid, monday)?.status)
        vm.cycleStatus(vm.row(maid))
        assertEquals(DayStatus.HALF, mark(maid, monday)?.status)
        vm.cycleStatus(vm.row(maid))
        assertNull("back to default must not leave a PRESENT row", mark(maid, monday))
        assertEquals(DayStatus.PRESENT, vm.row(maid).status)
    }

    @Test
    fun chipCycle_perUnit_skipsHalf() = runTest {
        val milk = add("Milk", BillingMode.PER_UNIT, Schedule.ALL_DAYS)
        val vm = vm()
        vm.selectDate(monday.toEpochDay())

        vm.cycleStatus(vm.row(milk))
        assertEquals(DayStatus.ABSENT, mark(milk, monday)?.status)
        vm.cycleStatus(vm.row(milk))
        assertNull(mark(milk, monday))
    }

    @Test
    fun chipReturnKeepsChangedQty() = runTest {
        val milk = add("Milk", BillingMode.PER_UNIT, Schedule.ALL_DAYS)
        val vm = vm()
        vm.selectDate(monday.toEpochDay())

        vm.setQty(vm.row(milk), 2.0, null)
        vm.cycleStatus(vm.row(milk)) // -> ABSENT
        vm.cycleStatus(vm.row(milk)) // -> PRESENT with the changed qty, so the mark stays
        val m = mark(milk, monday)!!
        assertEquals(DayStatus.PRESENT, m.status)
        assertEquals(2.0, m.qty!!, 0.0)
    }

    @Test
    fun setQty_thenReset_restoresDefault() = runTest {
        val milk = add("Milk", BillingMode.PER_UNIT, Schedule.ALL_DAYS)
        val vm = vm()
        vm.selectDate(monday.toEpochDay())

        vm.setQty(vm.row(milk), 2.5, " thick ")
        val m = mark(milk, monday)!!
        assertEquals(DayStatus.PRESENT, m.status)
        assertEquals(2.5, m.qty!!, 0.0)
        assertEquals("thick", m.note)

        vm.reset(vm.row(milk))
        assertNull(mark(milk, monday))
        assertEquals(1.0, vm.row(milk).qty, 0.0)
        assertEquals(DayStatus.PRESENT, vm.row(milk).status)
    }

    @Test
    fun setQtyBackToDefault_clearsMark() = runTest {
        val milk = add("Milk", BillingMode.PER_UNIT, Schedule.ALL_DAYS)
        val vm = vm()
        vm.selectDate(monday.toEpochDay())
        vm.setQty(vm.row(milk), 2.0, null)
        vm.setQty(vm.row(milk), 1.0, null)
        assertNull(mark(milk, monday))
    }

    @Test
    fun extraDelivery_createsExtraMarkWithDefaultQty() = runTest {
        val can = add("Water can", BillingMode.PER_UNIT, 0, ProviderType.WATER_CAN, qty = 2.0)
        val vm = vm()
        vm.selectDate(monday.toEpochDay())
        assertTrue(vm.state.value.rows.isEmpty())
        assertEquals(listOf(can.id), vm.state.value.extraCandidates.map { it.id })

        vm.addExtra(can)
        val m = mark(can, monday)!!
        assertEquals(DayStatus.EXTRA, m.status)
        assertEquals(2.0, m.qty!!, 0.0)
        assertEquals(DayStatus.EXTRA, vm.row(can).status)
        assertTrue(vm.state.value.extraCandidates.isEmpty())

        // Reset removes the extra delivery entirely.
        vm.reset(vm.row(can))
        assertNull(mark(can, monday))
        assertTrue(vm.state.value.rows.isEmpty())
    }

    @Test
    fun extraRow_cycleReturnsToExtraNotPresent() = runTest {
        val can = add("Water can", BillingMode.PER_UNIT, 0, ProviderType.WATER_CAN)
        val vm = vm()
        vm.selectDate(monday.toEpochDay())
        vm.addExtra(can)
        vm.cycleStatus(vm.row(can))
        assertEquals(DayStatus.ABSENT, mark(can, monday)?.status)
        vm.cycleStatus(vm.row(can))
        assertEquals(DayStatus.EXTRA, mark(can, monday)?.status)
    }

    @Test
    fun marking_logsDayMarked() = runTest {
        val milk = add("Milk", BillingMode.PER_UNIT, Schedule.ALL_DAYS)
        val vm = vm()
        vm.selectDate(monday.toEpochDay())
        vm.cycleStatus(vm.row(milk))
        assertTrue(AnalyticsEvents.DAY_MARKED in analytics.events)
    }

    @Test
    fun emptyState_andFreeLimit() = runTest {
        val vm = vm()
        assertTrue(vm.state.value.noProviders)
        assertTrue(vm.state.value.canAddProvider)
        repeat(3) { add("P$it", BillingMode.PER_UNIT, Schedule.ALL_DAYS) }
        assertEquals(3, vm.state.value.activeCount)
        assertFalse(vm.state.value.canAddProvider)
    }
}
