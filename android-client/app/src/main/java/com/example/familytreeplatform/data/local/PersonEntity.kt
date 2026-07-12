package com.example.familytreeplatform.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.familytreeplatform.models.PersonListItem

@Entity(tableName = "persons", indices = [Index("spaceId")])
data class PersonEntity(
    @PrimaryKey val personId: String,
    val spaceId: String,
    val fullName: String,
    val createdAt: String,
    val lifeStatus: String,
    val deceasedAt: String?,
    val birthDate: String?,
    val gender: String?
)

fun PersonEntity.toModel() = PersonListItem(
    personId, fullName, createdAt, lifeStatus, deceasedAt, birthDate, gender
)

fun PersonListItem.toEntity(spaceId: String) = PersonEntity(
    personId, spaceId, fullName, createdAt, lifeStatus, deceasedAt, birthDate, gender
)
