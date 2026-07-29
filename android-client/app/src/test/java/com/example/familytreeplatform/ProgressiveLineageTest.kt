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
    fun `child expansion belongs to one exact partnership ring`() {
        val relationships = listOf(
            spouse("parent", "partner-a"),
            spouse("parent", "partner-b"),
            parentChild("parent", "child-a"),
            parentChild("partner-a", "child-a"),
            parentChild("parent", "child-b"),
            parentChild("partner-b", "child-b")
        )
        val plan = planProgressiveLineage(
            baseVisiblePersonIds = setOf("parent", "partner-a", "partner-b"),
            expandedParentPersonIds = emptySet(),
            expandedChildPersonIds = emptySet(),
            expandedChildFamilyKeys = setOf(
                childFamilyBranchKey(setOf("parent", "partner-a"))
            ),
            relationships = relationships
        )

        assertTrue("child-a" in plan.visiblePersonIds)
        assertFalse("child-b" in plan.visiblePersonIds)
    }

    @Test
    fun `collapsed child branch forgets hidden deeper expansion state`() {
        val childFamilyKey =
            childFamilyBranchKey(setOf("younger", "younger-spouse"))
        val grandchildFamilyKey =
            childFamilyBranchKey(setOf("younger-child", "grandchild-parent"))
        val before = ProgressiveLineageExpansionState(
            childFamilyKeys = setOf(childFamilyKey, grandchildFamilyKey),
            partnershipPersonIds = setOf("younger-child")
        )
        val collapsed = pruneHiddenLineageExpansions(
            beforeBaseVisiblePersonIds = initialVisible,
            before = before,
            afterRootToggle = before.copy(
                childFamilyKeys = before.childFamilyKeys - childFamilyKey
            ),
            relationships = activeSpaceRelationships
        )

        assertFalse(grandchildFamilyKey in collapsed.childFamilyKeys)
        assertFalse("younger-child" in collapsed.partnershipPersonIds)

        val reopened = planProgressiveLineage(
            baseVisiblePersonIds = initialVisible,
            expandedParentPersonIds = collapsed.parentPersonIds,
            expandedChildPersonIds = emptySet(),
            expandedPartnershipPersonIds = collapsed.partnershipPersonIds,
            expandedChildFamilyKeys = collapsed.childFamilyKeys + childFamilyKey,
            relationships = activeSpaceRelationships
        )
        assertTrue("younger-child" in reopened.visiblePersonIds)
        assertFalse("grandchild" in reopened.visiblePersonIds)
    }

    @Test
    fun `collapsed parent branch reopens only one ancestor generation`() {
        val relationships = listOf(
            parentChild("parent", "child"),
            parentChild("grandparent", "parent")
        )
        val before = ProgressiveLineageExpansionState(
            parentPersonIds = setOf("child", "parent")
        )
        val collapsed = pruneHiddenLineageExpansions(
            beforeBaseVisiblePersonIds = setOf("child"),
            before = before,
            afterRootToggle = before.copy(
                parentPersonIds = before.parentPersonIds - "child"
            ),
            relationships = relationships
        )

        assertFalse("parent" in collapsed.parentPersonIds)
        val reopened = planProgressiveLineage(
            baseVisiblePersonIds = setOf("child"),
            expandedParentPersonIds = collapsed.parentPersonIds + "child",
            expandedChildPersonIds = emptySet(),
            expandedPartnershipPersonIds = collapsed.partnershipPersonIds,
            expandedChildFamilyKeys = collapsed.childFamilyKeys,
            relationships = relationships
        )
        assertTrue("parent" in reopened.visiblePersonIds)
        assertFalse("grandparent" in reopened.visiblePersonIds)
    }

    @Test
    fun `collapsed side branch forgets the hidden partner ancestry`() {
        val relationships = listOf(
            spouse("person", "partner"),
            parentChild("partner-parent", "partner")
        )
        val before = ProgressiveLineageExpansionState(
            parentPersonIds = setOf("partner"),
            partnershipPersonIds = setOf("person")
        )
        val collapsed = pruneHiddenLineageExpansions(
            beforeBaseVisiblePersonIds = setOf("person"),
            before = before,
            afterRootToggle = before.copy(partnershipPersonIds = emptySet()),
            relationships = relationships
        )

        assertFalse("partner" in collapsed.parentPersonIds)
        val reopened = planProgressiveLineage(
            baseVisiblePersonIds = setOf("person"),
            expandedParentPersonIds = collapsed.parentPersonIds,
            expandedChildPersonIds = emptySet(),
            expandedPartnershipPersonIds = setOf("person"),
            expandedChildFamilyKeys = collapsed.childFamilyKeys,
            relationships = relationships
        )
        assertTrue("partner" in reopened.visiblePersonIds)
        assertFalse("partner-parent" in reopened.visiblePersonIds)
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
