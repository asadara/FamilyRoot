package com.example.familytreeplatform

import com.example.familytreeplatform.models.ExportRelationship
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplexLineageTest {
    @Test
    fun `expanded families reserve disjoint horizontal lineage blocks`() {
        val relationships = listOf(
            spouse("parents-a", "father-a", "mother-a", "MARRIED", "1980-01-01"),
            spouse("parents-b", "father-b", "mother-b", "MARRIED", "1982-01-01"),
            spouse("child-a1-couple", "child-a1", "partner-a1", "MARRIED", "2010-01-01"),
            spouse("child-a2-couple", "child-a2", "partner-a2", "MARRIED", "2012-01-01"),
            spouse("child-b1-couple", "child-b1", "partner-b1", "MARRIED", "2011-01-01"),
            spouse("child-b2-couple", "child-b2", "partner-b2", "MARRIED", "2013-01-01"),
            parentChild("a-father-a1", "father-a", "child-a1", "BIOLOGICAL"),
            parentChild("a-mother-a1", "mother-a", "child-a1", "BIOLOGICAL"),
            parentChild("a-father-a2", "father-a", "child-a2", "BIOLOGICAL"),
            parentChild("a-mother-a2", "mother-a", "child-a2", "BIOLOGICAL"),
            parentChild("b-father-b1", "father-b", "child-b1", "BIOLOGICAL"),
            parentChild("b-mother-b1", "mother-b", "child-b1", "BIOLOGICAL"),
            parentChild("b-father-b2", "father-b", "child-b2", "BIOLOGICAL"),
            parentChild("b-mother-b2", "mother-b", "child-b2", "BIOLOGICAL")
        )
        val visible = relationships
            .flatMapTo(linkedSetOf()) { listOf(it.fromPersonId, it.toPersonId) }
        val placements = planProgressivePlacements(
            basePositions = mapOf(
                "father-a" to LineagePlacementRect(0f, 0f, 96f, 108f),
                "mother-a" to LineagePlacementRect(124f, 0f, 96f, 108f),
                "father-b" to LineagePlacementRect(248f, 0f, 96f, 108f),
                "mother-b" to LineagePlacementRect(372f, 0f, 96f, 108f)
            ),
            visiblePersonIds = visible,
            visibleRelationships = relationships,
            allRelationships = relationships,
            tileWidth = 96f,
            tileHeight = 108f,
            siblingGap = 28f,
            partnershipGap = 28f,
            rankGap = 44f,
            fallbackY = 0f
        )

        fun familyBounds(ids: Set<String>): Pair<Float, Float> =
            placements.filterKeys(ids::contains).values.let { rects ->
                rects.minOf { it.left } to rects.maxOf { it.right }
            }

        val familyA = familyBounds(
            setOf(
                "father-a", "mother-a", "child-a1", "partner-a1",
                "child-a2", "partner-a2"
            )
        )
        val familyB = familyBounds(
            setOf(
                "father-b", "mother-b", "child-b1", "partner-b1",
                "child-b2", "partner-b2"
            )
        )
        assertTrue(
            familyA.second + 28f <= familyB.first ||
                familyB.second + 28f <= familyA.first
        )
        placements.values.forEachIndexed { index, first ->
            placements.values.drop(index + 1).forEach { second ->
                assertFalse(first.overlaps(second, padding = 0f))
            }
        }
    }

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
    fun `all historical relationships fan around the shared person`() {
        val relationships = listOf(
            spouse("newer", "person", "newer-partner", "DIVORCED", "2012-01-01", "2018-01-01"),
            spouse("older", "person", "older-partner", "DIVORCED", "2001-01-01", "2009-01-01")
        )

        assertEquals(-1, partnershipHorizontalSlot("person", "older", relationships))
        assertEquals(1, partnershipHorizontalSlot("person", "newer", relationships))
    }

    @Test
    fun `swapping a couple carries each ancestry block to the same side`() {
        val relationships = listOf(
            spouse("budi-siti", "budi", "siti", "MARRIED", "2000-01-01"),
            spouse("budi-parents", "father-budi", "mother-budi", "MARRIED", "1970-01-01"),
            spouse("siti-parents", "father-siti", "mother-siti", "MARRIED", "1972-01-01"),
            parentChild("father-budi-child", "father-budi", "budi", "BIOLOGICAL"),
            parentChild("mother-budi-child", "mother-budi", "budi", "BIOLOGICAL"),
            parentChild("father-siti-child", "father-siti", "siti", "BIOLOGICAL"),
            parentChild("mother-siti-child", "mother-siti", "siti", "BIOLOGICAL")
        )
        val visible = relationships
            .flatMapTo(linkedSetOf()) { listOf(it.fromPersonId, it.toPersonId) }

        fun place(swapped: Boolean): Map<String, LineagePlacementRect> {
            val pairKey = partnershipPairKey("budi", "siti")
            val (leftId, rightId) = orientedPartnershipPair(
                "budi",
                "siti",
                if (swapped) setOf(pairKey) else emptySet()
            )
            return planProgressivePlacements(
                basePositions = mapOf(
                    leftId to LineagePlacementRect(0f, 152f, 96f, 108f),
                    rightId to LineagePlacementRect(124f, 152f, 96f, 108f)
                ),
                visiblePersonIds = visible,
                visibleRelationships = relationships,
                allRelationships = relationships,
                tileWidth = 96f,
                tileHeight = 108f,
                siblingGap = 28f,
                partnershipGap = 28f,
                rankGap = 44f,
                fallbackY = 152f,
                swappedPartnershipKeys = if (swapped) setOf(pairKey) else emptySet()
            )
        }

        fun ancestryCenter(
            placements: Map<String, LineagePlacementRect>,
            firstParentId: String,
            secondParentId: String
        ): Float = (
            placements.getValue(firstParentId).centerX +
                placements.getValue(secondParentId).centerX
            ) / 2f

        val normal = place(swapped = false)
        assertTrue(
            ancestryCenter(normal, "father-budi", "mother-budi") <
                ancestryCenter(normal, "father-siti", "mother-siti")
        )
        val swapped = place(swapped = true)
        assertTrue(
            ancestryCenter(swapped, "father-budi", "mother-budi") >
                ancestryCenter(swapped, "father-siti", "mother-siti")
        )
        swapped.values.forEachIndexed { index, firstRect ->
            swapped.values.drop(index + 1).forEach { secondRect ->
                assertFalse(firstRect.overlaps(secondRect, padding = 0f))
            }
        }
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
    fun `two simultaneous partnerships use opposite sides of the shared person`() {
        val relationships = listOf(
            spouse("first", "shared", "partner-a", "MARRIED", "2000-01-01"),
            spouse("second", "shared", "partner-b", "MARRIED", "2010-01-01")
        )

        assertEquals(-1, partnershipHorizontalSlot("shared", "first", relationships))
        assertEquals(1, partnershipHorizontalSlot("shared", "second", relationships))

        val positions = planProgressivePlacements(
            basePositions = mapOf(
                "shared" to LineagePlacementRect(0f, 0f, 96f, 108f)
            ),
            visiblePersonIds = setOf("shared", "partner-a", "partner-b"),
            visibleRelationships = relationships,
            allRelationships = relationships,
            tileWidth = 96f,
            tileHeight = 108f,
            siblingGap = 28f,
            partnershipGap = 28f,
            rankGap = 44f,
            fallbackY = 0f
        )

        assertTrue(positions.getValue("partner-a").right < positions.getValue("shared").left)
        assertTrue(positions.getValue("partner-b").left > positions.getValue("shared").right)
    }

    @Test
    fun `widowed partners and their children keep separate outward family blocks`() {
        val relationships = listOf(
            spouse("first", "shared", "partner-a", "WIDOWED", "2000-01-01"),
            spouse("second", "shared", "partner-b", "WIDOWED", "2010-01-01"),
            parentChild("shared-a1", "shared", "a1", "BIOLOGICAL"),
            parentChild("partner-a-a1", "partner-a", "a1", "BIOLOGICAL"),
            parentChild("shared-a2", "shared", "a2", "BIOLOGICAL"),
            parentChild("partner-a-a2", "partner-a", "a2", "BIOLOGICAL"),
            parentChild("shared-a3", "shared", "a3", "BIOLOGICAL"),
            parentChild("partner-a-a3", "partner-a", "a3", "BIOLOGICAL"),
            parentChild("shared-b1", "shared", "b1", "BIOLOGICAL"),
            parentChild("partner-b-b1", "partner-b", "b1", "BIOLOGICAL")
        )
        val people = setOf("shared", "partner-a", "partner-b", "a1", "a2", "a3", "b1")
        val positions = planProgressivePlacements(
            basePositions = mapOf(
                "shared" to LineagePlacementRect(0f, 0f, 96f, 108f)
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

        val firstFamily = listOf("a1", "a2", "a3").map(positions::getValue)
        val firstFamilyRight = firstFamily.maxOf { it.right }
        val firstJunctionX = (
            positions.getValue("partner-a").centerX +
                positions.getValue("shared").centerX
            ) / 2f
        val secondJunctionX = (
            positions.getValue("shared").centerX +
                positions.getValue("partner-b").centerX
            ) / 2f
        assertEquals("positions=$positions", firstJunctionX, firstFamily.maxOf { it.centerX }, 0.01f)
        assertEquals(secondJunctionX, positions.getValue("b1").centerX, 0.01f)
        assertTrue(firstFamilyRight < positions.getValue("b1").left)
        positions.values.forEachIndexed { index, firstRect ->
            positions.values.drop(index + 1).forEach { secondRect ->
                assertFalse(firstRect.overlaps(secondRect, padding = 0f))
            }
        }

        assertEquals(
            setOf("a1", "a2", "a3", "b1"),
            recordedChildrenForPrimaryFamily(
                primaryPersonId = "shared",
                visibleParentPersonIds = setOf("shared"),
                index = LineageRelationshipIndex.from(relationships)
            ).toSet()
        )
    }

    @Test
    fun `two widowed parents remarry without merging their inherited child blocks`() {
        val relationships = listOf(
            spouse(
                "sikem-first-marriage",
                "sikem",
                "sikem-first-partner",
                "WIDOWED",
                "1925-01-01",
                "1935-01-01"
            ),
            spouse(
                "cangkring-first-marriage",
                "cangkring",
                "cangkring-first-partner",
                "WIDOWED",
                "1926-01-01",
                "1936-01-01"
            ),
            spouse(
                "sikem-cangkring",
                "sikem",
                "cangkring",
                "MARRIED",
                "1938-01-01"
            ),
            parentChild("sikem-old-a", "sikem", "sikem-child-a", "BIOLOGICAL"),
            parentChild(
                "sikem-old-b",
                "sikem-first-partner",
                "sikem-child-a",
                "BIOLOGICAL"
            ),
            parentChild("cangkring-old-a", "cangkring", "cangkring-child-a", "BIOLOGICAL"),
            parentChild(
                "cangkring-old-b",
                "cangkring-first-partner",
                "cangkring-child-a",
                "BIOLOGICAL"
            ),
            parentChild("shared-a-sikem", "sikem", "shared-child-a", "BIOLOGICAL"),
            parentChild("shared-a-cangkring", "cangkring", "shared-child-a", "BIOLOGICAL"),
            parentChild("shared-b-sikem", "sikem", "shared-child-b", "BIOLOGICAL"),
            parentChild("shared-b-cangkring", "cangkring", "shared-child-b", "BIOLOGICAL")
        )
        val people = relationships
            .flatMapTo(linkedSetOf()) { listOf(it.fromPersonId, it.toPersonId) }
        listOf("sikem", "cangkring").forEach { focusedPersonId ->
            val positions = planProgressivePlacements(
                basePositions = mapOf(
                    focusedPersonId to LineagePlacementRect(0f, 0f, 96f, 108f)
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

            val sikemX = positions.getValue("sikem").centerX
            val cangkringX = positions.getValue("cangkring").centerX
            val sikemOldX = positions.getValue("sikem-first-partner").centerX
            val cangkringOldX =
                positions.getValue("cangkring-first-partner").centerX
            assertTrue((sikemOldX - sikemX) * (cangkringX - sikemX) < 0f)
            assertTrue((cangkringOldX - cangkringX) * (sikemX - cangkringX) < 0f)

            val familyBlocks = listOf(
                ((sikemOldX + sikemX) / 2f) to
                    listOf(positions.getValue("sikem-child-a")),
                ((sikemX + cangkringX) / 2f) to
                    listOf("shared-child-a", "shared-child-b").map(positions::getValue),
                ((cangkringX + cangkringOldX) / 2f) to
                    listOf(positions.getValue("cangkring-child-a"))
            ).sortedBy { it.first }
            familyBlocks.zipWithNext().forEach { (leftFamily, rightFamily) ->
                assertTrue(
                    leftFamily.second.maxOf { it.right } <
                        rightFamily.second.minOf { it.left }
                )
            }
            positions.values.forEachIndexed { index, firstRect ->
                positions.values.drop(index + 1).forEach { secondRect ->
                    assertFalse(firstRect.overlaps(secondRect, padding = 0f))
                }
            }
        }
    }

    @Test
    fun `childless current partner does not merge two historical child corridors`() {
        val relationships = listOf(
            spouse(
                "nn-cangkring",
                "nn",
                "cangkring",
                "WIDOWED",
                "1925-01-01",
                "1935-01-01"
            ),
            spouse(
                "sikem-cangkring",
                "sikem",
                "cangkring",
                "WIDOWED",
                "1938-01-01",
                "1950-01-01"
            ),
            spouse("sikem-manto", "sikem", "manto", "MARRIED", "1952-01-01"),
            parentChild("nn-karto", "nn", "karto-setiko", "BIOLOGICAL"),
            parentChild("cangkring-karto", "cangkring", "karto-setiko", "BIOLOGICAL"),
            parentChild("sikem-kalinem", "sikem", "kalinem", "BIOLOGICAL"),
            parentChild("cangkring-kalinem", "cangkring", "kalinem", "BIOLOGICAL"),
            parentChild("sikem-kasinem", "sikem", "kasinem", "BIOLOGICAL"),
            parentChild("cangkring-kasinem", "cangkring", "kasinem", "BIOLOGICAL"),
            parentChild("sikem-lasiyem", "sikem", "lasiyem", "BIOLOGICAL"),
            parentChild("cangkring-lasiyem", "cangkring", "lasiyem", "BIOLOGICAL")
        )
        val people = relationships
            .flatMapTo(linkedSetOf()) { listOf(it.fromPersonId, it.toPersonId) }
        val positions = planProgressivePlacements(
            basePositions = mapOf(
                "cangkring" to LineagePlacementRect(0f, 0f, 96f, 108f)
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

        val nnJunction = (
            positions.getValue("nn").centerX +
                positions.getValue("cangkring").centerX
            ) / 2f
        val sikemJunction = (
            positions.getValue("sikem").centerX +
                positions.getValue("cangkring").centerX
            ) / 2f
        assertTrue("positions=$positions", nnJunction < sikemJunction)
        val nnChild = positions.getValue("karto-setiko")
        val sikemChildren = listOf("kalinem", "kasinem", "lasiyem")
            .map(positions::getValue)
        assertTrue(nnChild.right < sikemChildren.minOf { it.left })
        assertEquals(nnJunction, nnChild.centerX, 0.01f)
        assertEquals(sikemJunction, sikemChildren.minOf { it.centerX }, 0.01f)
        positions.values.forEachIndexed { index, firstRect ->
            positions.values.drop(index + 1).forEach { secondRect ->
                assertFalse(firstRect.overlaps(secondRect, padding = 0f))
            }
        }
    }

    @Test
    fun `historical partnerships remain separate placement components`() {
        val relationships = listOf(
            spouse("nn-cangkring", "nn", "cangkring", "WIDOWED", "1925-01-01"),
            spouse("sikem-cangkring", "sikem", "cangkring", "WIDOWED", "1938-01-01"),
            spouse("sikem-manto", "sikem", "manto", "WIDOWED", "1952-01-01"),
            parentChild("nn-karto", "nn", "karto-setiko", "BIOLOGICAL"),
            parentChild("cangkring-karto", "cangkring", "karto-setiko", "BIOLOGICAL"),
            parentChild("sikem-kalinem", "sikem", "kalinem", "BIOLOGICAL"),
            parentChild("cangkring-kalinem", "cangkring", "kalinem", "BIOLOGICAL")
        )
        val people = relationships
            .flatMapTo(linkedSetOf()) { listOf(it.fromPersonId, it.toPersonId) }

        val positions = planProgressivePlacements(
            basePositions = mapOf(
                "cangkring" to LineagePlacementRect(0f, 0f, 96f, 108f),
                "nn" to LineagePlacementRect(-248f, 0f, 96f, 108f),
                "sikem" to LineagePlacementRect(248f, 0f, 96f, 108f),
                "manto" to LineagePlacementRect(496f, 0f, 96f, 108f)
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

        assertTrue(positions.getValue("nn").right < positions.getValue("cangkring").left)
        assertTrue(positions.getValue("cangkring").right < positions.getValue("sikem").left)
        assertTrue(positions.getValue("sikem").right < positions.getValue("manto").left)
        assertFalse(positions.getValue("karto-setiko").overlaps(positions.getValue("kalinem")))
    }

    @Test
    fun `sadinem and reso semito keep four children on one centered corridor`() {
        val relationships = listOf(
            spouse("sadinem-reso", "sadinem", "reso-semito", "MARRIED", "1960-01-01"),
            parentChild("sadinem-pangat", "sadinem", "pangat", "BIOLOGICAL"),
            parentChild("reso-pangat", "reso-semito", "pangat", "BIOLOGICAL"),
            parentChild("sadinem-pardi", "sadinem", "pardi", "BIOLOGICAL"),
            parentChild("reso-pardi", "reso-semito", "pardi", "BIOLOGICAL"),
            parentChild("sadinem-parti", "sadinem", "parti", "BIOLOGICAL"),
            parentChild("reso-parti", "reso-semito", "parti", "BIOLOGICAL"),
            parentChild("sadinem-ruki", "sadinem", "ruki", "BIOLOGICAL"),
            parentChild("reso-ruki", "reso-semito", "ruki", "BIOLOGICAL")
        )
        val people = relationships
            .flatMapTo(linkedSetOf()) { listOf(it.fromPersonId, it.toPersonId) }
        val positions = planProgressivePlacements(
            basePositions = mapOf(
                "sadinem" to LineagePlacementRect(0f, 0f, 96f, 108f)
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

        val junctionX = (
            positions.getValue("sadinem").centerX +
                positions.getValue("reso-semito").centerX
            ) / 2f
        val children = listOf("pangat", "pardi", "parti", "ruki")
            .map(positions::getValue)
            .sortedBy { it.centerX }
        assertEquals(
            junctionX,
            (children.first().centerX + children.last().centerX) / 2f,
            0.01f
        )
        children.zipWithNext().forEach { (left, right) ->
            assertEquals(28f, right.left - left.right, 0.01f)
        }
        positions.values.forEachIndexed { index, firstRect ->
            positions.values.drop(index + 1).forEach { secondRect ->
                assertFalse(firstRect.overlaps(secondRect, padding = 0f))
            }
        }
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
    fun `existing ancestry compacts toward the child before reserving space for in laws`() {
        val relationships = listOf(
            spouse("cangkring-sikem", "cangkring", "sikem", "WIDOWED", "1930-01-01"),
            spouse("cangkring-nn", "cangkring", "nn", "WIDOWED", "1940-01-01"),
            parentChild("cangkring-kalinem", "cangkring", "kalinem", "BIOLOGICAL"),
            parentChild("sikem-kalinem", "sikem", "kalinem", "BIOLOGICAL"),
            parentChild("cangkring-kasinem", "cangkring", "kasinem", "BIOLOGICAL"),
            parentChild("sikem-kasinem", "sikem", "kasinem", "BIOLOGICAL"),
            parentChild("cangkring-lasiyem", "cangkring", "lasiyem", "BIOLOGICAL"),
            parentChild("sikem-lasiyem", "sikem", "lasiyem", "BIOLOGICAL"),
            parentChild("cangkring-karto", "cangkring", "karto", "BIOLOGICAL"),
            parentChild("nn-karto", "nn", "karto", "BIOLOGICAL"),
            parentChild("kalinem-paridjo", "kalinem", "paridjo", "BIOLOGICAL"),
            spouse("paridjo-setiyasih", "paridjo", "setiyasih", "MARRIED", "1970-01-01"),
            parentChild("paridjo-restu", "paridjo", "restu", "BIOLOGICAL"),
            parentChild("setiyasih-restu", "setiyasih", "restu", "BIOLOGICAL"),
            parentChild("paridjo-nurul", "paridjo", "nurul", "BIOLOGICAL"),
            parentChild("setiyasih-nurul", "setiyasih", "nurul", "BIOLOGICAL"),
            parentChild("paridjo-kunto", "paridjo", "kunto", "BIOLOGICAL"),
            parentChild("setiyasih-kunto", "setiyasih", "kunto", "BIOLOGICAL"),
            parentChild("paridjo-aji", "paridjo", "aji", "BIOLOGICAL"),
            parentChild("setiyasih-aji", "setiyasih", "aji", "BIOLOGICAL")
        )
        val people = relationships
            .flatMapTo(linkedSetOf()) { listOf(it.fromPersonId, it.toPersonId) }
        val positions = planProgressivePlacements(
            basePositions = mapOf(
                "aji" to LineagePlacementRect(0f, 456f, 96f, 108f)
            ),
            visiblePersonIds = people,
            visibleRelationships = relationships,
            allRelationships = relationships,
            tileWidth = 96f,
            tileHeight = 108f,
            siblingGap = 28f,
            partnershipGap = 28f,
            rankGap = 44f,
            fallbackY = 456f
        )

        assertEquals(
            positions.getValue("paridjo").centerX,
            positions.getValue("kalinem").centerX,
            0.01f
        )
        positions.values.forEachIndexed { index, first ->
            positions.values.drop(index + 1).forEach { second ->
                assertFalse(first.overlaps(second, padding = 0f))
            }
        }
    }

    @Test
    fun `two in law ancestry blocks keep the shortest safe horizontal gap`() {
        val relationships = listOf(
            parentChild("kalinem-paridjo", "kalinem", "paridjo", "BIOLOGICAL"),
            spouse("paridjo-setiyasih", "paridjo", "setiyasih", "MARRIED", "1970-01-01"),
            spouse("setiyasih-parents", "setiyasih-father", "setiyasih-mother", "MARRIED", "1940-01-01"),
            parentChild(
                "setiyasih-father-child",
                "setiyasih-father",
                "setiyasih",
                "BIOLOGICAL"
            ),
            parentChild(
                "setiyasih-mother-child",
                "setiyasih-mother",
                "setiyasih",
                "BIOLOGICAL"
            )
        )
        val people = relationships
            .flatMapTo(linkedSetOf()) { listOf(it.fromPersonId, it.toPersonId) }
        val positions = planProgressivePlacements(
            basePositions = mapOf(
                "paridjo" to LineagePlacementRect(0f, 152f, 96f, 108f)
            ),
            visiblePersonIds = people,
            visibleRelationships = relationships,
            allRelationships = relationships,
            tileWidth = 96f,
            tileHeight = 108f,
            siblingGap = 28f,
            partnershipGap = 28f,
            rankGap = 44f,
            fallbackY = 152f
        )

        assertEquals(
            positions.getValue("paridjo").centerX,
            positions.getValue("kalinem").centerX,
            0.01f
        )
        val kalinem = positions.getValue("kalinem")
        val inLawParents = listOf("setiyasih-father", "setiyasih-mother")
            .map(positions::getValue)
        val nearestInLawGap = inLawParents.minOf { parent ->
            when {
                parent.left >= kalinem.right -> parent.left - kalinem.right
                kalinem.left >= parent.right -> kalinem.left - parent.right
                else -> 0f
            }
        }
        assertEquals(28f, nearestInLawGap, 0.01f)
        positions.values.forEachIndexed { index, first ->
            positions.values.drop(index + 1).forEach { second ->
                assertFalse(first.overlaps(second, padding = 0f))
            }
        }
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

    @Test
    fun `dense seven generation family stays collision free within the phase budget`() {
        val relationships = mutableListOf<ExportRelationship>()
        val people = linkedSetOf("root-a", "root-b")
        relationships += spouse(
            "root-partnership",
            "root-a",
            "root-b",
            "MARRIED",
            "1940-01-01"
        )
        var generationCouples = listOf("root-a" to "root-b")
        repeat(6) { generation ->
            val nextGeneration = mutableListOf<Pair<String, String>>()
            generationCouples.forEachIndexed { familyIndex, (firstParent, secondParent) ->
                repeat(3) { childIndex ->
                    val child = "g${generation + 1}-f$familyIndex-c$childIndex"
                    val partner = "$child-partner"
                    people += child
                    people += partner
                    relationships += parentChild(
                        "$firstParent-$child",
                        firstParent,
                        child,
                        "BIOLOGICAL"
                    )
                    relationships += parentChild(
                        "$secondParent-$child",
                        secondParent,
                        child,
                        "BIOLOGICAL"
                    )
                    relationships += spouse(
                        "$child-$partner",
                        child,
                        partner,
                        "MARRIED",
                        "${1960 + generation * 20}-01-01"
                    )
                    nextGeneration += child to partner
                }
            }
            generationCouples = nextGeneration
        }

        lateinit var placements: Map<String, LineagePlacementRect>
        val elapsed = measureTimeMillis {
            placements = planProgressivePlacements(
                basePositions = mapOf(
                    "root-a" to LineagePlacementRect(0f, 0f, 96f, 108f),
                    "root-b" to LineagePlacementRect(124f, 0f, 96f, 108f)
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
        }

        assertEquals(2_186, placements.size)
        assertTrue("Dense placement took ${elapsed}ms", elapsed <= 5_000L)
        placements.values
            .groupBy { it.y }
            .values
            .forEach { generationRects ->
                generationRects.sortedBy { it.left }.zipWithNext().forEach { (left, right) ->
                    assertFalse(left.overlaps(right, padding = 0f))
                }
            }
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
