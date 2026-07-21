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
    val centerX: Float get() = x + width / 2f

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
    val horizontalStep = tileWidth + siblingGap
    val partnershipStep = tileWidth + partnershipGap
    val verticalStep = tileHeight + rankGap
    val relationshipIndex = LineageRelationshipIndex.from(allRelationships)
    val visible = visiblePersonIds.toSet()
    val visiblePartnerships = visibleRelationships.filter {
        it.type == "SPOUSE" && it.fromPersonId in visible && it.toPersonId in visible
    }
    val components = PartnershipComponents(visible, visiblePartnerships)
    val units = components.personIdsByComponent.mapValues { (componentId, personIds) ->
        buildAtomicPlacementUnit(
            componentId = componentId,
            personIds = personIds,
            basePositions = basePositions,
            partnerships = visiblePartnerships,
            relationshipIndex = relationshipIndex,
            tileWidth = tileWidth,
            tileHeight = tileHeight,
            partnershipStep = partnershipStep
        )
    }
    val primaryUnitId = units.values
        .filter { unit -> unit.personIds.any(basePositions::containsKey) }
        .minWithOrNull(
            compareBy<AtomicPlacementUnit> {
                val y = it.baseOrigin?.y ?: Float.MAX_VALUE
                kotlin.math.abs(y - fallbackY)
            }.thenByDescending { unit -> unit.personIds.count(basePositions::containsKey) }
                .thenBy { it.baseOrigin?.x ?: Float.MAX_VALUE }
                .thenBy { it.id }
        )?.id
    val primaryCenterX = primaryUnitId?.let { units.getValue(it).proposedBounds().centerX }
        ?: basePositions.values.minOf { it.x }
    val placedOrigins = linkedMapOf<String, UnitOrigin>()
    val collisionIndex = PlacementCollisionIndex(tileWidth, tileHeight)

    fun placeUnit(
        unit: AtomicPlacementUnit,
        proposed: UnitOrigin,
        preferredDirection: Int = 0
    ): UnitOrigin {
        val attempts = visible.size + basePositions.size + 8
        repeat(attempts) { index ->
            val step = when {
                index == 0 -> 0
                preferredDirection != 0 -> index * preferredDirection
                index % 2 == 1 -> (index + 1) / 2
                else -> -(index / 2)
            }
            val candidate = proposed.shifted(horizontalStep * step)
            if (!collisionIndex.overlaps(unit.boundsAt(candidate))) {
                collisionIndex.add(unit.boundsAt(candidate))
                placedOrigins[unit.id] = candidate
                return candidate
            }
        }
        val rightEdge = placedOrigins.entries.maxOfOrNull { (id, origin) ->
            units.getValue(id).boundsAt(origin).right
        } ?: proposed.x
        return UnitOrigin(rightEdge + siblingGap - unit.minX, proposed.y).also {
            collisionIndex.add(unit.boundsAt(it))
            placedOrigins[unit.id] = it
        }
    }

    units.values
        .filter { it.baseOrigin != null }
        .sortedWith(
            compareByDescending<AtomicPlacementUnit> { it.id == primaryUnitId }
                .thenBy { kotlin.math.abs((it.baseOrigin?.y ?: fallbackY) - fallbackY) }
                .thenBy { it.baseOrigin?.y ?: fallbackY }
                .thenBy { it.baseOrigin?.x ?: 0f }
                .thenBy { it.id }
        )
        .forEach { unit ->
            val proposed = requireNotNull(unit.baseOrigin)
            val preferredDirection = unit.proposedBounds().centerX.compareTo(primaryCenterX)
            placeUnit(unit, proposed, preferredDirection)
        }

    val componentRelationships = visibleRelationships
        .filter { it.type == "PARENT_CHILD" }
        .mapNotNull { relationship ->
            val fromComponent = components.componentByPersonId[relationship.fromPersonId]
                ?: return@mapNotNull null
            val toComponent = components.componentByPersonId[relationship.toPersonId]
                ?: return@mapNotNull null
            if (fromComponent == toComponent) null else ComponentRelationship(
                relationship = relationship,
                fromComponentId = fromComponent,
                toComponentId = toComponent
            )
        }
    val relationshipsByComponent = buildMap<String, MutableList<ComponentRelationship>> {
        componentRelationships.forEach { relationship ->
            getOrPut(relationship.fromComponentId) { mutableListOf() }.add(relationship)
            getOrPut(relationship.toComponentId) { mutableListOf() }.add(relationship)
        }
    }
    val queue = ArrayDeque<String>().apply {
        placedOrigins.keys.forEach(::addLast)
    }
    val processed = mutableSetOf<String>()
    while (queue.isNotEmpty()) {
        val knownComponentId = queue.removeFirst()
        if (!processed.add(knownComponentId)) continue
        val knownUnit = units.getValue(knownComponentId)
        val knownOrigin = placedOrigins.getValue(knownComponentId)
        relationshipsByComponent[knownComponentId]
            .orEmpty()
            .sortedBy { it.relationship.relationshipId }
            .forEach { connection ->
                val nextComponentId = if (connection.fromComponentId == knownComponentId) {
                    connection.toComponentId
                } else connection.fromComponentId
                if (nextComponentId in placedOrigins) return@forEach
                val nextUnit = units.getValue(nextComponentId)
                val knownPersonId = if (connection.fromComponentId == knownComponentId) {
                    connection.relationship.fromPersonId
                } else connection.relationship.toPersonId
                val nextPersonId = connection.relationship.otherPersonId(knownPersonId)
                val knownRect = knownUnit.rectFor(knownPersonId, knownOrigin)
                val nextRelative = nextUnit.relativeRects.getValue(nextPersonId)
                val nextY = if (connection.fromComponentId == knownComponentId) {
                    knownRect.y + verticalStep - nextRelative.y
                } else {
                    knownRect.y - verticalStep - nextRelative.y
                }
                val proposed = UnitOrigin(
                    x = knownRect.centerX - nextRelative.centerX,
                    y = nextY
                )
                val placed = placeUnit(nextUnit, proposed)
                if (placedOrigins[nextComponentId] == placed) queue.addLast(nextComponentId)
            }
    }

    units.values.filterNot { it.id in placedOrigins }.sortedBy { it.id }.forEach { unit ->
        val rightEdge = placedOrigins.entries.maxOfOrNull { (id, origin) ->
            units.getValue(id).boundsAt(origin).right
        } ?: basePositions.values.maxOf { it.right }
        placeUnit(unit, UnitOrigin(rightEdge + siblingGap - unit.minX, fallbackY))
    }

    return buildMap {
        units.values.forEach { unit ->
            val origin = placedOrigins.getValue(unit.id)
            unit.personIds.forEach { personId -> put(personId, unit.rectFor(personId, origin)) }
        }
    }
}

