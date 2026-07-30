package com.example.familytreeplatform

import com.example.familytreeplatform.models.ExportRelationship
import kotlin.math.floor

private const val PartnershipPairKeySeparator = "\u001E"

internal fun partnershipPairKey(firstPersonId: String, secondPersonId: String): String =
    listOf(firstPersonId, secondPersonId)
        .sorted()
        .joinToString(PartnershipPairKeySeparator)

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
            val parentRelationships = relationships.filter {
                it.isLineageParentChild()
            }
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
 * relationships stay left of a single current relationship. When every recorded
 * relationship has ended, or several are simultaneously current, partners fan out
 * around the shared person so no partnership junction is hidden behind another card.
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
        val currentIndex = current
            .indexOfFirst { it.relationshipId == relationshipId }
            .coerceAtLeast(0)
        val rankFromLatest = current.lastIndex - currentIndex
        when {
            rankFromLatest == 0 -> 1
            rankFromLatest % 2 == 1 -> -((rankFromLatest + 1) / 2)
            else -> rankFromLatest / 2 + 1
        }
    } else if (current.isNotEmpty()) {
        val historicalIndex = historical
            .indexOfFirst { it.relationshipId == relationshipId }
            .coerceAtLeast(0)
        val occupiedCurrentLeftSlots = current.indices.count { currentIndex ->
            val rankFromLatest = current.lastIndex - currentIndex
            rankFromLatest % 2 == 1
        }
        -(occupiedCurrentLeftSlots + historical.size - historicalIndex)
    } else {
        val historicalIndex = historical
            .indexOfFirst { it.relationshipId == relationshipId }
            .coerceAtLeast(0)
        val rankFromLatest = historical.lastIndex - historicalIndex
        when {
            rankFromLatest == 0 -> 1
            rankFromLatest % 2 == 1 -> -((rankFromLatest + 1) / 2)
            else -> rankFromLatest / 2 + 1
        }
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
    fallbackY: Float,
    swappedPartnershipKeys: Set<String> = emptySet()
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
            partnershipStep = partnershipStep,
            swappedPartnershipKeys = swappedPartnershipKeys
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
                preferredDirection != 0 && index % 2 == 1 ->
                    ((index + 1) / 2) * preferredDirection
                preferredDirection != 0 -> -(index / 2) * preferredDirection
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
        .filter { it.isLineageParentChild() }
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

    val initialPlacements = buildMap {
        units.values.forEach { unit ->
            val origin = placedOrigins.getValue(unit.id)
            unit.personIds.forEach { personId -> put(personId, unit.rectFor(personId, origin)) }
        }
    }
    val refinedPlacements = refineFamilyBlockPlacements(
        initialPlacements = initialPlacements,
        basePositions = basePositions,
        components = components,
        componentRelationships = componentRelationships,
        primaryUnitId = primaryUnitId,
        relationshipIndex = relationshipIndex,
        siblingGap = siblingGap
    )
    val collisionResolvedPlacements = resolvePlacementCollisions(
        placements = refinedPlacements,
        components = components,
        primaryUnitId = primaryUnitId,
        horizontalStep = horizontalStep,
        siblingGap = siblingGap
    )
    val compactedPlacements = compactSingleChildAncestry(
        placements = collisionResolvedPlacements,
        components = components,
        componentRelationships = componentRelationships,
        primaryUnitId = primaryUnitId,
        siblingGap = siblingGap
    )
    return reserveLineageFamilyBlocks(
        placements = compactedPlacements,
        components = components,
        componentRelationships = componentRelationships,
        primaryUnitId = primaryUnitId,
        siblingGap = siblingGap
    )
}

/**
 * Reflows each recorded birth family as one horizontal block. The child branches
 * are measured using their complete visible descendant bounds, then packed with
 * one global card gap. A birth family connected to the primary partnership grows
 * away from that partnership, so the two biological families cannot interleave.
 */
