package com.example.familytreeplatform

import com.example.familytreeplatform.models.ExportRelationship

/**
 * The graph only expands relationships already delivered for the active Family Space.
 * Missing cross-space branches therefore cannot create controls, counts, or placeholders.
 */
internal data class ProgressiveLineagePlan(
    val visiblePersonIds: Set<String>,
    val visibleRelationships: List<ExportRelationship>
)

internal data class ProgressiveLineageExpansionState(
    val parentPersonIds: Set<String> = emptySet(),
    val childFamilyKeys: Set<String> = emptySet(),
    val partnershipPersonIds: Set<String> = emptySet()
)

internal data class RecordedChildFamily(
    val key: String,
    val parentPersonIds: Set<String>,
    val childPersonIds: List<String>
)

private const val ChildFamilyKeySeparator = "\u001F"

internal fun childFamilyBranchKey(parentPersonIds: Set<String>): String =
    parentPersonIds.sorted().joinToString(ChildFamilyKeySeparator)

private fun childFamilyParentPersonIds(key: String): Set<String> =
    key.split(ChildFamilyKeySeparator)
        .filterTo(linkedSetOf()) { it.isNotBlank() }

internal fun recordedChildFamilies(
    personId: String,
    index: LineageRelationshipIndex
): List<RecordedChildFamily> = index.childRelationships(personId)
    .map { it.toPersonId }
    .distinct()
    .flatMap { childId ->
        recordedParentGroups(childId, index)
            .filter { personId in it }
    }
    .distinct()
    .map { parentIds ->
        RecordedChildFamily(
            key = childFamilyBranchKey(parentIds),
            parentPersonIds = parentIds,
            childPersonIds = recordedChildrenForParentGroup(parentIds, index)
        )
    }
    .filter { it.childPersonIds.isNotEmpty() }
    .sortedBy { it.key }

internal fun planProgressiveLineage(
    baseVisiblePersonIds: Set<String>,
    expandedParentPersonIds: Set<String>,
    expandedChildPersonIds: Set<String>,
    expandedPartnershipPersonIds: Set<String> = emptySet(),
    expandedChildFamilyKeys: Set<String> = emptySet(),
    relationships: List<ExportRelationship>
): ProgressiveLineagePlan {
    if (baseVisiblePersonIds.isEmpty() || relationships.isEmpty()) {
        return ProgressiveLineagePlan(baseVisiblePersonIds, emptyList())
    }

    val index = LineageRelationshipIndex.from(relationships)
    val expandedChildFamiliesByPerson = if (expandedChildFamilyKeys.isEmpty()) {
        emptyMap()
    } else {
        expandedChildFamilyKeys
            .asSequence()
            .mapNotNull { key ->
                val parentPersonIds = childFamilyParentPersonIds(key)
                val childPersonIds =
                    recordedChildrenForParentGroup(parentPersonIds, index)
                RecordedChildFamily(key, parentPersonIds, childPersonIds)
                    .takeIf {
                        it.parentPersonIds.isNotEmpty() &&
                            it.childPersonIds.isNotEmpty()
                    }
            }
            .flatMap { family ->
                family.parentPersonIds.asSequence().map { parentId -> parentId to family }
            }
            .groupBy({ it.first }, { it.second })
    }
    val visible = linkedSetOf<String>().apply { addAll(baseVisiblePersonIds) }
    val queue = ArrayDeque<String>().apply { addAll(baseVisiblePersonIds) }

    fun reveal(personId: String) {
        if (visible.add(personId)) queue.addLast(personId)
    }

    while (queue.isNotEmpty()) {
        val personId = queue.removeFirst()
        if (personId in expandedParentPersonIds) {
            index.parentRelationships(personId).forEach { reveal(it.fromPersonId) }
        }

        if (personId in expandedChildPersonIds) {
            index.childRelationships(personId)
                .map { it.toPersonId }
                .distinct()
                .forEach { childId ->
                    reveal(childId)
                    // Reveal every explicitly recorded co-parent, regardless of parentage
                    // type, without inferring a partnership or another Family Space.
                    index.parentRelationships(childId).forEach { reveal(it.fromPersonId) }
                }
            index.partnerships(personId)
                .filter(::isCurrentPartnership)
                .forEach { reveal(it.otherPersonId(personId)) }
        }

        expandedChildFamiliesByPerson[personId].orEmpty().forEach { family ->
            family.parentPersonIds.forEach(::reveal)
            family.childPersonIds.forEach(::reveal)
        }

        if (personId in expandedPartnershipPersonIds) {
            index.partnerships(personId).forEach { reveal(it.otherPersonId(personId)) }
        }
    }

    return ProgressiveLineagePlan(
        visiblePersonIds = visible,
        visibleRelationships = relationships.filter {
            it.fromPersonId in visible && it.toPersonId in visible
        }
    )
}

