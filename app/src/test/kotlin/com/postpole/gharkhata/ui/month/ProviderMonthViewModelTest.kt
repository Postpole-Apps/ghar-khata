package com.postpole.gharkhata.ui.month

import androidx.lifecycle.SavedStateHandle
import com.postpole.gharkhata.analytics.AnalyticsEvents
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.data.RateChangeEntity
import com.postpole.gharkhata.domain.Schedule
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
import java.time.LocalDate

/**
 * Milk, 1 L daily at ₹50, billed from 1 Oct 2026. "Today" is pinned to 5 Dec 2026.
 * October has 31 days → ₹1,550 (155000 paise); November has 30 → ₹1,500.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderMonthViewModelTest {
    private val db = TestDb()
    private val analytics = RecordingAnalytics()
    private val today = LocalDate.of(2026, 12, 5).toEpochDay()
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun milk(mode: BillingMode = BillingMode.PER_UNIT, mask: Int = Schedule.ALL_DAYS): ProviderEntity {
        val id = db.providers.upsert(
            ProviderEntity(
                name = "Raju", type = ProviderType.MILK, billingMode = mode, unitLabel = "L", defaultQty = 1.0,
                scheduleMask = mask, startEpochDay = day(10, 1), createdAt = 0,
            ),
        )
        db.rates.insert(RateChangeEntity(providerId = id, effectiveFromEpochDay = day(10, 1), amountPaise = 5000))
        return db.providers.getById(id)!!
    }

    private fun TestScope.vm(p: ProviderEntity, ym: Int): ProviderMonthViewModel {
        val vm = ProviderMonthViewModel(
            SavedStateHandle(mapOf("id" to p.id, "yyyymm" to ym)), db.repository, analytics, { today },
        )
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.state.collect { } }
        return vm
    }

    @Test fun summaryMatchesCalculator() = runTest {
        val p = milk()
        val s = vm(p, 202610).state.value
        assertEquals(155_000L, s.bill!!.totalPaise)
        assertEquals(31, s.deliveredDays)
        assertEquals(31.0, s.totalQty, 0.0)
        assertEquals(31, s.cells.size)
        assertTrue(s.cells.all { it.kind == DayCellKind.PRESENT })
    }

    @Test fun shortPayment_carriesIntoNextMonth() = runTest {
        val p = milk()
        val oct = vm(p, 202610)
        assertTrue(oct.markPaid(paidPaise = 100_000, paidOnEpochDay = day(11, 2), note = "cash"))
        val saved = db.settlements.all.value.single()
        assertEquals(202610, saved.yearMonth)
        assertEquals(155_000L, saved.billedPaise)
        assertEquals(100_000L, saved.paidPaise)
        assertEquals("cash", saved.note)
        assertEquals(day(11, 2), saved.paidOnEpochDay)
        assertEquals(100_000L, oct.state.value.settlement!!.paidPaise)
        assertTrue(analytics.events.contains(AnalyticsEvents.SETTLEMENT_SAVED))

        val nov = vm(p, 202611).state.value
        assertEquals(55_000L, nov.bill!!.previousBalancePaise)
        assertEquals(150_000L, nov.bill!!.totalPaise)
        assertEquals(205_000L, nov.bill!!.duePaise)
    }

    @Test fun fullPayment_leavesNothingForNextMonth() = runTest {
        val p = milk()
        vm(p, 202610).markPaid(155_000, day(11, 2), null)
        val nov = vm(p, 202611).state.value
        assertEquals(0L, nov.bill!!.previousBalancePaise)
        assertEquals(150_000L, nov.bill!!.duePaise)
    }

    @Test fun overpayment_becomesCredit() = runTest {
        val p = milk()
        vm(p, 202610).markPaid(160_000, day(11, 2), null)
        val nov = vm(p, 202611).state.value
        assertEquals(-5_000L, nov.bill!!.previousBalancePaise)
        assertEquals(145_000L, nov.bill!!.duePaise)
    }

    @Test fun unsettledPreviousMonth_isCarried() = runTest {
        val p = milk()
        assertEquals(155_000L, vm(p, 202611).state.value.bill!!.previousBalancePaise)
    }

    @Test fun settlingNovemberCountsPreviousBalanceInItsSnapshot() = runTest {
        val p = milk()
        vm(p, 202610).markPaid(100_000, day(11, 2), null) // 55000 short
        val nov = vm(p, 202611)
        nov.markPaid(205_000, day(12, 1), null) // pays everything due
        assertEquals(205_000L, db.settlements.all.value.first { it.yearMonth == 202611 }.billedPaise)
        assertEquals(0L, vm(p, 202612).state.value.bill!!.previousBalancePaise)
    }

    @Test fun clearPayment_deletesRow_andRestoresDueAndNextMonthBalance() = runTest {
        val p = milk()
        val oct = vm(p, 202610)
        oct.markPaid(100_000, day(11, 2), null)
        val nov = vm(p, 202611)
        assertEquals(55_000L, nov.state.value.bill!!.previousBalancePaise)
        oct.clearPayment()
        assertTrue(db.settlements.all.value.isEmpty())
        assertNull(oct.state.value.settlement)
        assertEquals(155_000L, oct.state.value.bill!!.duePaise)
        assertEquals(155_000L, nov.state.value.bill!!.previousBalancePaise)
    }

    @Test fun clearPayment_onlyRemovesVisibleMonth() = runTest {
        val p = milk()
        val oct = vm(p, 202610)
        oct.markPaid(155_000, day(11, 2), null)
        val nov = vm(p, 202611)
        nov.markPaid(150_000, day(12, 1), null)
        nov.clearPayment()
        assertEquals(listOf(202610), db.settlements.all.value.map { it.yearMonth })
    }

    @Test fun clearPayment_withoutSettlement_isNoOp() = runTest {
        val p = milk()
        val oct = vm(p, 202610)
        oct.clearPayment()
        assertTrue(db.settlements.all.value.isEmpty())
        assertEquals(155_000L, oct.state.value.bill!!.duePaise)
    }

    @Test fun clearPayment_doesNotOfferProOrLogSettlement() = runTest {
        val p = milk()
        var reserved = 0
        val prompts = object : com.postpole.gharkhata.pro.ProPrompts by com.postpole.gharkhata.pro.NoProPrompts {
            override suspend fun reserve(
                trigger: com.postpole.gharkhata.pro.ProPromptTrigger,
                screen: com.postpole.gharkhata.pro.ProPromptScreen,
                freeLimitCardShowing: Boolean,
            ): Boolean { reserved++; return true }
        }
        val ym: Int = 202610
        val oct = ProviderMonthViewModel(
            SavedStateHandle(mapOf("id" to p.id, "yyyymm" to ym)), db.repository, analytics, { today }, prompts,
        )
        backgroundScope.launch(UnconfinedTestDispatcher()) { oct.state.collect { } }
        oct.markPaid(100_000, day(11, 2), null)
        val before = analytics.events.size
        reserved = 0
        oct.clearPayment()
        assertEquals(0, reserved)
        assertFalse(oct.autoProSheet.value)
        assertEquals(before, analytics.events.size)
    }

    @Test fun negativeAmountIsRejected() = runTest {
        val p = milk()
        assertFalse(vm(p, 202610).markPaid(-1, day(11, 2), null))
        assertTrue(db.settlements.all.value.isEmpty())
    }

    @Test fun dayMarkChange_recomputesSummary() = runTest {
        val p = milk()
        val v = vm(p, 202611)
        val before = v.state.value.bill!!.totalPaise
        v.saveDay(day(11, 3), DayStatus.ABSENT, null, null)
        val s = v.state.value
        assertEquals(before - 5_000, s.bill!!.totalPaise)
        assertEquals(DayCellKind.ABSENT, s.cells.first { it.epochDay == day(11, 3) }.kind)
        assertEquals(1, s.absentDays)
        assertEquals(29, s.deliveredDays)
        assertTrue(analytics.events.contains(AnalyticsEvents.DAY_MARKED))

        v.saveDay(day(11, 4), DayStatus.PRESENT, 2.0, "guests")
        assertEquals(before - 5_000 + 5_000, v.state.value.bill!!.totalPaise)
        assertEquals(DayCellKind.CHANGED, v.state.value.cells.first { it.epochDay == day(11, 4) }.kind)

        v.resetDay(day(11, 3))
        v.saveDay(day(11, 4), DayStatus.PRESENT, 1.0, null) // back to default → mark removed
        assertEquals(before, v.state.value.bill!!.totalPaise)
        assertTrue(db.marks.all.value.isEmpty())
    }

    @Test fun unscheduledDay_presentIsExtra() = runTest {
        val p = milk(mask = Schedule.WEEKDAYS)
        val v = vm(p, 202611)
        val sunday = day(11, 1)
        assertEquals(DayCellKind.UNSCHEDULED, v.state.value.cells.first { it.epochDay == sunday }.kind)
        v.saveDay(sunday, DayStatus.PRESENT, 1.5, null)
        val cell = v.state.value.cells.first { it.epochDay == sunday }
        assertEquals(DayCellKind.EXTRA, cell.kind)
        assertEquals(1.5, cell.qty!!, 0.0)
    }

    @Test fun futureAndBeforeStartCells_areNotEditable() = runTest {
        val p = milk()
        val dec = vm(p, 202612)
        val cells = dec.state.value.cells
        assertEquals(DayCellKind.PRESENT, cells.first { it.epochDay == day(12, 5) }.kind)
        val future = cells.first { it.epochDay == day(12, 6) }
        assertEquals(DayCellKind.FUTURE, future.kind)
        assertFalse(future.editable)
        dec.saveDay(day(12, 6), DayStatus.ABSENT, null, null) // ignored
        assertTrue(db.marks.all.value.isEmpty())

        val sep = ProviderMonthViewModel.cellFor(p, day(9, 30), null, today)
        assertEquals(DayCellKind.BEFORE_START, sep.kind)
        assertFalse(sep.editable)
    }

    @Test fun monthNavigation_staysBetweenStartAndCurrent() = runTest {
        val p = milk()
        val v = vm(p, 202612)
        assertFalse(v.state.value.canGoNext)
        v.nextMonth()
        assertEquals(202612, v.state.value.yearMonth)
        v.previousMonth()
        assertEquals(202611, v.state.value.yearMonth)
        v.previousMonth()
        assertEquals(202610, v.state.value.yearMonth)
        assertFalse(v.state.value.canGoPrevious)
        v.previousMonth()
        assertEquals(202610, v.state.value.yearMonth)
        v.nextMonth()
        assertEquals(202611, v.state.value.yearMonth)
    }

    @Test fun missingProvider_isLoadedWithoutProvider() = runTest {
        val v = ProviderMonthViewModel(SavedStateHandle(mapOf<String, Any>("id" to 99L, "yyyymm" to 202611)), db.repository, analytics, { today })
        backgroundScope.launch(UnconfinedTestDispatcher()) { v.state.collect { } }
        assertNull(v.state.value.provider)
        assertTrue(v.state.value.loaded)
    }

    @Test fun perVisitSummary_exposesAbsencesAndLeaves() = runTest {
        val p = milk(BillingMode.PER_VISIT, Schedule.ALL_DAYS).let {
            db.providers.upsert(it.copy(paidLeavesPerMonth = 1)); db.providers.getById(it.id)!!
        }
        val v = vm(p, 202611)
        v.saveDay(day(11, 3), DayStatus.ABSENT, null, null)
        v.saveDay(day(11, 4), DayStatus.ABSENT, null, null)
        val s = v.state.value
        assertEquals(2.0, s.absences, 0.0)
        assertEquals(1.0, s.paidLeavesUsed, 0.0)
    }

    @Test fun onCallTapped_logsProviderCallTapped_withProviderTypeOnly() = runTest {
        val p = milk()
        db.providers.upsert(p.copy(phone = "+91 98765-43210"))
        val v = vm(db.providers.getById(p.id)!!, 202611)
        v.onCallTapped()
        val deadline = System.currentTimeMillis() + 2_000
        while (AnalyticsEvents.PROVIDER_CALL_TAPPED !in analytics.events && System.currentTimeMillis() < deadline) Thread.sleep(10)
        val logged = analytics.params.filter { it.first == AnalyticsEvents.PROVIDER_CALL_TAPPED }
        assertEquals(1, logged.size)
        assertEquals(mapOf<String, Any>("provider_type" to ProviderType.MILK.name), logged.single().second)
    }
}
