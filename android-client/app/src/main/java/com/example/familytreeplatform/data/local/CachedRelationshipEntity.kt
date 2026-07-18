package com.example.familytreeplatform.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.familytreeplatform.models.RelationItem

@Entity(
    tableName = "relationships",
    indices = [Index("spaceId"), Index("fromPersonId"), Index("toPersonId"), Index("pendingMutationId")]
)
data class CachedRelationshipEntity(
    @PrimaryKey val relationshipId: String,
    val spaceId: String,
    val type: String,
    val fromPersonId: String,
    val toPersonId: String,
    val meta: String?,
    val startDate: String?,
    val endDate: String?,
    val createdAt: String,
    val pendingMutationId: String?
)

fun CachedRelationshipEntity.toModel() = RelationItem(
    relationshipId = relationshipId,
    type = type,
    fromPersonId = fromPersonId,
    toPersonId = toPersonId,
    meta = meta,
    createdAt = createdAt,
    startDate = startDate,
    endDate = endDate
)

fun RelationItem.toEntity(spaceId: String, pendingMutationId: String? = null) = CachedRelationshipEntity(
    relationshipId = relationshipId,
    spaceId = spaceId,
    type = type,
    fromPersonId = fromPersonId,
    toPersonId = toPersonId,
    meta = meta,
    startDate = startDate,
    endDate = endDate,
    createdAt = createdAt,
    pendingMutationId = pendingMutationId
)
