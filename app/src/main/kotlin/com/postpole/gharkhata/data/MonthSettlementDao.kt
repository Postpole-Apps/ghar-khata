package com.postpole.gharkhata.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthSettlementDao {
    @Query("SELECT * FROM month_settlements WHERE providerId = :providerId ORDER BY yearMonth DESC")
    fun observeFor(providerId: Long): Flow<List<MonthSettlementEntity>>

    /** Every settlement, for widget redraws (no per-provider fan-out). */
    @Query("SELECT * FROM month_settlements ORDER BY providerId, yearMonth")
    fun observeAll(): Flow<List<MonthSettlementEntity>>

    @Query("SELECT * FROM month_settlements WHERE providerId = :providerId AND yearMonth = :yyyymm")
    suspend fun get(providerId: Long, yyyymm: Int): MonthSettlementEntity?

    @Upsert
    suspend fun upsert(settlement: MonthSettlementEntity)

    @Query("DELETE FROM month_settlements WHERE providerId = :providerId AND yearMonth = :yyyymm")
    suspend fun delete(providerId: Long, yyyymm: Int)

    // Backup support.
    @Query("SELECT * FROM month_settlements ORDER BY providerId, yearMonth")
    suspend fun getAll(): List<MonthSettlementEntity>

    @Insert
    suspend fun insertAll(settlements: List<MonthSettlementEntity>)

    @Query("DELETE FROM month_settlements")
    suspend fun deleteAll()
}
