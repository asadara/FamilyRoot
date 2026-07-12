package com.example.familytreeplatform.models

data class PersonListItem(
    val personId: String,
    val fullName: String,
    val createdAt: String,
    val lifeStatus: String,
    val deceasedAt: String? = null,
    val birthDate: String? = null,
    val gender: String? = null
)
