package com.postpole.gharkhata.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** App-facing API over the provider, rate, day-mark and settlement DAOs. */
@Singleton
class ProviderRepository @Inject constructor(
    private val providers: ProviderDao,
    private val rates: RateChangeDao,
    private val marks: DayMarkDao,
    private val settlements: MonthSettlementDao,
) {
    fun observeActive(): Flow<List<ProviderEntity>> = providers.observeActive()
    fun observeAll(): Flow<List<ProviderEntity>> = providers.observeAll()
    fun observeActiveWithRates(): Flow<List<ProviderWithRates>> = providers.observeActiveWithRates()
    fun countActive(): Flow<Int> = providers.countActive()
    suspend fun getProvider(id: Long): ProviderEntity? = providers.getById(id)
    suspend fun saveProvider(provider: ProviderEntity): Long = providers.upsert(provider)
    suspend fun setArchived(id: Long, archived: Boolean) = providers.setArchived(id, archived)
    suspend fun deleteProvider(provider: ProviderEntity) = providers.delete(provider)

    fun observeRates(providerId: Long): Flow<List<RateChangeEntity>> = rates.observeFor(providerId)
    suspend fun addRate(rate: RateChangeEntity): Long = rates.insert(rate)
    suspend fun rateOn(providerId: Long, epochDay: Long): Long? = rates.rateOn(providerId, epochDay)

    fun observeMarksForDay(epochDay: Long): Flow<List<DayMarkEntity>> = marks.observeForDay(epochDay)
    fun observeMarks(providerId: Long, fromDay: Long, toDay: Long): Flow<List<DayMarkEntity>> =
        marks.observeRange(providerId, fromDay, toDay)
    suspend fun setMark(mark: DayMarkEntity) = marks.upsert(mark)
    suspend fun clearMark(providerId: Long, epochDay: Long) = marks.clear(providerId, epochDay)

    fun observeSettlements(providerId: Long): Flow<List<MonthSettlementEntity>> =
        settlements.observeFor(providerId)
    fun observeAllSettlements(): Flow<List<MonthSettlementEntity>> = settlements.observeAll()
    suspend fun getSettlement(providerId: Long, yyyymm: Int): MonthSettlementEntity? =
        settlements.get(providerId, yyyymm)
    suspend fun saveSettlement(s: MonthSettlementEntity) = settlements.upsert(s)
    suspend fun deleteSettlement(providerId: Long, yyyymm: Int) = settlements.delete(providerId, yyyymm)
}
