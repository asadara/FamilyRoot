package com.example.familytreeplatform

import com.example.familytreeplatform.models.ExportRelationship
import kotlin.math.floor

/**
 * Read-only index over relationships already delivered for the active Family Space.
 * Keeping this index local to the graph prevents repeated full-list scans while the
 * user progressively opens a large lineage.
 */
internal class LineageRelationshipIndex private constructor(
    val relationships: List<ExportRelationship>,
    private val parentsByChild: Map<String, List<ExportRelationship>>,
    private val childrenByParent: Map<String, List<ExportRelationship>>,
    private val partnershipsByPerson: Map<String, List<ExportRelationship>>
) {
    fun parentRelationships(personId: String): List<ExportRelationship> =
        parentsByChild[personId].orEmpty()

    fun childRelationships(personId: String): List<ExportRelationship> =
        childrenByParent[personId].orEmpty()

    fun partnerships(personId: String): List<ExportRelationship> =
        partnershipsByPerson[personId].orEmpty()

    fun recordedParentPersonIds(personId: String): Set<String> =
        parentRelationships(personId).mapTo(linkedSetOf()) { it.fromPersonId }

    fun recordedChildFamilyPersonIds(personId: String): Set<String> {
        val childIds = childRelationships(personId)
            .mapTo(linkedSetOf()) { it.toPersonId }
        if (childIds.isEmpty()) return emptySet()

        val familyIds = linkedSetOf<String>()
        familyIds += childIds
        childIds.forEach { childId ->
            familyIds += parentRelationships(childId).map { it.fromPersonId }
        }
        familyIds.remove(personId)
        return familyIds
    }

    fun recordedPartnershipPersonIds(personId: String): List<String> =
        partnerships(personId).map { it.otherPersonId(personId) }.distinct()

    companion object {
        fun from(relationships: List<ExportRelationship>): LineageRelationshipIndex {
            val parentRelationships = relationships.filter { it.type == "PARENT_CHILD" }
            val partnerships = relationships.filter { it.type == "SPOUSE" }
            val partnershipsByPerson = buildMap<String, MutableList<ExportRelationship>> {
                partnerships.forEach { relationship ->
                    getOrPut(relationship.fromPersonId) { mutableListOf() }.add(relationship)
                    getOrPut(relationship.toPersonId) { mutableListOf() }.add(relationship)
                }
                values.forEach { it.sortWith(partnershipChronologyComparator) }
            }
            return LineageRelationshipIndex(
                relationships = relationships,
                parentsByChild = parentRelationships.groupBy { it.toPersonId },
                childrenByParent = parentRelationships.groupBy { it.fromPersonId },
                partnershipsByPerson = partnershipsByPerson
            )
        }
    }
}

internal fun recordedPartnerships(
    personId: String,
    relationships: List<ExportRelationship>
): List<ExportRelationship> = LineageRelationshipIndex.from(relationships).partnerships(personId)

internal fun recordedPartnershipPersonIds(
    personId: String,
    relationships: List<ExportRelationship>
): List<String> = LineageRelationshipIndex.from(relationships)
    .recordedPartnershipPersonIds(personId)

internal fun isCurrentPartnership(relationship: ExportRelationship): Boolean =
    relationship.type == "SPOUSE" &&
        relationship.meta == "MARRIED" &&
        relationship.endDate.isNullOrBlank()

internal fun latestCurrentPartnership(
    personId: String,
    relationships: List<ExportRelationship>
): ExportRelationship? = recordedPartnerships(personId, relationships)
    .filter(::isCurrentPartnership)
    .lastOrNull()

/**
 * Returns a stable horizontal slot for a partner relative to [personId]. Historical
 * relationships stay left of the current relationship. If every relationship is
 * historical, their slots progress left-to-right in chronological order.
 */
internal fun partnershipHorizontalSlot(
    personId: String,
    relationshipId: String,
    relationships: List<ExportRelationship>
): Int = partnershipHorizontalSlot(
    personId = personId,
    relationshipId = relationshipId,
    index = LineageRelationshipIndex.from(relationships)
)

internal fun partnershipHorizontalSlot(
    personId: String,
    relationshipId: String,
    index: LineageRelationshipIndex
): Int {
    val ordered = index.partnerships(personId)
    val target = ordered.firstOrNull { it.relationshipId == relationshipId } ?: return 1
    val historical = ordered.filterNot(::isCurrentPartnership)
    val current = ordered.filter(::isCurrentPartnership)
    return if (isCurrentPartnership(target)) {
        current.indexOfFirst { it.relationshipId == relationshipId }.coerceAtLeast(0) + 1
    } else if (current.isNotEmpty()) {
        val historicalIndex = historical
            .indexOfFirst { it.relationshipId == relationshipId }
            .coerceAtLeast(0)
        -(historical.size - historicalIndex)
    } else {
        historical.indexOfFirst { it.relationshipId == relationshipId }.coerceAtLeast(0) + 1
    }
}

