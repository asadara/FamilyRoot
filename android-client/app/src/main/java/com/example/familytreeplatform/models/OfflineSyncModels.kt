package com.example.familytreeplatform.models

data class LifeStatusMutationPayload(
    val lifeStatus: String,
    val deceasedAt: String? = null
)

data class ProfileMutationPayload(
    val birthPlace: String,
    val notes: String,
    val fullName: String? = null,
    val nickName: String? = null,
    val gender: String? = null,
    val birthDate: String? = null,
    val deathPlace: String? = null
)

data class ParentChildMutationPayload(
    val parentId: String,
    val childId: String,
    val meta: String
)

data class SpouseMutationPayload(
    val personAId: String,
    val personBId: String,
    val meta: String,
    val startDate: String?,
    val endDate: String? = null
)

data class PersonConflictSnapshot(
    val personId: String,
    val version: Int,
    val lifeStatus: String? = null,
    val deceasedAt: String? = null,
    val fullName: String? = null,
    val nickName: String? = null,
    val gender: String? = null,
    val birthDate: String? = null,
    val birthPlace: String? = null,
    val deathPlace: String? = null,
    val notes: String? = null,
    val updatedAt: String? = null
)

data class ApiConflictEnvelope(
    val code: String? = null,
    val message: String? = null,
    val details: PersonConflictSnapshot? = null
)