private fun refineFamilyBlockPlacements(
    initialPlacements: Map<String, LineagePlacementRect>,
    basePositions: Map<String, LineagePlacementRect>,
    components: PartnershipComponents,
    componentRelationships: List<ComponentRelationship>,
    primaryUnitId: String?,
    relationshipIndex: LineageRelationshipIndex,
    siblingGap: Float
): Map<String, LineagePlacementRect> {
    if (
        primaryUnitId == null ||
        componentRelationships.isEmpty() ||
        initialPlacements.size > 512
    ) return initialPlacements

    val placements = initialPlacements.toMutableMap()
    val childComponentsByParent = componentRelationships
        .groupBy { it.fromComponentId }
        .mapValues { (_, relationships) ->
            relationships.map { it.toComponentId }.distinct()
        }

    fun componentBounds(componentIds: Set<String>): LineagePlacementRect {
        val rects = componentIds
            .flatMap { components.personIdsByComponent[it].orEmpty() }
            .mapNotNull(placements::get)
        return LineagePlacementRect(
            x = rects.minOf { it.left },
            y = rects.minOf { it.top },
            width = rects.maxOf { it.right } - rects.minOf { it.left },
            height = rects.maxOf { it.bottom } - rects.minOf { it.top }
        )
    }

    fun shiftComponents(componentIds: Set<String>, dx: Float) {
        componentIds
            .flatMap { components.personIdsByComponent[it].orEmpty() }
            .forEach { personId ->
                placements[personId] = placements.getValue(personId).shifted(dx)
            }
    }

    fun descendantBlock(startId: String, blockedParentId: String): Set<String> {
        if (startId == primaryUnitId) return setOf(startId)
        val result = linkedSetOf<String>()
        val queue = ArrayDeque<String>().apply { add(startId) }
        while (queue.isNotEmpty()) {
            val componentId = queue.removeFirst()
            if (
                componentId == blockedParentId ||
                componentId == primaryUnitId ||
                !result.add(componentId)
            ) continue
            childComponentsByParent[componentId].orEmpty().forEach(queue::addLast)
        }
        return result
    }

    val parentOrder = childComponentsByParent.keys.sortedWith(
        compareByDescending<String> { it == primaryUnitId }
            .thenByDescending { parentId ->
                primaryUnitId in childComponentsByParent[parentId].orEmpty()
            }
            .thenBy { parentId -> componentBounds(setOf(parentId)).top }
            .thenBy { it }
    )

    parentOrder.forEach { parentId ->
        val childIds = childComponentsByParent[parentId].orEmpty()
        if (childIds.size < 2) return@forEach
        val childBlocks = childIds.map { childId ->
            childId to descendantBlock(childId, parentId)
        }
        val componentSets = childBlocks.map { it.second }
        val occupiedMoreThanOnce = componentSets
            .flatMap { it }
            .groupingBy { it }
            .eachCount()
            .any { (_, count) -> count > 1 }
        if (occupiedMoreThanOnce) return@forEach

        val parentConnections = componentRelationships.filter {
            it.fromComponentId == parentId && it.toComponentId in childIds
        }
        val sourceRects = parentConnections
            .map { it.relationship.fromPersonId }
            .distinct()
            .mapNotNull(placements::get)
        if (sourceRects.isEmpty()) return@forEach
        val sourceCenterX = sourceRects.map { it.centerX }.average().toFloat()
        val primaryChild = childBlocks.firstOrNull { it.first == primaryUnitId }

        if (primaryChild == null) {
            data class OriginGroup(
                val sourceIds: Set<String>,
                val children: List<Pair<String, Set<String>>>,
                val sourceCenterX: Float,
                val width: Float
            )

            val connectionsByChild = parentConnections.groupBy { it.toComponentId }
            val originGroups = childBlocks
                .groupBy { (childId, _) ->
                    connectionsByChild[childId]
                        .orEmpty()
                        .mapTo(sortedSetOf()) { it.relationship.fromPersonId }
                }
                .map { (sourceIds, branches) ->
                    val orderedBranches = branches.sortedWith(
                        compareBy<Pair<String, Set<String>>> {
                            componentBounds(it.second).left
                        }.thenBy { it.first }
                    )
                    val branchWidths = orderedBranches.map {
                        componentBounds(it.second).width
                    }
                    val groupWidth = branchWidths.sum() +
                        siblingGap * (branchWidths.size - 1).coerceAtLeast(0)
                    val groupSourceRects = sourceIds.mapNotNull(placements::get)
                    OriginGroup(
                        sourceIds = sourceIds,
                        children = orderedBranches,
                        sourceCenterX = groupSourceRects
                            .map { it.centerX }
                            .average()
                            .toFloat()
                            .takeUnless(Float::isNaN) ?: sourceCenterX,
                        width = groupWidth
                    )
                }
                .sortedWith(
                    compareBy<OriginGroup> { it.sourceCenterX }
                        .thenBy { it.sourceIds.joinToString() }
                )

            val currentFamilyGroup = originGroups.firstOrNull { group ->
                group.sourceIds.size == 2 &&
                    relationshipIndex.relationships.any { relationship ->
                        isCurrentPartnership(relationship) &&
                            setOf(
                                relationship.fromPersonId,
                                relationship.toPersonId
                            ) == group.sourceIds
                    }
            }
            val familyAxisX = currentFamilyGroup?.sourceCenterX
                ?: originGroups
                    .takeIf { it.size > 1 }
                    ?.let { groups ->
                        (groups.first().sourceCenterX + groups.last().sourceCenterX) / 2f
                    }
                ?: originGroups.firstOrNull()?.sourceCenterX
                ?: componentBounds(setOf(parentId)).centerX
            fun groupComponents(group: OriginGroup): Set<String> =
                group.children.flatMapTo(linkedSetOf()) { it.second }

            originGroups.forEach { group ->
                var branchCursor = group.sourceCenterX - group.width / 2f
                group.children.forEach { (_, blockIds) ->
                    val bounds = componentBounds(blockIds)
                    shiftComponents(blockIds, branchCursor - bounds.left)
                    branchCursor += bounds.width + siblingGap
                }

                if (originGroups.size > 1 && group != currentFamilyGroup) {
                    val direction = group.sourceCenterX.compareTo(familyAxisX)
                    val anchorChild = when {
                        direction < 0 -> group.children.last()
                        direction > 0 -> group.children.first()
                        else -> null
                    }
                    if (anchorChild != null) {
                        val anchorPersonId = connectionsByChild[anchorChild.first]
                            .orEmpty()
                            .firstOrNull()
                            ?.relationship
                            ?.toPersonId
                        val anchorCenterX = anchorPersonId
                            ?.let(placements::get)
                            ?.centerX
                        if (anchorCenterX != null) {
                            shiftComponents(
                                groupComponents(group),
                                group.sourceCenterX - anchorCenterX
                            )
                        }
                    }
                }
            }

            if (currentFamilyGroup != null) {
                val currentBounds = componentBounds(groupComponents(currentFamilyGroup))
                var leftCursor = currentBounds.left - siblingGap
                originGroups
                    .filter { it.sourceCenterX < currentFamilyGroup.sourceCenterX }
                    .sortedByDescending { it.sourceCenterX }
                    .forEach { group ->
                        val groupIds = groupComponents(group)
                        var bounds = componentBounds(groupIds)
                        if (bounds.right > leftCursor) {
                            shiftComponents(groupIds, leftCursor - bounds.right)
                            bounds = componentBounds(groupIds)
                        }
                        leftCursor = bounds.left - siblingGap
                    }
                var rightCursor = currentBounds.right + siblingGap
                originGroups
                    .filter { it.sourceCenterX > currentFamilyGroup.sourceCenterX }
                    .sortedBy { it.sourceCenterX }
                    .forEach { group ->
                        val groupIds = groupComponents(group)
                        var bounds = componentBounds(groupIds)
                        if (bounds.left < rightCursor) {
                            shiftComponents(groupIds, rightCursor - bounds.left)
                            bounds = componentBounds(groupIds)
                        }
                        rightCursor = bounds.right + siblingGap
                    }
            }
            return@forEach
        }

        val targetPersonId = parentConnections
            .firstOrNull { it.toComponentId == primaryUnitId }
            ?.relationship
            ?.toPersonId
        val targetRect = targetPersonId?.let(placements::get) ?: return@forEach
        val primaryBounds = componentBounds(setOf(primaryUnitId))
        val direction = if (targetRect.centerX <= primaryBounds.centerX) -1 else 1
        val outwardBlocks = childBlocks
            .filterNot { it.first == primaryUnitId }
            .sortedWith(
                compareBy<Pair<String, Set<String>>> {
                    componentBounds(it.second).left
                }.thenBy { it.first }
            )

        if (direction < 0) {
            var cursor = targetRect.left - siblingGap
            outwardBlocks.asReversed().forEach { (_, blockIds) ->
                val bounds = componentBounds(blockIds)
                shiftComponents(blockIds, cursor - bounds.right)
                cursor -= bounds.width + siblingGap
            }
        } else {
            var cursor = targetRect.right + siblingGap
            outwardBlocks.forEach { (_, blockIds) ->
                val bounds = componentBounds(blockIds)
                shiftComponents(blockIds, cursor - bounds.left)
                cursor += bounds.width + siblingGap
            }
        }

        val familyRects = outwardBlocks.map { componentBounds(it.second) } + targetRect
        val familyCenterX = (
            familyRects.minOf { it.left } + familyRects.maxOf { it.right }
            ) / 2f
        val parentBounds = componentBounds(setOf(parentId))
        shiftComponents(setOf(parentId), familyCenterX - parentBounds.centerX)
    }

    // The primary card remains the stable visual focus even when base rows are reflowed.
    val primaryBasePerson = components.personIdsByComponent
        .getValue(primaryUnitId)
        .firstOrNull(basePositions::containsKey)
    if (primaryBasePerson != null) {
        val base = basePositions.getValue(primaryBasePerson)
        val placed = placements.getValue(primaryBasePerson)
        shiftComponents(setOf(primaryUnitId), base.x - placed.x)
    }
    return placements
}

