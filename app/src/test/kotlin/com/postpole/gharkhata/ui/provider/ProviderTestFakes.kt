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

    private val byOrder = compareBy<ProviderEntity>({ it.sortOrder }, { it.name })

    override fun observeActive(): Flow<List<ProviderEntity>> = all.map { l -> l.filter { !it.archived }.sortedWith(byOrder) }
    override fun observeAll(): Flow<List<ProviderEntity>> = all.map { it.sortedWith(byOrder) }
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

    override suspend fun setSortOrder(id: Long, order: Int) {
        all.value = all.value.map { if (it.id == id) it.copy(sortOrder = order) else it }
    }

    override suspend fun maxSortOrder(): Int = all.value.maxOfOrNull { it.sortOrder } ?: -1

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
    val all = MutableStateFlow<List<DayMarkEntity>>(emptyList())

    override fun observeForDay(epochDay: Long): Flow<List<DayMarkEntity>> =
        all.map { l -> l.filter { it.epochDay == epochDay } }

    override fun observeRange(providerId: Long, fromDay: Long, toDay: Long): Flow<List<DayMarkEntity>> =
        all.map { l -> l.filter { it.providerId == providerId && it.epochDay in fromDay..toDay } }

    override suspend fun upsert(mark: DayMarkEntity) {
        all.value = all.value.filterNot { it.providerId == mark.providerId && it.epochDay == mark.epochDay } + mark
    }

    override suspend fun clear(providerId: Long, epochDay: Long) {
        all.value = all.value.filterNot { it.providerId == providerId && it.epochDay == epochDay }
    }


    override suspend fun getAll(): List<DayMarkEntity> = all.value
    override suspend fun insertAll(marks: List<DayMarkEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

class FakeSettlementDao : MonthSettlementDao {
    val all = MutableStateFlow<List<MonthSettlementEntity>>(emptyList())

    override fun observeFor(providerId: Long): Flow<List<MonthSettlementEntity>> =
        all.map { l -> l.filter { it.providerId == providerId }.sortedByDescending { it.yearMonth } }

    override fun observeAll(): Flow<List<MonthSettlementEntity>> = all

    override suspend fun get(providerId: Long, yyyymm: Int): MonthSettlementEntity? =
        all.value.firstOrNull { it.providerId == providerId && it.yearMonth == yyyymm }

    override suspend fun upsert(settlement: MonthSettlementEntity) {
        all.value = all.value.filterNot { it.providerId == settlement.providerId && it.yearMonth == settlement.yearMonth } + settlement
    }

    override suspend fun delete(providerId: Long, yyyymm: Int) {
        all.value = all.value.filterNot { it.providerId == providerId && it.yearMonth == yyyymm }
    }

    override suspend fun getAll(): List<MonthSettlementEntity> = all.value
    override suspend fun insertAll(settlements: List<MonthSettlementEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

/** Fake Pro entitlement (no Play Billing). */
class FakeProStatus(pro: Boolean = false) : ProStatus {
    override val isPro = MutableStateFlow(pro)
}

class RecordingAnalytics : Analytics {
    /** Thread-safe: the ViewModel logs from viewModelScope while tests read. */
    val events: MutableList<String> = java.util.concurrent.CopyOnWriteArrayList()
    val params: MutableList<Pair<String, Map<String, Any>>> = java.util.concurrent.CopyOnWriteArrayList()
    override fun logEvent(name: String, params: Map<String, Any>) {
        this.params += name to params
        events += name
    }
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun recordNonFatal(throwable: Throwable) = Unit
}

class TestDb {
    val rates = FakeRateDao()
    val providers = FakeProviderDao(rates)
    val marks = FakeMarkDao()
    val settlements = FakeSettlementDao()
    val repository = ProviderRepository(providers, rates, marks, settlements)
}
