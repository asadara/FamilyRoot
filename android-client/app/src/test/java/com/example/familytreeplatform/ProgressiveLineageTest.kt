package com.example.familytreeplatform

import com.example.familytreeplatform.models.ExportRelationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveLineageTest {
    private val activeSpaceRelationships = listOf(
        parentChild("parent-a", "older"),
        parentChild("parent-b", "older"),
        parentChild("parent-a", "younger"),
        parentChild("parent-b", "younger"),
        spouse("older", "older-spouse"),
        parentChild("older", "older-child"),
        parentChild("older-spouse", "older-child"),
        spouse("younger", "younger-spouse"),
        parentChild("younger", "younger-child"),
        parentChild("younger-spouse", "younger-child"),
        spouse("younger-child", "grandchild-parent"),
        parentChild("younger-child", "grandchild"),
        parentChild("grandchild-parent", "grandchild")
    )

    private val initialVisible = setOf(
        "parent-a",
        "parent-b",
        "older",
        "older-spouse",
        "older-child",
        "younger"
    )

    @Test
    fun `expanding a sibling opens only that sibling immediate family`() {
        val plan = planProgressiveLineage(
            baseVisiblePersonIds = initialVisible,
            expandedParentPersonIds = emptySet(),
            expandedChildPersonIds = setOf("younger"),
            relationships = activeSpaceRelationships
        )

        assertTrue("younger-spouse" in plan.visiblePersonIds)
        assertTrue("younger-child" in plan.visiblePersonIds)
        assertFalse("grandchild" in plan.visiblePersonIds)
        assertEquals(plan.visiblePersonIds.size, plan.visiblePersonIds.distinct().size)
    }

    @Test
    fun `recursive expansion advances one requested branch at a time`() {
        val plan = planProgressiveLineage(
            baseVisiblePersonIds = initialVisible,
            expandedParentPersonIds = emptySet(),
            expandedChildPersonIds = setOf("younger", "younger-child"),
            relationships = activeSpaceRelationships
        )

        assertTrue("younger-spouse" in plan.visiblePersonIds)
        assertTrue("younger-child" in plan.visiblePersonIds)
        assertTrue("grandchild-parent" in plan.visiblePersonIds)
        assertTrue("grandchild" in plan.visiblePersonIds)
    }

    @Test
    fun `collapsing a branch removes its family while preserving the base graph`() {
        val plan = planProgressiveLineage(
            baseVisiblePersonIds = initialVisible,
            expandedParentPersonIds = emptySet(),
            expandedChildPersonIds = emptySet(),
            relationships = activeSpaceRelationships
        )

        assertEquals(initialVisible, plan.visiblePersonIds)
        assertFalse("younger-spouse" in plan.visiblePersonIds)
        assertFalse("younger-child" in plan.visiblePersonIds)
    }

    @Test
    fun `a spouse natal branch cannot be inferred when it is absent from active space data`() {
        val plan = planProgressiveLineage(
            baseVisiblePersonIds = initialVisible,
            expandedParentPersonIds = setOf("younger-spouse"),
            expandedChildPersonIds = setOf("younger"),
            relationships = activeSpaceRelationships
        )

        assertFalse("private-natal-parent" in plan.visiblePersonIds)
        assertFalse(hasRecordedParents("younger-spouse", activeSpaceRelationships))
    }

    @Test
    fun `branch controls are based only on recorded relationships`() {
        assertTrue(hasRecordedChildren("younger", activeSpaceRelationships))
        assertTrue(hasRecordedParents("younger-child", activeSpaceRelationships))
        assertFalse(hasRecordedParents("younger-spouse", activeSpaceRelationships))
        assertFalse(hasRecordedChildren("unlisted-person", activeSpaceRelationships))
    }

    private fun parentChild(parentId: String, childId: String) = ExportRelationship(
        relationshipId = "$parentId-$childId",
        type = "PARENT_CHILD",
        fromPersonId = parentId,
        toPersonId = childId,
        meta = "BIOLOGICAL",
        createdAt = "2026-07-19"
    )

    private fun spouse(personId: String, spouseId: String) = ExportRelationship(
        relationshipId = "$personId-$spouseId",
        type = "SPOUSE",
        fromPersonId = personId,
        toPersonId = spouseId,
        meta = "MARRIED",
        createdAt = "2026-07-19"
    )
}
