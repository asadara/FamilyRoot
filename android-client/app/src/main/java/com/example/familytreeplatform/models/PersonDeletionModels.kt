package com.example.familytreeplatform.models

data class PersonDeletionBlocker(
    val code: String,
    val message: String,
    val count: Int
)

data class PersonDeletionImpact(
    val personId: String,
    val fullName: String,
    val relationshipCount: Int,
    val claimCount: Int,
    val mediaCount: Int,
    val sourceCount: Int,
    val pendingProposalCount: Int,
    val canDelete: Boolean,
    val blockers: List<PersonDeletionBlocker>,
    val localMutationCount: Int = 0
)

data class DeletePersonRequest(val spaceId: String)

data class RequestPersonDeletionRequest(
    val spaceId: String,
    val reason: String
)

data class DeletePersonResponse(
    val personId: String,
    val deleted: Boolean
)
