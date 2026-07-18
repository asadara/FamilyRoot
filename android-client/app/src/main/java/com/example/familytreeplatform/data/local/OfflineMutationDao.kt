package com.example.familytreeplatform.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineMutationDao {
    @Query("SELECT * FROM offline_mutations WHERE personId = :personId ORDER BY createdAt ASC")
    fun observeForPerson(personId: String): Flow<List<OfflineMutationEntity>>

    @Query("SELECT * FROM offline_mutations WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun listReady(limit: Int = 20): List<OfflineMutationEntity>

    @Query("SELECT * FROM offline_mutations WHERE spaceId = :spaceId")
    suspend fun listForSpace(spaceId: String): List<OfflineMutationEntity>

    @Query("SELECT COUNT(*) FROM offline_mutations WHERE spaceId = :spaceId")
    suspend fun countForSpace(spaceId: String): Int

    @Query("SELECT * FROM offline_mutations WHERE mutationId = :mutationId LIMIT 1")
    suspend fun getById(mutationId: String): OfflineMutationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: OfflineMutationEntity)

    @Query("DELETE FROM offline_mutations WHERE mutationId = :mutationId")
    suspend fun delete(mutationId: String)

    @Query("DELETE FROM offline_mutations WHERE spaceId = :spaceId")
    suspend fun deleteBySpace(spaceId: String)

    @Query("DELETE FROM offline_mutations WHERE personId = :personId AND mutationType = :mutationType")
    suspend fun deleteForPersonAndType(personId: String, mutationType: String)

    @Query(
        """UPDATE offline_mutations SET baseVersion = :baseVersion, updatedAt = :updatedAt
            WHERE personId = :personId AND mutationId != :excludeMutationId AND status = 'PENDING'"""
    )
    suspend fun rebasePendingForPerson(
        personId: String,
        excludeMutationId: String,
        baseVersion: Int,
        updatedAt: Long
    )

    @Query(
        """UPDATE offline_mutations
            SET status = :status, attemptCount = attemptCount + 1,
                lastError = :error, updatedAt = :updatedAt
            WHERE mutationId = :mutationId"""
    )
    suspend fun markAttempt(mutationId: String, status: String, error: String?, updatedAt: Long)

    @Query(
        """UPDATE offline_mutations
            SET status = :status, lastError = :error, updatedAt = :updatedAt
            WHERE mutationId = :mutationId"""
    )
    suspend fun markStatus(mutationId: String, status: String, error: String?, updatedAt: Long)

    @Query("UPDATE offline_mutations SET status = 'PENDING', updatedAt = :updatedAt WHERE status = 'SYNCING'")
    suspend fun resetInterrupted(updatedAt: Long)

    @Query("UPDATE offline_mutations SET status = 'PENDING', lastError = NULL, updatedAt = :updatedAt WHERE status = 'FAILED'")
    suspend fun retryAllFailed(updatedAt: Long)

    @Query(
        """UPDATE offline_mutations
            SET status = 'CONFLICT', attemptCount = attemptCount + 1,
                lastError = :error, conflictVersion = :serverVersion,
                conflictPayloadJson = :conflictPayloadJson, updatedAt = :updatedAt
            WHERE mutationId = :mutationId"""
    )
    suspend fun markConflict(
        mutationId: String,
        error: String,
        serverVersion: Int?,
        conflictPayloadJson: String?,
        updatedAt: Long
    )

    @Query(
        """UPDATE offline_mutations
            SET status = 'PENDING', baseVersion = :baseVersion,
                lastError = NULL, conflictVersion = NULL,
                conflictPayloadJson = NULL, updatedAt = :updatedAt
            WHERE mutationId = :mutationId"""
    )
    suspend fun retryWithVersion(mutationId: String, baseVersion: Int, updatedAt: Long)
}
