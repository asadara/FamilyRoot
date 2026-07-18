package com.example.familytreeplatform.models

data class PersonResponse(
    val personId: String,
    val spaceId: String,
    val fullName: String,
    val title: String?,
    val firstName: String?,
    val lastName: String?,
    val suffix: String?,
    val nickName: String?,
    val gender: String?,
    val birthDate: String?,
    val birthPlace: String?,
    val deathDate: String?,
    val deathPlace: String?,
    val idNumber: String?,
    val lifeStatus: String?,
    val deceasedAt: String?,
    val notes: String?,
    val version: Int,
    val createdAt: String,
    val updatedAt: String
)
