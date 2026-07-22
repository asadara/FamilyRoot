package com.example.familytreeplatform.feature.graph

import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem

internal data class RelationshipIntegrityConflict(
    val id: String,
    val title: String,
    val detail: String,
    val recommendation: String,
    val relationshipIds: Set<String>,
    val recommendedRelationshipId: String
)

internal fun detectRelationshipIntegrityConflicts(
    persons: List<PersonListItem>,
    relationships: List<ExportRelationship>
): List<RelationshipIntegrityConflict> {
    val names = persons.associate { it.personId to it.fullName }
    val conflicts = mutableListOf<RelationshipIntegrityConflict>()
    val relationshipsByPair = relationships.groupBy {
        canonicalPair(it.fromPersonId, it.toPersonId)
    }

    relationshipsByPair.forEach { (pair, pairRelationships) ->
        val partnerships = pairRelationships.filter { it.type == "SPOUSE" }
        val parentage = pairRelationships.filter { it.type == "PARENT_CHILD" }
        if (partnerships.isEmpty() || parentage.isEmpty()) return@forEach

        val recommended = (partnerships + parentage).maxWithOrNull(
            compareBy<ExportRelationship> { it.createdAt }
                .thenBy { it.relationshipId }
        ) ?: return@forEach
        val firstName = names[pair.first] ?: "Person pertama"
        val secondName = names[pair.second] ?: "Person kedua"
        val recommendedLabel = relationshipRecommendationLabel(recommended, names)
        conflicts += RelationshipIntegrityConflict(
            id = "partner-parent:${pair.first}:${pair.second}",
            title = "$firstName dan $secondName memiliki dua jenis hubungan",
            detail = "Keduanya tercatat sebagai pasangan sekaligus orang tua-anak. " +
                "Kombinasi ini membuat garis dan tingkat generasi menjadi rancu.",
            recommendation = "Rekomendasi: hapus $recommendedLabel karena hubungan ini " +
                "ditambahkan paling akhir.",
            relationshipIds = (partnerships + parentage)
                .mapTo(mutableSetOf()) { it.relationshipId },
            recommendedRelationshipId = recommended.relationshipId
        )
    }

    relationships
        .filter { it.type == "PARENT_CHILD" && it.meta == "BIOLOGICAL" }
        .groupBy { it.toPersonId }
        .filterValues { it.size > 2 }
        .forEach { (childId, parentRelationships) ->
            val recommended = parentRelationships.maxWithOrNull(
                compareBy<ExportRelationship> { it.createdAt }
                    .thenBy { it.relationshipId }
            ) ?: return@forEach
            val childName = names[childId] ?: "Person ini"
            conflicts += RelationshipIntegrityConflict(
                id = "biological-parents:$childId",
                title = "$childName memiliki lebih dari dua orang tua biologis",
                detail = "Sistem menemukan ${parentRelationships.size} hubungan orang tua biologis.",
                recommendation = "Rekomendasi: hapus ${relationshipRecommendationLabel(recommended, names)} " +
                    "karena hubungan ini ditambahkan paling akhir.",
                relationshipIds = parentRelationships.mapTo(mutableSetOf()) { it.relationshipId },
                recommendedRelationshipId = recommended.relationshipId
            )
        }

    return conflicts.sortedBy { it.title }
}

internal fun validateProposedRelationship(
    sourcePersonId: String,
    targetPersonId: String,
    kind: ExistingRelationKind,
    meta: String,
    relationships: List<ExportRelationship>
): String? {
    if (sourcePersonId == targetPersonId) return "Person tidak dapat dihubungkan dengan dirinya sendiri."
    val parentId = when (kind) {
        ExistingRelationKind.TARGET_PARENT -> targetPersonId
        ExistingRelationKind.TARGET_CHILD -> sourcePersonId
        ExistingRelationKind.PARTNER -> null
    }
    val childId = when (kind) {
        ExistingRelationKind.TARGET_PARENT -> sourcePersonId
        ExistingRelationKind.TARGET_CHILD -> targetPersonId
        ExistingRelationKind.PARTNER -> null
    }

    if (kind == ExistingRelationKind.PARTNER) {
        if (relationships.any {
                it.type == "SPOUSE" &&
                    canonicalPair(it.fromPersonId, it.toPersonId) ==
                    canonicalPair(sourcePersonId, targetPersonId)
            }
        ) return "Kedua person sudah tercatat sebagai pasangan."
        if (
            hasDescendantPath(sourcePersonId, targetPersonId, relationships) ||
            hasDescendantPath(targetPersonId, sourcePersonId, relationships)
        ) {
            return "Hubungan pasangan tidak dapat dibuat karena salah satu person berada pada jalur leluhur yang lain."
        }
        return null
    }

    requireNotNull(parentId)
    requireNotNull(childId)
    if (relationships.any {
            it.type == "SPOUSE" &&
                canonicalPair(it.fromPersonId, it.toPersonId) == canonicalPair(parentId, childId)
        }
    ) return "Hubungan orang tua-anak tidak dapat dibuat karena kedua person sudah tercatat sebagai pasangan."
    if (relationships.any {
            it.type == "PARENT_CHILD" &&
                it.fromPersonId == parentId && it.toPersonId == childId
        }
    ) return "Hubungan orang tua-anak tersebut sudah tercatat."
    if (hasDescendantPath(childId, parentId, relationships)) {
        return "Hubungan ini membentuk lingkaran keturunan dan tidak dapat disimpan."
    }
    if (meta == "BIOLOGICAL") {
        val biologicalParentCount = relationships.count {
            it.type == "PARENT_CHILD" && it.toPersonId == childId && it.meta == "BIOLOGICAL"
        }
        if (biologicalParentCount >= 2) {
            return "Person ini sudah memiliki dua orang tua biologis. Gunakan hubungan adopsi atau tiri bila sesuai."
        }
    }
    return null
}

private fun hasDescendantPath(
    ancestorId: String,
    descendantId: String,
    relationships: List<ExportRelationship>
): Boolean {
    val childrenByParent = relationships
        .asSequence()
        .filter { it.type == "PARENT_CHILD" }
        .groupBy({ it.fromPersonId }, { it.toPersonId })
    val queue = ArrayDeque<String>().apply { add(ancestorId) }
    val visited = mutableSetOf(ancestorId)
    while (queue.isNotEmpty()) {
        childrenByParent[queue.removeFirst()].orEmpty().forEach { childId ->
            if (childId == descendantId) return true
            if (visited.add(childId)) queue.add(childId)
        }
    }
    return false
}

private fun relationshipRecommendationLabel(
    relationship: ExportRelationship,
    names: Map<String, String>
): String = when (relationship.type) {
    "SPOUSE" -> "hubungan pasangan ${names[relationship.fromPersonId] ?: "person pertama"} dan " +
        (names[relationship.toPersonId] ?: "person kedua")
    else -> "hubungan orang tua-anak ${names[relationship.fromPersonId] ?: "orang tua"} ke " +
        (names[relationship.toPersonId] ?: "anak")
}

private fun canonicalPair(firstId: String, secondId: String): Pair<String, String> =
    if (firstId <= secondId) firstId to secondId else secondId to firstId
