package com.example.familytreeplatform.models

data class SourceItem(
    val sourceId: String,
    val spaceId: String,
    val personId: String,
    val title: String,
    val type: String,
    val url: String? = null,
    val note: String? = null,
    val createdAt: String
)

data class SourceRequest(
    val spaceId: String,
    val title: String,
    val type: String = "DOCUMENT",
    val url: String? = null,
    val note: String? = null
)

data class MediaItem(
    val mediaId: String,
    val spaceId: String,
    val personId: String,
    val label: String,
    val kind: String,
    val uri: String,
    val sourceId: String? = null,
    val createdAt: String
)

data class MediaRequest(
    val spaceId: String,
    val label: String,
    val kind: String = "PHOTO",
    val uri: String,
    val sourceId: String? = null
)

data class ProfilePhotoItem(
    val personId: String,
    val mediaId: String,
    val url: String,
    val expiresIn: Int
)

data class ProposalItem(
    val proposalId: String,
    val spaceId: String,
    val personId: String,
    val field: String,
    val proposedValue: String,
    val reason: String? = null,
    val status: String,
    val reviewedByUserId: String? = null,
    val reviewedAt: String? = null,
    val createdAt: String
)

data class ProposalRequest(
    val spaceId: String,
    val personId: String,
    val field: String = "notes",
    val proposedValue: String,
    val reason: String? = null
)

data class ReviewProposalRequest(
    val spaceId: String,
    val proposalId: String
)

data class DuplicateGroup(
    val reason: String,
    val people: List<PersonListItem>
)

data class MergePersonsRequest(
    val spaceId: String,
    val sourcePersonId: String,
    val targetPersonId: String
)

data class RelationshipPathPerson(
    val personId: String,
    val fullName: String
)

data class RelationshipPathEdge(
    val relationshipId: String,
    val type: String,
    val fromPersonId: String,
    val toPersonId: String,
    val meta: String? = null,
    val direction: String
)

data class RelationshipPathResponse(
    val found: Boolean,
    val people: List<RelationshipPathPerson> = emptyList(),
    val edges: List<RelationshipPathEdge> = emptyList()
)
