package com.example.familytreeplatform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationsResponse

private data class PointDp(val x: Dp, val y: Dp)

private data class RectDp(val x: Dp, val y: Dp, val w: Dp, val h: Dp) {
    val left: Dp get() = x
    val top: Dp get() = y
    val right: Dp get() = x + w
    val bottom: Dp get() = y + h

    fun shift(dx: Dp, dy: Dp): RectDp = copy(x = x + dx, y = y + dy)
    fun topCenter(): PointDp = PointDp(x + w / 2f, y)
    fun bottomCenter(): PointDp = PointDp(x + w / 2f, y + h)
    fun center(): PointDp = PointDp(x + w / 2f, y + h / 2f)
}

private data class TileRender(
    val id: String,
    val label: String,
    val role: String,
    val rect: RectDp,
    val isCenter: Boolean
)

private sealed class GraphNode(open val role: String) {
    abstract fun bounds(): RectDp
    abstract fun anchorTop(): PointDp
    abstract fun anchorBottom(): PointDp
    abstract fun spouseLine(): Pair<PointDp, PointDp>?
    abstract fun tiles(): List<TileRender>
    abstract fun hitId(worldPx: Offset, density: androidx.compose.ui.unit.Density): String?
    abstract fun shift(dx: Dp, dy: Dp): GraphNode
}

private data class PersonNode(
    val id: String,
    val label: String,
    val rect: RectDp,
    override val role: String
) : GraphNode(role) {
    override fun bounds(): RectDp = rect
    override fun anchorTop(): PointDp = rect.topCenter()
    override fun anchorBottom(): PointDp = rect.bottomCenter()
    override fun spouseLine(): Pair<PointDp, PointDp>? = null
    override fun tiles(): List<TileRender> = listOf(
        TileRender(id = id, label = label, role = role, rect = rect, isCenter = role == "CENTER")
    )

    override fun hitId(worldPx: Offset, density: androidx.compose.ui.unit.Density): String? {
        val left = with(density) { rect.left.toPx() }
        val right = with(density) { rect.right.toPx() }
        val top = with(density) { rect.top.toPx() }
        val bottom = with(density) { rect.bottom.toPx() }
        return if (worldPx.x in left..right && worldPx.y in top..bottom) id else null
    }

    override fun shift(dx: Dp, dy: Dp): GraphNode = copy(rect = rect.shift(dx, dy))
}

private data class CoupleNode(
    val leftId: String,
    val rightId: String,
    val leftLabel: String,
    val rightLabel: String,
    val leftRect: RectDp,
    val rightRect: RectDp,
    val wrapper: RectDp,
    val leftRole: String,
    val rightRole: String,
    override val role: String
) : GraphNode(role) {
    override fun bounds(): RectDp = wrapper
    override fun anchorTop(): PointDp = wrapper.topCenter()
    override fun anchorBottom(): PointDp = wrapper.bottomCenter()
    override fun spouseLine(): Pair<PointDp, PointDp> = leftRect.center() to rightRect.center()

    override fun tiles(): List<TileRender> = listOf(
        TileRender(id = leftId, label = leftLabel, role = leftRole, rect = leftRect, isCenter = role == "CENTER"),
        TileRender(id = rightId, label = rightLabel, role = rightRole, rect = rightRect, isCenter = role == "CENTER")
    )

    override fun hitId(worldPx: Offset, density: androidx.compose.ui.unit.Density): String? {
        val left = with(density) { leftRect.left.toPx() }
        val right = with(density) { leftRect.right.toPx() }
        val top = with(density) { leftRect.top.toPx() }
        val bottom = with(density) { leftRect.bottom.toPx() }
        if (worldPx.x in left..right && worldPx.y in top..bottom) return leftId

        val l2 = with(density) { rightRect.left.toPx() }
        val r2 = with(density) { rightRect.right.toPx() }
        val t2 = with(density) { rightRect.top.toPx() }
        val b2 = with(density) { rightRect.bottom.toPx() }
        return if (worldPx.x in l2..r2 && worldPx.y in t2..b2) rightId else null
    }

    override fun shift(dx: Dp, dy: Dp): GraphNode = copy(
        leftRect = leftRect.shift(dx, dy),
        rightRect = rightRect.shift(dx, dy),
        wrapper = wrapper.shift(dx, dy)
    )
}

