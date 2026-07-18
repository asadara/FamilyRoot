package com.example.familytreeplatform.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_mutations",
    indices = [Index("spaceId"), Index("personId"), Index("status")]
)
data class OfflineMutationEntity(
    @PrimaryKey val mutationId: String,
    val spaceId: String,
    val personId: String,
    val mutationType: String,
    val payloadJson: String,
    val baseVersion: Int,
    val status: String,
    val attemptCount: Int,
    val lastError: String?,
    val conflictVersion: Int?,
    val conflictPayloadJson: String?,
    val createdAt: Long,
    val updatedAt: Long
)

object OfflineMutationStatus {
    const val PENDING = "PENDING"
    const val SYNCING = "SYNCING"
    const val FAILED = "FAILED"
    const val CONFLICT = "CONFLICT"
}

object OfflineMutationType {
    const val UPDATE_LIFE_STATUS = "UPDATE_LIFE_STATUS"
    const val UPDATE_PROFILE = "UPDATE_PROFILE"
    const val ADD_PARENT_CHILD = "ADD_PARENT_CHILD"
    const val ADD_SPOUSE = "ADD_SPOUSE"
}