/**
 * Family-block refinement can move complete descendant branches after the initial
 * spatial pass. Run one final deterministic pass over partnership components so
 * no card can overlap another card. Candidates alternate around the requested
 * position, preventing the old right-only drift on dense trees.
 */
private fun resolvePlacementCollisions(
    placements: Map<String, LineagePlacementRect>,
    components: PartnershipComponents,
    primaryUnitId: String?,
    horizontalStep: Float,
    siblingGap: Float
): Map<String, LineagePlacementRect> {
    if (placements.size < 2 || placements.size > 512) return placements
    val result = placements.toMutableMap()

    fun bounds(componentId: String): LineagePlacementRect {
        val rects = components.personIdsByComponent
            .getValue(componentId)
            .mapNotNull(result::get)
        return LineagePlacementRect(
            x = rects.minOf { it.left },
            y = rects.minOf { it.top },
            width = rects.maxOf { it.right } - rects.minOf { it.left },
            height = rects.maxOf { it.bottom } - rects.minOf { it.top }
        )
    }

    fun shift(componentId: String, dx: Float) {
        components.personIdsByComponent.getValue(componentId).forEach { personId ->
            result[personId] = result.getValue(personId).shifted(dx)
        }
    }

    val primaryCenterX = primaryUnitId?.let { bounds(it).centerX }
        ?: placements.values.map { it.centerX }.average().toFloat()
    val occupied = mutableListOf<LineagePlacementRect>()
    components.personIdsByComponent.keys
        .filter { componentId ->
            components.personIdsByComponent.getValue(componentId).any(placements::containsKey)
        }
        .sortedWith(
            compareByDescending<String> { it == primaryUnitId }
                .thenBy { kotlin.math.abs(bounds(it).centerX - primaryCenterX) }
                .thenBy { bounds(it).top }
                .thenBy { it }
        )
        .forEach { componentId ->
            val proposed = bounds(componentId)
            val preferredDirection = proposed.centerX.compareTo(primaryCenterX).takeIf { it != 0 } ?: 1
            val attempts = placements.size + 8
            val candidate = (0 until attempts).firstNotNullOfOrNull { index ->
                val step = when {
                    index == 0 -> 0
                    index % 2 == 1 -> ((index + 1) / 2) * preferredDirection
                    else -> -(index / 2) * preferredDirection
                }
                proposed.shifted(horizontalStep * step).takeIf { rect ->
                    occupied.none { rect.overlaps(it, padding = siblingGap) }
                }
            } ?: run {
                val rightEdge = occupied.maxOfOrNull { it.right } ?: proposed.left
                proposed.shifted(rightEdge + siblingGap - proposed.left)
            }
            shift(componentId, candidate.left - proposed.left)
            occupied += candidate
        }
    return result
}

