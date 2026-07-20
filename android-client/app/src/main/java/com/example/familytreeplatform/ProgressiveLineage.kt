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

internal fun planProgressiveLineage(
    baseVisiblePersonIds: Set<String>,
    expandedParentPersonIds: Set<String>,
    expandedChildPersonIds: Set<String>,
    expandedPartnershipPersonIds: Set<String> = emptySet(),
    relationships: List<ExportRelationship>
): ProgressiveLineagePlan {
    if (baseVisiblePersonIds.isEmpty() || relationships.isEmpty()) {
        return ProgressiveLineagePlan(baseVisiblePersonIds, emptyList())
    }

    val index = LineageRelationshipIndex.from(relationships)
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