private data class GraphLayout(
    val centerCoupleId: String,
    val center: GraphNode,
    val parents: List<GraphNode>,
    val children: List<GraphNode>,
    val nodes: List<GraphNode>,
    val lineageEdges: List<LineageEdge>,
    val width: Dp,
    val height: Dp
)

private data class LineageEdge(
    val from: PointDp,
    val to: PointDp,
    val meta: String?
)

private data class ChildRender(
    val id: String,
    val label: String,
    val role: String,
    val spouseId: String? = null,
    val spouseLabel: String? = null
)

private data class VisibleTree(
    val children: List<ChildRender>,
    val edges: List<Pair<String, String>>,
    val coupleId: String
)

@Composable
fun GraphScreen(
    centerPersonId: String,
    persons: List<PersonListItem>,
    relations: RelationsResponse?,
    allRelationships: List<ExportRelationship>,
    onSelectPerson: (String) -> Unit,
    onBack: () -> Unit
) {
    if (relations == null) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Graph: no data yet")
            Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Text("Back")
            }
        }
        return
    }

    val personById = remember(persons) { persons.associateBy { it.personId } }
    val displayName: (String) -> String = { id ->
        val p = personById[id]
        p?.fullName ?: "Unknown person"
    }

    val tileW = 150.dp
    val tileH = 56.dp
    val spouseGapX = 16.dp
    val siblingGapX = 24.dp
    val rankGapY = 140.dp
    val viewportHeight = 460.dp
    val margin = 80.dp

    var collapsedCouples by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    val layout by remember(centerPersonId, persons, relations, allRelationships, collapsedCouples) {
        derivedStateOf {
            buildCoupleGraphLayout(
                centerPersonId = centerPersonId,
                relations = relations,
                allRelationships = allRelationships,
                displayName = displayName,
                persons = persons,
                collapsedCouples = collapsedCouples,
                tileW = tileW,
                tileH = tileH,
                spouseGapX = spouseGapX,
                siblingGapX = siblingGapX,
                rankGapY = rankGapY,
                margin = margin
            )
        }
    }

    val density = LocalDensity.current
    val onSelectPersonState = rememberUpdatedState(onSelectPerson)
    val tiles = remember(layout) { layout.nodes.flatMap { it.tiles() } }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val minScale = 0.5f
    val maxScale = 2.5f

    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Tree Graph", style = MaterialTheme.typography.titleMedium)
                Text("Pinch to zoom, drag to pan", style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = {
                scale = 1f
                offset = Offset.Zero
            }) {
                Text("Reset View")
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(viewportHeight)
                .pointerInput(tiles, scale, offset) {
                    detectTapGestures(
                        onTap = { tap ->
                            if (tiles.isEmpty()) return@detectTapGestures
                            val world = (tap - offset) / scale
                            val hit = tiles.asReversed().firstOrNull { tile ->
                                val left = with(density) { tile.rect.left.toPx() }
                                val right = with(density) { tile.rect.right.toPx() }
                                val top = with(density) { tile.rect.top.toPx() }
                                val bottom = with(density) { tile.rect.bottom.toPx() }
                                world.x in left..right && world.y in top..bottom
                            }
                            if (hit != null) {
                                if (hit.id.startsWith("placeholder-")) {
                                    val coupleId = hit.id.removePrefix("placeholder-")
                                    collapsedCouples = collapsedCouples + (coupleId to false)
                                } else if (hit.id == centerPersonId) {
                                    val coupleId = layout.centerCoupleId
                                    val current = collapsedCouples[coupleId] == true
                                    collapsedCouples = collapsedCouples + (coupleId to !current)
                                } else {
                                    onSelectPersonState.value(hit.id)
                                }
                            }
                        },
                        onDoubleTap = {
                            scale = 1f
                            offset = Offset.Zero
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        val scaleFactor = newScale / scale
                        scale = newScale
                        offset = (offset - centroid) * scaleFactor + centroid + pan
                    }
                }
        ) {
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val viewportHeightPx = with(density) { maxHeight.toPx() }
            val graphWidthPx = with(density) { layout.width.toPx() }
            val graphHeightPx = with(density) { layout.height.toPx() }

            LaunchedEffect(centerPersonId, graphWidthPx, graphHeightPx, viewportWidthPx, viewportHeightPx) {
                val centerWorld = Offset(graphWidthPx / 2f, graphHeightPx / 2f)
                offset = Offset(
                    x = viewportWidthPx / 2f - centerWorld.x * scale,
                    y = viewportHeightPx / 2f - centerWorld.y * scale
                )
            }

            Box(
                modifier = Modifier
                    .size(layout.width, layout.height)
                    .graphicsLayer {
                        translationX = offset.x
                        translationY = offset.y
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val lineColor = Color(0xFF3A5A40)
                    val spouseColor = Color(0xFF6B6B6B)

                    layout.nodes.forEach { node ->
                        node.spouseLine()?.let { (a, b) ->
                            drawLine(
                                color = spouseColor,
                                start = Offset(with(density) { a.x.toPx() }, with(density) { a.y.toPx() }),
                                end = Offset(with(density) { b.x.toPx() }, with(density) { b.y.toPx() }),
                                strokeWidth = 4f
                            )
                        }
                    }

                    layout.lineageEdges.forEach { edge ->
                        val start = Offset(
                            with(density) { edge.from.x.toPx() },
                            with(density) { edge.from.y.toPx() }
                        )
                        val end = Offset(
                            with(density) { edge.to.x.toPx() },
                            with(density) { edge.to.y.toPx() }
                        )
                        val hubY = start.y + (end.y - start.y) / 2f
                        drawLine(
                            color = lineColor,
                            start = start,
                            end = Offset(start.x, hubY),
                            strokeWidth = 4f
                        )
                        drawLine(
                            color = lineColor,
                            start = Offset(start.x, hubY),
                            end = Offset(end.x, hubY),
                            strokeWidth = 4f
                        )
                        drawLine(
                            color = lineColor,
                            start = Offset(end.x, hubY),
                            end = end,
                            strokeWidth = 4f
                        )
                    }
                }

                tiles.forEach { tile ->
                    Card(
                        modifier = Modifier
                            .size(tile.rect.w, tile.rect.h)
                            .offset(tile.rect.x, tile.rect.y),
                        colors = if (tile.isCenter) {
                            CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                        } else {
                            CardDefaults.cardColors()
                        }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = tile.label, style = MaterialTheme.typography.bodyMedium)
                            Text(text = tile.role.lowercase(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

private enum class RowKind { PERSON, COUPLE }

private data class RowItem(
    val kind: RowKind,
    val idA: String,
    val idB: String? = null,
    val labelA: String,
    val labelB: String? = null,
    val role: String = "CHILD",
    val width: Float
)

private data class PlacedItem(val item: RowItem, val x: Float)

private fun layoutRow(items: List<RowItem>, centerX: Float, gap: Float): List<PlacedItem> {
    if (items.isEmpty()) return emptyList()
    val totalWidth = items.fold(0f) { acc, item -> acc + item.width } +
        gap * (items.size - 1).coerceAtLeast(0)
    var cursor = centerX - totalWidth / 2f
    val placed = mutableListOf<PlacedItem>()

    items.forEach { item ->
        var left = cursor
        if (placed.isNotEmpty()) {
            val prev = placed.last()
            val minLeft = prev.x + prev.item.width + gap
            if (left < minLeft) left = minLeft
        }
        placed.add(PlacedItem(item, left))
        cursor = left + item.width + gap
    }

    val rowLeft = placed.first().x
    val rowRight = placed.last().x + placed.last().item.width
    val rowCenter = rowLeft + (rowRight - rowLeft) / 2f
    val shift = centerX - rowCenter
    return placed.map { it.copy(x = it.x + shift) }
}

private fun buildCoupleGraphLayout(
    centerPersonId: String,
    relations: RelationsResponse,
    allRelationships: List<ExportRelationship>,
    displayName: (String) -> String,
    persons: List<PersonListItem>,
    collapsedCouples: Map<String, Boolean>,
    tileW: Dp,
    tileH: Dp,
    spouseGapX: Dp,
    siblingGapX: Dp,
    rankGapY: Dp,
    margin: Dp
): GraphLayout {
    val activeSpouseId = findActiveSpouseId(centerPersonId, relations, allRelationships)
    val centerCoupleId = canonicalCoupleId(centerPersonId, activeSpouseId)

    val centerNode = if (activeSpouseId != null) {
        coupleNode(
            leftId = centerPersonId,
            rightId = activeSpouseId,
            leftLabel = displayName(centerPersonId),
            rightLabel = displayName(activeSpouseId),
            role = "CENTER",
            leftRole = "CENTER",
            rightRole = "SPOUSE",
            x = (-((tileW.value * 2f + spouseGapX.value) / 2f)).dp,
            y = 0.dp,
            tileW = tileW,
            tileH = tileH,
            gap = spouseGapX
        )
    } else {
        personNode(
            id = centerPersonId,
            label = displayName(centerPersonId),
            role = "CENTER",
            x = (-tileW.value / 2f).dp,
            y = 0.dp,
            tileW = tileW,
            tileH = tileH
        )
    }

    val centerMemberIds = setOfNotNull(centerPersonId, activeSpouseId)
    val parentRelationships = if (allRelationships.isNotEmpty()) {
        allRelationships.filter {
            it.type == "PARENT_CHILD" && it.toPersonId in centerMemberIds
        }
    } else {
        relations.parents.map {
            ExportRelationship(
                relationshipId = it.relationshipId,
                type = "PARENT_CHILD",
                fromPersonId = it.fromPersonId,
                toPersonId = it.toPersonId,
                meta = it.meta,
                startDate = it.startDate,
                endDate = it.endDate,
                createdAt = it.createdAt
            )
        }
    }
    val parentIds = parentRelationships
        .sortedBy { relationship ->
            when (relationship.toPersonId) {
                centerPersonId -> 0
                activeSpouseId -> 1
                else -> 2
            }
        }
        .map { it.fromPersonId }
        .distinct()
    val parentsY = (-rankGapY.value - tileH.value).dp
    val parentItems = buildParentRowItems(parentIds, allRelationships, displayName, tileW, spouseGapX)
    val parentPlaced = layoutRow(parentItems, centerX = 0f, gap = siblingGapX.value)
    val parentNodes = parentPlaced.map { placed ->
        when (placed.item.kind) {
            RowKind.PERSON -> personNode(
                id = placed.item.idA,
                label = placed.item.labelA,
                role = "PARENT",
                x = placed.x.dp,
                y = parentsY,
                tileW = tileW,
                tileH = tileH
            )
            RowKind.COUPLE -> coupleNode(
                leftId = placed.item.idA,
                rightId = placed.item.idB ?: "",
                leftLabel = placed.item.labelA,
                rightLabel = placed.item.labelB ?: "",
                role = "PARENT",
                leftRole = "PARENT",
                rightRole = "PARENT",
                x = placed.x.dp,
                y = parentsY,
                tileW = tileW,
                tileH = tileH,
                gap = spouseGapX
            )
        }
    }

    val childrenY = (rankGapY.value + tileH.value).dp
    val visibleTree = preprocessTree(
        centerPersonId = centerPersonId,
        activeSpouseId = activeSpouseId,
        relations = relations,
        allRelationships = allRelationships,
        persons = persons,
        displayName = displayName,
        collapsedCouples = collapsedCouples
    )

    val childItems = visibleTree.children.map { child ->
        if (child.spouseId != null) {
            RowItem(
                kind = RowKind.COUPLE,
                idA = child.id,
                idB = child.spouseId,
                labelA = child.label,
                labelB = child.spouseLabel,
                role = child.role,
                width = tileW.value * 2f + spouseGapX.value
            )
        } else {
            RowItem(
                kind = RowKind.PERSON,
                idA = child.id,
                labelA = child.label,
                role = child.role,
                width = tileW.value
            )
        }
    }
    val childPlaced = layoutRow(childItems, centerX = 0f, gap = siblingGapX.value)
    val childNodes = childPlaced.map { placed ->
        when (placed.item.kind) {
            RowKind.PERSON -> personNode(
                id = placed.item.idA,
                label = placed.item.labelA,
                role = placed.item.role,
                x = placed.x.dp,
                y = childrenY,
                tileW = tileW,
                tileH = tileH
            )
            RowKind.COUPLE -> coupleNode(
                leftId = placed.item.idA,
                rightId = placed.item.idB ?: "",
                leftLabel = placed.item.labelA,
                rightLabel = placed.item.labelB ?: "",
                role = "CHILD",
                leftRole = "CHILD",
                rightRole = "SPOUSE",
                x = placed.x.dp,
                y = childrenY,
                tileW = tileW,
                tileH = tileH,
                gap = spouseGapX
            )
        }
    }

    val allNodes = parentNodes + centerNode + childNodes
    val minX = allNodes.minOf { it.bounds().left.value }
    val maxX = allNodes.maxOf { it.bounds().right.value }
    val minY = allNodes.minOf { it.bounds().top.value }
    val maxY = allNodes.maxOf { it.bounds().bottom.value }

    val width = (maxX - minX).dp + margin * 2f
    val height = (maxY - minY).dp + margin * 2f
    val shiftX = (-minX).dp + margin
    val shiftY = (-minY).dp + margin

    val shiftedNodes = allNodes.map { it.shift(shiftX, shiftY) }
    val shiftedCenter = shiftedNodes.first { it.role == "CENTER" }
    val shiftedParents = shiftedNodes.filter { it.role == "PARENT" }
    val shiftedChildren = shiftedNodes.filter { it.role == "CHILD" }
    val parentLineageEdges = parentRelationships.mapNotNull { relationship ->
        val from = shiftedNodes.tileRect(relationship.fromPersonId)?.bottomCenter()
        val to = shiftedNodes.tileRect(relationship.toPersonId)?.topCenter()
        if (from != null && to != null) LineageEdge(from, to, relationship.meta) else null
    }
    val childLineageEdges = visibleTree.children.mapNotNull { child ->
        if (child.role == "PLACEHOLDER") return@mapNotNull null
        val to = shiftedNodes.tileRect(child.id)?.topCenter() ?: return@mapNotNull null
        val meta = allRelationships.firstOrNull {
            it.type == "PARENT_CHILD" &&
                it.toPersonId == child.id &&
                it.fromPersonId in centerMemberIds
        }?.meta
        LineageEdge(shiftedCenter.anchorBottom(), to, meta)
    }

    return GraphLayout(
        centerCoupleId = centerCoupleId,
        center = shiftedCenter,
        parents = shiftedParents,
        children = shiftedChildren,
        nodes = shiftedNodes,
        lineageEdges = parentLineageEdges + childLineageEdges,
        width = width,
        height = height
    )
}

private fun buildParentRowItems(
    parentIds: List<String>,
    allRelationships: List<ExportRelationship>,
    displayName: (String) -> String,
    tileW: Dp,
    spouseGapX: Dp
): List<RowItem> {
    val remaining = parentIds.toMutableList()
    val items = mutableListOf<RowItem>()
    while (remaining.isNotEmpty()) {
        val personId = remaining.removeAt(0)
        val spouseId = remaining.firstOrNull {
            isActiveSpouseBetween(personId, it, allRelationships)
        }
        if (spouseId != null) {
            remaining.remove(spouseId)
            items += RowItem(
                kind = RowKind.COUPLE,
                idA = personId,
                idB = spouseId,
                labelA = displayName(personId),
                labelB = displayName(spouseId),
                width = tileW.value * 2f + spouseGapX.value
            )
        } else {
            items += RowItem(
                kind = RowKind.PERSON,
                idA = personId,
                labelA = displayName(personId),
                width = tileW.value
            )
        }
    }
    return items
}

private fun buildChildrenIds(
    centerPersonId: String,
    spouseId: String?,
    relations: RelationsResponse,
    allRelationships: List<ExportRelationship>
): List<String> {
    if (spouseId != null && allRelationships.isNotEmpty()) {
        return allRelationships
            .filter { it.type == "PARENT_CHILD" && (it.fromPersonId == centerPersonId || it.fromPersonId == spouseId) }
            .map { it.toPersonId }
            .distinct()
    }
    return relations.children.map { it.toPersonId }.distinct()
}

private fun preprocessTree(
    centerPersonId: String,
    activeSpouseId: String?,
    relations: RelationsResponse,
    allRelationships: List<ExportRelationship>,
    persons: List<PersonListItem>,
    displayName: (String) -> String,
    collapsedCouples: Map<String, Boolean>
): VisibleTree {
    val coupleId = canonicalCoupleId(centerPersonId, activeSpouseId)
    val children = collectChildrenForCouple(
        coupleId = coupleId,
        centerPersonId = centerPersonId,
        activeSpouseId = activeSpouseId,
        relations = relations,
        allRelationships = allRelationships
    )

    val orderedChildren = orderChildren(children, persons)
    val isCollapsed = collapsedCouples[coupleId] == true

    // TODO: integrate subtreeWidth/layout phases once available.
    if (isCollapsed) {
        val placeholderId = "placeholder-$coupleId"
        val label = "${orderedChildren.size} hidden"
        return VisibleTree(
            children = listOf(ChildRender(id = placeholderId, label = label, role = "PLACEHOLDER")),
            edges = listOf(coupleId to placeholderId),
            coupleId = coupleId
        )
    }

    return VisibleTree(
        children = orderedChildren.map { id ->
            val spouseId = findActiveSpouseId(id, allRelationships)
            ChildRender(
                id = id,
                label = displayName(id),
                role = "CHILD",
                spouseId = spouseId,
                spouseLabel = spouseId?.let(displayName)
            )
        },
        edges = orderedChildren.map { id -> coupleId to id },
        coupleId = coupleId
    )
}

private fun collectChildrenForCouple(
    coupleId: String,
    centerPersonId: String,
    activeSpouseId: String?,
    relations: RelationsResponse,
    allRelationships: List<ExportRelationship>
): List<String> {
    if (allRelationships.isEmpty()) {
        // TODO: feed full relationship cache to avoid fallback path.
        return relations.children.map { it.toPersonId }.distinct()
    }

    val parentMap = mutableMapOf<String, MutableList<String>>()
    allRelationships
        .filter { it.type == "PARENT_CHILD" }
        .forEach { rel ->
            parentMap.getOrPut(rel.toPersonId) { mutableListOf() }.add(rel.fromPersonId)
        }

    val grouped = mutableMapOf<String, MutableList<String>>()
    parentMap.forEach { (childId, parents) ->
        val uniqueParents = parents.distinct().sorted()
        val key = when {
            uniqueParents.size >= 2 -> canonicalCoupleId(uniqueParents[0], uniqueParents[1])
            uniqueParents.isNotEmpty() -> canonicalCoupleId(uniqueParents[0], null)
            else -> canonicalCoupleId(centerPersonId, activeSpouseId)
        }
        grouped.getOrPut(key) { mutableListOf() }.add(childId)
    }

    return grouped[coupleId]?.distinct() ?: emptyList()
}

private fun orderChildren(children: List<String>, persons: List<PersonListItem>): List<String> {
    val personById = persons.associateBy { it.personId }
    val keys = children.distinct().associateWith { id ->
        val person = personById[id]
        val birthDate = person?.birthDate
        val approxYear = parseApproxBirthYear(person?.createdAt)
        ChildSortKey(
            hasBirthDate = if (birthDate == null) 1 else 0,
            birthDate = birthDate ?: "",
            approxBirthYear = approxYear ?: Int.MAX_VALUE,
            personId = id
        )
    }

    return keys.keys.sortedWith { a, b ->
        val ka = keys[a]!!
        val kb = keys[b]!!
        when {
            ka.hasBirthDate != kb.hasBirthDate -> ka.hasBirthDate - kb.hasBirthDate
            ka.birthDate != kb.birthDate -> ka.birthDate.compareTo(kb.birthDate)
            ka.approxBirthYear != kb.approxBirthYear -> ka.approxBirthYear - kb.approxBirthYear
            else -> ka.personId.compareTo(kb.personId)
        }
    }
}

private data class ChildSortKey(
    val hasBirthDate: Int,
    val birthDate: String,
    val approxBirthYear: Int,
    val personId: String
)

private fun parseApproxBirthYear(createdAt: String?): Int? {
    if (createdAt.isNullOrBlank()) return null
    val yearPart = createdAt.take(4)
    return yearPart.toIntOrNull()
}

private fun canonicalCoupleId(a: String, b: String?): String {
    if (b.isNullOrBlank()) return "virtual:$a"
    val (left, right) = if (a <= b) a to b else b to a
    return "couple:$left:$right"
}

private fun findActiveSpouseId(
    personId: String,
    relations: RelationsResponse,
    allRelationships: List<ExportRelationship>
): String? {
    val fromExport = findActiveSpouseId(personId, allRelationships)
    if (fromExport != null) return fromExport

    val fromRelations = relations.spouses.firstOrNull {
        it.meta == "MARRIED" &&
            it.endDate == null &&
            (it.fromPersonId == personId || it.toPersonId == personId)
    } ?: return null

    return if (fromRelations.fromPersonId == personId) fromRelations.toPersonId else fromRelations.fromPersonId
}

private fun findActiveSpouseId(
    personId: String,
    allRelationships: List<ExportRelationship>
): String? {
    val relationship = allRelationships.firstOrNull {
        it.type == "SPOUSE" &&
            it.meta == "MARRIED" &&
            it.endDate == null &&
            (it.fromPersonId == personId || it.toPersonId == personId)
    } ?: return null

    return if (relationship.fromPersonId == personId) relationship.toPersonId else relationship.fromPersonId
}

private fun isActiveSpouseBetween(
    a: String,
    b: String,
    allRelationships: List<ExportRelationship>
): Boolean {
    return allRelationships.any {
        it.type == "SPOUSE" &&
            it.meta == "MARRIED" &&
            it.endDate == null &&
            ((it.fromPersonId == a && it.toPersonId == b) || (it.fromPersonId == b && it.toPersonId == a))
    }
}

private fun personNode(
    id: String,
    label: String,
    role: String,
    x: Dp,
    y: Dp,
    tileW: Dp,
    tileH: Dp
): GraphNode {
    return PersonNode(
        id = id,
        label = label,
        rect = RectDp(x, y, tileW, tileH),
        role = role
    )
}

private fun coupleNode(
    leftId: String,
    rightId: String,
    leftLabel: String,
    rightLabel: String,
    role: String,
    leftRole: String,
    rightRole: String,
    x: Dp,
    y: Dp,
    tileW: Dp,
    tileH: Dp,
    gap: Dp
): GraphNode {
    val leftRect = RectDp(x, y, tileW, tileH)
    val rightRect = RectDp(x + tileW + gap, y, tileW, tileH)
    val wrapper = RectDp(x, y, tileW * 2f + gap, tileH)
    return CoupleNode(
        leftId = leftId,
        rightId = rightId,
        leftLabel = leftLabel,
        rightLabel = rightLabel,
        leftRect = leftRect,
        rightRect = rightRect,
        wrapper = wrapper,
        leftRole = leftRole,
        rightRole = rightRole,
        role = role
    )
}

private fun List<GraphNode>.tileRect(personId: String): RectDp? =
    asSequence()
        .flatMap { it.tiles().asSequence() }
        .firstOrNull { it.id == personId }
        ?.rect
