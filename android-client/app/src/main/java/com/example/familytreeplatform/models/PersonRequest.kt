package com.example.familytreeplatform.models

data class PersonRequest(
    val spaceId: String,
    val title: String? = null,
    val firstName: String,
    val lastName: String? = null,
    val suffix: String? = null,
    val nickName: String,
    val gender: String,
    val birthDate: String? = null,
    val birthPlace: String? = null,
    val deathDate: String? = null,
    val deathPlace: String? = null,
    val idNumber: String? = null,
    val lifeStatus: String? = null
)
