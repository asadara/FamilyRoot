package com.example.familytreeplatform.models

data class PersonListItem(
    val personId: String,
    val fullName: String,
    val createdAt: String,
    val lifeStatus: String,
    val deceasedAt: String? = null,
    val birthDate: String? = null,
    val birthPlace: String? = null,
    val gender: String? = null,
    val notes: String? = null,
    val version: Int = 1,
    val nickName: String? = null,
    val deathPlace: String? = null
)
