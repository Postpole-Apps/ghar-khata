package com.postpole.gharkhata.ui.today

import androidx.lifecycle.viewModelScope
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.analytics.AnalyticsParams
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.data.SettingsRepository
import com.postpole.gharkhata.domain.Schedule
import com.postpole.gharkhata.ui.MainDispatcherRule
import com.postpole.gharkhata.ui.provider.FakeProStatus
import com.postpole.gharkhata.ui.provider.RecordingAnalytics
import com.postpole.gharkhata.ui.provider.TestDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TodayViewModelUndoTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val dataStoreScope = CoroutineScope(mainRule.dispatcher + SupervisorJob())
    private val db = TestDb()
    private val analytics = RecordingAnalytics()
    private val settings = SettingsRepository(
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            java.io.File.createTempFile("undo", ".preferences_pb").also { it.delete() }
        },
    )
    private val start = LocalDate.of(2020, 1, 1).toEpochDay()
    private val day = LocalDate.of(2024, 1, 1).toEpochDay()
    private val otherDay = day - 1
    private var now = 1_000L
    private val vms = mutableListOf<TodayViewModel>()

    @After
    fun tearDown() {
        vms.forEach { it.viewModelScope.cancel() }
        dataStoreScope.cancel()
    }

    private suspend fun add(name: String, mode: BillingMode, mask: Int = Schedule.ALL_DAYS, qty: Double = 1.0): ProviderEntity {
        val id = db.providers.upsert(
            ProviderEntity(
                name = name, type = ProviderType.MILK, billingMode = mode, unitLabel = "L", defaultQty = qty,
                scheduleMask = mask, startEpochDay = start, createdAt = 0,
            ),
        )
        return db.providers.getById(id)!!
    }

    private fun TestScope.vm(): TodayViewModel {
        val vm = TodayViewModel(db.repository, FakeProStatus(), analytics, settings) { now++ }.also { vms += it }
        backgroundScope.launch(mainRule.dispatcher) { vm.state.collect { } }
        vm.selectDate(day)
        return vm
    }

    private fun TodayViewModel.row(p: ProviderEntity) = state.value.rows.first { it.provider.id == p.id }

    private fun stored(p: ProviderEntity, d: Long = day) =
        db.marks.all.value.firstOrNull { it.providerId == p.id && it.epochDay == d }

    private suspend fun awaitEvents(n: Int) {
        withTimeout(10_000) { while (analytics.events.size < n) yield() }
    }

    @Test
    fun cycle_thenUndo_restoresPreviousNullMark() = runTest {
        val maid = add("Maid", BillingMode.MONTHLY_FIXED)
        val vm = vm()
        vm.cycleStatus(vm.row(maid))
        assertEquals(DayStatus.ABSENT, stored(maid)?.status)
        assertNull(vm.pendingUndo.value!!.previous)
        vm.undoLast()
        assertNull(stored(maid))
        assertNull(vm.pendingUndo.value)
    }

    @Test
    fun cycle_thenUndo_restoresPreviousStoredMark() = runTest {
        val maid = add("Maid", BillingMode.MONTHLY_FIXED)
        val half = DayMarkEntity(maid.id, day, DayStatus.HALF, qty = 2.0, note = "late", updatedAt = 5)
        db.marks.upsert(half)
        val vm = vm()
        vm.cycleStatus(vm.row(maid)) // Half -> Present
        vm.undoLast()
        assertEquals(half, stored(maid))
    }

    @Test
    fun reset_thenUndo_reinsertsExactMark() = runTest {
        val milk = add("Milk", BillingMode.PER_UNIT)
        val absent = DayMarkEntity(milk.id, day, DayStatus.ABSENT, qty = 3.0, note = "away", updatedAt = 9)
        db.marks.upsert(absent)
        val vm = vm()
        vm.reset(vm.row(milk))
        assertNull(stored(milk))
        assertNull(vm.pendingUndo.value!!.newStatus)
        vm.undoLast()
        assertEquals(absent, stored(milk))
    }

    @Test
    fun setQty_thenUndo_restoresPreviousQty() = runTest {
        val milk = add("Milk", BillingMode.PER_UNIT)
        val before = DayMarkEntity(milk.id, day, DayStatus.PRESENT, qty = 2.0, note = "old", updatedAt = 3)
        db.marks.upsert(before)
        val vm = vm()
        vm.setQty(vm.row(milk), 4.0, "new")
        assertEquals(4.0, stored(milk)?.qty)
        vm.undoLast()
        assertEquals(before, stored(milk))
    }

    @Test
    fun addExtra_thenUndo_clearsMark() = runTest {
        val maid = add("Maid", BillingMode.MONTHLY_FIXED, mask = Schedule.ALL_DAYS and Schedule.MON.inv())
        val vm = vm()
        vm.addExtra(maid)
        assertEquals(DayStatus.EXTRA, stored(maid)?.status)
        vm.undoLast()
        assertNull(stored(maid))
    }

    @Test
    fun twoChanges_undoRevertsOnlyLatest() = runTest {
        val a = add("A", BillingMode.MONTHLY_FIXED)
        val b = add("B", BillingMode.MONTHLY_FIXED)
        val vm = vm()
        vm.cycleStatus(vm.row(a))
        vm.cycleStatus(vm.row(b))
        assertEquals(b.id, vm.pendingUndo.value!!.providerId)
        vm.undoLast()
        assertEquals(DayStatus.ABSENT, stored(a)?.status)
        assertNull(stored(b))
        // Only one level: a second undo does nothing.
        vm.undoLast()
        assertEquals(DayStatus.ABSENT, stored(a)?.status)
    }

    @Test
    fun threeTapsOnSameChip_undoRestoresStateBeforeThirdTap() = runTest {
        val a = add("A", BillingMode.MONTHLY_FIXED)
        val vm = vm()
        vm.cycleStatus(vm.row(a)) // Absent
        vm.cycleStatus(vm.row(a)) // Half
        vm.cycleStatus(vm.row(a)) // Present (default)
        assertNull(stored(a))
        vm.undoLast()
        assertEquals(DayStatus.HALF, stored(a)?.status)
    }

    @Test
    fun selectDate_clearsPendingUndo() = runTest {
        val a = add("A", BillingMode.MONTHLY_FIXED)
        val vm = vm()
        vm.cycleStatus(vm.row(a))
        assertNotNull(vm.pendingUndo.value)
        vm.selectDate(otherDay)
        assertNull(vm.pendingUndo.value)
        vm.undoLast()
        assertEquals(DayStatus.ABSENT, stored(a)?.status)
        assertNull(stored(a, otherDay))
    }

    @Test
    fun undoLast_whenNothingPending_isNoOp() = runTest {
        val a = add("A", BillingMode.MONTHLY_FIXED)
        val vm = vm()
        vm.undoLast()
        assertNull(stored(a))
        assertEquals(emptyList<String>(), analytics.events.toList())
    }

    @Test
    fun clearUndo_dropsPending() = runTest {
        val a = add("A", BillingMode.MONTHLY_FIXED)
        val vm = vm()
        vm.cycleStatus(vm.row(a))
        vm.clearUndo()
        assertNull(vm.pendingUndo.value)
    }

    @Test
    fun write_usesInjectedClock() = runTest {
        val a = add("A", BillingMode.MONTHLY_FIXED)
        now = 777L
        val vm = vm()
        vm.cycleStatus(vm.row(a))
        assertEquals(777L, stored(a)?.updatedAt)
    }

    @Test
    fun undo_logsDayMarkUndone_withStatusParam_andNoExtraDayMarked() = runTest {
        val a = add("A", BillingMode.MONTHLY_FIXED)
        val b = add("B", BillingMode.MONTHLY_FIXED)
        db.marks.upsert(DayMarkEntity(b.id, day, DayStatus.HALF, updatedAt = 1))
        val vm = vm()

        vm.cycleStatus(vm.row(a)) // day_marked
        awaitEvents(1)
        vm.undoLast() // back to default
        awaitEvents(2)
        vm.cycleStatus(vm.row(b)) // Half -> Present, day_marked
        awaitEvents(3)
        vm.undoLast() // restores Half
        awaitEvents(4)

        val snapshot = analytics.params.toList()
        assertEquals(
            listOf(AnalyticsEvents.DAY_MARKED, AnalyticsEvents.DAY_MARK_UNDONE, AnalyticsEvents.DAY_MARKED, AnalyticsEvents.DAY_MARK_UNDONE),
            snapshot.map { it.first },
        )
        assertEquals("default", snapshot[1].second[AnalyticsParams.STATUS])
        assertEquals(DayStatus.HALF.name, snapshot[3].second[AnalyticsParams.STATUS])
        assertEquals(setOf(AnalyticsParams.STATUS), snapshot[1].second.keys)
    }
}