/**
 * Removes expansion state owned by cards that disappeared after a branch toggle.
 * This makes reopening the branch start at one generation instead of restoring
 * hidden grandchildren or partner branches from a previous exploration.
 */
internal fun pruneHiddenLineageExpansions(
    beforeBaseVisiblePersonIds: Set<String>,
    afterBaseVisiblePersonIds: Set<String> = beforeBaseVisiblePersonIds,
    before: ProgressiveLineageExpansionState,
    afterRootToggle: ProgressiveLineageExpansionState,
    relationships: List<ExportRelationship>
): ProgressiveLineageExpansionState {
    if (relationships.isEmpty()) return afterRootToggle
    val beforePlan = planProgressiveLineage(
        baseVisiblePersonIds = beforeBaseVisiblePersonIds,
        expandedParentPersonIds = before.parentPersonIds,
        expandedChildPersonIds = emptySet(),
        expandedPartnershipPersonIds = before.partnershipPersonIds,
        expandedChildFamilyKeys = before.childFamilyKeys,
        relationships = relationships
    )
    val afterPlan = planProgressiveLineage(
        baseVisiblePersonIds = afterBaseVisiblePersonIds,
        expandedParentPersonIds = afterRootToggle.parentPersonIds,
        expandedChildPersonIds = emptySet(),
        expandedPartnershipPersonIds = afterRootToggle.partnershipPersonIds,
        expandedChildFamilyKeys = afterRootToggle.childFamilyKeys,
        relationships = relationships
    )
    val hiddenPersonIds = beforePlan.visiblePersonIds - afterPlan.visiblePersonIds
    if (hiddenPersonIds.isEmpty()) return afterRootToggle

    return afterRootToggle.copy(
        parentPersonIds = afterRootToggle.parentPersonIds - hiddenPersonIds,
        childFamilyKeys = afterRootToggle.childFamilyKeys.filterTo(linkedSetOf()) { key ->
            childFamilyParentPersonIds(key).none { it in hiddenPersonIds }
        },
        partnershipPersonIds =
            afterRootToggle.partnershipPersonIds - hiddenPersonIds
    )
}

internal fun hasRecordedParents(
    personId: String,
    relationships: List<ExportRelationship>
): Boolean = recordedParentPersonIds(personId, relationships).isNotEmpty()

internal fun hasRecordedChildren(
    personId: String,
    relationships: List<ExportRelationship>
): Boolean = recordedChildFamilyPersonIds(personId, relationships).isNotEmpty()

internal fun recordedParentPersonIds(
    personId: String,
    relationships: List<ExportRelationship>
): Set<String> = LineageRelationshipIndex.from(relationships)
    .recordedParentPersonIds(personId)

internal fun recordedChildFamilyPersonIds(
    personId: String,
    relationships: List<ExportRelationship>
): Set<String> = LineageRelationshipIndex.from(relationships)
    .recordedChildFamilyPersonIds(personId)
