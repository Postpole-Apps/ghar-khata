package com.postpole.gharkhata.widget

import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.data.RateChangeEntity
import com.postpole.gharkhata.ui.provider.TestDb
import kotlinx.coroutines.runBlocking
import com.postpole.gharkhata.ui.today.TodayRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TodayWidgetStateTest {
    // 2026-11-21 is a Saturday, 2026-11-22 a Sunday (bit0 = Mon … bit6 = Sun).
    private val saturday = LocalDate.of(2026, 11, 21).toEpochDay()
    private val sunday = saturday + 1
    private val monSat = 0b0111111

    private fun provider(
        id: Long,
        mode: BillingMode = BillingMode.PER_VISIT,
        mask: Int = monSat,
        qty: Double = 1.0,
        start: Long = saturday - 100,
        sort: Int = 0,
    ) = ProviderEntity(
        id = id, name = "P$id", type = ProviderType.MAID, billingMode = mode, unitLabel = "visit",
        defaultQty = qty, scheduleMask = mask, startEpochDay = start, sortOrder = sort, createdAt = 0,
    )

    private fun mark(p: ProviderEntity, day: Long, status: DayStatus, qty: Double? = null) =
        DayMarkEntity(p.id, day, status, qty, null, 0)

    @Test
    fun monSatProviderIsHiddenOnSundayUnlessMarked() {
        val maid = provider(1)
        assertEquals(1, TodayWidgetState.build(listOf(maid), emptyList(), saturday).rows.size)
        assertTrue(TodayWidgetState.build(listOf(maid), emptyList(), sunday).rows.isEmpty())
        val marked = TodayWidgetState.build(listOf(maid), listOf(mark(maid, sunday, DayStatus.EXTRA)), sunday)
        assertEquals(1, marked.rows.size)
        assertFalse(marked.rows[0].scheduled)
    }

    @Test
    fun onlyTodaysMarksAreUsed() {
        val maid = provider(1)
        val state = TodayWidgetState.build(listOf(maid), listOf(mark(maid, saturday - 1, DayStatus.ABSENT)), saturday)
        assertNull(state.rows.single().mark)
        assertEquals(0, state.changes)
    }

    @Test
    fun providerNotStartedYetIsHidden() {
        val later = provider(1, start = saturday + 5)
        assertTrue(TodayWidgetState.build(listOf(later), emptyList(), saturday).rows.isEmpty())
    }

    @Test
    fun defaultPresentRowsCountInTotalButNotInChanges() {
        val a = provider(1)
        val b = provider(2)
        val c = provider(3)
        val state = TodayWidgetState.build(listOf(a, b, c), listOf(mark(b, saturday, DayStatus.ABSENT)), saturday)
        assertEquals(3, state.rows.size)
        assertEquals(1, state.changes)
        assertEquals(DayStatus.PRESENT, state.rows[0].status)
        assertEquals(3, state.activeCount)
    }

    @Test
    fun rowsFollowSortOrderThenId() {
        val state = TodayWidgetState.build(
            listOf(provider(1, sort = 2), provider(2, sort = 1), provider(3, sort = 1)), emptyList(), saturday,
        )
        assertEquals(listOf(2L, 3L, 1L), state.rows.map { it.provider.id })
    }

    @Test
    fun noProvidersIsEmptyState() {
        assertTrue(TodayWidgetState.build(emptyList(), emptyList(), saturday).noProviders)
    }

    private fun rowOf(p: ProviderEntity, mark: DayMarkEntity?, day: Long = saturday): TodayRow =
        TodayWidgetState.build(listOf(p), listOfNotNull(mark), day).rows.single()

    private fun cycleStatus(p: ProviderEntity, row: TodayRow): DayStatus? =
        (TodayWidgetState.cycle(row, saturday, 5) as? ChipWrite.Set)?.mark?.status

    @Test
    fun perVisitCycleIsPresentAbsentHalfPresent() {
        val p = provider(1)
        val absent = TodayWidgetState.cycle(rowOf(p, null), saturday, 5)
        assertEquals(DayStatus.ABSENT, (absent as ChipWrite.Set).mark.status)
        assertEquals(DayStatus.HALF, cycleStatus(p, rowOf(p, mark(p, saturday, DayStatus.ABSENT))))
        // Half → Present on a scheduled day returns to default: clear, not a redundant PRESENT.
        assertEquals(
            ChipWrite.Clear(1, saturday),
            TodayWidgetState.cycle(rowOf(p, mark(p, saturday, DayStatus.HALF)), saturday, 5),
        )
    }

    @Test
    fun perUnitSkipsHalf() {
        val p = provider(1, mode = BillingMode.PER_UNIT, qty = 2.0)
        assertEquals(DayStatus.ABSENT, cycleStatus(p, rowOf(p, null)))
        assertEquals(
            ChipWrite.Clear(1, saturday),
            TodayWidgetState.cycle(rowOf(p, mark(p, saturday, DayStatus.ABSENT)), saturday, 5),
        )
    }

    @Test
    fun perUnitAbsentBackToPresentKeepsChangedQtyMark() {
        val p = provider(1, mode = BillingMode.PER_UNIT, qty = 2.0)
        // An absent mark that remembers a non-default qty returns to PRESENT with that qty, so it is kept.
        val write = TodayWidgetState.cycle(rowOf(p, mark(p, saturday, DayStatus.ABSENT, qty = 3.0)), saturday, 5)
        assertEquals(DayStatus.PRESENT, (write as ChipWrite.Set).mark.status)
        assertEquals(3.0, write.mark.qty)
    }

    @Test
    fun extraBehavesLikePresentInTheCycle() {
        val p = provider(1)
        val row = rowOf(p, mark(p, sunday, DayStatus.EXTRA), day = sunday)
        assertEquals(DayStatus.ABSENT, (TodayWidgetState.cycle(row, sunday, 5) as ChipWrite.Set).mark.status)
    }

    @Test
    fun unscheduledPresentIsStoredAsExtra() {
        val p = provider(1)
        // Unscheduled Sunday, marked Half: next is Present, which must be stored as EXTRA.
        val row = rowOf(p, mark(p, sunday, DayStatus.HALF), day = sunday)
        val write = TodayWidgetState.cycle(row, sunday, 5) as ChipWrite.Set
        assertEquals(DayStatus.EXTRA, write.mark.status)
        assertEquals(sunday, write.mark.epochDay)
    }

    @Test
    fun cycleKeepsStoredQtyAndNote() {
        val p = provider(1, mode = BillingMode.PER_UNIT, qty = 1.0)
        val stored = DayMarkEntity(1, saturday, DayStatus.PRESENT, 2.5, "extra", 0)
        val write = TodayWidgetState.cycle(rowOf(p, stored), saturday, 99) as ChipWrite.Set
        assertEquals(DayStatus.ABSENT, write.mark.status)
        assertEquals(2.5, write.mark.qty)
        assertEquals("extra", write.mark.note)
        assertEquals(99L, write.mark.updatedAt)
    }

    @Test
    fun visibleRowsShowsAllWhenTheyFit() {
        assertEquals(3, TodayWidgetState.visibleRows(3, 150f))
        assertEquals(0, TodayWidgetState.visibleRows(0, 10f))
    }

    @Test
    fun visibleRowsReservesASlotForMoreInApp() {
        // 150dp fits 3 rows; 5 rows do not fit, so 2 rows + the "more in the app" line.
        assertEquals(2, TodayWidgetState.visibleRows(5, 150f))
        assertEquals(0, TodayWidgetState.visibleRows(5, 40f))
    }

    // --- Previous-month bills-ready cue ---

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    private fun monthlyProvider(db: TestDb, name: String): Long = runBlocking {
        val start = day(2026, 10, 1)
        val id = db.repository.saveProvider(
            ProviderEntity(
                name = name, type = ProviderType.MAID, billingMode = BillingMode.MONTHLY_FIXED, unitLabel = "day",
                scheduleMask = 0b1111111, startEpochDay = start, createdAt = 0,
            ),
        )
        db.repository.addRate(RateChangeEntity(providerId = id, effectiveFromEpochDay = start, amountPaise = 300_000))
        id
    }

    private fun settle(db: TestDb, id: Long, ym: Int, paid: Long) = runBlocking {
        db.repository.saveSettlement(MonthSettlementEntity(id, ym, 300_000, paid, paidOnEpochDay = 0))
    }

    private fun cue(db: TestDb, today: Long) = runBlocking { TodayWidgetState.loadPrevBills(db.repository, today) }

    @Test
    fun cueIsOnlyLoadedOnDaysOneToFive() {
        val db = TestDb()
        monthlyProvider(db, "Sunita")
        assertEquals(202611, cue(db, day(2026, 12, 1))!!.previousYearMonth)
        assertEquals(202611, cue(db, day(2026, 12, 5))!!.previousYearMonth)
        assertNull(cue(db, day(2026, 12, 6)))
        assertNull(cue(db, day(2026, 11, 30)))
    }

    @Test
    fun oneOrManyDueProvidersAreListedWithoutAmounts() {
        val db = TestDb()
        val a = monthlyProvider(db, "Sunita")
        assertEquals(listOf("Sunita"), cue(db, day(2026, 12, 3))!!.providers.map { it.name })
        monthlyProvider(db, "Raju")
        assertEquals(2, cue(db, day(2026, 12, 3))!!.providers.size)
        settle(db, a, 202610, 300_000)
        settle(db, a, 202611, 300_000)
        assertEquals(listOf("Raju"), cue(db, day(2026, 12, 3))!!.providers.map { it.name })
    }

    @Test
    fun creditAndSettledProvidersGiveNoCue() {
        val db = TestDb()
        val a = monthlyProvider(db, "Sunita")
        settle(db, a, 202610, 300_000)
        settle(db, a, 202611, 500_000)
        assertNull(cue(db, day(2026, 12, 3)))
    }

    @Test
    fun withPrevBillsKeepsRowsCountsAndCycle() {
        val maid = provider(1)
        val base = TodayWidgetState.build(listOf(maid), listOf(mark(maid, saturday, DayStatus.ABSENT)), saturday)
        val db = TestDb()
        monthlyProvider(db, "Sunita")
        val withCue = TodayWidgetState.withPrevBills(base, cue(db, day(2026, 12, 3)))
        assertTrue(withCue.prevBills != null)
        assertEquals(base.rows, withCue.rows)
        assertEquals(base.changes, withCue.changes)
        assertEquals(base.activeCount, withCue.activeCount)
        assertNull(TodayWidgetState.withPrevBills(withCue, null).prevBills)
        assertEquals(
            TodayWidgetState.cycle(base.rows.single(), saturday, 5),
            TodayWidgetState.cycle(withCue.rows.single(), saturday, 5),
        )
    }

    @Test
    fun cueName_shortNameUnchanged() {
        assertEquals("Sunita", TodayWidgetState.cueName("Sunita"))
    }

    @Test
    fun cueName_exactlyAtLimitUnchanged() {
        val name = "A".repeat(12)
        assertEquals(12, name.length)
        assertEquals(name, TodayWidgetState.cueName(name))
    }

    @Test
    fun cueName_longNameIsCappedWithEllipsis() {
        val name = "Mahalakshmi Dairy"
        val capped = TodayWidgetState.cueName(name)
        assertEquals(name.take(11) + "…", capped)
        assertEquals(12, capped.length)
    }

    @Test
    fun cueFirst_belowThresholdWithCueIsFalse() {
        assertFalse(TodayWidgetState.cueFirst(1.0f, true))
        assertFalse(TodayWidgetState.cueFirst(1.29f, true))
    }

    @Test
    fun cueFirst_atOrAboveThresholdWithCueIsTrue() {
        assertTrue(TodayWidgetState.cueFirst(1.3f, true))
        assertTrue(TodayWidgetState.cueFirst(2.0f, true))
    }

    @Test
    fun cueFirst_noCueIsFalseEvenAtLargeScale() {
        assertFalse(TodayWidgetState.cueFirst(2.0f, false))
    }
}
