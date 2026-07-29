package com.example.familytreeplatform.repository

import com.example.familytreeplatform.data.local.OfflineMutationEntity
import com.example.familytreeplatform.data.local.OfflineMutationStatus
import com.example.familytreeplatform.data.local.OfflineMutationType
import com.example.familytreeplatform.models.DeleteRelationshipMutationPayload
import com.example.familytreeplatform.models.ParentChildMutationPayload
import com.example.familytreeplatform.models.RelationItem
import com.example.familytreeplatform.models.SpouseMutationPayload
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMutationRemapTest {
    @Test
    fun `person remap updates parent child payload and focus atomically`() {
        val mutation = mutation(
            type = OfflineMutationType.ADD_PARENT_CHILD,
            personId = LOCAL_ID,
            payload = Gson().toJson(
                ParentChildMutationPayload(LOCAL_ID, "child", "BIOLOGICAL")
            )
        )

        val remapped = mutation.remapPersonReference(LOCAL_ID, SERVER_ID, updatedAt = 99)
        val payload = Gson().fromJson(
            remapped.payloadJson,
            ParentChildMutationPayload::class.java
        )

        assertEquals(SERVER_ID, remapped.personId)
        assertEquals(SERVER_ID, payload.parentId)
        assertEquals("child", payload.childId)
        assertEquals(99, remapped.updatedAt)
    }

    @Test
    fun `person remap updates spouse and delete snapshot endpoints`() {
        val spouse = mutation(
            type = OfflineMutationType.ADD_SPOUSE,
            payload = Gson().toJson(
                SpouseMutationPayload("anchor", LOCAL_ID, "MARRIED", null)
            )
        ).remapPersonReference(LOCAL_ID, SERVER_ID)
        val spousePayload = Gson().fromJson(
            spouse.payloadJson,
            SpouseMutationPayload::class.java
        )
        assertEquals(SERVER_ID, spousePayload.personBId)

        val delete = mutation(
            type = OfflineMutationType.DELETE_RELATIONSHIP,
            payload = Gson().toJson(
                DeleteRelationshipMutationPayload(
                    RelationItem(
                        relationshipId = "relationship",
                        type = "PARENT_CHILD",
                        fromPersonId = LOCAL_ID,
                        toPersonId = "child",
                        meta = "FOSTER",
                        createdAt = "2026-07-28"
                    )
                )
            )
        ).remapPersonReference(LOCAL_ID, SERVER_ID)
        val deletePayload = Gson().fromJson(
            delete.payloadJson,
            DeleteRelationshipMutationPayload::class.java
        )
        assertEquals(SERVER_ID, deletePayload.relationship.fromPersonId)
    }

    @Test
    fun `dependency detection covers relationship payload references`() {
        val related = mutation(
            type = OfflineMutationType.ADD_PARENT_CHILD,
            payload = Gson().toJson(
                ParentChildMutationPayload("parent", LOCAL_ID, "BIOLOGICAL")
            )
        )
        val unrelated = mutation(
            type = OfflineMutationType.ADD_PARENT_CHILD,
            payload = Gson().toJson(
                ParentChildMutationPayload("parent", "other", "BIOLOGICAL")
            )
        )

        assertTrue(related.referencesPerson(LOCAL_ID))
        assertFalse(unrelated.referencesPerson(LOCAL_ID))
    }

    @Test
    fun `source dependency follows focus person remap`() {
        val source = mutation(
            type = OfflineMutationType.CREATE_SOURCE,
            personId = LOCAL_ID,
            payload = "{}"
        )

        val remapped = source.remapPersonReference(LOCAL_ID, SERVER_ID)

        assertEquals(SERVER_ID, remapped.personId)
        assertTrue(source.referencesPerson(LOCAL_ID))
    }

    private fun mutation(
        type: String,
        personId: String = "focus",
        payload: String
    ) = OfflineMutationEntity(
        mutationId = "mutation",
        spaceId = "space",
        personId = personId,
        mutationType = type,
        payloadJson = payload,
        baseVersion = 0,
        status = OfflineMutationStatus.PENDING,
        attemptCount = 0,
        lastError = null,
        conflictVersion = null,
        conflictPayloadJson = null,
        createdAt = 1,
        updatedAt = 1
    )

    private companion object {
        const val LOCAL_ID = "local-person"
        const val SERVER_ID = "server-person"
    }
}
