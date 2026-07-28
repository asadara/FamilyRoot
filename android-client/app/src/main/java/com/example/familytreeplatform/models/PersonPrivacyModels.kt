package com.example.familytreeplatform.models

data class UpdatePersonVisibilityRequest(
    val spaceId: String,
    val visibility: String,
    val expectedVersion: Int
)

fun personVisibilityLabel(visibility: String): String = when (visibility) {
    "PRIVATE" -> "Privat"
    "LIMITED" -> "Terbatas"
    else -> "Keluarga"
}
