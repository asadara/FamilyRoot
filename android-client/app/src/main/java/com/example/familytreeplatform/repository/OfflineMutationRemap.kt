package com.example.familytreeplatform.repository

import com.example.familytreeplatform.data.local.CachedRelationshipEntity
import com.example.familytreeplatform.data.local.OfflineMutationEntity
import com.example.familytreeplatform.data.local.OfflineMutationType
import com.example.familytreeplatform.models.DeleteRelationshipMutationPayload
import com.example.familytreeplatform.models.ParentChildMutationPayload
import com.example.familytreeplatform.models.SpouseMutationPayload
import com.google.gson.Gson

internal fun String.replacePersonId(oldId: String, newId: String): String =
    if (this == oldId) newId else this

internal fun OfflineMutationEntity.remapPersonReference(
    oldId: String,
    newId: String,
    updatedAt: Long = System.currentTimeMillis()
): OfflineMutationEntity = copy(
    personId = personId.replacePersonId(oldId, newId),
    payloadJson = remapMutationPayload(mutationType, payloadJson, oldId, newId),
    updatedAt = updatedAt
)

internal fun OfflineMutationEntity.referencesPerson(personId: String): Boolean =
    this.personId == personId ||
        when (mutationType) {
            OfflineMutationType.ADD_PARENT_CHILD -> runCatching {
                Gson().fromJson(payloadJson, ParentChildMutationPayload::class.java)
            }.getOrNull()?.let {
                it.parentId == personId || it.childId == personId
            } == true
            OfflineMutationType.ADD_SPOUSE -> runCatching {
                Gson().fromJson(payloadJson, SpouseMutationPayload::class.java)
            }.getOrNull()?.let {
                it.personAId == personId || it.personBId == personId
            } == true
            OfflineMutationType.DELETE_RELATIONSHIP -> runCatching {
                Gson().fromJson(
                    payloadJson,
                    DeleteRelationshipMutationPayload::class.java
                )
            }.getOrNull()?.relationship?.let {
                it.fromPersonId == personId || it.toPersonId == personId
            } == true
            else -> false
        }

internal fun OfflineMutationEntity.hasUnresolvedLocalPersonReference(): Boolean =
    when (mutationType) {
        OfflineMutationType.ADD_PARENT_CHILD -> runCatching {
            Gson().fromJson(payloadJson, ParentChildMutationPayload::class.java)
        }.getOrNull()?.let { payload ->
            payload.parentId.isLocalPersonReference() ||
                payload.childId.isLocalPersonReference()
        } == true
        OfflineMutationType.ADD_SPOUSE -> runCatching {
            Gson().fromJson(payloadJson, SpouseMutationPayload::class.java)
        }.getOrNull()?.let { payload ->
            payload.personAId.isLocalPersonReference() ||
                payload.personBId.isLocalPersonReference()
        } == true
        else -> false
    }

internal fun OfflineMutationEntity.isResolvedBy(
    relationships: List<CachedRelationshipEntity>
): Boolean = when (mutationType) {
    OfflineMutationType.ADD_PARENT_CHILD -> runCatching {
        Gson().fromJson(payloadJson, ParentChildMutationPayload::class.java)
    }.getOrNull()?.let { payload ->
        relationships.any { relationship ->
            relationship.pendingMutationId == null &&
                relationship.type == "PARENT_CHILD" &&
                relationship.fromPersonId == payload.parentId &&
                relationship.toPersonId == payload.childId &&
                relationship.meta == payload.meta &&
                relationship.startDate == payload.startDate &&
                relationship.endDate == payload.endDate &&
                relationship.careContext.normalizedContext() ==
                payload.careContext.normalizedContext()
        }
    } == true
    OfflineMutationType.ADD_SPOUSE -> runCatching {
        Gson().fromJson(payloadJson, SpouseMutationPayload::class.java)
    }.getOrNull()?.let { payload ->
        relationships.any { relationship ->
            relationship.pendingMutationId == null &&
                relationship.type == "SPOUSE" &&
                setOf(relationship.fromPersonId, relationship.toPersonId) ==
                setOf(payload.personAId, payload.personBId) &&
                relationship.meta == payload.meta &&
                relationship.startDate == payload.startDate &&
                relationship.endDate == payload.endDate
        }
    } == true
    else -> false
}

private fun String.isLocalPersonReference(): Boolean = startsWith("local-person-")

private fun String?.normalizedContext(): String? = this?.trim()?.ifBlank { null }

internal fun remapMutationPayload(
    mutationType: String,
    payloadJson: String,
    oldId: String,
    newId: String
): String = when (mutationType) {
    OfflineMutationType.ADD_PARENT_CHILD -> Gson().fromJson(
        payloadJson,
        ParentChildMutationPayload::class.java
    ).let { payload ->
        Gson().toJson(
            payload.copy(
                parentId = payload.parentId.replacePersonId(oldId, newId),
                childId = payload.childId.replacePersonId(oldId, newId)
            )
        )
    }
    OfflineMutationType.ADD_SPOUSE -> Gson().fromJson(
        payloadJson,
        SpouseMutationPayload::class.java
    ).let { payload ->
        Gson().toJson(
            payload.copy(
                personAId = payload.personAId.replacePersonId(oldId, newId),
                personBId = payload.personBId.replacePersonId(oldId, newId)
            )
        )
    }
    OfflineMutationType.DELETE_RELATIONSHIP -> Gson().fromJson(
        payloadJson,
        DeleteRelationshipMutationPayload::class.java
    ).let { payload ->
        Gson().toJson(
            payload.copy(
                relationship = payload.relationship.copy(
                    fromPersonId = payload.relationship.fromPersonId
                        .replacePersonId(oldId, newId),
                    toPersonId = payload.relationship.toPersonId
                        .replacePersonId(oldId, newId)
                )
            )
        )
    }
    else -> payloadJson
}
