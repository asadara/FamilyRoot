package com.example.familytreeplatform.models

data class RelationsResponse(
    val personId: String,
    val parents: List<RelationItem> = emptyList(),
    val children: List<RelationItem> = emptyList(),
    val spouses: List<RelationItem> = emptyList()
)