private data class UnitOrigin(val x: Float, val y: Float) {
    fun shifted(dx: Float): UnitOrigin = copy(x = x + dx)
}

private data class ComponentRelationship(
    val relationship: ExportRelationship,
    val fromComponentId: String,
    val toComponentId: String
)

private data class AtomicPlacementUnit(
    val id: String,
    val personIds: Set<String>,
    val relativeRects: Map<String, LineagePlacementRect>,
    val baseOrigin: UnitOrigin?
) {
    val minX: Float = relativeRects.values.minOf { it.left }
    private val minY: Float = relativeRects.values.minOf { it.top }
    private val maxX: Float = relativeRects.values.maxOf { it.right }
    private val maxY: Float = relativeRects.values.maxOf { it.bottom }

    fun boundsAt(origin: UnitOrigin): LineagePlacementRect = LineagePlacementRect(
        x = origin.x + minX,
        y = origin.y + minY,
        width = maxX - minX,
        height = maxY - minY
    )

    fun proposedBounds(): LineagePlacementRect = boundsAt(baseOrigin ?: UnitOrigin(0f, 0f))

    fun rectFor(personId: String, origin: UnitOrigin): LineagePlacementRect =
        relativeRects.getValue(personId).shifted(origin.x, origin.y)
}

private fun buildAtomicPlacementUnit(
    componentId: String,
    personIds: Set<String>,
    basePositions: Map<String, LineagePlacementRect>,
    partnerships: List<ExportRelationship>,
    relationshipIndex: LineageRelationshipIndex,
    tileWidth: Float,
    tileHeight: Float,
    partnershipStep: Float
): AtomicPlacementUnit {
    val componentPartnerships = partnerships.filter {
        it.fromPersonId in personIds && it.toPersonId in personIds
    }
    val baseMembers = personIds.filter(basePositions::containsKey)
        .sortedWith(compareBy<String> { basePositions.getValue(it).x }.thenBy { it })
    val anchorId = baseMembers.firstOrNull()
        ?: personIds.maxWithOrNull(
            compareBy<String> { personId ->
                componentPartnerships.count {
                    it.fromPersonId == personId || it.toPersonId == personId
                }
            }.thenByDescending { it }
        )
        ?: componentId
    val anchorBase = basePositions[anchorId]
    val relativeRects = linkedMapOf<String, LineagePlacementRect>()
    if (anchorBase != null) {
        baseMembers.forEach { personId ->
            val rect = basePositions.getValue(personId)
            relativeRects[personId] = LineagePlacementRect(
                x = rect.x - anchorBase.x,
                y = rect.y - anchorBase.y,
                width = rect.width,
                height = rect.height
            )
        }
    } else {
        relativeRects[anchorId] = LineagePlacementRect(0f, 0f, tileWidth, tileHeight)
    }
    val occupiedSlots = relativeRects.values.mapTo(mutableSetOf()) {
        kotlin.math.round(it.x / partnershipStep).toInt()
    }
    val queue = ArrayDeque<String>().apply { addAll(relativeRects.keys) }
    val processed = mutableSetOf<String>()
    while (queue.isNotEmpty()) {
        val knownPersonId = queue.removeFirst()
        if (!processed.add(knownPersonId)) continue
        val knownRect = relativeRects.getValue(knownPersonId)
        componentPartnerships
            .filter { it.fromPersonId == knownPersonId || it.toPersonId == knownPersonId }
            .sortedWith(partnershipChronologyComparator)
            .forEach { relationship ->
                val partnerId = relationship.otherPersonId(knownPersonId)
                if (partnerId in relativeRects) return@forEach
                var slot = kotlin.math.round(knownRect.x / partnershipStep).toInt() +
                    partnershipHorizontalSlot(
                        personId = knownPersonId,
                        relationshipId = relationship.relationshipId,
                        index = relationshipIndex
                    )
                val direction = slot.compareTo(kotlin.math.round(knownRect.x / partnershipStep).toInt())
                    .takeIf { it != 0 } ?: 1
                while (slot in occupiedSlots) slot += direction
                occupiedSlots += slot
                relativeRects[partnerId] = LineagePlacementRect(
                    x = slot * partnershipStep,
                    y = knownRect.y,
                    width = tileWidth,
                    height = tileHeight
                )
                queue.addLast(partnerId)
            }
    }
    personIds.filterNot(relativeRects::containsKey).sorted().forEach { personId ->
        var slot = (occupiedSlots.maxOrNull() ?: -1) + 1
        while (slot in occupiedSlots) slot++
        occupiedSlots += slot
        relativeRects[personId] = LineagePlacementRect(
            x = slot * partnershipStep,
            y = 0f,
            width = tileWidth,
            height = tileHeight
        )
    }
    return AtomicPlacementUnit(
        id = componentId,
        personIds = personIds,
        relativeRects = relativeRects,
        baseOrigin = anchorBase?.let { UnitOrigin(it.x, it.y) }
    )
}

private class PartnershipComponents(
    personIds: Set<String>,
    partnerships: List<ExportRelationship>
) {
    private val parent = personIds.associateWith { it }.toMutableMap()

    private fun find(personId: String): String {
        val current = parent.getValue(personId)
        if (current == personId) return personId
        return find(current).also { parent[personId] = it }
    }

    private fun union(first: String, second: String) {
        val firstRoot = find(first)
        val secondRoot = find(second)
        if (firstRoot == secondRoot) return
        if (firstRoot < secondRoot) parent[secondRoot] = firstRoot else parent[firstRoot] = secondRoot
    }

    init {
        partnerships.forEach { union(it.fromPersonId, it.toPersonId) }
    }

    val componentByPersonId: Map<String, String> = personIds.associateWith(::find)
    val personIdsByComponent: Map<String, Set<String>> = componentByPersonId.entries
        .groupBy({ it.value }, { it.key })
        .mapValues { (_, ids) -> ids.toSet() }
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
