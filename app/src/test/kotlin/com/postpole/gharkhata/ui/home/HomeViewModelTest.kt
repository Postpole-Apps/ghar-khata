package com.postpole.gharkhata.ui.home

import com.postpole.gharkhata.data.BillingMode
import com.postpole.gharkhata.data.DayMarkDao
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.MonthSettlementDao
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderDao
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderRepository
import com.postpole.gharkhata.data.ProviderType
import com.postpole.gharkhata.data.ProviderWithRates
import com.postpole.gharkhata.data.RateChangeDao
import com.postpole.gharkhata.data.RateChangeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private class FakeProviderDao : ProviderDao {
        val active = MutableStateFlow<List<ProviderEntity>>(emptyList())
        override fun observeActive(): Flow<List<ProviderEntity>> = active
        override fun observeAll(): Flow<List<ProviderEntity>> = active
        override suspend fun getById(id: Long): ProviderEntity? = active.value.firstOrNull { it.id == id }
        override fun countActive(): Flow<Int> = emptyFlow()
        override fun observeActiveWithRates(): Flow<List<ProviderWithRates>> = emptyFlow()
        override suspend fun upsert(provider: ProviderEntity): Long = provider.id
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
        override suspend fun delete(provider: ProviderEntity) = Unit
        override suspend fun getAll(): List<ProviderEntity> = active.value
        override suspend fun insertAll(providers: List<ProviderEntity>) = Unit
        override suspend fun deleteAll() = Unit
    }

    private class NoRates : RateChangeDao {
        override fun observeFor(providerId: Long): Flow<List<RateChangeEntity>> = emptyFlow()
        override suspend fun insert(rate: RateChangeEntity): Long = 0
        override suspend fun rateOn(providerId: Long, epochDay: Long): Long? = null
        override suspend fun getAll(): List<RateChangeEntity> = emptyList()
        override suspend fun insertAll(rates: List<RateChangeEntity>) = Unit
        override suspend fun deleteAll() = Unit
    }

    private class NoMarks : DayMarkDao {
        override fun observeForDay(epochDay: Long): Flow<List<DayMarkEntity>> = emptyFlow()
        override fun observeRange(providerId: Long, fromDay: Long, toDay: Long): Flow<List<DayMarkEntity>> = emptyFlow()
        override suspend fun upsert(mark: DayMarkEntity) = Unit
        override suspend fun clear(providerId: Long, epochDay: Long) = Unit
        override suspend fun getAll(): List<DayMarkEntity> = emptyList()
        override suspend fun insertAll(marks: List<DayMarkEntity>) = Unit
        override suspend fun deleteAll() = Unit
    }

    private class NoSettlements : MonthSettlementDao {
        override fun observeFor(providerId: Long): Flow<List<MonthSettlementEntity>> = emptyFlow()
        override suspend fun get(providerId: Long, yyyymm: Int): MonthSettlementEntity? = null
        override suspend fun upsert(settlement: MonthSettlementEntity) = Unit
        override suspend fun delete(providerId: Long, yyyymm: Int) = Unit
        override suspend fun getAll(): List<MonthSettlementEntity> = emptyList()
        override suspend fun insertAll(settlements: List<MonthSettlementEntity>) = Unit
        override suspend fun deleteAll() = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun providers_reflectsActiveProviders() = runTest {
        val dao = FakeProviderDao()
        val vm = HomeViewModel(ProviderRepository(dao, NoRates(), NoMarks(), NoSettlements()))
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.providers.collect { } }
        assertEquals(emptyList<ProviderEntity>(), vm.providers.value)

        val p = ProviderEntity(
            id = 1, name = "Milk", type = ProviderType.MILK, billingMode = BillingMode.PER_UNIT,
            unitLabel = "L", startEpochDay = 1, createdAt = 1,
        )
        dao.active.value = listOf(p)
        assertEquals(listOf(p), vm.providers.value)
    }
}