/**
 * A visible parent family with only one visible child does not need to reserve
 * horizontal room for hypothetical relatives. Move its complete ancestry side
 * toward that child's own card. Cutting the connecting lineage edge before the
 * move keeps the child's partnership and descendants stable; if an in-law block
 * already occupies the ideal position, boundary candidates choose the nearest
 * collision-free gap.
 */
private fun compactSingleChildAncestry(
    placements: Map<String, LineagePlacementRect>,
    components: PartnershipComponents,
    componentRelationships: List<ComponentRelationship>,
    primaryUnitId: String?,
    siblingGap: Float
): Map<String, LineagePlacementRect> {
    if (componentRelationships.isEmpty() || placements.size > 512) return placements
    val result = placements.toMutableMap()
    val outgoingChildrenBySource = componentRelationships
        .groupBy { it.fromComponentId }
        .mapValues { (_, connections) ->
            connections.mapTo(linkedSetOf()) { it.toComponentId }
        }
    val adjacency = buildMap<String, MutableSet<String>> {
        componentRelationships.forEach { connection ->
            getOrPut(connection.fromComponentId) { linkedSetOf() }
                .add(connection.toComponentId)
            getOrPut(connection.toComponentId) { linkedSetOf() }
                .add(connection.fromComponentId)
        }
    }

    data class SingleChildAnchor(
        val sourceComponentId: String,
        val childComponentId: String,
        val childPersonId: String,
        val sourcePersonIds: Set<String>
    )

    val anchors = componentRelationships
        .groupBy {
            Triple(it.fromComponentId, it.toComponentId, it.relationship.toPersonId)
        }
        .mapNotNull { (key, connections) ->
            val (sourceComponentId, childComponentId, childPersonId) = key
            if (outgoingChildrenBySource[sourceComponentId]?.size != 1) {
                return@mapNotNull null
            }
            SingleChildAnchor(
                sourceComponentId = sourceComponentId,
                childComponentId = childComponentId,
                childPersonId = childPersonId,
                sourcePersonIds = connections
                    .mapTo(linkedSetOf()) { it.relationship.fromPersonId }
            )
        }
        .sortedWith(
            compareByDescending<SingleChildAnchor> {
                result[it.childPersonId]?.top ?: Float.MIN_VALUE
            }.thenBy { it.sourceComponentId }
                .thenBy { it.childComponentId }
                .thenBy { it.childPersonId }
        )

    fun ancestrySide(anchor: SingleChildAnchor): Set<String> {
        val side = linkedSetOf<String>()
        val queue = ArrayDeque<String>().apply { add(anchor.sourceComponentId) }
        while (queue.isNotEmpty()) {
            val componentId = queue.removeFirst()
            if (componentId == anchor.childComponentId || !side.add(componentId)) continue
            adjacency[componentId].orEmpty().forEach { adjacentId ->
                if (adjacentId != anchor.childComponentId) queue.addLast(adjacentId)
            }
        }
        return side
    }

    fun verticalRangesConflict(
        first: LineagePlacementRect,
        second: LineagePlacementRect
    ): Boolean = first.top < second.bottom + siblingGap &&
        first.bottom + siblingGap > second.top

    anchors.forEach { anchor ->
        val movingComponents = ancestrySide(anchor)
        if (
            movingComponents.isEmpty() ||
            primaryUnitId in movingComponents
        ) return@forEach
        val movingPersonIds = movingComponents
            .flatMapTo(linkedSetOf()) { components.personIdsByComponent[it].orEmpty() }
            .filterTo(linkedSetOf()) { it in result }
        val stationaryPersonIds = result.keys - movingPersonIds
        val sourceRects = anchor.sourcePersonIds.mapNotNull(result::get)
        val childRect = result[anchor.childPersonId]
        if (
            movingPersonIds.isEmpty() ||
            stationaryPersonIds.isEmpty() ||
            sourceRects.isEmpty() ||
            childRect == null
        ) return@forEach

        val sourceCenterX = sourceRects.map { it.centerX }.average().toFloat()
        val desiredDx = childRect.centerX - sourceCenterX
        if (kotlin.math.abs(desiredDx) < 0.01f) return@forEach

        val movingRects = movingPersonIds.map(result::getValue)
        val stationaryRects = stationaryPersonIds.map(result::getValue)
        val candidateShifts = linkedSetOf(0f, desiredDx)
        movingRects.forEach { movingRect ->
            stationaryRects
                .filter { stationaryRect ->
                    verticalRangesConflict(movingRect, stationaryRect)
                }
                .forEach { stationaryRect ->
                    candidateShifts += stationaryRect.left - siblingGap - movingRect.right
                    candidateShifts += stationaryRect.right + siblingGap - movingRect.left
                }
        }
        val bestDx = candidateShifts
            .asSequence()
            .filter { dx ->
                movingRects.none { movingRect ->
                    stationaryRects.any { stationaryRect ->
                        movingRect.shifted(dx).overlaps(
                            stationaryRect,
                            padding = siblingGap
                        )
                    }
                }
            }
            .minWithOrNull(
                compareBy<Float> { dx -> kotlin.math.abs(desiredDx - dx) }
                    .thenBy { dx -> kotlin.math.abs(dx) }
                    .thenBy { it }
            )
            ?: return@forEach
        if (
            kotlin.math.abs(desiredDx - bestDx) + 0.01f >=
            kotlin.math.abs(desiredDx)
        ) return@forEach

        movingPersonIds.forEach { personId ->
            result[personId] = result.getValue(personId).shifted(bestDx)
        }
    }
    return result
}

