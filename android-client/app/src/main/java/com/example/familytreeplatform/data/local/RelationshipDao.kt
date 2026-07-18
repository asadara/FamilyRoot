package com.example.familytreeplatform.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RelationshipDao {
    @Query("SELECT * FROM relationships WHERE spaceId = :spaceId ORDER BY createdAt DESC")
    fun observeBySpace(spaceId: String): Flow<List<CachedRelationshipEntity>>

    @Query("SELECT * FROM relationships WHERE spaceId = :spaceId ORDER BY createdAt DESC")
    suspend fun listBySpace(spaceId: String): List<CachedRelationshipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedRelationshipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedRelationshipEntity>)

    @Query("DELETE FROM relationships WHERE relationshipId = :relationshipId")
    suspend fun delete(relationshipId: String)

    @Query("DELETE FROM relationships WHERE pendingMutationId = :mutationId")
    suspend fun deleteByMutation(mutationId: String)

    @Query("DELETE FROM relationships WHERE spaceId = :spaceId AND pendingMutationId IS NULL")
    suspend fun deleteSyncedBySpace(spaceId: String)

    @Query("DELETE FROM relationships WHERE spaceId = :spaceId")
    suspend fun deleteBySpace(spaceId: String)

    @Transaction
    suspend fun replaceSynced(spaceId: String, items: List<CachedRelationshipEntity>) {
        deleteSyncedBySpace(spaceId)
        upsertAll(items)
    }
}
