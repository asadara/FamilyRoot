package com.example.familytreeplatform.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.familytreeplatform.models.SourceItem

@Entity(
    tableName = "sources",
    indices = [
        Index("spaceId"),
        Index("personId"),
        Index("pendingMutationId")
    ]
)
data class CachedSourceEntity(
    @PrimaryKey val sourceId: String,
    val spaceId: String,
    val personId: String,
    val title: String,
    val type: String,
    val url: String?,
    val note: String?,
    val createdAt: String,
    val pendingMutationId: String?
)

fun CachedSourceEntity.toModel() = SourceItem(
    sourceId = sourceId,
    spaceId = spaceId,
    personId = personId,
    title = title,
    type = type,
    url = url,
    note = note,
    createdAt = createdAt,
    pendingSync = pendingMutationId != null
)

fun SourceItem.toEntity() = CachedSourceEntity(
    sourceId = sourceId,
    spaceId = spaceId,
    personId = personId,
    title = title,
    type = type,
    url = url,
    note = note,
    createdAt = createdAt,
    pendingMutationId = null
)
