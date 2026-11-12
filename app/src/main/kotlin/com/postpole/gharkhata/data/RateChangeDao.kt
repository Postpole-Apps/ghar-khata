package com.postpole.gharkhata.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RateChangeDao {
    @Query("SELECT * FROM rate_changes WHERE providerId = :providerId ORDER BY effectiveFromEpochDay DESC")
    fun observeFor(providerId: Long): Flow<List<RateChangeEntity>>

    /** Replaces an existing change on the same effective date (unique per provider + date). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rate: RateChangeEntity): Long

    @Query(
        "SELECT amountPaise FROM rate_changes WHERE providerId = :providerId " +
            "AND effectiveFromEpochDay <= :epochDay ORDER BY effectiveFromEpochDay DESC LIMIT 1",
    )
    suspend fun rateOn(providerId: Long, epochDay: Long): Long?

    // Backup support.
    @Query("SELECT * FROM rate_changes ORDER BY id")
    suspend fun getAll(): List<RateChangeEntity>

    @Insert
    suspend fun insertAll(rates: List<RateChangeEntity>)

    @Query("DELETE FROM rate_changes")
    suspend fun deleteAll()
}