internal data class LineagePlacementRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    val left: Float get() = x
    val top: Float get() = y
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    fun shifted(dx: Float, dy: Float = 0f): LineagePlacementRect =
        copy(x = x + dx, y = y + dy)

    fun overlaps(other: LineagePlacementRect, padding: Float = 10f): Boolean =
        left < other.right + padding &&
            right + padding > other.left &&
            top < other.bottom + padding &&
            bottom + padding > other.top
}

/**
 * Deterministic placement shared by the production graph and the large-lineage
 * performance gate. A small spatial index keeps collision checks near-linear.
 */
internal fun planProgressivePlacements(
    basePositions: Map<String, LineagePlacementRect>,
    visiblePersonIds: Set<String>,
    visibleRelationships: List<ExportRelationship>,
    allRelationships: List<ExportRelationship>,
    tileWidth: Float,
    tileHeight: Float,
    siblingGap: Float,
    partnershipGap: Float,
    rankGap: Float,
    fallbackY: Float
): Map<String, LineagePlacementRect> {
    if (basePositions.isEmpty() || visiblePersonIds.isEmpty()) return basePositions
    val positions = linkedMapOf<String, LineagePlacementRect>().apply {
        putAll(basePositions)
    }
    val missingPersonIds = visiblePersonIds
        .filterNot { it in positions }
        .toMutableSet()
    if (missingPersonIds.isEmpty()) return positions

    val horizontalStep = tileWidth + siblingGap
    val partnershipStep = tileWidth + partnershipGap
    val verticalStep = tileHeight + rankGap
    val relationshipIndex = LineageRelationshipIndex.from(allRelationships)
    val collisionIndex = PlacementCollisionIndex(tileWidth, tileHeight).apply {
        positions.values.forEach(::add)
    }

    fun findFreeRect(
        proposed: LineagePlacementRect,
        preferredDirection: Int = 0
    ): LineagePlacementRect {
        val attempts = visiblePersonIds.size + basePositions.size + 8
        repeat(attempts) { index ->
            val step = when {
                index == 0 -> 0
                preferredDirection != 0 -> index * preferredDirection
                index % 2 == 1 -> (index + 1) / 2
                else -> -(index / 2)
            }
            val candidate = proposed.shifted(horizontalStep * step)
            if (!collisionIndex.overlaps(candidate)) return candidate
        }
        val rightEdge = positions.values.maxOf { it.right }
        return LineagePlacementRect(
            x = rightEdge + siblingGap,
            y = proposed.y,
            width = tileWidth,
            height = tileHeight
        )
    }

    fun addPerson(
        personId: String,
        proposed: LineagePlacementRect,
        preferredDirection: Int = 0
    ) {
        val rect = findFreeRect(proposed, preferredDirection)
        positions[personId] = rect
        collisionIndex.add(rect)
        missingPersonIds.remove(personId)
    }

    val relationshipsByPerson = buildMap<String, MutableList<ExportRelationship>> {
        visibleRelationships.forEach { relationship ->
            getOrPut(relationship.fromPersonId) { mutableListOf() }.add(relationship)
            getOrPut(relationship.toPersonId) { mutableListOf() }.add(relationship)
        }
    }
    val queue = ArrayDeque<String>().apply {
        positions.entries
            .sortedWith(
                compareBy<Map.Entry<String, LineagePlacementRect>> { it.value.y }
                    .thenBy { it.value.x }
                    .thenBy { it.key }
            )
            .forEach { addLast(it.key) }
    }
    val processed = mutableSetOf<String>()

    while (queue.isNotEmpty()) {
        val knownPersonId = queue.removeFirst()
        if (!processed.add(knownPersonId)) continue
        val knownRect = positions[knownPersonId] ?: continue
        relationshipsByPerson[knownPersonId]
            .orEmpty()
            .sortedWith(
                compareBy<ExportRelationship> { if (it.type == "SPOUSE") 0 else 1 }
                    .thenBy {
                        if (it.type == "SPOUSE") {
                            partnershipHorizontalSlot(
                                personId = knownPersonId,
                                relationshipId = it.relationshipId,
                                index = relationshipIndex
                            )
                        } else 0
                    }
                    .thenBy { it.relationshipId }
            )
            .forEach { relationship ->
                val personId = relationship.otherPersonId(knownPersonId)
                if (personId !in missingPersonIds) return@forEach
                val partnershipSlot = if (relationship.type == "SPOUSE") {
                    partnershipHorizontalSlot(
                        personId = knownPersonId,
                        relationshipId = relationship.relationshipId,
                        index = relationshipIndex
                    )
                } else 0
                val proposed = when {
                    relationship.type == "SPOUSE" -> LineagePlacementRect(
                        x = knownRect.x + partnershipStep * partnershipSlot,
                        y = knownRect.y,
                        width = tileWidth,
                        height = tileHeight
                    )
                    relationship.fromPersonId == personId -> LineagePlacementRect(
                        x = knownRect.x,
                        y = knownRect.y - verticalStep,
                        width = tileWidth,
                        height = tileHeight
                    )
                    else -> LineagePlacementRect(
                        x = knownRect.x,
                        y = knownRect.y + verticalStep,
                        width = tileWidth,
                        height = tileHeight
                    )
                }
                addPerson(personId, proposed, partnershipSlot.compareTo(0))
                queue.addLast(personId)
            }
    }

    missingPersonIds.toList().sorted().forEach { personId ->
        val rightEdge = positions.values.maxOf { it.right }
        addPerson(
            personId = personId,
            proposed = LineagePlacementRect(
                x = rightEdge + siblingGap,
                y = fallbackY,
                width = tileWidth,
                height = tileHeight
            )
        )
    }
    return positions
}

