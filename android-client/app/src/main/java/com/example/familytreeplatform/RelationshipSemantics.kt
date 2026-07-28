package com.example.familytreeplatform

import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.RelationItem

internal fun isCareRelationshipMeta(meta: String?): Boolean =
    meta == "FOSTER" || meta == "GUARDIAN"

internal fun isLineageParentChildMeta(meta: String?): Boolean =
    meta == null || meta == "BIOLOGICAL" || meta == "ADOPTIVE" || meta == "STEP"

internal fun ExportRelationship.isCareRelationship(): Boolean =
    type == "PARENT_CHILD" && isCareRelationshipMeta(meta)

internal fun ExportRelationship.isLineageParentChild(): Boolean =
    type == "PARENT_CHILD" && isLineageParentChildMeta(meta)

internal fun RelationItem.isCareRelationship(): Boolean =
    type == "PARENT_CHILD" && isCareRelationshipMeta(meta)

internal fun RelationItem.isLineageParentChild(): Boolean =
    type == "PARENT_CHILD" && isLineageParentChildMeta(meta)