/**
 * Reserves one horizontal interval for each disjoint visible birth-family block.
 *
 * Earlier passes optimize individual partnerships and sibling groups. On a fully
 * expanded tree, two otherwise collision-free branches can still occupy the same
 * lineage corridor. This final bottom-up pass treats a source partnership and all
 * of its visible descendants as one movable block. Blocks on the same generation
 * are pushed to the nearest free horizontal interval without changing their Y
 * coordinates, so lineage lines remain on one level inside the generation gap.
 *
 * Blocks whose descendants meet again are merged before packing. This preserves
 * the one-card-per-person invariant and avoids pulling a shared descendant in two
 * directions.
 */
private fun reserveLineageFamilyBlocks(
    placements: Map<String, LineagePlacementRect>,
    components: PartnershipComponents,
    componentRelationships: List<ComponentRelationship>,
    primaryUnitId: String?,
    siblingGap: Float
): Map<String, LineagePlacementRect> {
    if (componentRelationships.isEmpty() || placements.size > 800) return placements
    val result = placements.toMutableMap()
    val childrenBySource = componentRelationships
        .groupBy { it.fromComponentId }
        .mapValues { (_, connections) ->
            connections.mapTo(linkedSetOf()) { it.toComponentId }
        }

    fun descendantsIncluding(sourceId: String): Set<String> {
        val descendants = linkedSetOf<String>()
        val queue = ArrayDeque<String>().apply { add(sourceId) }
        while (queue.isNotEmpty()) {
            val componentId = queue.removeFirst()
            if (!descendants.add(componentId)) continue
            childrenBySource[componentId].orEmpty().forEach(queue::addLast)
        }
        return descendants
    }

    fun personIds(componentIds: Set<String>): Set<String> =
        componentIds.flatMapTo(linkedSetOf()) {
            components.personIdsByComponent[it].orEmpty()
        }.filterTo(linkedSetOf(), result::containsKey)

    fun bounds(componentIds: Set<String>): LineagePlacementRect {
        val rects = personIds(componentIds).map(result::getValue)
        return LineagePlacementRect(
            x = rects.minOf { it.left },
            y = rects.minOf { it.top },
            width = rects.maxOf { it.right } - rects.minOf { it.left },
            height = rects.maxOf { it.bottom } - rects.minOf { it.top }
        )
    }

    fun shift(componentIds: Set<String>, dx: Float) {
        if (kotlin.math.abs(dx) < 0.01f) return
        personIds(componentIds).forEach { personId ->
            result[personId] = result.getValue(personId).shifted(dx)
        }
    }

    data class FamilyBlock(
        val sourceIds: Set<String>,
        val componentIds: Set<String>
    )

    val blocksByGeneration = childrenBySource.keys
        .mapNotNull { sourceId ->
            val sourceRects = components.personIdsByComponent[sourceId]
                .orEmpty()
                .mapNotNull(result::get)
            if (sourceRects.isEmpty()) null else {
                val generationKey = kotlin.math.round(
                    sourceRects.minOf { it.top } * 10f
                ).toInt()
                generationKey to FamilyBlock(
                    sourceIds = setOf(sourceId),
                    componentIds = descendantsIncluding(sourceId)
                )
            }
        }
        .groupBy({ it.first }, { it.second })

    blocksByGeneration.keys.sortedDescending().forEach { generationKey ->
        val merged = mutableListOf<FamilyBlock>()
        blocksByGeneration.getValue(generationKey).forEach { block ->
            val touchingIndexes = merged.indices.filter { index ->
                merged[index].componentIds.any(block.componentIds::contains)
            }
            if (touchingIndexes.isEmpty()) {
                merged += block
            } else {
                val touching = touchingIndexes.map(merged::get)
                touchingIndexes.asReversed().forEach(merged::removeAt)
                merged += FamilyBlock(
                    sourceIds = touching
                        .flatMapTo(linkedSetOf()) { it.sourceIds }
                        .apply { addAll(block.sourceIds) },
                    componentIds = touching
                        .flatMapTo(linkedSetOf()) { it.componentIds }
                        .apply { addAll(block.componentIds) }
                )
            }
        }
        if (merged.size < 2) return@forEach

        fun sourceCenter(block: FamilyBlock): Float {
            val sourceRects = personIds(block.sourceIds).map(result::getValue)
            return sourceRects.map { it.centerX }.average().toFloat()
        }
        val ordered = merged.sortedWith(
            compareBy<FamilyBlock>(::sourceCenter)
                .thenBy { it.sourceIds.sorted().joinToString() }
        )
        val hasOverlap = ordered.zipWithNext().any { (left, right) ->
            bounds(left.componentIds).right + siblingGap >
                bounds(right.componentIds).left
        }
        if (!hasOverlap) return@forEach

        val anchorIndex = ordered.indexOfFirst { primaryUnitId in it.componentIds }
        if (anchorIndex >= 0) {
            val anchorBounds = bounds(ordered[anchorIndex].componentIds)
            var leftCursor = anchorBounds.left - siblingGap
            ordered.subList(0, anchorIndex).asReversed().forEach { block ->
                val current = bounds(block.componentIds)
                if (current.right > leftCursor) {
                    shift(block.componentIds, leftCursor - current.right)
                }
                leftCursor = bounds(block.componentIds).left - siblingGap
            }
            var rightCursor = anchorBounds.right + siblingGap
            ordered.subList(anchorIndex + 1, ordered.size).forEach { block ->
                val current = bounds(block.componentIds)
                if (current.left < rightCursor) {
                    shift(block.componentIds, rightCursor - current.left)
                }
                rightCursor = bounds(block.componentIds).right + siblingGap
            }
        } else {
            val originalLeft = ordered.minOf { bounds(it.componentIds).left }
            val originalRight = ordered.maxOf { bounds(it.componentIds).right }
            var cursor = bounds(ordered.first().componentIds).right + siblingGap
            ordered.drop(1).forEach { block ->
                val current = bounds(block.componentIds)
                if (current.left < cursor) {
                    shift(block.componentIds, cursor - current.left)
                }
                cursor = bounds(block.componentIds).right + siblingGap
            }
            val packedLeft = ordered.minOf { bounds(it.componentIds).left }
            val packedRight = ordered.maxOf { bounds(it.componentIds).right }
            val centerCorrection =
                (originalLeft + originalRight - packedLeft - packedRight) / 2f
            ordered.forEach { shift(it.componentIds, centerCorrection) }
        }
    }
    return result
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
    partnershipStep: Float,
    swappedPartnershipKeys: Set<String>
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
            .sortedWith(
                compareByDescending<ExportRelationship>(::isCurrentPartnership)
                    .then(partnershipChronologyComparator)
            )
            .forEach { relationship ->
                val partnerId = relationship.otherPersonId(knownPersonId)
                if (partnerId in relativeRects) return@forEach
                val knownSlot = kotlin.math.round(knownRect.x / partnershipStep).toInt()
                var relativeSlot = partnershipHorizontalSlot(
                        personId = knownPersonId,
                        relationshipId = relationship.relationshipId,
                        index = relationshipIndex
                    )
                if (!isCurrentPartnership(relationship)) {
                    val currentPartnerRect = relationshipIndex
                        .partnerships(knownPersonId)
                        .filter(::isCurrentPartnership)
                        .lastOrNull()
                        ?.otherPersonId(knownPersonId)
                        ?.let(relativeRects::get)
                    if (currentPartnerRect != null) {
                        val currentPartnerSlot =
                            kotlin.math.round(currentPartnerRect.x / partnershipStep).toInt()
                        val outwardDirection = knownSlot
                            .compareTo(currentPartnerSlot)
                            .takeIf { it != 0 }
                        if (outwardDirection != null) {
                            relativeSlot = kotlin.math.abs(relativeSlot) * outwardDirection
                        }
                    }
                }
                if (
                    partnershipPairKey(
                        relationship.fromPersonId,
                        relationship.toPersonId
                    ) in swappedPartnershipKeys
                ) {
                    relativeSlot = -relativeSlot
                }
                var slot = knownSlot + relativeSlot
                val direction = slot.compareTo(knownSlot)
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
    val firstParentId = parentPersonIds.first()
    return index.childRelationships(firstParentId)
        .asSequence()
        .map { it.toPersonId }
        .distinct()
        .filter { childId -> parentPersonIds in recordedParentGroups(childId, index) }
        .toList()
}

/**
 * Direct children of the focused person remain visible even when they belong to
 * different recorded partnerships. Exact co-parent groups are preserved for
 * connector rendering; this function only decides which child cards are visible.
 */
internal fun recordedChildrenForPrimaryFamily(
    primaryPersonId: String,
    visibleParentPersonIds: Set<String>,
    index: LineageRelationshipIndex
): List<String> = index.relationships
    .asSequence()
    .filter { it.isLineageParentChild() }
    .map { it.toPersonId }
    .distinct()
    .filter { childId ->
        recordedParentGroups(childId, index).any { recordedGroup ->
            recordedGroup == visibleParentPersonIds ||
                (recordedGroup.size == 1 && recordedGroup.first() in visibleParentPersonIds) ||
                primaryPersonId in recordedGroup
        }
    }
    .toList()

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