private class PlacementCollisionIndex(
    tileWidth: Float,
    tileHeight: Float
) {
    private val bucketWidth = tileWidth + 12f
    private val bucketHeight = tileHeight + 12f
    private val buckets = mutableMapOf<Pair<Int, Int>, MutableList<LineagePlacementRect>>()

    fun add(rect: LineagePlacementRect) {
        bucketKeys(rect).forEach { key -> buckets.getOrPut(key) { mutableListOf() }.add(rect) }
    }

    fun overlaps(rect: LineagePlacementRect): Boolean = bucketKeys(rect)
        .asSequence()
        .flatMap { buckets[it].orEmpty().asSequence() }
        .distinct()
        .any { rect.overlaps(it) }

    private fun bucketKeys(rect: LineagePlacementRect): List<Pair<Int, Int>> {
        val minX = floor((rect.left - 10f) / bucketWidth).toInt()
        val maxX = floor((rect.right + 10f) / bucketWidth).toInt()
        val minY = floor((rect.top - 10f) / bucketHeight).toInt()
        val maxY = floor((rect.bottom + 10f) / bucketHeight).toInt()
        return buildList {
            for (x in minX..maxX) for (y in minY..maxY) add(x to y)
        }
    }
}

/**
 * Parent groups are derived only from explicit parent-child edges. Same-type
 * biological/adoptive pairs form a lineage family. STEP remains explicit and never
 * creates a partnership inference. A mixed pair is grouped only when exactly those
 * two recorded parents exist.
 */
internal fun recordedParentGroups(
    childId: String,
    index: LineageRelationshipIndex
): List<Set<String>> {
    val relationships = index.parentRelationships(childId)
        .distinctBy { it.fromPersonId to it.meta }
    if (relationships.isEmpty()) return emptyList()

    val groups = mutableListOf<Set<String>>()
    val covered = mutableSetOf<String>()
    listOf("BIOLOGICAL", "ADOPTIVE").forEach { type ->
        val ids = relationships
            .filter { it.meta == type }
            .map { it.fromPersonId }
            .distinct()
            .sorted()
        if (ids.size >= 2) {
            ids.chunked(2).forEach { pair ->
                if (pair.size == 2) {
                    groups += pair.toSet()
                    covered += pair
                }
            }
        }
    }

    val allParentIds = relationships.map { it.fromPersonId }.distinct().sorted()
    if (groups.isEmpty() && allParentIds.size == 2) {
        groups += allParentIds.toSet()
        covered += allParentIds
    }
    allParentIds.filterNot { it in covered }.forEach { groups += setOf(it) }
    return groups.distinct()
}

internal fun recordedChildrenForParentGroup(
    parentPersonIds: Set<String>,
    index: LineageRelationshipIndex
): List<String> {
    if (parentPersonIds.isEmpty()) return emptyList()
    return index.relationships
        .asSequence()
        .filter { it.type == "PARENT_CHILD" }
        .map { it.toPersonId }
        .distinct()
        .filter { childId -> parentPersonIds in recordedParentGroups(childId, index) }
        .toList()
}

private val partnershipChronologyComparator =
    compareBy<ExportRelationship> { partnershipChronologyDate(it) == null }
        .thenBy { partnershipChronologyDate(it).orEmpty() }
        .thenBy { it.createdAt.orEmpty() }
        .thenBy { it.relationshipId }

private fun partnershipChronologyDate(relationship: ExportRelationship): String? =
    relationship.startDate?.takeIf(String::isNotBlank)
        ?: relationship.endDate?.takeIf(String::isNotBlank)
        ?: relationship.createdAt.takeIf(String::isNotBlank)

internal fun ExportRelationship.otherPersonId(personId: String): String =
    if (fromPersonId == personId) toPersonId else fromPersonId
