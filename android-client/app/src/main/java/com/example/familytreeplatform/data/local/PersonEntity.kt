package com.example.familytreeplatform.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.PersonResponse

@Entity(tableName = "persons", indices = [Index("spaceId")])
data class PersonEntity(
    @PrimaryKey val personId: String,
    val spaceId: String,
    val fullName: String,
    val createdAt: String,
    val lifeStatus: String,
    val deceasedAt: String?,
    val birthDate: String?,
    val birthPlace: String? = null,
    val gender: String?,
    val notes: String? = null,
    @ColumnInfo(defaultValue = "1") val version: Int = 1
)

fun PersonEntity.toModel() = PersonListItem(
    personId = personId,
    fullName = fullName,
    createdAt = createdAt,
    lifeStatus = lifeStatus,
    deceasedAt = deceasedAt,
    birthDate = birthDate,
    birthPlace = birthPlace,
    gender = gender,
    notes = notes,
    version = version
)

fun PersonListItem.toEntity(spaceId: String) = PersonEntity(
    personId = personId,
    spaceId = spaceId,
    fullName = fullName,
    createdAt = createdAt,
    lifeStatus = lifeStatus,
    deceasedAt = deceasedAt,
    birthDate = birthDate,
    birthPlace = birthPlace,
    gender = gender,
    notes = notes,
    version = version
)

fun PersonResponse.toEntity() = PersonEntity(
    personId = personId,
    spaceId = spaceId,
    fullName = fullName,
    createdAt = createdAt,
    lifeStatus = lifeStatus ?: "ALIVE",
    deceasedAt = deceasedAt,
    birthDate = birthDate,
    birthPlace = birthPlace,
    gender = gender,
    notes = notes,
    version = version
)
