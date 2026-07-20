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
    relationships: List<ExportRelationship>
): ProgressiveLineagePlan {
    if (baseVisiblePersonIds.isEmpty() || relationships.isEmpty()) {
        return ProgressiveLineagePlan(baseVisiblePersonIds, emptyList())
    }

    val parentRelationships = relationships.filter { it.type == "PARENT_CHILD" }
    val spouseRelationships = relationships.filter { it.type == "SPOUSE" }
    val parentsByChild = parentRelationships.groupBy { it.toPersonId }
    val childrenByParent = parentRelationships.groupBy { it.fromPersonId }
    val spousesByPerson = buildMap<String, List<ExportRelationship>> {
        spouseRelationships.forEach { relationship ->
            put(
                relationship.fromPersonId,
                get(relationship.fromPersonId).orEmpty() + relationship
            )
            put(
                relationship.toPersonId,
                get(relationship.toPersonId).orEmpty() + relationship
            )
        }
    }

    val visible = baseVisiblePersonIds.toMutableSet()
    var changed: Boolean
    do {
        changed = false
        val currentVisible = visible.toList()
        currentVisible.forEach { personId ->
            if (personId in expandedParentPersonIds) {
                parentsByChild[personId].orEmpty().forEach { relationship ->
                    changed = visible.add(relationship.fromPersonId) || changed
                }
            }

            if (personId in expandedChildPersonIds) {
                val childIds = childrenByParent[personId]
                    .orEmpty()
                    .map { it.toPersonId }
                    .toSet()
                childIds.forEach { childId ->
                    changed = visible.add(childId) || changed
                    // A child is attached to every recorded parent in this space. This
                    // reveals the co-parent card without inferring anyone from another room.
                    parentsByChild[childId].orEmpty().forEach { relationship ->
                        changed = visible.add(relationship.fromPersonId) || changed
                    }
                }

                spousesByPerson[personId]
                    .orEmpty()
                    .filter(::isCurrentPartnership)
                    .forEach { relationship ->
                        val spouseId = relationship.otherPersonId(personId)
                        changed = visible.add(spouseId) || changed
                    }
            }
        }
    } while (changed)

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
): Set<String> = relationships
    .asSequence()
    .filter { it.type == "PARENT_CHILD" && it.toPersonId == personId }
    .mapTo(mutableSetOf()) { it.fromPersonId }

internal fun recordedChildFamilyPersonIds(
    personId: String,
    relationships: List<ExportRelationship>
): Set<String> {
    val childIds = relationships
        .asSequence()
        .filter { it.type == "PARENT_CHILD" && it.fromPersonId == personId }
        .mapTo(mutableSetOf()) { it.toPersonId }
    if (childIds.isEmpty()) return emptySet()

    val familyIds = childIds.toMutableSet()
    relationships
        .asSequence()
        .filter { it.type == "PARENT_CHILD" && it.toPersonId in childIds }
        .mapTo(familyIds) { it.fromPersonId }
    relationships
        .asSequence()
        .filter {
            it.type == "SPOUSE" &&
                isCurrentPartnership(it) &&
                (it.fromPersonId == personId || it.toPersonId == personId)
        }
        .mapTo(familyIds) { it.otherPersonId(personId) }
    familyIds.remove(personId)
    return familyIds
}

private fun isCurrentPartnership(relationship: ExportRelationship): Boolean =
    relationship.meta == "MARRIED" && relationship.endDate == null

private fun ExportRelationship.otherPersonId(personId: String): String =
    if (fromPersonId == personId) toPersonId else fromPersonId
