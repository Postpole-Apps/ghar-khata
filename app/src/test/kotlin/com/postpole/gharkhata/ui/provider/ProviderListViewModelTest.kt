package com.postpole.gharkhata.ui.provider

import androidx.lifecycle.SavedStateHandle
import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.data.RateChangeEntity
import com.postpole.gharkhata.domain.Schedule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderListViewModelTest {

    private val db = TestDb()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun add(name: String, pro: FakeProStatus) {
        val v = ProviderEditViewModel(SavedStateHandle(mapOf("id" to NEW_PROVIDER_ID)), db.repository, pro, RecordingAnalytics())
        v.selectType(ProviderType.MILK)
        v.update { it.copy(name = name, rate = "60", startEpochDay = LocalDate.now().withDayOfMonth(1).toEpochDay()) }
        v.save()
    }

    @Test
    fun activeListedWithMonthTotal_archivedSeparate_andAddGate() = runTest {
        val pro = FakeProStatus(false)
        add("A", pro)
        add("B", pro)
        add("C", pro)
        val vm = ProviderListViewModel(db.repository, pro)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.state.collect { } }

        var s = vm.state.value
        assertEquals(listOf("A", "B", "C"), s.active.map { it.provider.name }.sorted())
        // Daily milk at ₹60 from the 1st through today: at least one day billed.
        assertTrue(s.active.all { it.monthTotalPaise >= 6000L })
        assertEquals(STRIP_DAYS, s.active.first().strip.size)
        assertFalse(s.canAdd)

        db.providers.setArchived(s.active.first().provider.id, true)
        s = vm.state.value
        assertEquals(2, s.active.size)
        assertEquals(1, s.archived.size)
        assertTrue(s.canAdd)

        pro.isPro.value = true
        assertTrue(vm.state.value.isPro)
    }

    private suspend fun addFixed(startsOn: LocalDate): Long {
        val id = db.providers.upsert(
            ProviderEntity(
                name = "Maid", type = ProviderType.MAID, billingMode = BillingMode.MONTHLY_FIXED, unitLabel = "day",
                scheduleMask = Schedule.ALL_DAYS, startEpochDay = startsOn.toEpochDay(), createdAt = 0,
            ),
        )
        db.rates.insert(RateChangeEntity(providerId = id, effectiveFromEpochDay = startsOn.toEpochDay(), amountPaise = 300_000L))
        return id
    }

    private fun TestScope.listVm(): ProviderListViewModel {
        val vm = ProviderListViewModel(db.repository, FakeProStatus(true))
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.state.collect { } }
        return vm
    }

    @Test
    fun currentMonthSettlement_marksRowSettled() = runTest {
        val id = addFixed(LocalDate.now().withDayOfMonth(1))
        val vm = listVm()
        val before = vm.state.value.active.single()
        assertFalse(before.isCurrentMonthSettled)
        assertEquals(before.monthTotalPaise, before.netDuePaise)

        val ym = LocalDate.now().let { it.year * 100 + it.monthValue }
        db.settlements.upsert(
            MonthSettlementEntity(id, ym, billedPaise = before.monthTotalPaise, paidPaise = before.monthTotalPaise, paidOnEpochDay = LocalDate.now().toEpochDay()),
        )
        val after = vm.state.value.active.single()
        assertTrue(after.isCurrentMonthSettled)
        assertEquals(0L, after.netDuePaise)
        assertEquals(0L, after.previousBalancePaise)
    }

    @Test
    fun unpaidPreviousMonth_showsPreviousDue() = runTest {
        val prev = LocalDate.now().withDayOfMonth(1).minusMonths(1)
        val id = addFixed(prev)
        db.settlements.upsert(
            MonthSettlementEntity(id, prev.year * 100 + prev.monthValue, billedPaise = 300_000, paidPaise = 100_000, paidOnEpochDay = prev.toEpochDay()),
        )
        val row = listVm().state.value.active.single()
        assertEquals(200_000L, row.previousBalancePaise)
        assertFalse(row.isCurrentMonthSettled)
        assertEquals(row.monthTotalPaise + 200_000L, row.netDuePaise)
    }

    @Test
    fun overpaidPreviousMonth_showsCredit() = runTest {
        val prev = LocalDate.now().withDayOfMonth(1).minusMonths(1)
        val id = addFixed(prev)
        db.settlements.upsert(
            MonthSettlementEntity(id, prev.year * 100 + prev.monthValue, billedPaise = 300_000, paidPaise = 400_000, paidOnEpochDay = prev.toEpochDay()),
        )
        val row = listVm().state.value.active.single()
        assertEquals(-100_000L, row.previousBalancePaise)
        assertFalse(row.isCurrentMonthSettled)
    }
}
