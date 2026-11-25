package com.postpole.gharkhata.ui.provider

import androidx.lifecycle.SavedStateHandle
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderType
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

    private suspend fun add(
        name: String,
        pro: FakeProStatus,
        start: LocalDate = LocalDate.now().withDayOfMonth(1),
    ) {
        val v = ProviderEditViewModel(SavedStateHandle(mapOf("id" to NEW_PROVIDER_ID)), db.repository, pro, RecordingAnalytics())
        v.selectType(ProviderType.MILK)
        v.update { it.copy(name = name, rate = "60", startEpochDay = start.toEpochDay()) }
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

    private fun ym(d: LocalDate) = d.year * 100 + d.monthValue

    @Test
    fun settledCurrentMonth_showsSettled() = runTest {
        val pro = FakeProStatus(true)
        add("A", pro)
        val vm = ProviderListViewModel(db.repository, pro)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.state.collect { } }
        val row = vm.state.value.active.single()
        assertFalse(row.isCurrentMonthSettled)

        val today = LocalDate.now()
        db.repository.saveSettlement(
            MonthSettlementEntity(row.provider.id, ym(today), row.monthTotalPaise, row.monthTotalPaise, today.toEpochDay()),
        )
        val settled = vm.state.value.active.single()
        assertTrue(settled.isCurrentMonthSettled)
        assertEquals(0L, settled.netDuePaise)
        assertEquals(0L, settled.previousBalancePaise)
    }

    @Test
    fun unsettledPreviousMonth_showsPrevDue() = runTest {
        val pro = FakeProStatus(true)
        add("A", pro, start = LocalDate.now().withDayOfMonth(1).minusMonths(1))
        val vm = ProviderListViewModel(db.repository, pro)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.state.collect { } }
        val row = vm.state.value.active.single()
        assertTrue(row.previousBalancePaise > 0L)
        assertFalse(row.isCurrentMonthSettled)
        assertEquals(row.monthTotalPaise + row.previousBalancePaise, row.netDuePaise)
    }

    @Test
    fun overpaidPreviousMonth_showsCredit() = runTest {
        val pro = FakeProStatus(true)
        val prev = LocalDate.now().withDayOfMonth(1).minusMonths(1)
        add("A", pro, start = prev)
        val id = db.providers.all.value.first().id
        db.repository.saveSettlement(MonthSettlementEntity(id, ym(prev), 10_000L, 25_000L, prev.toEpochDay()))
        val vm = ProviderListViewModel(db.repository, pro)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.state.collect { } }
        val row = vm.state.value.active.single()
        assertEquals(-15_000L, row.previousBalancePaise)
        assertFalse(row.isCurrentMonthSettled)
    }
}
