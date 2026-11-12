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

    @Query("UPDATE providers SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

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
