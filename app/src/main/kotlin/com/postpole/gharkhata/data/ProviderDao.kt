package com.postpole.gharkhata.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun getById(id: Long): ProviderEntity?

    @Query("SELECT COUNT(*) FROM providers WHERE archived = 0")
    fun countActive(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM providers WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActiveWithRates(): Flow<List<ProviderWithRates>>

    /** Uses a true upsert (not REPLACE) so updating a provider never cascade-deletes its children. */
    @Upsert
    suspend fun upsert(provider: ProviderEntity): Long

    @Query(
        "UPDATE providers SET archived = :archived, archivedOnEpochDay = " +
            "CASE WHEN :archived THEN CAST(strftime('%s', 'now', 'localtime') / 86400 AS INTEGER) ELSE NULL END " +
            "WHERE id = :id",
    )
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE providers SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    /** Writes 0..n-1 in the given order, atomically. Providers not listed (archived) are untouched. */
    @Transaction
    suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { i, id -> setSortOrder(id, i) }
    }

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM providers")
    suspend fun maxSortOrder(): Int

    @Delete
    suspend fun delete(provider: ProviderEntity)

    // Backup support.
    @Query("SELECT * FROM providers ORDER BY id")
    suspend fun getAll(): List<ProviderEntity>

    @Insert
    suspend fun insertAll(providers: List<ProviderEntity>)

    @Query("DELETE FROM providers")
    suspend fun deleteAll()
}
