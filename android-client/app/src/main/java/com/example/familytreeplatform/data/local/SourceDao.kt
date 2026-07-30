package com.example.familytreeplatform.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources WHERE personId = :personId ORDER BY createdAt DESC")
    fun observeByPerson(personId: String): Flow<List<CachedSourceEntity>>

    @Query("SELECT * FROM sources WHERE personId = :personId ORDER BY createdAt DESC")
    suspend fun listByPerson(personId: String): List<CachedSourceEntity>

    @Query("SELECT * FROM sources WHERE spaceId = :spaceId ORDER BY createdAt DESC")
    suspend fun listBySpace(spaceId: String): List<CachedSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedSourceEntity>)

    @Query("DELETE FROM sources WHERE sourceId = :sourceId")
    suspend fun delete(sourceId: String)

    @Query("DELETE FROM sources WHERE pendingMutationId = :mutationId")
    suspend fun deleteByMutation(mutationId: String)

    @Query(
        """DELETE FROM sources
            WHERE spaceId = :spaceId
              AND pendingMutationId IS NOT NULL
              AND NOT EXISTS (
                SELECT 1 FROM offline_mutations
                WHERE offline_mutations.mutationId = sources.pendingMutationId
              )"""
    )
    suspend fun deleteOrphanedPending(spaceId: String)

    @Query("DELETE FROM sources WHERE personId = :personId")
    suspend fun deleteByPerson(personId: String)

    @Query("DELETE FROM sources WHERE spaceId = :spaceId")
    suspend fun deleteBySpace(spaceId: String)

    @Query("DELETE FROM sources")
    suspend fun deleteAll()

    @Query("DELETE FROM sources WHERE personId = :personId AND pendingMutationId IS NULL")
    suspend fun deleteSyncedByPerson(personId: String)

    @Query("UPDATE sources SET personId = :newId WHERE personId = :oldId")
    suspend fun remapPerson(oldId: String, newId: String)

    @Transaction
    suspend fun replaceSynced(personId: String, items: List<CachedSourceEntity>) {
        deleteSyncedByPerson(personId)
        upsertAll(items)
    }
}
