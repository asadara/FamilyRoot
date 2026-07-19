package com.example.familytreeplatform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.models.RelationshipPathResponse
import java.util.Calendar

private data class PointDp(val x: Dp, val y: Dp)

private data class GraphViewportTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

private val GraphViewportTransformSaver = listSaver<GraphViewportTransform, Float>(
    save = { listOf(it.scale, it.offsetX, it.offsetY) },
    restore = { GraphViewportTransform(it[0], it[1], it[2]) }
)

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
    val center: GraphNode,
    val parents: List<GraphNode>,
    val siblings: List<GraphNode>,
    val hasLeftSiblings: Boolean,
    val hasRightSiblings: Boolean,
    val children: List<GraphNode>,
    val nodes: List<GraphNode>,
    val lineageEdges: List<LineageEdge>,
    val width: Dp,
    val height: Dp
)

private data class LineageEdge(
    val relationshipIds: Set<String>,
    val from: PointDp,
    val to: PointDp,
    val meta: String?,
    val type: String = "PARENT_CHILD"
)

private data class ChildRender(
    val id: String,
    val label: String,
    val role: String,
    val spouseId: String? = null,
    val spouseLabel: String? = null
)

private data class VisibleTree(
    val children: List<ChildRender>
)

@Composable
fun GraphScreen(
    centerPersonId: String,
    selectedPersonId: String?,
    persons: List<PersonListItem>,
    relations: RelationsResponse?,
    allRelationships: List<ExportRelationship>,
    explorationHistory: List<String> = emptyList(),
    explorationBreadcrumbVisible: Boolean = true,
    relationshipPath: RelationshipPathResponse? = null,
    showRelationshipPathInGraph: Boolean = false,
    resetViewRequest: Int = 0,
    onSelectPerson: (String) -> Unit,
    onClearSelection: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onShowRelationshipPath: () -> Unit = {},
    onHideExplorationBreadcrumb: () -> Unit = {},
    onBack: () -> Unit,
) {
    if (relations == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Pohon keluarga belum tersedia")
                TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Kembali ke keluarga")
                }
            }
        }
        return
    }

    val personById = remember(persons) { persons.associateBy { it.personId } }
    val displayName: (String) -> String = { id ->
        val p = personById[id]
        p?.fullName ?: "Unknown person"
    }
    val pathPersonIds = remember(relationshipPath) {
        relationshipPath?.people?.map { it.personId }?.toSet().orEmpty()
    }
    val pathRelationshipIds = remember(relationshipPath) {
        relationshipPath?.edges?.map { it.relationshipId }?.toSet().orEmpty()
    }

    val tileW = 120.dp
    val tileH = 152.dp
    val spouseGapX = 28.dp
    val siblingGapX = 28.dp
    val rankGapY = 64.dp
    val margin = 88.dp

    var childrenCollapsed by rememberSaveable(centerPersonId) { mutableStateOf(false) }
    var parentsCollapsed by rememberSaveable(centerPersonId) { mutableStateOf(false) }
    var siblingsCollapsed by rememberSaveable(centerPersonId) { mutableStateOf(false) }

    val layout by remember(
        centerPersonId,
        persons,
        relations,
        allRelationships,
        childrenCollapsed,
        parentsCollapsed,
        siblingsCollapsed,
        relationshipPath,
        showRelationshipPathInGraph
    ) {
        derivedStateOf {
            val baseLayout = buildCoupleGraphLayout(
                centerPersonId = centerPersonId,
                relations = relations,
                allRelationships = allRelationships,
                displayName = displayName,
                persons = persons,
                childrenCollapsed = childrenCollapsed,
                parentsCollapsed = parentsCollapsed,
                siblingsCollapsed = siblingsCollapsed,
                tileW = tileW,
                tileH = tileH,
                spouseGapX = spouseGapX,
                siblingGapX = siblingGapX,
                rankGapY = rankGapY,
                margin = margin
            )
            if (showRelationshipPathInGraph && relationshipPath?.found == true) {
                augmentLayoutWithRelationshipPath(
                    base = baseLayout,
                    relationshipPath = relationshipPath,
                    displayName = displayName,
                    tileW = tileW,
                    tileH = tileH,
                    spouseGapX = spouseGapX,
                    siblingGapX = siblingGapX,
                    rankGapY = rankGapY,
                    margin = margin
                )
            } else {
                baseLayout
            }
        }
    }

    val density = LocalDensity.current
    val tiles = remember(layout) { layout.nodes.flatMap { it.tiles() } }
    val activeSpouseId = remember(centerPersonId, relations, allRelationships) {
        findActiveSpouseId(centerPersonId, relations, allRelationships)
    }
    val centerMemberIds = remember(centerPersonId, activeSpouseId) {
        setOfNotNull(centerPersonId, activeSpouseId)
    }
    val hasParents = remember(centerMemberIds, relations, allRelationships) {
        if (allRelationships.isNotEmpty()) {
            allRelationships.any { it.type == "PARENT_CHILD" && it.toPersonId in centerMemberIds }
        } else {
            relations.parents.isNotEmpty()
        }
    }
    val hasChildren = remember(centerPersonId, activeSpouseId, relations, allRelationships) {
        collectChildrenForCouple(
            coupleId = canonicalCoupleId(centerPersonId, activeSpouseId),
            centerPersonId = centerPersonId,
            activeSpouseId = activeSpouseId,
            relations = relations,
            allRelationships = allRelationships
        ).isNotEmpty()
    }
    val hasLeftSiblings = layout.hasLeftSiblings
    val hasRightSiblings = layout.hasRightSiblings
    LaunchedEffect(showRelationshipPathInGraph, pathPersonIds) {
        if (!showRelationshipPathInGraph) return@LaunchedEffect
        if (allRelationships.any {
                it.type == "PARENT_CHILD" &&
                    it.toPersonId in centerMemberIds &&
                    it.fromPersonId in pathPersonIds
            }
        ) parentsCollapsed = false
        if (allRelationships.any {
                it.type == "PARENT_CHILD" &&
                    it.fromPersonId in centerMemberIds &&
                    it.toPersonId in pathPersonIds
            }
        ) childrenCollapsed = false
        if (findSiblingIds(centerPersonId, allRelationships, persons).any { it in pathPersonIds }) {
            siblingsCollapsed = false
        }
    }
    val transformState = rememberSaveable(
        centerPersonId,
        stateSaver = GraphViewportTransformSaver
    ) { mutableStateOf(GraphViewportTransform()) }
    var resetViewVersion by remember { mutableStateOf(0) }
    var viewportInitialized by rememberSaveable(centerPersonId) { mutableStateOf(false) }
    val minScale = 0.5f
    val maxScale = 2.5f

    LaunchedEffect(resetViewRequest) {
        if (resetViewRequest > 0) {
            viewportInitialized = false
            resetViewVersion++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useSideInspector = maxWidth >= 720.dp
            val selectedPerson = selectedPersonId?.let(personById::get)

            BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .testTag("graph-workspace")
                .clipToBounds()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(
                    tiles,
                    layout,
                    selectedPersonId,
                    parentsCollapsed,
                    siblingsCollapsed,
                    childrenCollapsed
                ) {
                    detectTapGestures(
                        onTap = { tap ->
                            val transform = transformState.value
                            val currentOffset = Offset(transform.offsetX, transform.offsetY)
                            val world = (tap - currentOffset) / transform.scale
                            val controlRadiusPx = with(density) { 24.dp.toPx() }
                            fun hits(point: PointDp?): Boolean {
                                if (point == null) return false
                                val pointPx = Offset(
                                    with(density) { point.x.toPx() },
                                    with(density) { point.y.toPx() }
                                )
                                val dx = world.x - pointPx.x
                                val dy = world.y - pointPx.y
                                return dx * dx + dy * dy <= controlRadiusPx * controlRadiusPx
                            }

                            val parentControl = if (hasParents) {
                                PointDp(
                                    layout.center.anchorTop().x,
                                    layout.center.anchorTop().y - 24.dp
                                )
                            } else null
                            val childControl = if (hasChildren) {
                                PointDp(
                                    layout.center.anchorBottom().x,
                                    layout.center.anchorBottom().y + 24.dp
                                )
                            } else null
                            val leftSiblingControl = if (hasLeftSiblings) {
                                PointDp(
                                    layout.center.bounds().left - 24.dp,
                                    layout.center.bounds().center().y
                                )
                            } else null
                            val rightSiblingControl = if (hasRightSiblings) {
                                PointDp(
                                    layout.center.bounds().right + 24.dp,
                                    layout.center.bounds().center().y
                                )
                            } else null
                            val selectedTile = tiles.firstOrNull { it.id == selectedPersonId }
                            val selectedNode = layout.nodes.firstOrNull { node ->
                                node.tiles().any { it.id == selectedPersonId }
                            }
                            val addControl = selectedTile?.let { tile ->
                                val x = when {
                                    selectedNode is CoupleNode && selectedNode.leftId == tile.id -> tile.rect.left - 12.dp
                                    else -> tile.rect.right + 12.dp
                                }
                                PointDp(x, tile.rect.top + 20.dp)
                            }

                            when {
                                hits(addControl) && selectedPersonId != null -> {
                                    onOpenPerson(selectedPersonId)
                                    return@detectTapGestures
                                }
                                hits(parentControl) -> {
                                    parentsCollapsed = !parentsCollapsed
                                    return@detectTapGestures
                                }
                                hits(childControl) -> {
                                    childrenCollapsed = !childrenCollapsed
                                    return@detectTapGestures
                                }
                                hits(leftSiblingControl) || hits(rightSiblingControl) -> {
                                    siblingsCollapsed = !siblingsCollapsed
                                    return@detectTapGestures
                                }
                            }

                            val hit = tiles.asReversed().firstOrNull { tile ->
                                val left = with(density) { tile.rect.left.toPx() }
                                val right = with(density) { tile.rect.right.toPx() }
                                val top = with(density) { tile.rect.top.toPx() }
                                val bottom = with(density) { tile.rect.bottom.toPx() }
                                world.x in left..right && world.y in top..bottom
                            }
                            if (hit != null) {
                                onSelectPerson(hit.id)
                            } else {
                                onClearSelection()
                            }
                        },
                        onDoubleTap = {
                            viewportInitialized = false
                            resetViewVersion++
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val previousTransform = transformState.value
                        val previousScale = previousTransform.scale
                        val previousOffset =
                            Offset(previousTransform.offsetX, previousTransform.offsetY)
                        val newScale = (previousScale * zoom).coerceIn(minScale, maxScale)
                        val scaleFactor = newScale / previousScale
                        val nextOffset =
                            (previousOffset - centroid) * scaleFactor + centroid + pan
                        transformState.value = GraphViewportTransform(
                            scale = newScale,
                            offsetX = nextOffset.x,
                            offsetY = nextOffset.y
                        )
                    }
                }
            ) {
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val viewportHeightPx = with(density) { maxHeight.toPx() }
            val graphWidthPx = with(density) { layout.width.toPx() }
            val graphHeightPx = with(density) { layout.height.toPx() }

            LaunchedEffect(centerPersonId, viewportWidthPx, viewportHeightPx, resetViewVersion) {
                if (viewportInitialized) return@LaunchedEffect
                val fittedScale = (
                    minOf(
                        viewportWidthPx / graphWidthPx,
                        viewportHeightPx / graphHeightPx
                    ) * 0.94f
                ).coerceIn(0.78f, 1f)
                val centerWorld = Offset(graphWidthPx / 2f, graphHeightPx / 2f)
                transformState.value = GraphViewportTransform(
                    scale = fittedScale,
                    offsetX = viewportWidthPx / 2f - centerWorld.x * fittedScale,
                    offsetY = viewportHeightPx / 2f - centerWorld.y * fittedScale
                )
                viewportInitialized = true
            }

            LaunchedEffect(
                showRelationshipPathInGraph,
                pathPersonIds,
                layout,
                viewportWidthPx,
                viewportHeightPx,
                useSideInspector
            ) {
                if (!showRelationshipPathInGraph || pathPersonIds.isEmpty()) {
                    return@LaunchedEffect
                }
                val pathRects = tiles.filter { it.id in pathPersonIds }.map { it.rect }
                if (pathRects.isEmpty()) return@LaunchedEffect
                val minPathX = pathRects.minOf { with(density) { it.left.toPx() } }
                val maxPathX = pathRects.maxOf { with(density) { it.right.toPx() } }
                val minPathY = pathRects.minOf { with(density) { it.top.toPx() } }
                val maxPathY = pathRects.maxOf { with(density) { it.bottom.toPx() } }
                val paddingPx = with(density) { 56.dp.toPx() }
                val inspectorWidthPx = if (useSideInspector && selectedPerson != null) {
                    with(density) { 360.dp.toPx() }
                } else 0f
                val availableWidthPx = (viewportWidthPx - inspectorWidthPx).coerceAtLeast(1f)
                val pathWidthPx = maxPathX - minPathX
                val pathHeightPx = maxPathY - minPathY
                val fittedScale = minOf(
                    availableWidthPx / (pathWidthPx + paddingPx * 2f),
                    viewportHeightPx / (pathHeightPx + paddingPx * 2f),
                    1f
                ).coerceIn(minScale, maxScale)
                val pathCenter = Offset(
                    x = minPathX + pathWidthPx / 2f,
                    y = minPathY + pathHeightPx / 2f
                )
                transformState.value = GraphViewportTransform(
                    scale = fittedScale,
                    offsetX = availableWidthPx / 2f - pathCenter.x * fittedScale,
                    offsetY = viewportHeightPx / 2f - pathCenter.y * fittedScale
                )
            }

            Box(
                modifier = Modifier
                    .size(layout.width, layout.height)
                    .graphicsLayer {
                        val transform = transformState.value
                        translationX = transform.offsetX
                        translationY = transform.offsetY
                        scaleX = transform.scale
                        scaleY = transform.scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
            ) {
                val connectorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
                val coupleRingColor = MaterialTheme.colorScheme.secondary
                val pathAccentColor = MaterialTheme.colorScheme.tertiary
                Canvas(modifier = Modifier.matchParentSize()) {
                    val lineStroke = 1.5.dp.toPx()

                    layout.nodes.forEach { node ->
                        node.spouseLine()?.let { (a, b) ->
                            val highlighted = showRelationshipPathInGraph &&
                                node.tiles().count { it.id in pathPersonIds } >= 2
                            val center = Offset(
                                with(density) { ((a.x + b.x) / 2f).toPx() },
                                with(density) { ((a.y + b.y) / 2f).toPx() }
                            )
                            val radius = 7.dp.toPx()
                            val separation = 5.dp.toPx()
                            drawCircle(
                                color = if (highlighted) pathAccentColor else coupleRingColor,
                                radius = radius,
                                center = center.copy(x = center.x - separation),
                                style = Stroke(width = if (highlighted) 3.dp.toPx() else 1.5.dp.toPx())
                            )
                            drawCircle(
                                color = if (highlighted) pathAccentColor else coupleRingColor,
                                radius = radius,
                                center = center.copy(x = center.x + separation),
                                style = Stroke(width = if (highlighted) 3.dp.toPx() else 1.5.dp.toPx())
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
                        val highlighted = showRelationshipPathInGraph &&
                            edge.relationshipIds.any { it in pathRelationshipIds }
                        if (edge.type == "SPOUSE") {
                            val center = Offset(
                                x = (start.x + end.x) / 2f,
                                y = (start.y + end.y) / 2f
                            )
                            val radius = 7.dp.toPx()
                            val separation = 5.dp.toPx()
                            drawCircle(
                                color = if (highlighted) pathAccentColor else coupleRingColor,
                                radius = radius,
                                center = center.copy(x = center.x - separation),
                                style = Stroke(width = if (highlighted) 3.dp.toPx() else 1.5.dp.toPx())
                            )
                            drawCircle(
                                color = if (highlighted) pathAccentColor else coupleRingColor,
                                radius = radius,
                                center = center.copy(x = center.x + separation),
                                style = Stroke(width = if (highlighted) 3.dp.toPx() else 1.5.dp.toPx())
                            )
                            return@forEach
                        }
                        val hubY = start.y + (end.y - start.y) / 2f
                        val pathEffect = when (edge.meta) {
                            "ADOPTIVE" -> PathEffect.dashPathEffect(
                                floatArrayOf(8.dp.toPx(), 5.dp.toPx())
                            )
                            "STEP" -> PathEffect.dashPathEffect(
                                floatArrayOf(2.dp.toPx(), 5.dp.toPx())
                            )
                            else -> null
                        }
                        val edgeColor = if (highlighted) pathAccentColor else connectorColor
                        val edgeStroke = if (highlighted) 3.dp.toPx() else lineStroke
                        drawLine(
                            color = edgeColor,
                            start = start,
                            end = Offset(start.x, hubY),
                            strokeWidth = edgeStroke,
                            pathEffect = pathEffect
                        )
                        drawLine(
                            color = edgeColor,
                            start = Offset(start.x, hubY),
                            end = Offset(end.x, hubY),
                            strokeWidth = edgeStroke,
                            pathEffect = pathEffect
                        )
                        drawLine(
                            color = edgeColor,
                            start = Offset(end.x, hubY),
                            end = end,
                            strokeWidth = edgeStroke,
                            pathEffect = pathEffect
                        )
                    }
                }

                tiles.forEach { tile ->
                    val person = personById[tile.id]
                    if (person != null) {
                        PersonGraphCard(
                            person = person,
                            selected = tile.id == selectedPersonId,
                            highlighted = showRelationshipPathInGraph && tile.id in pathPersonIds,
                            modifier = Modifier
                                .size(tile.rect.w, tile.rect.h)
                                .offset(tile.rect.x, tile.rect.y)
                        )
                    } else {
                        PlaceholderGraphCard(
                            label = tile.label,
                            modifier = Modifier
                                .size(tile.rect.w, tile.rect.h)
                                .offset(tile.rect.x, tile.rect.y)
                        )
                    }
                }

                val controlSurfaceColor = MaterialTheme.colorScheme.surface
                val controlOutlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                val controlAccentColor = MaterialTheme.colorScheme.primary
                val controlOnAccentColor = MaterialTheme.colorScheme.onPrimary
                Canvas(modifier = Modifier.matchParentSize()) {
                    fun pointOffset(point: PointDp) = Offset(
                        with(density) { point.x.toPx() },
                        with(density) { point.y.toPx() }
                    )
                    fun drawChevron(point: PointDp, pointsUp: Boolean) {
                        val center = pointOffset(point)
                        val halfWidth = 5.dp.toPx()
                        val height = 3.5.dp.toPx()
                        val centerY = if (pointsUp) center.y + height / 2f else center.y - height / 2f
                        val tipY = if (pointsUp) center.y - height else center.y + height
                        drawLine(
                            color = controlAccentColor,
                            start = Offset(center.x - halfWidth, centerY),
                            end = Offset(center.x, tipY),
                            strokeWidth = 1.8.dp.toPx()
                        )
                        drawLine(
                            color = controlAccentColor,
                            start = Offset(center.x, tipY),
                            end = Offset(center.x + halfWidth, centerY),
                            strokeWidth = 1.8.dp.toPx()
                        )
                    }
                    fun drawControl(point: PointDp, pointsUp: Boolean) {
                        drawCircle(
                            color = controlSurfaceColor,
                            radius = 14.dp.toPx(),
                            center = pointOffset(point)
                        )
                        drawCircle(
                            color = controlOutlineColor,
                            radius = 14.dp.toPx(),
                            center = pointOffset(point),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawChevron(point, pointsUp)
                    }
                    fun drawHorizontalControl(point: PointDp, pointsLeft: Boolean) {
                        val center = pointOffset(point)
                        drawCircle(
                            color = controlSurfaceColor,
                            radius = 14.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = controlOutlineColor,
                            radius = 14.dp.toPx(),
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        val halfHeight = 5.dp.toPx()
                        val width = 3.5.dp.toPx()
                        val centerX = if (pointsLeft) center.x + width / 2f else center.x - width / 2f
                        val tipX = if (pointsLeft) center.x - width else center.x + width
                        drawLine(
                            color = controlAccentColor,
                            start = Offset(centerX, center.y - halfHeight),
                            end = Offset(tipX, center.y),
                            strokeWidth = 1.8.dp.toPx()
                        )
                        drawLine(
                            color = controlAccentColor,
                            start = Offset(tipX, center.y),
                            end = Offset(centerX, center.y + halfHeight),
                            strokeWidth = 1.8.dp.toPx()
                        )
                    }

                    if (hasParents) {
                        drawControl(
                            PointDp(
                                layout.center.anchorTop().x,
                                layout.center.anchorTop().y - 24.dp
                            ),
                            pointsUp = parentsCollapsed
                        )
                    }
                    if (hasChildren) {
                        drawControl(
                            PointDp(
                                layout.center.anchorBottom().x,
                                layout.center.anchorBottom().y + 24.dp
                            ),
                            pointsUp = !childrenCollapsed
                        )
                    }
                    if (hasLeftSiblings) {
                        drawHorizontalControl(
                            PointDp(
                                layout.center.bounds().left - 24.dp,
                                layout.center.bounds().center().y
                            ),
                            pointsLeft = !siblingsCollapsed
                        )
                    }
                    if (hasRightSiblings) {
                        drawHorizontalControl(
                            PointDp(
                                layout.center.bounds().right + 24.dp,
                                layout.center.bounds().center().y
                            ),
                            pointsLeft = siblingsCollapsed
                        )
                    }

                    val selectedTile = tiles.firstOrNull { it.id == selectedPersonId }
                    val selectedNode = layout.nodes.firstOrNull { node ->
                        node.tiles().any { it.id == selectedPersonId }
                    }
                    selectedTile?.let { tile ->
                        val x = when {
                            selectedNode is CoupleNode && selectedNode.leftId == tile.id -> tile.rect.left - 12.dp
                            else -> tile.rect.right + 12.dp
                        }
                        val center = pointOffset(PointDp(x, tile.rect.top + 20.dp))
                        drawCircle(
                            color = controlAccentColor,
                            radius = 16.dp.toPx(),
                            center = center
                        )
                        val arm = 5.dp.toPx()
                        drawLine(
                            color = controlOnAccentColor,
                            start = Offset(center.x - arm, center.y),
                            end = Offset(center.x + arm, center.y),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawLine(
                            color = controlOnAccentColor,
                            start = Offset(center.x, center.y - arm),
                            end = Offset(center.x, center.y + arm),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    OutlinedButton(onClick = {
                        val transform = transformState.value
                        transformState.value = transform.copy(
                            scale = (transform.scale - 0.15f).coerceIn(minScale, maxScale)
                        )
                    }) { Text("−") }
                    OutlinedButton(onClick = {
                        val transform = transformState.value
                        transformState.value = transform.copy(
                            scale = (transform.scale + 0.15f).coerceIn(minScale, maxScale)
                        )
                    }) { Text("+") }
                }
            }

            if (explorationBreadcrumbVisible && explorationHistory.size > 1) {
                ExplorationBreadcrumb(
                    personIds = explorationHistory,
                    people = personById,
                    onDismiss = onHideExplorationBreadcrumb,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                )
            }

            selectedPerson?.let { person ->
                val inspectorModifier = if (useSideInspector) {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(360.dp)
                        .fillMaxHeight()
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.72f)
                }
                PersonInspector(
                    person = person,
                    people = personById,
                    relationships = allRelationships,
                    relationshipPath = relationshipPath,
                    showRelationshipPathInGraph = showRelationshipPathInGraph,
                    onClose = onClearSelection,
                    onOpenProfile = { onOpenPerson(person.personId) },
                    onShowRelationshipPath = onShowRelationshipPath,
                    modifier = inspectorModifier
                )
            }
        }
    }
}

@Composable
private fun ExplorationBreadcrumb(
    personIds: List<String>,
    people: Map<String, PersonListItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 3.dp,
        modifier = modifier.widthIn(max = 680.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "Jejak",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                personIds.forEach { personId ->
                    Text(
                        "  ›  ${people[personId]?.let { familiarName(it.fullName) } ?: "Person"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = "Sembunyikan jejak" }
            ) {
                Text("×")
            }
        }
    }
}

@Composable
private fun PersonGraphCard(
    person: PersonListItem,
    selected: Boolean,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val deceased = person.lifeStatus == "DECEASED"
    val cardColor = if (deceased) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (deceased) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (highlighted) {
        MaterialTheme.colorScheme.tertiary
    } else if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val age = if (person.lifeStatus == "ALIVE") calculateAge(person.birthDate) else null
    val shortName = familiarName(person.fullName)

    Surface(
        modifier = modifier.semantics {
            contentDescription = buildString {
                append(person.fullName)
                if (deceased) append(", telah meninggal")
                age?.let { append(", $it tahun") }
            }
            this.selected = selected
        },
        shape = RoundedCornerShape(14.dp),
        color = cardColor,
        contentColor = contentColor,
        border = BorderStroke(if (selected || highlighted) 2.dp else 1.dp, borderColor),
        shadowElevation = if (selected) 3.dp else 1.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(12.dp)
        ) {
            FallbackAvatar(
                gender = person.gender,
                muted = deceased,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = shortName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            age?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$it tahun",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.76f)
                )
            }
        }
    }
}

@Composable
private fun PlaceholderGraphCard(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FallbackAvatar(
    gender: String?,
    muted: Boolean,
    modifier: Modifier = Modifier
) {
    val background = if (muted) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val foreground = if (muted) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    Canvas(modifier = modifier) {
        drawCircle(background)
        drawCircle(
            color = foreground,
            radius = size.minDimension * 0.14f,
            center = Offset(size.width / 2f, size.height * 0.36f)
        )
        drawOval(
            color = foreground,
            topLeft = Offset(size.width * 0.24f, size.height * 0.55f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.52f, size.height * 0.30f)
        )
        if (gender == "FEMALE") {
            drawArc(
                color = foreground,
                startAngle = 185f,
                sweepAngle = 170f,
                useCenter = false,
                topLeft = Offset(size.width * 0.31f, size.height * 0.23f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.38f, size.height * 0.38f),
                style = Stroke(width = size.minDimension * 0.055f)
            )
        }
    }
}

@Composable
private fun PersonInspector(
    person: PersonListItem,
    people: Map<String, PersonListItem>,
    relationships: List<ExportRelationship>,
    relationshipPath: RelationshipPathResponse?,
    showRelationshipPathInGraph: Boolean,
    onClose: () -> Unit,
    onOpenProfile: () -> Unit,
    onShowRelationshipPath: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = remember {
        mutableStateMapOf(
            "identity" to true,
            "family" to false,
            "events" to false,
            "stories" to false
        )
    }
    val parentNames = relationships
        .filter { it.type == "PARENT_CHILD" && it.toPersonId == person.personId }
        .mapNotNull { people[it.fromPersonId]?.fullName }
    val childNames = relationships
        .filter { it.type == "PARENT_CHILD" && it.fromPersonId == person.personId }
        .mapNotNull { people[it.toPersonId]?.fullName }
    val spouseNames = relationships
        .filter {
            it.type == "SPOUSE" &&
                (it.fromPersonId == person.personId || it.toPersonId == person.personId)
        }
        .mapNotNull {
            val otherId = if (it.fromPersonId == person.personId) it.toPersonId else it.fromPersonId
            people[otherId]?.fullName
        }
    val age = if (person.lifeStatus == "ALIVE") calculateAge(person.birthDate) else null

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                FallbackAvatar(
                    gender = person.gender,
                    muted = person.lifeStatus == "DECEASED",
                    modifier = Modifier.size(72.dp)
                )
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(person.fullName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        when {
                            person.lifeStatus == "DECEASED" -> "Telah meninggal"
                            age != null -> "$age tahun"
                            else -> "Informasi kehidupan belum lengkap"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onClose) { Text("Tutup") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                relationshipPath?.let { path ->
                    RelationshipPathPanel(
                        path = path,
                        shownInGraph = showRelationshipPathInGraph,
                        onShowInGraph = onShowRelationshipPath
                    )
                }
                InspectorSection(
                    title = "Identitas & Kehidupan",
                    expanded = expanded["identity"] == true,
                    onToggle = { expanded["identity"] = expanded["identity"] != true }
                ) {
                    InspectorValue("Nama lengkap", person.fullName)
                    InspectorValue("Tanggal lahir", person.birthDate)
                    InspectorValue("Tempat lahir", person.birthPlace)
                    InspectorValue(
                        "Status",
                        when (person.lifeStatus) {
                            "ALIVE" -> age?.let { "$it tahun" } ?: "Masih hidup"
                            "DECEASED" -> "Telah meninggal"
                            else -> "Belum diketahui"
                        }
                    )
                }
                InspectorSection(
                    title = "Keluarga & Hubungan",
                    expanded = expanded["family"] == true,
                    onToggle = { expanded["family"] = expanded["family"] != true }
                ) {
                    InspectorValue("Orang tua", parentNames.takeIf { it.isNotEmpty() }?.joinToString())
                    InspectorValue("Pasangan", spouseNames.takeIf { it.isNotEmpty() }?.joinToString())
                    InspectorValue("Anak", childNames.takeIf { it.isNotEmpty() }?.joinToString())
                }
                if (person.birthDate != null || person.deceasedAt != null) {
                    InspectorSection(
                        title = "Peristiwa Kehidupan",
                        expanded = expanded["events"] == true,
                        onToggle = { expanded["events"] = expanded["events"] != true }
                    ) {
                        InspectorValue("Kelahiran", person.birthDate)
                        InspectorValue("Meninggal", person.deceasedAt)
                    }
                }
                if (!person.notes.isNullOrBlank()) {
                    InspectorSection(
                        title = "Cerita & Catatan",
                        expanded = expanded["stories"] == true,
                        onToggle = { expanded["stories"] = expanded["stories"] != true }
                    ) {
                        Text(person.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
            TextButton(
                onClick = onOpenProfile,
                modifier = Modifier.align(Alignment.End).padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Lihat profil lengkap")
            }
        }
    }
}

@Composable
private fun RelationshipPathPanel(
    path: RelationshipPathResponse,
    shownInGraph: Boolean,
    onShowInGraph: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Jalur hubungan terpendek",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (!path.found || path.people.isEmpty()) {
                Text(
                    "Hubungan belum ditemukan dalam data keluarga.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Text(
                    path.people.first().fullName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                path.edges.forEachIndexed { index, edge ->
                    val nextName = path.people.getOrNull(index + 1)?.fullName ?: "Person"
                    Text(
                        "↓ ${relationshipPathLabel(edge.type, edge.meta, edge.direction, nextName)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                    Text(
                        nextName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (path.edges.isNotEmpty()) {
                    TextButton(
                        enabled = !shownInGraph,
                        onClick = onShowInGraph,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                    ) {
                        Text(if (shownInGraph) "Jalur disorot" else "Tampilkan jalur di pohon")
                    }
                }
            }
        }
    }
}

internal fun relationshipPathLabel(
    type: String,
    meta: String?,
    direction: String,
    nextName: String
): String = when (type) {
    "PARENT_CHILD" -> when {
        meta == "ADOPTIVE" && direction == "FORWARD" -> "orang tua angkat dari $nextName"
        meta == "ADOPTIVE" -> "anak angkat dari $nextName"
        direction == "FORWARD" -> "orang tua dari $nextName"
        else -> "anak dari $nextName"
    }
    "SPOUSE" -> "pasangan dari $nextName"
    else -> "terhubung dengan $nextName"
}

@Composable
private fun InspectorSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                if (expanded) "−" else "+",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp
            )
        }
        if (expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                content = content
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    }
}

@Composable
private fun InspectorValue(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun familiarName(fullName: String): String {
    val normalized = fullName.trim().replace(Regex("\\s+"), " ")
    return normalized.substringBefore(' ').ifBlank { fullName }
}

private fun calculateAge(birthDate: String?): Int? {
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(birthDate ?: return null)
        ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    val today = Calendar.getInstance()
    var age = today.get(Calendar.YEAR) - year
    val currentMonth = today.get(Calendar.MONTH) + 1
    val currentDay = today.get(Calendar.DAY_OF_MONTH)
    if (currentMonth < month || (currentMonth == month && currentDay < day)) age--
    return age.takeIf { it in 0..130 }
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
    childrenCollapsed: Boolean,
    parentsCollapsed: Boolean,
    siblingsCollapsed: Boolean,
    tileW: Dp,
    tileH: Dp,
    spouseGapX: Dp,
    siblingGapX: Dp,
    rankGapY: Dp,
    margin: Dp
): GraphLayout {
    val activeSpouseId = findActiveSpouseId(centerPersonId, relations, allRelationships)
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

    val siblingIds = findSiblingIds(centerPersonId, allRelationships, persons)
    val orderedGeneration = orderChildren((siblingIds + centerPersonId).distinct(), persons)
    val centerIndex = orderedGeneration.indexOf(centerPersonId).coerceAtLeast(0)
    val olderSiblingIds = orderedGeneration.take(centerIndex).filter { it != activeSpouseId }
    val youngerSiblingIds = orderedGeneration.drop(centerIndex + 1).filter { it != activeSpouseId }
    val centerBounds = centerNode.bounds()
    val completeSiblingNodes = buildList {
        var leftCursor = centerBounds.left.value - siblingGapX.value
        olderSiblingIds.asReversed().forEach { siblingId ->
            leftCursor -= tileW.value
            add(
                personNode(
                    id = siblingId,
                    label = displayName(siblingId),
                    role = "SIBLING_LEFT",
                    x = leftCursor.dp,
                    y = 0.dp,
                    tileW = tileW,
                    tileH = tileH
                )
            )
            leftCursor -= siblingGapX.value
        }
        var rightCursor = centerBounds.right.value + siblingGapX.value
        youngerSiblingIds.forEach { siblingId ->
            add(
                personNode(
                    id = siblingId,
                    label = displayName(siblingId),
                    role = "SIBLING_RIGHT",
                    x = rightCursor.dp,
                    y = 0.dp,
                    tileW = tileW,
                    tileH = tileH
                )
            )
            rightCursor += tileW.value + siblingGapX.value
        }
    }

    val childrenY = (rankGapY.value + tileH.value).dp
    val completeTree = preprocessTree(
        centerPersonId = centerPersonId,
        activeSpouseId = activeSpouseId,
        relations = relations,
        allRelationships = allRelationships,
        persons = persons,
        displayName = displayName
    )

    val childItems = completeTree.children.map { child ->
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
    val completeChildNodes = childPlaced.map { placed ->
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

    val childNodes = if (childrenCollapsed) emptyList() else completeChildNodes
    val siblingNodes = if (siblingsCollapsed) emptyList() else completeSiblingNodes
    val allNodesForBounds = parentNodes + completeSiblingNodes + centerNode + completeChildNodes
    val visibleNodes =
        (if (parentsCollapsed) emptyList() else parentNodes) +
            siblingNodes + centerNode + childNodes
    val minX = allNodesForBounds.minOf { it.bounds().left.value }
    val maxX = allNodesForBounds.maxOf { it.bounds().right.value }
    val minY = allNodesForBounds.minOf { it.bounds().top.value }
    val maxY = allNodesForBounds.maxOf { it.bounds().bottom.value }

    val width = (maxX - minX).dp + margin * 2f
    val height = (maxY - minY).dp + margin * 2f
    val shiftX = (-minX).dp + margin
    val shiftY = (-minY).dp + margin

    val shiftedNodes = visibleNodes.map { it.shift(shiftX, shiftY) }
    val shiftedCenter = shiftedNodes.first { it.role == "CENTER" }
    val shiftedParents = shiftedNodes.filter { it.role == "PARENT" }
    val shiftedSiblings = shiftedNodes.filter { it.role.startsWith("SIBLING") }
    val shiftedChildren = shiftedNodes.filter { it.role == "CHILD" }
    val parentLineageEdges = parentRelationships.mapNotNull { relationship ->
        val from = shiftedNodes.tileRect(relationship.fromPersonId)?.bottomCenter()
        val to = shiftedNodes.tileRect(relationship.toPersonId)?.topCenter()
        if (from != null && to != null) {
            LineageEdge(setOf(relationship.relationshipId), from, to, relationship.meta)
        } else null
    }
    val childLineageEdges = completeTree.children.mapNotNull { child ->
        val to = shiftedNodes.tileRect(child.id)?.topCenter() ?: return@mapNotNull null
        val matchingRelationships = allRelationships.filter {
            it.type == "PARENT_CHILD" &&
                it.toPersonId == child.id &&
                it.fromPersonId in centerMemberIds
        }
        LineageEdge(
            relationshipIds = matchingRelationships.mapTo(mutableSetOf()) { it.relationshipId }
                .ifEmpty { setOf("child:${child.id}") },
            from = shiftedCenter.anchorBottom(),
            to = to,
            meta = matchingRelationships.firstOrNull()?.meta
        )
    }
    val siblingLineageEdges = allRelationships.mapNotNull { relationship ->
        if (
            relationship.type != "PARENT_CHILD" ||
            relationship.toPersonId !in siblingIds
        ) return@mapNotNull null
        val from = shiftedNodes.tileRect(relationship.fromPersonId)?.bottomCenter()
        val to = shiftedNodes.tileRect(relationship.toPersonId)?.topCenter()
        if (from != null && to != null) {
            LineageEdge(setOf(relationship.relationshipId), from, to, relationship.meta)
        } else null
    }

    return GraphLayout(
        center = shiftedCenter,
        parents = shiftedParents,
        siblings = shiftedSiblings,
        hasLeftSiblings = olderSiblingIds.isNotEmpty(),
        hasRightSiblings = youngerSiblingIds.isNotEmpty(),
        children = shiftedChildren,
        nodes = shiftedNodes,
        lineageEdges = parentLineageEdges + siblingLineageEdges + childLineageEdges,
        width = width,
        height = height
    )
}

private fun augmentLayoutWithRelationshipPath(
    base: GraphLayout,
    relationshipPath: RelationshipPathResponse,
    displayName: (String) -> String,
    tileW: Dp,
    tileH: Dp,
    spouseGapX: Dp,
    siblingGapX: Dp,
    rankGapY: Dp,
    margin: Dp
): GraphLayout {
    val pathPersonIds = relationshipPath.people.map { it.personId }
    if (pathPersonIds.size < 2) return base

    val positions = base.nodes
        .flatMap(GraphNode::tiles)
        .associate { it.id to it.rect }
        .toMutableMap()
    val occupied = base.nodes.flatMap(GraphNode::tiles).map { it.rect }.toMutableList()
    val extraNodes = mutableListOf<GraphNode>()
    val horizontalStep = tileW + siblingGapX
    val verticalStep = tileH + rankGapY

    fun RectDp.overlaps(other: RectDp, padding: Dp = 8.dp): Boolean =
        left < other.right + padding &&
            right + padding > other.left &&
            top < other.bottom + padding &&
            bottom + padding > other.top

    fun findFreeRect(initial: RectDp, direction: Int = 1): RectDp {
        var candidate = initial
        repeat(24) {
            if (occupied.none { candidate.overlaps(it) }) return candidate
            candidate = candidate.shift(horizontalStep * direction.toFloat(), 0.dp)
        }
        return candidate
    }

    fun addPathNode(personId: String, proposed: RectDp, direction: Int = 1): RectDp {
        val rect = findFreeRect(proposed, direction)
        extraNodes += personNode(
            id = personId,
            label = displayName(personId),
            role = "PATH",
            x = rect.x,
            y = rect.y,
            tileW = tileW,
            tileH = tileH
        )
        positions[personId] = rect
        occupied += rect
        return rect
    }

    fun traversalGenerationDelta(index: Int): Int {
        val edge = relationshipPath.edges.getOrNull(index) ?: return 0
        if (edge.type != "PARENT_CHILD") return 0
        val currentId = pathPersonIds[index]
        val nextId = pathPersonIds[index + 1]
        return when {
            edge.fromPersonId == currentId && edge.toPersonId == nextId -> 1
            edge.toPersonId == currentId && edge.fromPersonId == nextId -> -1
            edge.direction == "FORWARD" -> 1
            else -> -1
        }
    }

    var anchorIndex = pathPersonIds.indexOfFirst(positions::containsKey)
    if (anchorIndex < 0) {
        anchorIndex = 0
        val rightEdge = base.nodes.maxOf { it.bounds().right.value }.dp
        addPathNode(
            personId = pathPersonIds.first(),
            proposed = RectDp(
                x = rightEdge + siblingGapX,
                y = base.center.bounds().top,
                w = tileW,
                h = tileH
            )
        )
    }

    for (index in (anchorIndex + 1)..pathPersonIds.lastIndex) {
        val personId = pathPersonIds[index]
        if (positions.containsKey(personId)) continue
        val previous = positions[pathPersonIds[index - 1]] ?: continue
        val edge = relationshipPath.edges.getOrNull(index - 1)
        val delta = traversalGenerationDelta(index - 1)
        val proposed = when {
            edge?.type == "SPOUSE" -> RectDp(
                previous.right + spouseGapX,
                previous.y,
                tileW,
                tileH
            )
            delta > 0 -> RectDp(previous.x, previous.y + verticalStep, tileW, tileH)
            delta < 0 -> RectDp(previous.x, previous.y - verticalStep, tileW, tileH)
            else -> RectDp(previous.x + horizontalStep, previous.y, tileW, tileH)
        }
        addPathNode(personId, proposed)
    }

    for (index in (anchorIndex - 1) downTo 0) {
        val personId = pathPersonIds[index]
        if (positions.containsKey(personId)) continue
        val next = positions[pathPersonIds[index + 1]] ?: continue
        val edge = relationshipPath.edges.getOrNull(index)
        val delta = traversalGenerationDelta(index)
        val proposed = when {
            edge?.type == "SPOUSE" -> RectDp(
                next.left - spouseGapX - tileW,
                next.y,
                tileW,
                tileH
            )
            delta > 0 -> RectDp(next.x, next.y - verticalStep, tileW, tileH)
            delta < 0 -> RectDp(next.x, next.y + verticalStep, tileW, tileH)
            else -> RectDp(next.x - horizontalStep, next.y, tileW, tileH)
        }
        addPathNode(personId, proposed, direction = -1)
    }

    if (extraNodes.isEmpty()) return base

    val existingRelationshipIds = base.lineageEdges
        .flatMapTo(mutableSetOf()) { it.relationshipIds }
    val supplementalEdges = relationshipPath.edges.mapIndexedNotNull { index, edge ->
        val currentId = pathPersonIds.getOrNull(index) ?: return@mapIndexedNotNull null
        val nextId = pathPersonIds.getOrNull(index + 1) ?: return@mapIndexedNotNull null
        val currentRect = positions[currentId] ?: return@mapIndexedNotNull null
        val nextRect = positions[nextId] ?: return@mapIndexedNotNull null
        if (edge.type == "SPOUSE") {
            val alreadyRenderedAsCouple = base.nodes.any { node ->
                node.spouseLine() != null &&
                    node.tiles().mapTo(mutableSetOf()) { it.id }.containsAll(setOf(currentId, nextId))
            }
            if (alreadyRenderedAsCouple) return@mapIndexedNotNull null
            LineageEdge(
                relationshipIds = setOf(edge.relationshipId),
                from = currentRect.center(),
                to = nextRect.center(),
                meta = edge.meta,
                type = "SPOUSE"
            )
        } else {
            if (edge.relationshipId in existingRelationshipIds) return@mapIndexedNotNull null
            val parentRect = positions[edge.fromPersonId] ?: return@mapIndexedNotNull null
            val childRect = positions[edge.toPersonId] ?: return@mapIndexedNotNull null
            LineageEdge(
                relationshipIds = setOf(edge.relationshipId),
                from = parentRect.bottomCenter(),
                to = childRect.topCenter(),
                meta = edge.meta
            )
        }
    }

    val combinedNodes = base.nodes + extraNodes
    val minX = combinedNodes.minOf { it.bounds().left.value }
    val minY = combinedNodes.minOf { it.bounds().top.value }
    val shiftX = (margin.value - minX).coerceAtLeast(0f).dp
    val shiftY = (margin.value - minY).coerceAtLeast(0f).dp
    val shiftedNodes = combinedNodes.map { it.shift(shiftX, shiftY) }
    val shiftedEdges = (base.lineageEdges + supplementalEdges).map { edge ->
        edge.copy(
            from = PointDp(edge.from.x + shiftX, edge.from.y + shiftY),
            to = PointDp(edge.to.x + shiftX, edge.to.y + shiftY)
        )
    }
    val maxX = shiftedNodes.maxOf { it.bounds().right.value }
    val maxY = shiftedNodes.maxOf { it.bounds().bottom.value }

    return base.copy(
        center = base.center.shift(shiftX, shiftY),
        parents = base.parents.map { it.shift(shiftX, shiftY) },
        siblings = base.siblings.map { it.shift(shiftX, shiftY) },
        children = base.children.map { it.shift(shiftX, shiftY) },
        nodes = shiftedNodes,
        lineageEdges = shiftedEdges,
        width = maxOf(base.width + shiftX, maxX.dp + margin),
        height = maxOf(base.height + shiftY, maxY.dp + margin)
    )
}

internal fun findSiblingIds(
    personId: String,
    allRelationships: List<ExportRelationship>,
    persons: List<PersonListItem>
): List<String> {
    if (allRelationships.isEmpty()) return emptyList()
    val parentIds = allRelationships
        .filter { it.type == "PARENT_CHILD" && it.toPersonId == personId }
        .map { it.fromPersonId }
        .toSet()
    if (parentIds.isEmpty()) return emptyList()
    val siblingIds = allRelationships
        .filter { it.type == "PARENT_CHILD" && it.fromPersonId in parentIds }
        .map { it.toPersonId }
        .filter { it != personId }
        .distinct()
    return orderChildren(siblingIds, persons)
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

private fun preprocessTree(
    centerPersonId: String,
    activeSpouseId: String?,
    relations: RelationsResponse,
    allRelationships: List<ExportRelationship>,
    persons: List<PersonListItem>,
    displayName: (String) -> String
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
    // TODO: integrate subtreeWidth/layout phases once available.
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
        }
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
