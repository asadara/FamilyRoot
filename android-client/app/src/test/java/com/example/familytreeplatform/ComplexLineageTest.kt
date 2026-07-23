package com.example.familytreeplatform

import com.example.familytreeplatform.models.ExportRelationship
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplexLineageTest {
    @Test
    fun `horizontal lineage hub sits midway inside the generation gap`() {
        val parentBottom = 108f
        val childTop = 152f

        assertEquals(130f, lineageHubY(parentBottom, childTop), 0.01f)
    }

    @Test
    fun `partnership history is chronological and keeps the current relationship rightmost`() {
        val relationships = listOf(
            spouse("current", "person", "current-partner", "MARRIED", "2020-01-01"),
            spouse("oldest", "person", "first-partner", "DIVORCED", "2000-01-01", "2008-01-01"),
            spouse("middle", "person", "second-partner", "WIDOWED", "2010-01-01", "2018-01-01")
        )

        assertEquals(
            listOf("first-partner", "second-partner", "current-partner"),
            recordedPartnershipPersonIds("person", relationships)
        )
        assertEquals(-2, partnershipHorizontalSlot("person", "oldest", relationships))
        assertEquals(-1, partnershipHorizontalSlot("person", "middle", relationships))
        assertEquals(1, partnershipHorizontalSlot("person", "current", relationships))
        assertEquals("current", latestCurrentPartnership("person", relationships)?.relationshipId)
    }

    @Test
    fun `all historical relationships retain chronological left to right order`() {
        val relationships = listOf(
            spouse("newer", "person", "newer-partner", "DIVORCED", "2012-01-01", "2018-01-01"),
            spouse("older", "person", "older-partner", "DIVORCED", "2001-01-01", "2009-01-01")
        )

        assertEquals(1, partnershipHorizontalSlot("person", "older", relationships))
        assertEquals(2, partnershipHorizontalSlot("person", "newer", relationships))
    }

    @Test
    fun `historical placement is deterministic chronological and collision free`() {
        val relationships = listOf(
            spouse("current", "person", "current-partner", "MARRIED", "2020-01-01"),
            spouse("oldest", "person", "first-partner", "DIVORCED", "2000-01-01", "2008-01-01"),
            spouse("middle", "person", "second-partner", "WIDOWED", "2010-01-01", "2018-01-01")
        )
        val base = mapOf(
            "person" to LineagePlacementRect(0f, 0f, 120f, 152f),
            "current-partner" to LineagePlacementRect(148f, 0f, 120f, 152f)
        )
        fun place() = planProgressivePlacements(
            basePositions = base,
            visiblePersonIds = setOf("person", "first-partner", "second-partner", "current-partner"),
            visibleRelationships = relationships,
            allRelationships = relationships,
            tileWidth = 120f,
            tileHeight = 152f,
            siblingGap = 28f,
            partnershipGap = 28f,
            rankGap = 64f,
            fallbackY = 0f
        )

        val positions = place()
        assertEquals(positions, place())
        assertTrue(positions.getValue("first-partner").x < positions.getValue("second-partner").x)
        assertTrue(positions.getValue("second-partner").x < positions.getValue("person").x)
        assertTrue(positions.getValue("person").x < positions.getValue("current-partner").x)
        positions.values.forEachIndexed { index, first ->
            positions.values.drop(index + 1).forEach { second ->
                assertFalse(first.overlaps(second))
            }
        }
    }

    @Test
    fun `sibling couples remain atomic and ordered regardless of relationship response order`() {
        val relationships = listOf(
            spouse("raka-alya", "raka", "alya", "MARRIED", "2022-01-01"),
            spouse("rieke-antony", "rieke", "antony", "MARRIED", "2024-01-01")
        )
        val base = mapOf(
            "raka" to LineagePlacementRect(0f, 0f, 120f, 152f),
            "alya" to LineagePlacementRect(148f, 0f, 120f, 152f),
            "rieke" to LineagePlacementRect(296f, 0f, 120f, 152f)
        )
        fun place(input: List<ExportRelationship>) = planProgressivePlacements(
            basePositions = base,
            visiblePersonIds = setOf("raka", "alya", "rieke", "antony"),
            visibleRelationships = input,
            allRelationships = input,
            tileWidth = 120f,
            tileHeight = 152f,
            siblingGap = 28f,
            partnershipGap = 28f,
            rankGap = 64f,
            fallbackY = 0f
        )

        val positions = place(relationships)
        assertEquals(positions, place(relationships.reversed()))
        assertTrue(positions.getValue("raka").x < positions.getValue("alya").x)
        assertTrue(positions.getValue("alya").x < positions.getValue("rieke").x)
        assertEquals(
            148f,
            positions.getValue("antony").x - positions.getValue("rieke").x,
            0.01f
        )
        assertTrue(positions.getValue("rieke").x < positions.getValue("antony").x)
    }

    @Test
    fun `parent couple opened upward is placed as one atomic unit`() {
        val relationships = listOf(
            parentChild("budi-raka", "budi", "raka", "BIOLOGICAL"),
            parentChild("siti-raka", "siti", "raka", "BIOLOGICAL"),
            spouse("budi-siti", "budi", "siti", "MARRIED", "2000-01-01")
        )
        val positions = planProgressivePlacements(
            basePositions = mapOf("raka" to LineagePlacementRect(0f, 0f, 120f, 152f)),
            visiblePersonIds = setOf("raka", "budi", "siti"),
            visibleRelationships = relationships,
            allRelationships = relationships,
            tileWidth = 120f,
            tileHeight = 152f,
            siblingGap = 28f,
            partnershipGap = 28f,
            rankGap = 64f,
            fallbackY = 0f
        )

        val budi = positions.getValue("budi")
        val siti = positions.getValue("siti")
        assertEquals(budi.y, siti.y, 0.01f)
        assertEquals(148f, kotlin.math.abs(budi.x - siti.x), 0.01f)
        assertTrue(budi.bottom < positions.getValue("raka").top)
    }

    @Test
    fun `multiple partnerships share one person card and keep their own junction slots`() {
        val relationships = listOf(
            spouse("old", "raka", "alya", "DIVORCED", "2010-01-01", "2018-01-01"),
            spouse("current", "raka", "maya", "MARRIED", "2022-01-01")
        )
        val positions = planProgressivePlacements(
            basePositions = mapOf("raka" to LineagePlacementRect(0f, 0f, 120f, 152f)),
            visiblePersonIds = setOf("raka", "alya", "maya"),
            visibleRelationships = relationships,
            allRelationships = relationships,
            tileWidth = 120f,
            tileHeight = 152f,
            siblingGap = 28f,
            partnershipGap = 28f,
            rankGap = 64f,
            fallbackY = 0f
        )

        assertEquals(3, positions.size)
        assertTrue(positions.getValue("alya").x < positions.getValue("raka").x)
        assertTrue(positions.getValue("raka").x < positions.getValue("maya").x)
    }

    @Test
    fun `partnership expansion reveals every recorded partner without inferring children`() {
        val relationships = listOf(
            spouse("old", "person", "old-partner", "DIVORCED", "2000-01-01", "2007-01-01"),
            spouse("current", "person", "current-partner", "MARRIED", "2015-01-01"),
            parentChild("person-old-child", "person", "old-child", "BIOLOGICAL"),
            parentChild("partner-old-child", "old-partner", "old-child", "BIOLOGICAL"),
            parentChild("person-current-child", "person", "current-child", "BIOLOGICAL"),
            parentChild("partner-current-child", "current-partner", "current-child", "BIOLOGICAL")
        )

        val partnersOnly = planProgressiveLineage(
            baseVisiblePersonIds = setOf("person"),
            expandedParentPersonIds = emptySet(),
            expandedChildPersonIds = emptySet(),
            expandedPartnershipPersonIds = setOf("person"),
            relationships = relationships
        )
        assertTrue("old-partner" in partnersOnly.visiblePersonIds)
        assertTrue("current-partner" in partnersOnly.visiblePersonIds)
        assertFalse("old-child" in partnersOnly.visiblePersonIds)
        assertFalse("current-child" in partnersOnly.visiblePersonIds)

        val completeFamilies = planProgressiveLineage(
            baseVisiblePersonIds = setOf("person"),
            expandedParentPersonIds = emptySet(),
            expandedChildPersonIds = setOf("person"),
            expandedPartnershipPersonIds = setOf("person"),
            relationships = relationships
        )
        assertTrue("old-child" in completeFamilies.visiblePersonIds)
        assertTrue("current-child" in completeFamilies.visiblePersonIds)
        assertEquals(
            completeFamilies.visiblePersonIds.size,
            completeFamilies.visiblePersonIds.distinct().size
        )
    }

    @Test
    fun `complex parentage preserves origin groups without inferring step partnership`() {
        val relationships = listOf(
            parentChild("bio-a", "biological-a", "child", "BIOLOGICAL"),
            parentChild("bio-b", "biological-b", "child", "BIOLOGICAL"),
            parentChild("step", "step-parent", "child", "STEP")
        )
        val index = LineageRelationshipIndex.from(relationships)

        assertEquals(
            listOf(setOf("biological-a", "biological-b"), setOf("step-parent")),
            recordedParentGroups("child", index)
        )
        assertEquals(
            listOf("child"),
            recordedChildrenForParentGroup(
                setOf("biological-a", "biological-b"),
                index
            )
        )
        assertTrue(
            recordedChildrenForParentGroup(
                setOf("biological-a", "step-parent"),
                index
            ).isEmpty()
        )
    }

    @Test
    fun `mixed two-parent types remain one explicit child family`() {
        val relationships = listOf(
            parentChild("bio", "parent-a", "child", "BIOLOGICAL"),
            parentChild("adoptive", "parent-b", "child", "ADOPTIVE")
        )

        assertEquals(
            listOf(setOf("parent-a", "parent-b")),
            recordedParentGroups("child", LineageRelationshipIndex.from(relationships))
        )
    }

    @Test
    fun `child expansion works with two explicit parents and no partnership inference`() {
        val relationships = listOf(
            parentChild("parent-a", "parent-a", "child", "BIOLOGICAL"),
            parentChild("parent-b", "parent-b", "child", "ADOPTIVE")
        )

        val plan = planProgressiveLineage(
            baseVisiblePersonIds = setOf("parent-a"),
            expandedParentPersonIds = emptySet(),
            expandedChildPersonIds = setOf("parent-a"),
            relationships = relationships
        )

        assertTrue("child" in plan.visiblePersonIds)
        assertTrue("parent-b" in plan.visiblePersonIds)
        assertTrue(plan.visibleRelationships.none { it.type == "SPOUSE" })
    }

    @Test
    fun `two biological family blocks grow outward from the primary partnership`() {
        val relationships = listOf(
            spouse("aji-anisa", "aji", "anisa", "MARRIED", "2020-01-01"),
            spouse("aji-parents", "paridjo", "setiyasih", "MARRIED", "1970-01-01"),
            spouse("anisa-parents", "riyanto", "saminah", "MARRIED", "1972-01-01"),
            parentChild("paridjo-aji", "paridjo", "aji", "BIOLOGICAL"),
            parentChild("setiyasih-aji", "setiyasih", "aji", "BIOLOGICAL"),
            parentChild("paridjo-kunto", "paridjo", "kunto", "BIOLOGICAL"),
            parentChild("setiyasih-kunto", "setiyasih", "kunto", "BIOLOGICAL"),
            parentChild("paridjo-nurul", "paridjo", "nurul", "BIOLOGICAL"),
            parentChild("setiyasih-nurul", "setiyasih", "nurul", "BIOLOGICAL"),
            parentChild("riyanto-anisa", "riyanto", "anisa", "BIOLOGICAL"),
            parentChild("saminah-anisa", "saminah", "anisa", "BIOLOGICAL"),
            parentChild("riyanto-dimas", "riyanto", "dimas", "BIOLOGICAL"),
            parentChild("saminah-dimas", "saminah", "dimas", "BIOLOGICAL"),
            parentChild("riyanto-fajar", "riyanto", "fajar", "BIOLOGICAL"),
            parentChild("saminah-fajar", "saminah", "fajar", "BIOLOGICAL")
        )
        val base = mapOf(
            "aji" to LineagePlacementRect(0f, 152f, 96f, 108f),
            "anisa" to LineagePlacementRect(124f, 152f, 96f, 108f),
            "paridjo" to LineagePlacementRect(-62f, 0f, 96f, 108f),
            "setiyasih" to LineagePlacementRect(62f, 0f, 96f, 108f),
            "riyanto" to LineagePlacementRect(186f, 0f, 96f, 108f),
            "saminah" to LineagePlacementRect(310f, 0f, 96f, 108f),
            // Deliberately interleaved input positions reproduce the old visual collision.
            "kunto" to LineagePlacementRect(248f, 152f, 96f, 108f),
            "nurul" to LineagePlacementRect(372f, 152f, 96f, 108f),
            "dimas" to LineagePlacementRect(-248f, 152f, 96f, 108f),
            "fajar" to LineagePlacementRect(-124f, 152f, 96f, 108f)
        )

        val positions = planProgressivePlacements(
            basePositions = base,
            visiblePersonIds = base.keys,
            visibleRelationships = relationships,
            allRelationships = relationships,
            tileWidth = 96f,
            tileHeight = 108f,
            siblingGap = 28f,
            partnershipGap = 28f,
            rankGap = 44f,
            fallbackY = 152f
        )

        assertTrue(positions.getValue("kunto").right < positions.getValue("aji").left)
        assertTrue(positions.getValue("nurul").right < positions.getValue("aji").left)
        assertTrue(positions.getValue("dimas").left > positions.getValue("anisa").right)
        assertTrue(positions.getValue("fajar").left > positions.getValue("anisa").right)
        positions.values.forEachIndexed { index, first ->
            positions.values.drop(index + 1).forEach { second ->
                assertFalse(first.overlaps(second, padding = 0f))
            }
        }
    }

    @Test
    fun `children use measured subtree widths and keep one global gap`() {
        val relationships = listOf(
            spouse("parents", "parent-a", "parent-b", "MARRIED", "2000-01-01"),
            parentChild("a-child-one", "parent-a", "child-one", "BIOLOGICAL"),
            parentChild("b-child-one", "parent-b", "child-one", "BIOLOGICAL"),
            parentChild("a-child-two", "parent-a", "child-two", "BIOLOGICAL"),
            parentChild("b-child-two", "parent-b", "child-two", "BIOLOGICAL"),
            spouse("child-one-partner", "child-one", "partner", "MARRIED", "2024-01-01"),
            parentChild("child-one-grand-a", "child-one", "grand-a", "BIOLOGICAL"),
            parentChild("partner-grand-a", "partner", "grand-a", "BIOLOGICAL"),
            parentChild("child-one-grand-b", "child-one", "grand-b", "BIOLOGICAL"),
            parentChild("partner-grand-b", "partner", "grand-b", "BIOLOGICAL")
        )
        val people = setOf(
            "parent-a",
            "parent-b",
            "child-one",
            "child-two",
            "partner",
            "grand-a",
            "grand-b"
        )
        val positions = planProgressivePlacements(
            basePositions = mapOf(
                "parent-a" to LineagePlacementRect(0f, 0f, 96f, 108f),
                "parent-b" to LineagePlacementRect(124f, 0f, 96f, 108f)
            ),
            visiblePersonIds = people,
            visibleRelationships = relationships,
            allRelationships = relationships,
            tileWidth = 96f,
            tileHeight = 108f,
            siblingGap = 28f,
            partnershipGap = 28f,
            rankGap = 44f,
            fallbackY = 0f
        )

        val firstBlock = listOf("child-one", "partner", "grand-a", "grand-b")
            .map(positions::getValue)
        val secondBlock = positions.getValue("child-two")
        val firstLeft = firstBlock.minOf { it.left }
        val firstRight = firstBlock.maxOf { it.right }
        val gap = if (firstRight < secondBlock.left) {
            secondBlock.left - firstRight
        } else {
            firstLeft - secondBlock.right
        }
        assertEquals(28f, gap, 0.01f)

        val envelopeLeft = minOf(firstLeft, secondBlock.left)
        val envelopeRight = maxOf(firstRight, secondBlock.right)
        val unionCenter = (
            positions.getValue("parent-a").centerX +
                positions.getValue("parent-b").centerX
            ) / 2f
        assertEquals(unionCenter, (envelopeLeft + envelopeRight) / 2f, 0.01f)
    }

    @Test
    fun `progressive planner handles ten thousand people within the phase budget`() {
        val relationships = (0 until 9_999).map { index ->
            parentChild(
                id = "edge-$index",
                parentId = "person-$index",
                childId = "person-${index + 1}",
                meta = "BIOLOGICAL"
            )
        }
        val expanded = (0 until 9_999).mapTo(mutableSetOf()) { "person-$it" }
        lateinit var plan: ProgressiveLineagePlan

        val elapsed = measureTimeMillis {
            plan = planProgressiveLineage(
                baseVisiblePersonIds = setOf("person-0"),
                expandedParentPersonIds = emptySet(),
                expandedChildPersonIds = expanded,
                relationships = relationships
            )
        }

        assertEquals(10_000, plan.visiblePersonIds.size)
        assertEquals(9_999, plan.visibleRelationships.size)
        assertTrue("Planner took ${elapsed}ms", elapsed <= 1_500L)

        lateinit var placements: Map<String, LineagePlacementRect>
        val placementElapsed = measureTimeMillis {
            placements = planProgressivePlacements(
                basePositions = mapOf(
                    "person-0" to LineagePlacementRect(0f, 0f, 120f, 152f)
                ),
                visiblePersonIds = plan.visiblePersonIds,
                visibleRelationships = plan.visibleRelationships,
                allRelationships = relationships,
                tileWidth = 120f,
                tileHeight = 152f,
                siblingGap = 28f,
                partnershipGap = 28f,
                rankGap = 64f,
                fallbackY = 0f
            )
        }
        assertEquals(10_000, placements.size)
        assertTrue("Placement took ${placementElapsed}ms", placementElapsed <= 1_500L)
        assertFalse(
            placements.getValue("person-5").overlaps(placements.getValue("person-6"))
        )
    }

    private fun parentChild(
        id: String,
        parentId: String,
        childId: String,
        meta: String
    ) = ExportRelationship(
        relationshipId = id,
        type = "PARENT_CHILD",
        fromPersonId = parentId,
        toPersonId = childId,
        meta = meta,
        createdAt = "2026-07-20"
    )

    private fun spouse(
        id: String,
        personId: String,
        partnerId: String,
        meta: String,
        startDate: String,
        endDate: String? = null
    ) = ExportRelationship(
        relationshipId = id,
        type = "SPOUSE",
        fromPersonId = personId,
        toPersonId = partnerId,
        meta = meta,
        startDate = startDate,
        endDate = endDate,
        createdAt = startDate
    )
}
