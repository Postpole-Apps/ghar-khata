package com.postpole.gharkhata.widget

import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.DayStatus
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.ui.provider.TestDb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TodayWidgetUpdaterTest {
    private val today = LocalDate.now().toEpochDay()

    @Test
    fun emitsAfterAMarkAndAfterASettlement() = runTest(UnconfinedTestDispatcher()) {
        val db = TestDb()
        val id = db.repository.saveProvider(
            ProviderEntity(
                name = "Sunita", type = ProviderType.MAID, billingMode = BillingMode.PER_VISIT, unitLabel = "visit",
                scheduleMask = 0b1111111, startEpochDay = today - 30, createdAt = 0,
            ),
        )
        var emissions = 0
        val job = TodayWidgetUpdater.changes(db.repository).onEach { emissions++ }.launchIn(this)

        assertEquals(0, emissions)
        db.repository.setMark(DayMarkEntity(id, today, DayStatus.ABSENT, null, null, 0))
        assertEquals(1, emissions)
        db.repository.saveSettlement(MonthSettlementEntity(id, 202611, 100, 100, paidOnEpochDay = today))
        assertEquals(2, emissions)
        job.cancel()
    }
}
