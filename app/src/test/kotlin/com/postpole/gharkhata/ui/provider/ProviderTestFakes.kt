package com.postpole.gharkhata.ui.provider

import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.data.DayMarkDao
import com.postpole.gharkhata.data.DayMarkEntity
import com.postpole.gharkhata.data.MonthSettlementDao
import com.postpole.gharkhata.data.MonthSettlementEntity
import com.postpole.gharkhata.data.ProviderDao
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderRepository
import com.postpole.gharkhata.data.ProviderWithRates
import com.postpole.gharkhata.data.RateChangeDao
import com.postpole.gharkhata.data.RateChangeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory DAOs so ViewModel tests run on the JVM without Room. */
class FakeProviderDao(private val rates: FakeRateDao) : ProviderDao {
    val all = MutableStateFlow<List<ProviderEntity>>(emptyList())
    private var nextId = 1L

    override fun observeActive(): Flow<List<ProviderEntity>> = all.map { l -> l.filter { !it.archived } }
    override fun observeAll(): Flow<List<ProviderEntity>> = all
    override suspend fun getById(id: Long): ProviderEntity? = all.value.firstOrNull { it.id == id }
    override fun countActive(): Flow<Int> = all.map { l -> l.count { !it.archived } }
    override fun observeActiveWithRates(): Flow<List<ProviderWithRates>> =
        kotlinx.coroutines.flow.combine(observeActive(), rates.all) { providers, r ->
            providers.map { p -> ProviderWithRates(p, r.filter { it.providerId == p.id }) }
        }

    override suspend fun upsert(provider: ProviderEntity): Long {
        val id = if (provider.id == 0L) nextId++ else provider.id
        val saved = provider.copy(id = id)
        all.value = all.value.filter { it.id != id } + saved
        return id
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        all.value = all.value.map { if (it.id == id) it.copy(archived = archived) else it }
    }

    override suspend fun delete(provider: ProviderEntity) {
        all.value = all.value.filter { it.id != provider.id }
        rates.all.value = rates.all.value.filter { it.providerId != provider.id }
    }

    override suspend fun getAll(): List<ProviderEntity> = all.value
    override suspend fun insertAll(providers: List<ProviderEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

class FakeRateDao : RateChangeDao {
    val all = MutableStateFlow<List<RateChangeEntity>>(emptyList())
    override fun observeFor(providerId: Long): Flow<List<RateChangeEntity>> =
        all.map { l -> l.filter { it.providerId == providerId }.sortedByDescending { it.effectiveFromEpochDay } }

    override suspend fun insert(rate: RateChangeEntity): Long {
        all.value = all.value.filterNot { it.providerId == rate.providerId && it.effectiveFromEpochDay == rate.effectiveFromEpochDay } + rate
        return 1
    }

    override suspend fun rateOn(providerId: Long, epochDay: Long): Long? =
        all.value.filter { it.providerId == providerId && it.effectiveFromEpochDay <= epochDay }
            .maxByOrNull { it.effectiveFromEpochDay }?.amountPaise

    override suspend fun getAll(): List<RateChangeEntity> = all.value
    override suspend fun insertAll(rates: List<RateChangeEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

class FakeMarkDao : DayMarkDao {
    override fun observeForDay(epochDay: Long): Flow<List<DayMarkEntity>> = MutableStateFlow(emptyList())
    override fun observeRange(providerId: Long, fromDay: Long, toDay: Long): Flow<List<DayMarkEntity>> =
        MutableStateFlow(emptyList())

    override suspend fun upsert(mark: DayMarkEntity) = Unit
    override suspend fun clear(providerId: Long, epochDay: Long) = Unit
    override suspend fun getAll(): List<DayMarkEntity> = emptyList()
    override suspend fun insertAll(marks: List<DayMarkEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

class FakeSettlementDao : MonthSettlementDao {
    override fun observeFor(providerId: Long): Flow<List<MonthSettlementEntity>> = MutableStateFlow(emptyList())
    override suspend fun get(providerId: Long, yyyymm: Int): MonthSettlementEntity? = null
    override suspend fun upsert(settlement: MonthSettlementEntity) = Unit
    override suspend fun delete(providerId: Long, yyyymm: Int) = Unit
    override suspend fun getAll(): List<MonthSettlementEntity> = emptyList()
    override suspend fun insertAll(settlements: List<MonthSettlementEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

/** Fake Pro entitlement (no Play Billing). */
class FakeProStatus(pro: Boolean = false) : ProStatus {
    override val isPro = MutableStateFlow(pro)
}

class RecordingAnalytics : Analytics {
    val events = mutableListOf<String>()
    override fun logEvent(name: String, params: Map<String, Any>) { events += name }
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun recordNonFatal(throwable: Throwable) = Unit
}

class TestDb {
    val rates = FakeRateDao()
    val providers = FakeProviderDao(rates)
    val repository = ProviderRepository(providers, rates, FakeMarkDao(), FakeSettlementDao())
}
