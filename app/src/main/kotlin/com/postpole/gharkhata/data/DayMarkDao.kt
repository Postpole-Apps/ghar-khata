package com.postpole.gharkhata.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DayMarkDao {
    @Query("SELECT * FROM day_marks WHERE epochDay = :epochDay")
    fun observeForDay(epochDay: Long): Flow<List<DayMarkEntity>>

    @Query(
        "SELECT * FROM day_marks WHERE providerId = :providerId " +
            "AND epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay",
    )
    fun observeRange(providerId: Long, fromDay: Long, toDay: Long): Flow<List<DayMarkEntity>>

    @Upsert
    suspend fun upsert(mark: DayMarkEntity)

    /** Removes the exception, returning the day to the provider's default. */
    @Query("DELETE FROM day_marks WHERE providerId = :providerId AND epochDay = :epochDay")
    suspend fun clear(providerId: Long, epochDay: Long)

    // Backup support.
    @Query("SELECT * FROM day_marks ORDER BY providerId, epochDay")
    suspend fun getAll(): List<DayMarkEntity>

    @Insert
    suspend fun insertAll(marks: List<DayMarkEntity>)

    @Query("DELETE FROM day_marks")
    suspend fun deleteAll()
}
