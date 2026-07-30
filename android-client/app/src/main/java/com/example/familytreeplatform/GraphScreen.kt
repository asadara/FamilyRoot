package com.example.familytreeplatform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.models.RelationshipPathResponse
import com.example.familytreeplatform.feature.graph.GraphQuickAddRequest
import com.example.familytreeplatform.feature.graph.GraphCardDetail
import com.example.familytreeplatform.feature.graph.GraphRenderMode
import com.example.familytreeplatform.feature.graph.GraphRenderRect
import com.example.familytreeplatform.feature.graph.GraphTextFallbackEntry
import com.example.familytreeplatform.feature.graph.GraphMinimapLine
import com.example.familytreeplatform.feature.graph.GraphMinimapOverview
import com.example.familytreeplatform.feature.graph.QuickRelationKind
import com.example.familytreeplatform.feature.graph.graphCardDetail
import com.example.familytreeplatform.feature.graph.graphRenderMode
import com.example.familytreeplatform.feature.graph.orderGraphTextFallbackEntries
import com.example.familytreeplatform.feature.graph.graphMinimapProjection
import com.example.familytreeplatform.feature.graph.graphViewportOffsetsForCenter
import com.example.familytreeplatform.feature.graph.minimapWorldPoint
import com.example.familytreeplatform.feature.graph.projectMinimapRect
import com.example.familytreeplatform.feature.graph.shouldShowGraphMinimap
import com.example.familytreeplatform.feature.graph.visibleWorldRect
import java.util.Calendar

private data class PointDp(val x: Dp, val y: Dp)

data class GraphPreferredPosition(
    val centerX: Float,
    val centerY: Float
)

private data class GraphViewportTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

private val PersonIdSetSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() }
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

private fun RectDp.toRenderRect(density: Density): GraphRenderRect = GraphRenderRect(
    left = with(density) { left.toPx() },
    top = with(density) { top.toPx() },
    right = with(density) { right.toPx() },
    bottom = with(density) { bottom.toPx() }
)

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

data class GraphExportTile(
    val id: String,
    val label: String,
    val role: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val gender: String? = null,
    val lifeStatus: String? = null,
    val age: Int? = null
)

data class GraphExportLine(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val type: String,
    val meta: String?
)

data class GraphExportPlaceholder(
    val label: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class GraphExportSnapshot(
    val width: Float,
    val height: Float,
    val tiles: List<GraphExportTile>,
    val spouseLines: List<GraphExportLine>,
    val lineageLines: List<GraphExportLine>,
    val placeholders: List<GraphExportPlaceholder> = emptyList()
)

private data class LineageEdge(
    val relationshipIds: Set<String>,
    val from: PointDp,
    val to: PointDp,
    val meta: String?,
    val type: String = "PARENT_CHILD",
    val sourceExitY: Dp = from.y
)

private fun LineageEdge.toRenderRect(density: Density): GraphRenderRect {
    val fromX = with(density) { from.x.toPx() }
    val fromY = with(density) { from.y.toPx() }
    val sourceY = with(density) { sourceExitY.toPx() }
    val toX = with(density) { to.x.toPx() }
    val toY = with(density) { to.y.toPx() }
    return GraphRenderRect(
        left = minOf(fromX, toX),
        top = minOf(fromY, sourceY, toY),
        right = maxOf(fromX, toX),
        bottom = maxOf(fromY, sourceY, toY)
    )
}

internal fun lineageHubY(sourceExitY: Float, targetY: Float): Float =
    sourceExitY + (targetY - sourceExitY) / 2f

private enum class BranchDirection { PARENTS, CHILDREN, PARTNERSHIPS }

private data class BranchControl(
    val personId: String,
    val direction: BranchDirection,
    val point: PointDp,
    val expanded: Boolean,
    val horizontalSide: Int = 0,
    val childFamilyKey: String? = null
)

private data class QuickAddControl(
    val request: GraphQuickAddRequest,
    val point: PointDp
)

private data class PartnershipSwapControl(
    val pairKey: String,
    val firstPersonId: String,
    val secondPersonId: String,
    val point: PointDp
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

internal enum class GraphGenerationFilter { ALL, SAME, ANCESTORS, DESCENDANTS }

@Composable
fun GraphScreen(
    centerPersonId: String,
    selectedPersonId: String?,
    inspectedPersonId: String? = null,
    persons: List<PersonListItem>,
    profilePhotoUrls: Map<String, String> = emptyMap(),
    relations: RelationsResponse?,
    allRelationships: List<ExportRelationship>,
    explorationHistory: List<String> = emptyList(),
    explorationBreadcrumbVisible: Boolean = true,
    relationshipPath: RelationshipPathResponse? = null,
    showRelationshipPathInGraph: Boolean = false,
    resetViewRequest: Int = 0,
    revealPersonId: String? = null,
    revealPersonRequest: Int = 0,
    preferredUnconnectedPositions: Map<String, GraphPreferredPosition> = emptyMap(),
    autoExpandChildFamilyParentIds: Set<String>? = null,
    autoExpandChildFamilyRequest: Int = 0,
    onSelectPerson: (String) -> Unit,
    onInspectPerson: (String) -> Unit = {},
    onFocusPerson: (String) -> Unit = {},
    canEditRelationships: Boolean = true,
    onAddPersonRequest: (GraphPreferredPosition?) -> Unit = {},
    onQuickAddRequest: (GraphQuickAddRequest) -> Unit = {},
    onConnectPersons: (String, String) -> Unit = { _, _ -> },
    onClearSelection: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onShowRelationshipPath: () -> Unit = {},
    onHideExplorationBreadcrumb: () -> Unit = {},
    onExportSnapshotChanged: (GraphExportSnapshot) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (relations == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

    val tileW = 96.dp
    val tileH = 108.dp
    val cardGapX = 28.dp
    val spouseGapX = cardGapX
    val siblingGapX = cardGapX
    val rankGapY = 44.dp
    val margin = 80.dp

    var childrenCollapsed by rememberSaveable(centerPersonId) { mutableStateOf(false) }
    var parentsCollapsed by rememberSaveable(centerPersonId) { mutableStateOf(false) }
    var generationFilter by rememberSaveable(centerPersonId) {
        mutableStateOf(GraphGenerationFilter.ALL)
    }
    var expandedParentPersonIds by rememberSaveable(
        centerPersonId,
        stateSaver = PersonIdSetSaver
    ) { mutableStateOf(emptySet()) }
    var expandedChildFamilyKeys by rememberSaveable(
        centerPersonId,
        stateSaver = PersonIdSetSaver
    ) { mutableStateOf(emptySet()) }
    var expandedPartnershipPersonIds by rememberSaveable(
        centerPersonId,
        stateSaver = PersonIdSetSaver
    ) { mutableStateOf(emptySet()) }
    var swappedPartnershipKeys by rememberSaveable(
        stateSaver = PersonIdSetSaver
    ) { mutableStateOf(emptySet()) }
    val relationshipIndex = remember(allRelationships) {
        LineageRelationshipIndex.from(allRelationships)
    }
    LaunchedEffect(autoExpandChildFamilyRequest, centerPersonId) {
        val parentIds = autoExpandChildFamilyParentIds.orEmpty()
        if (autoExpandChildFamilyRequest > 0 && parentIds.isNotEmpty()) {
            expandedChildFamilyKeys =
                expandedChildFamilyKeys + childFamilyBranchKey(parentIds)
        }
    }

    val baseLayout by remember(
        centerPersonId,
        persons,
        relations,
        allRelationships,
        childrenCollapsed,
        parentsCollapsed,
        swappedPartnershipKeys
    ) {
        derivedStateOf {
            buildCoupleGraphLayout(
                centerPersonId = centerPersonId,
                relations = relations,
                allRelationships = allRelationships,
                displayName = displayName,
                persons = persons,
                childrenCollapsed = childrenCollapsed,
                parentsCollapsed = parentsCollapsed,
                siblingsCollapsed = false,
                tileW = tileW,
                tileH = tileH,
                spouseGapX = spouseGapX,
                siblingGapX = siblingGapX,
                rankGapY = rankGapY,
                margin = margin,
                swappedPartnershipKeys = swappedPartnershipKeys
            )
        }
    }
    val baseVisiblePersonIds = remember(baseLayout) {
        baseLayout.nodes
            .flatMap(GraphNode::tiles)
            .mapTo(linkedSetOf()) { it.id }
    }
    val layout by remember(
        baseLayout,
        persons,
        allRelationships,
        expandedParentPersonIds,
        expandedChildFamilyKeys,
        expandedPartnershipPersonIds,
        swappedPartnershipKeys,
        relationshipPath,
        showRelationshipPathInGraph,
        selectedPersonId,
        inspectedPersonId,
        preferredUnconnectedPositions
    ) {
        derivedStateOf {
            val progressiveLayout = augmentLayoutWithProgressiveLineage(
                base = baseLayout,
                relationships = allRelationships,
                expandedParentPersonIds = expandedParentPersonIds,
                expandedChildFamilyKeys = expandedChildFamilyKeys,
                expandedPartnershipPersonIds = expandedPartnershipPersonIds,
                displayName = displayName,
                tileW = tileW,
                tileH = tileH,
                spouseGapX = spouseGapX,
                siblingGapX = siblingGapX,
                rankGapY = rankGapY,
                margin = margin,
                swappedPartnershipKeys = swappedPartnershipKeys
            )
            val connectedLayout = if (showRelationshipPathInGraph && relationshipPath?.found == true) {
                augmentLayoutWithRelationshipPath(
                    base = progressiveLayout,
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
                progressiveLayout
            }
            val completeLayout = augmentLayoutWithUnconnectedPersons(
                base = connectedLayout,
                persons = persons,
                relationships = allRelationships,
                displayName = displayName,
                tileW = tileW,
                tileH = tileH,
                siblingGapX = siblingGapX,
                rankGapY = rankGapY,
                margin = margin,
                preferredPositions = preferredUnconnectedPositions
            )
            attachFocusedCareRelationships(
                base = completeLayout,
                relationships = allRelationships,
                focusedPersonId = inspectedPersonId ?: selectedPersonId ?: centerPersonId
            )
        }
    }

    val layoutTileCount = remember(layout) {
        layout.nodes.sumOf { it.tiles().size }
    }
    if (graphRenderMode(layoutTileCount) == GraphRenderMode.TEXT_FALLBACK) {
        LaunchedEffect(centerPersonId, layoutTileCount) {
            onExportSnapshotChanged(
                GraphExportSnapshot(
                    width = 1f,
                    height = 1f,
                    tiles = emptyList(),
                    spouseLines = emptyList(),
                    lineageLines = emptyList()
                )
            )
        }
        LargeGraphTextFallback(
            persons = persons,
            centerPersonId = centerPersonId,
            renderedTileCount = layoutTileCount,
            onOpenPerson = onOpenPerson,
            onBack = onBack,
            modifier = modifier
        )
        return
    }

    val density = LocalDensity.current
    LaunchedEffect(layout, personById) {
        onExportSnapshotChanged(layout.toExportSnapshot(personById))
    }
    val generationLevels = remember(centerPersonId, allRelationships) {
        familyGenerationLevels(centerPersonId, allRelationships)
    }
    val generationRootIds = remember(centerPersonId, allRelationships) {
        buildSet {
            add(centerPersonId)
            allRelationships
                .filter {
                    it.type == "SPOUSE" &&
                        (it.fromPersonId == centerPersonId || it.toPersonId == centerPersonId)
                }
                .forEach {
                    add(if (it.fromPersonId == centerPersonId) it.toPersonId else it.fromPersonId)
                }
        }
    }
    val generationVisiblePersonIds = remember(
        persons,
        generationLevels,
        generationFilter,
        generationRootIds
    ) {
        persons.mapNotNullTo(mutableSetOf()) { person ->
            val generation = generationLevels[person.personId]
            val visible = when (generationFilter) {
                GraphGenerationFilter.ALL -> true
                GraphGenerationFilter.SAME -> generation == 0
                GraphGenerationFilter.ANCESTORS -> generation != null && generation < 0
                GraphGenerationFilter.DESCENDANTS -> generation != null && generation > 0
            }
            person.personId.takeIf { visible || it in generationRootIds }
        }
    }
    val visibleRelationshipIds = remember(allRelationships, generationVisiblePersonIds) {
        allRelationships
            .filter {
                it.fromPersonId in generationVisiblePersonIds &&
                    it.toPersonId in generationVisiblePersonIds
            }
            .mapTo(mutableSetOf()) { it.relationshipId }
    }
    val tiles = remember(layout, generationVisiblePersonIds) {
        layout.nodes.flatMap { it.tiles() }.filter { it.id in generationVisiblePersonIds }
    }
    val partnershipSwapControls = remember(tiles, allRelationships) {
        val tilesById = tiles.associateBy { it.id }
        allRelationships
            .asSequence()
            .filter { it.type == "SPOUSE" }
            .distinctBy {
                partnershipPairKey(it.fromPersonId, it.toPersonId)
            }
            .mapNotNull { relationship ->
                val firstTile = tilesById[relationship.fromPersonId]
                    ?: return@mapNotNull null
                val secondTile = tilesById[relationship.toPersonId]
                    ?: return@mapNotNull null
                val firstCenter = firstTile.rect.center()
                val secondCenter = secondTile.rect.center()
                PartnershipSwapControl(
                    pairKey = partnershipPairKey(
                        relationship.fromPersonId,
                        relationship.toPersonId
                    ),
                    firstPersonId = relationship.fromPersonId,
                    secondPersonId = relationship.toPersonId,
                    point = PointDp(
                        x = (firstCenter.x + secondCenter.x) / 2f,
                        y = (firstCenter.y + secondCenter.y) / 2f - 28.dp
                    )
                )
            }
            .toList()
    }
    val swapPartnership: (String) -> Unit = { pairKey ->
        swappedPartnershipKeys = if (pairKey in swappedPartnershipKeys) {
            swappedPartnershipKeys - pairKey
        } else {
            swappedPartnershipKeys + pairKey
        }
    }
    val linkHandleTile = tiles.firstOrNull { it.id == selectedPersonId }
    val linkHandlePoint = linkHandleTile?.let { tile ->
        PointDp(tile.rect.right + 18.dp, tile.rect.bottom - 18.dp)
    }
    var linkDragSourceId by remember { mutableStateOf<String?>(null) }
    var linkDragPoint by remember { mutableStateOf<PointDp?>(null) }
    val activeSpouseId = remember(centerPersonId, relations, allRelationships) {
        findActiveSpouseId(centerPersonId, relations, allRelationships)
    }
    val centerMemberIds = remember(centerPersonId, activeSpouseId) {
        setOfNotNull(centerPersonId, activeSpouseId)
    }
    val centerControlsVisible = selectedPersonId in centerMemberIds
    val branchControls = remember(
        tiles,
        centerMemberIds,
        allRelationships,
        expandedParentPersonIds,
        expandedChildFamilyKeys,
        expandedPartnershipPersonIds,
        selectedPersonId
    ) {
        buildList {
            val visiblePersonIds = tiles.mapTo(mutableSetOf()) { it.id }
            tiles
                .distinctBy { it.id }
                .filter { it.id == selectedPersonId }
                .forEach { tile ->
                    if (tile.id !in centerMemberIds) {
                        val parentPersonIds = relationshipIndex.recordedParentPersonIds(tile.id)
                        if (
                            tile.id in expandedParentPersonIds ||
                            parentPersonIds.any { it !in visiblePersonIds }
                        ) {
                            add(
                                BranchControl(
                                    personId = tile.id,
                                    direction = BranchDirection.PARENTS,
                                    point = PointDp(tile.rect.topCenter().x, tile.rect.top - 22.dp),
                                    expanded = tile.id in expandedParentPersonIds
                                )
                            )
                        }
                    }
                    val childFamilies = recordedChildFamilies(tile.id, relationshipIndex)
                    childFamilies.forEachIndexed { familyIndex, family ->
                        val expanded = family.key in expandedChildFamilyKeys
                        val hasHiddenFamilyMember =
                            family.childPersonIds.any { it !in visiblePersonIds } ||
                                family.parentPersonIds.any { it !in visiblePersonIds }
                        if (
                            tile.id in centerMemberIds ||
                            (!expanded && !hasHiddenFamilyMember)
                        ) return@forEachIndexed

                        val coParentId = family.parentPersonIds
                            .firstOrNull { it != tile.id }
                        val coParentRect = coParentId?.let { partnerId ->
                            tiles.firstOrNull { it.id == partnerId }?.rect
                        }
                        val recordedPartnership = coParentId != null &&
                            relationshipIndex.partnerships(tile.id).any {
                                it.otherPersonId(tile.id) == coParentId
                            }
                        if (recordedPartnership && coParentRect == null) {
                            // The side control reveals the direct partner first. Once
                            // visible, the child control belongs to that exact ring.
                            return@forEachIndexed
                        }
                        val childPoint = if (coParentRect != null) {
                            PointDp(
                                x = (tile.rect.center().x + coParentRect.center().x) / 2f,
                                y = maxOf(tile.rect.bottom, coParentRect.bottom) + 22.dp
                            )
                        } else {
                            val offsetIndex =
                                familyIndex - (childFamilies.lastIndex / 2f)
                            PointDp(
                                tile.rect.bottomCenter().x + 34.dp * offsetIndex,
                                tile.rect.bottom + 22.dp
                            )
                        }
                        add(
                            BranchControl(
                                personId = tile.id,
                                direction = BranchDirection.CHILDREN,
                                point = childPoint,
                                expanded = expanded,
                                childFamilyKey = family.key
                            )
                        )
                    }
                    val partnershipPersonIds =
                        relationshipIndex.recordedPartnershipPersonIds(tile.id)
                    if (
                        tile.id in expandedPartnershipPersonIds ||
                        partnershipPersonIds.any { it !in visiblePersonIds }
                    ) {
                        val partnershipRelationships = relationshipIndex.partnerships(tile.id)
                        val hiddenRelationships = partnershipRelationships.filter {
                            it.otherPersonId(tile.id) !in visiblePersonIds
                        }
                        val sideSource = hiddenRelationships.ifEmpty { partnershipRelationships }
                        val currentPartnerRect = partnershipRelationships
                            .filter(::isCurrentPartnership)
                            .lastOrNull()
                            ?.otherPersonId(tile.id)
                            ?.let { partnerId ->
                                tiles.firstOrNull { it.id == partnerId }?.rect
                            }
                        val opensHistoricalPartners =
                            hiddenRelationships.isNotEmpty() &&
                                hiddenRelationships.all { !isCurrentPartnership(it) }
                        val horizontalSide = if (
                            opensHistoricalPartners &&
                            currentPartnerRect != null
                        ) {
                            if (currentPartnerRect.center().x < tile.rect.center().x) 1 else -1
                        } else if (sideSource.any {
                                partnershipHorizontalSlot(
                                    personId = tile.id,
                                    relationshipId = it.relationshipId,
                                    relationships = allRelationships
                                ) < 0
                            }
                        ) {
                            -1
                        } else {
                            1
                        }
                        val visiblePartnershipRects = buildList {
                            add(tile.rect)
                            partnershipPersonIds.forEach { partnerId ->
                                tiles.firstOrNull { it.id == partnerId }
                                    ?.rect
                                    ?.let(::add)
                            }
                        }
                        val partnershipBlockLeft =
                            visiblePartnershipRects.minOf { it.left }
                        val partnershipBlockRight =
                            visiblePartnershipRects.maxOf { it.right }
                        val partnershipBlockTop =
                            visiblePartnershipRects.minOf { it.top }
                        add(
                            BranchControl(
                                personId = tile.id,
                                direction = BranchDirection.PARTNERSHIPS,
                                point = PointDp(
                                    if (horizontalSide < 0) {
                                        partnershipBlockLeft - 22.dp
                                    } else {
                                        partnershipBlockRight + 22.dp
                                    },
                                    partnershipBlockTop - 22.dp
                                ),
                                expanded = tile.id in expandedPartnershipPersonIds,
                                horizontalSide = horizontalSide
                            )
                        )
                    }
                }
        }
    }
    fun currentExpansionState() = ProgressiveLineageExpansionState(
        parentPersonIds = expandedParentPersonIds,
        childFamilyKeys = expandedChildFamilyKeys,
        partnershipPersonIds = expandedPartnershipPersonIds
    )
    fun applyExpansionState(state: ProgressiveLineageExpansionState) {
        expandedParentPersonIds = state.parentPersonIds
        expandedChildFamilyKeys = state.childFamilyKeys
        expandedPartnershipPersonIds = state.partnershipPersonIds
    }
    val toggleBranch: (BranchControl) -> Unit = { control ->
        val before = currentExpansionState()
        val afterRootToggle = when (control.direction) {
            BranchDirection.PARENTS -> {
                before.copy(
                    parentPersonIds = if (control.personId in before.parentPersonIds) {
                        before.parentPersonIds - control.personId
                    } else {
                        before.parentPersonIds + control.personId
                    }
                )
            }
            BranchDirection.CHILDREN -> {
                val familyKey = requireNotNull(control.childFamilyKey)
                before.copy(
                    childFamilyKeys = if (familyKey in before.childFamilyKeys) {
                        before.childFamilyKeys - familyKey
                    } else {
                        before.childFamilyKeys + familyKey
                    }
                )
            }
            BranchDirection.PARTNERSHIPS -> {
                before.copy(
                    partnershipPersonIds =
                    if (control.personId in before.partnershipPersonIds) {
                        before.partnershipPersonIds - control.personId
                    } else {
                        before.partnershipPersonIds + control.personId
                    }
                )
            }
        }
        val collapsing = when (control.direction) {
            BranchDirection.PARENTS ->
                control.personId in before.parentPersonIds
            BranchDirection.CHILDREN ->
                control.childFamilyKey in before.childFamilyKeys
            BranchDirection.PARTNERSHIPS ->
                control.personId in before.partnershipPersonIds
        }
        applyExpansionState(
            if (collapsing) {
                pruneHiddenLineageExpansions(
                    beforeBaseVisiblePersonIds = baseVisiblePersonIds,
                    before = before,
                    afterRootToggle = afterRootToggle,
                    relationships = allRelationships
                )
            } else {
                afterRootToggle
            }
        )
    }
    val missingRelationshipControls = remember(
        selectedPersonId,
        tiles,
        layout.nodes,
        allRelationships,
        persons
    ) {
        val selectedId = selectedPersonId ?: return@remember emptyList()
        val tile = tiles.firstOrNull { it.id == selectedId } ?: return@remember emptyList()
        val anchorName = persons.firstOrNull { it.personId == selectedId }?.fullName
            ?: tile.label
        val parentIds = relationshipIndex.recordedParentPersonIds(selectedId)
        val partnerIds = relationshipIndex.recordedPartnershipPersonIds(selectedId)
        buildList {
            if (parentIds.isEmpty()) {
                add(
                    QuickAddControl(
                        GraphQuickAddRequest(selectedId, anchorName, QuickRelationKind.PARENT),
                        PointDp(tile.rect.topCenter().x, tile.rect.top - 22.dp)
                    )
                )
            }
            val visiblePersonIds = tiles.mapTo(mutableSetOf()) { it.id }
            val visiblePartnerIds = partnerIds.filter { it in visiblePersonIds }
            if (visiblePartnerIds.isNotEmpty()) {
                visiblePartnerIds.forEach { coParentId ->
                    val coParentName = persons
                        .firstOrNull { it.personId == coParentId }
                        ?.fullName
                    val coParentRect = tiles.first { it.id == coParentId }.rect
                    val ringX = (tile.rect.center().x + coParentRect.center().x) / 2f
                    val ringOffset = if (coParentRect.center().x < tile.rect.center().x) {
                        (-32).dp
                    } else {
                        32.dp
                    }
                    add(
                        QuickAddControl(
                            GraphQuickAddRequest(
                                anchorPersonId = selectedId,
                                anchorName = anchorName,
                                kind = QuickRelationKind.CHILD,
                                coParentId = coParentId,
                                coParentName = coParentName
                            ),
                            PointDp(
                                x = ringX + ringOffset,
                                y = maxOf(tile.rect.bottom, coParentRect.bottom) + 22.dp
                            )
                        )
                    )
                }
            } else if (partnerIds.isEmpty()) {
                add(
                    QuickAddControl(
                        GraphQuickAddRequest(
                            anchorPersonId = selectedId,
                            anchorName = anchorName,
                            kind = QuickRelationKind.CHILD
                        ),
                        PointDp(tile.rect.bottomCenter().x + 32.dp, tile.rect.bottom + 22.dp)
                    )
                )
            }
            if (partnerIds.isEmpty()) {
                add(
                    QuickAddControl(
                        GraphQuickAddRequest(selectedId, anchorName, QuickRelationKind.PARTNER),
                        PointDp(tile.rect.right + 22.dp, tile.rect.center().y)
                    )
                )
            }
        }
    }
    val quickAddControls = if (canEditRelationships) missingRelationshipControls else emptyList()
    val lockedControls = if (canEditRelationships) emptyList() else missingRelationshipControls
    val hasParents = remember(centerMemberIds, relations, allRelationships) {
        if (allRelationships.isNotEmpty()) {
            allRelationships.any {
                it.isLineageParentChild() && it.toPersonId in centerMemberIds
            }
        } else {
            relations.parents.isNotEmpty()
        }
    }
    val hasChildren = remember(centerPersonId, activeSpouseId, relations, allRelationships) {
        collectChildrenForParentGroup(
            parentPersonIds = setOfNotNull(centerPersonId, activeSpouseId),
            relations = relations,
            allRelationships = allRelationships
        ).isNotEmpty()
    }
    fun visibleBasePersonIds(
        collapseParents: Boolean,
        collapseChildren: Boolean
    ): Set<String> = buildCoupleGraphLayout(
        centerPersonId = centerPersonId,
        relations = relations,
        allRelationships = allRelationships,
        displayName = displayName,
        persons = persons,
        childrenCollapsed = collapseChildren,
        parentsCollapsed = collapseParents,
        siblingsCollapsed = false,
        tileW = tileW,
        tileH = tileH,
        spouseGapX = spouseGapX,
        siblingGapX = siblingGapX,
        rankGapY = rankGapY,
        margin = margin,
        swappedPartnershipKeys = swappedPartnershipKeys
    ).nodes.flatMap(GraphNode::tiles).mapTo(linkedSetOf()) { it.id }

    val toggleCenterParents: () -> Unit = {
        if (!parentsCollapsed) {
            val before = currentExpansionState()
            applyExpansionState(
                pruneHiddenLineageExpansions(
                    beforeBaseVisiblePersonIds = baseVisiblePersonIds,
                    afterBaseVisiblePersonIds = visibleBasePersonIds(
                        collapseParents = true,
                        collapseChildren = childrenCollapsed
                    ),
                    before = before,
                    afterRootToggle = before,
                    relationships = allRelationships
                )
            )
        }
        parentsCollapsed = !parentsCollapsed
    }
    val toggleCenterChildren: () -> Unit = {
        if (!childrenCollapsed) {
            val before = currentExpansionState()
            applyExpansionState(
                pruneHiddenLineageExpansions(
                    beforeBaseVisiblePersonIds = baseVisiblePersonIds,
                    afterBaseVisiblePersonIds = visibleBasePersonIds(
                        collapseParents = parentsCollapsed,
                        collapseChildren = true
                    ),
                    before = before,
                    afterRootToggle = before,
                    relationships = allRelationships
                )
            )
        }
        childrenCollapsed = !childrenCollapsed
    }
    LaunchedEffect(showRelationshipPathInGraph, pathPersonIds) {
        if (!showRelationshipPathInGraph) return@LaunchedEffect
        if (allRelationships.any {
                it.isLineageParentChild() &&
                    it.toPersonId in centerMemberIds &&
                    it.fromPersonId in pathPersonIds
            }
        ) parentsCollapsed = false
        if (allRelationships.any {
                it.isLineageParentChild() &&
                    it.fromPersonId in centerMemberIds &&
                    it.toPersonId in pathPersonIds
            }
        ) childrenCollapsed = false
    }
    val transformState = remember(centerPersonId) {
        mutableStateOf(GraphViewportTransform())
    }
    var resetViewVersion by remember { mutableStateOf(0) }
    var viewportInitialized by remember(centerPersonId) { mutableStateOf(false) }
    var previousViewportAnchorId by remember(centerPersonId) {
        mutableStateOf<String?>(null)
    }
    var previousViewportAnchorPoint by remember(centerPersonId) {
        mutableStateOf<PointDp?>(null)
    }
    val minScale = 0.5f
    val maxScale = 2.5f

    val viewportAnchorId = selectedPersonId ?: centerPersonId
    val currentViewportAnchorPoint = tiles
        .firstOrNull { it.id == viewportAnchorId }
        ?.rect
        ?.center()
        ?: layout.center.bounds().center()
    LaunchedEffect(
        viewportAnchorId,
        currentViewportAnchorPoint,
        showRelationshipPathInGraph
    ) {
        val previous = previousViewportAnchorPoint
        if (
            previous != null &&
            previousViewportAnchorId == viewportAnchorId &&
            viewportInitialized &&
            !showRelationshipPathInGraph
        ) {
            val transform = transformState.value
            val deltaX = with(density) {
                (currentViewportAnchorPoint.x - previous.x).toPx()
            }
            val deltaY = with(density) {
                (currentViewportAnchorPoint.y - previous.y).toPx()
            }
            transformState.value = transform.copy(
                offsetX = transform.offsetX - deltaX * transform.scale,
                offsetY = transform.offsetY - deltaY * transform.scale
            )
        }
        previousViewportAnchorId = viewportAnchorId
        previousViewportAnchorPoint = currentViewportAnchorPoint
    }

    LaunchedEffect(resetViewRequest) {
        if (resetViewRequest > 0) {
            viewportInitialized = false
            resetViewVersion++
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useSideInspector = maxWidth >= 720.dp
            val inspectedPerson = inspectedPersonId?.let(personById::get)

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
                    centerControlsVisible,
                    parentsCollapsed,
                    childrenCollapsed,
                    canEditRelationships,
                    onAddPersonRequest
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

                            val parentControl = if (centerControlsVisible && hasParents) {
                                PointDp(
                                    layout.center.anchorTop().x,
                                    layout.center.anchorTop().y - 24.dp
                                )
                            } else null
                            val childControl = if (centerControlsVisible && hasChildren) {
                                PointDp(
                                    layout.center.anchorBottom().x,
                                    layout.center.anchorBottom().y + 24.dp
                                )
                            } else null
                            when {
                                hits(parentControl) -> {
                                    toggleCenterParents()
                                    return@detectTapGestures
                                }
                                hits(childControl) -> {
                                    toggleCenterChildren()
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
                                if (selectedPersonId == hit.id) {
                                    onInspectPerson(hit.id)
                                } else {
                                    onSelectPerson(hit.id)
                                }
                            } else {
                                onClearSelection()
                            }
                        },
                        onLongPress = { press ->
                            if (!canEditRelationships) return@detectTapGestures
                            val transform = transformState.value
                            val world = (
                                press - Offset(transform.offsetX, transform.offsetY)
                                ) / transform.scale
                            val hit = tiles.asReversed().firstOrNull { tile ->
                                val left = with(density) { tile.rect.left.toPx() }
                                val right = with(density) { tile.rect.right.toPx() }
                                val top = with(density) { tile.rect.top.toPx() }
                                val bottom = with(density) { tile.rect.bottom.toPx() }
                                world.x in left..right && world.y in top..bottom
                            }
                            if (hit == null) {
                                onAddPersonRequest(
                                    GraphPreferredPosition(
                                        centerX = with(density) { world.x.toDp().value },
                                        centerY = with(density) { world.y.toDp().value }
                                    )
                                )
                            }
                        },
                        onDoubleTap = { tap ->
                            val transform = transformState.value
                            val world = (
                                tap - Offset(transform.offsetX, transform.offsetY)
                                ) / transform.scale
                            val hit = tiles.asReversed().firstOrNull { tile ->
                                val left = with(density) { tile.rect.left.toPx() }
                                val right = with(density) { tile.rect.right.toPx() }
                                val top = with(density) { tile.rect.top.toPx() }
                                val bottom = with(density) { tile.rect.bottom.toPx() }
                                world.x in left..right && world.y in top..bottom
                            }
                            if (hit != null) onInspectPerson(hit.id)
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
            val renderTransform = transformState.value
            val renderViewport = remember(
                renderTransform,
                viewportWidthPx,
                viewportHeightPx,
                density
            ) {
                visibleWorldRect(
                    scale = renderTransform.scale,
                    offsetX = renderTransform.offsetX,
                    offsetY = renderTransform.offsetY,
                    viewportWidth = viewportWidthPx,
                    viewportHeight = viewportHeightPx,
                    overscanWorld = with(density) { 144.dp.toPx() }
                )
            }
            val cardDetail = graphCardDetail(renderTransform.scale)
            val renderedTiles = remember(tiles, renderViewport, density) {
                tiles.filter { it.rect.toRenderRect(density).intersects(renderViewport) }
            }
            val renderedNodeIds = remember(layout.nodes, renderViewport, density) {
                layout.nodes
                    .filter { it.bounds().toRenderRect(density).intersects(renderViewport) }
                    .flatMapTo(mutableSetOf()) { node -> node.tiles().map { it.id } }
            }
            fun pointIsRendered(point: PointDp): Boolean = renderViewport.contains(
                x = with(density) { point.x.toPx() },
                y = with(density) { point.y.toPx() }
            )
            val exactViewport = remember(
                renderTransform,
                viewportWidthPx,
                viewportHeightPx
            ) {
                visibleWorldRect(
                    scale = renderTransform.scale,
                    offsetX = renderTransform.offsetX,
                    offsetY = renderTransform.offsetY,
                    viewportWidth = viewportWidthPx,
                    viewportHeight = viewportHeightPx
                )
            }
            val minimapOverview = remember(
                layout.nodes,
                layout.lineageEdges,
                generationVisiblePersonIds,
                visibleRelationshipIds,
                generationFilter,
                density
            ) {
                val activeNodes = layout.nodes
                    .flatMap(GraphNode::tiles)
                    .filter { it.id in generationVisiblePersonIds }
                    .map { it.rect.toRenderRect(density) }
                val activeEdges = layout.lineageEdges
                    .filter { edge ->
                        generationFilter == GraphGenerationFilter.ALL ||
                            edge.relationshipIds.any { it in visibleRelationshipIds }
                    }
                    .map { edge ->
                        GraphMinimapLine(
                            fromX = with(density) { edge.from.x.toPx() },
                            fromY = with(density) { edge.from.y.toPx() },
                            toX = with(density) { edge.to.x.toPx() },
                            toY = with(density) { edge.to.y.toPx() }
                        )
                    }
                GraphMinimapOverview(
                    nodeRects = activeNodes,
                    edgeLines = activeEdges
                )
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .testTag("graph-background")
                    .semantics {
                        onClick(label = "Tutup navigasi card") {
                            onClearSelection()
                            true
                        }
                    }
            )

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

            var handledRevealPersonRequest by remember { mutableStateOf(0) }
            LaunchedEffect(
                revealPersonRequest,
                tiles,
                viewportWidthPx,
                viewportHeightPx
            ) {
                if (
                    revealPersonRequest <= handledRevealPersonRequest ||
                    revealPersonId == null
                ) {
                    return@LaunchedEffect
                }
                val tile = tiles.firstOrNull { it.id == revealPersonId }
                    ?: return@LaunchedEffect
                val transform = transformState.value
                val tileCenter = Offset(
                    with(density) { tile.rect.center().x.toPx() },
                    with(density) { tile.rect.center().y.toPx() }
                )
                transformState.value = transform.copy(
                    offsetX = viewportWidthPx / 2f - tileCenter.x * transform.scale,
                    offsetY = viewportHeightPx / 2f - tileCenter.y * transform.scale
                )
                handledRevealPersonRequest = revealPersonRequest
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
                val inspectorWidthPx = if (useSideInspector && inspectedPerson != null) {
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

                    layout.nodes
                        .filter { node -> node.tiles().all { it.id in generationVisiblePersonIds } }
                        .filter { node -> node.tiles().any { it.id in renderedNodeIds } }
                        .forEach { node ->
                        node.spouseLine()?.let { (a, b) ->
                            val highlighted = showRelationshipPathInGraph &&
                                node.tiles().count { it.id in pathPersonIds } >= 2
                            val start = Offset(
                                with(density) { a.x.toPx() },
                                with(density) { a.y.toPx() }
                            )
                            val end = Offset(
                                with(density) { b.x.toPx() },
                                with(density) { b.y.toPx() }
                            )
                            val center = Offset(
                                with(density) { ((a.x + b.x) / 2f).toPx() },
                                with(density) { ((a.y + b.y) / 2f).toPx() }
                            )
                            val radius = 7.dp.toPx()
                            val separation = 5.dp.toPx()
                            drawLine(
                                color = if (highlighted) {
                                    pathAccentColor
                                } else {
                                    coupleRingColor.copy(alpha = 0.68f)
                                },
                                start = start,
                                end = end,
                                strokeWidth = if (highlighted) 3.dp.toPx() else lineStroke
                            )
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

                    layout.lineageEdges
                        .filter { edge ->
                            generationFilter == GraphGenerationFilter.ALL ||
                                edge.relationshipIds.any { it in visibleRelationshipIds }
                        }
                        .filter { edge -> edge.toRenderRect(density).intersects(renderViewport) }
                        .groupBy { edge ->
                            if (edge.type == "SPOUSE") {
                                "spouse:${edge.relationshipIds.sorted().joinToString()}"
                            } else {
                                listOf(
                                    edge.from.x.value,
                                    edge.from.y.value,
                                    edge.sourceExitY.value,
                                    edge.to.y.value,
                                    edge.meta.orEmpty()
                                ).joinToString(":")
                            }
                        }
                        .values
                        .forEach { edgeGroup ->
                        val edge = edgeGroup.first()
                        val start = Offset(
                            with(density) { edge.from.x.toPx() },
                            with(density) { edge.from.y.toPx() }
                        )
                        val highlighted = showRelationshipPathInGraph &&
                            edgeGroup.any { candidate ->
                                candidate.relationshipIds.any { it in pathRelationshipIds }
                            }
                        if (edge.type == "SPOUSE") {
                            val end = Offset(
                                with(density) { edge.to.x.toPx() },
                                with(density) { edge.to.y.toPx() }
                            )
                            val center = Offset(
                                x = (start.x + end.x) / 2f,
                                y = (start.y + end.y) / 2f
                            )
                            val radius = 7.dp.toPx()
                            val separation = if (edge.meta == "DIVORCED") {
                                10.dp.toPx()
                            } else {
                                5.dp.toPx()
                            }
                            val relationshipColor = when {
                                highlighted -> pathAccentColor
                                edge.meta == "WIDOWED" -> coupleRingColor.copy(alpha = 0.48f)
                                else -> coupleRingColor
                            }
                            drawLine(
                                color = relationshipColor.copy(
                                    alpha = if (highlighted) 1f else 0.68f
                                ),
                                start = start,
                                end = end,
                                strokeWidth = if (highlighted) 3.dp.toPx() else lineStroke
                            )
                            drawCircle(
                                color = relationshipColor,
                                radius = radius,
                                center = center.copy(x = center.x - separation),
                                style = Stroke(width = if (highlighted) 3.dp.toPx() else 1.5.dp.toPx())
                            )
                            drawCircle(
                                color = relationshipColor,
                                radius = radius,
                                center = center.copy(x = center.x + separation),
                                style = Stroke(width = if (highlighted) 3.dp.toPx() else 1.5.dp.toPx())
                            )
                            return@forEach
                        }
                        if (edge.type == "CARE") {
                            val end = Offset(
                                with(density) { edge.to.x.toPx() },
                                with(density) { edge.to.y.toPx() }
                            )
                            val carePattern = when (edge.meta) {
                                "FOSTER" -> floatArrayOf(
                                    10.dp.toPx(),
                                    4.dp.toPx(),
                                    2.dp.toPx(),
                                    4.dp.toPx()
                                )
                                else -> floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                            }
                            val careColor =
                                if (highlighted) pathAccentColor else coupleRingColor
                            drawLine(
                                color = careColor,
                                start = start,
                                end = end,
                                strokeWidth = if (highlighted) 3.dp.toPx() else 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(carePattern)
                            )
                            if (edge.meta == "GUARDIAN") {
                                drawCircle(
                                    color = careColor,
                                    radius = 4.dp.toPx(),
                                    center = Offset(
                                        (start.x + end.x) / 2f,
                                        (start.y + end.y) / 2f
                                    ),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                            return@forEach
                        }
                        val targets = edgeGroup.map { candidate ->
                            Offset(
                                with(density) { candidate.to.x.toPx() },
                                with(density) { candidate.to.y.toPx() }
                            )
                        }
                        val sourceExitY = with(density) { edge.sourceExitY.toPx() }
                        val hubY = lineageHubY(sourceExitY, targets.first().y)
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
                            start = Offset(minOf(start.x, targets.minOf { it.x }), hubY),
                            end = Offset(maxOf(start.x, targets.maxOf { it.x }), hubY),
                            strokeWidth = edgeStroke,
                            pathEffect = pathEffect
                        )
                        targets.forEach { end ->
                            drawLine(
                                color = edgeColor,
                                start = Offset(end.x, hubY),
                                end = end,
                                strokeWidth = edgeStroke,
                                pathEffect = pathEffect
                            )
                        }
                    }
                }

                layout.nodes.filterIsInstance<SingleParentNode>()
                    .filter { generationFilter == GraphGenerationFilter.ALL }
                    .filter { it.placeholderRect.toRenderRect(density).intersects(renderViewport) }
                    .forEach { node ->
                    PlaceholderGraphCard(
                        label = if (canEditRelationships) {
                            "+\nOrang tua lain\nbelum tercatat"
                        } else {
                            "Orang tua lain\nbelum tercatat"
                        },
                        modifier = Modifier
                            .size(node.placeholderRect.w, node.placeholderRect.h)
                            .offset(node.placeholderRect.x, node.placeholderRect.y)
                            .testTag("unrecorded-parent-${node.childId}")
                            .then(
                                if (canEditRelationships) {
                                    Modifier.clickable {
                                        onQuickAddRequest(
                                            GraphQuickAddRequest(
                                                anchorPersonId = node.childId,
                                                anchorName = displayName(node.childId),
                                                kind = QuickRelationKind.PARENT
                                            )
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .semantics {
                                contentDescription = if (canEditRelationships) {
                                    "Orang tua lain ${displayName(node.childId)} belum tercatat"
                                } else {
                                    "Orang tua lain ${displayName(node.childId)} belum tercatat, hanya baca"
                                }
                            }
                    )
                }

                tiles.filter { it.role == "UNCONNECTED" }
                    .minByOrNull { it.rect.y.value }
                    ?.let { firstUnconnected ->
                        Text(
                            "Belum terhubung · ${tiles.count { it.role == "UNCONNECTED" }} person",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(
                                x = firstUnconnected.rect.x,
                                y = firstUnconnected.rect.y - 30.dp
                            )
                        )
                    }

                renderedTiles.forEach { tile ->
                    val person = personById[tile.id]
                    if (person != null) {
                        PersonGraphCard(
                            person = person,
                            profilePhotoUrl = profilePhotoUrls[tile.id],
                            selected = tile.id == selectedPersonId,
                            highlighted = showRelationshipPathInGraph && tile.id in pathPersonIds,
                            detail = cardDetail,
                            onActivate = {
                                if (selectedPersonId == tile.id) {
                                    onInspectPerson(tile.id)
                                } else {
                                    onSelectPerson(tile.id)
                                }
                            },
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

                    branchControls.filter { pointIsRendered(it.point) }.forEach { control ->
                        when (control.direction) {
                            BranchDirection.PARENTS -> drawControl(
                                point = control.point,
                                pointsUp = !control.expanded
                            )
                            BranchDirection.CHILDREN -> drawControl(
                                point = control.point,
                                pointsUp = control.expanded
                            )
                            BranchDirection.PARTNERSHIPS -> drawHorizontalControl(
                                point = control.point,
                                pointsLeft = if (control.horizontalSide < 0) {
                                    !control.expanded
                                } else {
                                    control.expanded
                                }
                            )
                        }
                    }

                    if (centerControlsVisible && hasParents) {
                        drawControl(
                            PointDp(
                                layout.center.anchorTop().x,
                                layout.center.anchorTop().y - 24.dp
                            ),
                            pointsUp = parentsCollapsed
                        )
                    }
                    if (centerControlsVisible && hasChildren) {
                        drawControl(
                            PointDp(
                                layout.center.anchorBottom().x,
                                layout.center.anchorBottom().y + 24.dp
                            ),
                            pointsUp = !childrenCollapsed
                        )
                    }
                    quickAddControls.filter { pointIsRendered(it.point) }.forEach { control ->
                        val center = pointOffset(control.point)
                        drawCircle(
                            color = controlAccentColor,
                            radius = 14.dp.toPx(),
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
                    lockedControls.filter { pointIsRendered(it.point) }.forEach { control ->
                        val center = pointOffset(control.point)
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
                        drawArc(
                            color = controlOutlineColor,
                            startAngle = 195f,
                            sweepAngle = 150f,
                            useCenter = false,
                            topLeft = Offset(center.x - 5.dp.toPx(), center.y - 7.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(10.dp.toPx(), 10.dp.toPx()),
                            style = Stroke(width = 1.8.dp.toPx())
                        )
                        drawRoundRect(
                            color = controlOutlineColor,
                            topLeft = Offset(center.x - 6.dp.toPx(), center.y - 1.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 9.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                        )
                    }
                    if (
                        canEditRelationships &&
                        linkHandlePoint != null &&
                        pointIsRendered(linkHandlePoint)
                    ) {
                        val handle = pointOffset(linkHandlePoint)
                        linkDragPoint?.let { dragPoint ->
                            drawLine(
                                color = controlAccentColor,
                                start = handle,
                                end = pointOffset(dragPoint),
                                strokeWidth = 2.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(8.dp.toPx(), 5.dp.toPx())
                                )
                            )
                        }
                        drawCircle(
                            color = controlAccentColor,
                            radius = 12.dp.toPx(),
                            center = handle
                        )
                        drawCircle(
                            color = controlOnAccentColor,
                            radius = 3.dp.toPx(),
                            center = handle
                        )
                    }
                }

                partnershipSwapControls
                    .filter {
                        selectedPersonId == it.firstPersonId ||
                            selectedPersonId == it.secondPersonId
                    }
                    .filter { pointIsRendered(it.point) }
                    .forEach { control ->
                        Surface(
                            shape = CircleShape,
                            color = controlSurfaceColor,
                            border = BorderStroke(1.dp, controlOutlineColor),
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .size(30.dp)
                                .offset(control.point.x - 15.dp, control.point.y - 15.dp)
                                .testTag("swap-partnership-${control.pairKey.hashCode()}")
                                .clickable { swapPartnership(control.pairKey) }
                                .semantics {
                                    contentDescription =
                                        "Tukar posisi ${displayName(control.firstPersonId)} " +
                                        "dan ${displayName(control.secondPersonId)}"
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = "\u21C4",
                                    color = controlOutlineColor,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                branchControls.filter { pointIsRendered(it.point) }.forEach { control ->
                    val action = if (control.expanded) "Tutup" else "Buka"
                    val branch = when (control.direction) {
                        BranchDirection.PARENTS -> "orang tua"
                        BranchDirection.CHILDREN -> "anak"
                        BranchDirection.PARTNERSHIPS -> "riwayat pasangan"
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .offset(control.point.x - 20.dp, control.point.y - 20.dp)
                            .testTag(
                                buildString {
                                    append("lineage-")
                                    append(control.direction.name.lowercase())
                                    append("-")
                                    append(control.personId)
                                    control.childFamilyKey?.let { key ->
                                        append("-")
                                        append(key.hashCode())
                                    }
                                }
                            )
                            .clickable { toggleBranch(control) }
                            .semantics {
                                contentDescription = "$action cabang $branch"
                            }
                    )
                }
                if (centerControlsVisible && hasParents) {
                    val point = PointDp(
                        layout.center.anchorTop().x,
                        layout.center.anchorTop().y - 24.dp
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .offset(point.x - 20.dp, point.y - 20.dp)
                            .testTag("lineage-parents-center")
                            .clickable(onClick = toggleCenterParents)
                            .semantics {
                                contentDescription =
                                    if (parentsCollapsed) "Buka cabang orang tua"
                                    else "Tutup cabang orang tua"
                            }
                    )
                }
                if (centerControlsVisible && hasChildren) {
                    val point = PointDp(
                        layout.center.anchorBottom().x,
                        layout.center.anchorBottom().y + 24.dp
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .offset(point.x - 20.dp, point.y - 20.dp)
                            .testTag("lineage-children-center")
                            .clickable(onClick = toggleCenterChildren)
                            .semantics {
                                contentDescription =
                                    if (childrenCollapsed) "Buka cabang anak"
                                    else "Tutup cabang anak"
                            }
                    )
                }
                quickAddControls.filter { pointIsRendered(it.point) }.forEach { control ->
                    val relation = when (control.request.kind) {
                        QuickRelationKind.PARENT -> "orang tua"
                        QuickRelationKind.CHILD -> "anak"
                        QuickRelationKind.PARTNER -> "pasangan"
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .offset(control.point.x - 20.dp, control.point.y - 20.dp)
                            .testTag(
                                buildString {
                                    append("quick-add-")
                                    append(control.request.kind.name.lowercase())
                                    append("-")
                                    append(control.request.anchorPersonId)
                                    control.request.coParentId?.let {
                                        append("-")
                                        append(it)
                                    }
                                }
                            )
                            .clickable { onQuickAddRequest(control.request) }
                            .semantics { contentDescription = "Tambah $relation" }
                    )
                }
                lockedControls.filter { pointIsRendered(it.point) }.forEach { control ->
                    val relation = when (control.request.kind) {
                        QuickRelationKind.PARENT -> "orang tua"
                        QuickRelationKind.CHILD -> "anak"
                        QuickRelationKind.PARTNER -> "pasangan"
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .offset(control.point.x - 20.dp, control.point.y - 20.dp)
                            .testTag(
                                buildString {
                                    append("locked-")
                                    append(control.request.kind.name.lowercase())
                                    append("-")
                                    append(control.request.anchorPersonId)
                                    control.request.coParentId?.let {
                                        append("-")
                                        append(it)
                                    }
                                }
                            )
                            .semantics {
                                contentDescription =
                                    "Tambah $relation terkunci untuk akses hanya baca"
                            }
                    )
                }
                if (
                    canEditRelationships &&
                    linkHandlePoint != null &&
                    selectedPersonId != null &&
                    pointIsRendered(linkHandlePoint)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .offset(linkHandlePoint.x - 22.dp, linkHandlePoint.y - 22.dp)
                            .testTag("connect-handle-$selectedPersonId")
                            .pointerInput(tiles, selectedPersonId) {
                                detectDragGestures(
                                    onDragStart = {
                                        linkDragSourceId = selectedPersonId
                                        linkDragPoint = linkHandlePoint
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val current = linkDragPoint ?: linkHandlePoint
                                        linkDragPoint = PointDp(
                                            current.x + with(density) { dragAmount.x.toDp() },
                                            current.y + with(density) { dragAmount.y.toDp() }
                                        )
                                    },
                                    onDragCancel = {
                                        linkDragSourceId = null
                                        linkDragPoint = null
                                    },
                                    onDragEnd = {
                                        val sourceId = linkDragSourceId
                                        val point = linkDragPoint
                                        val target = point?.let { dropPoint ->
                                            tiles.asReversed().firstOrNull { tile ->
                                                dropPoint.x in tile.rect.left..tile.rect.right &&
                                                    dropPoint.y in tile.rect.top..tile.rect.bottom
                                            }
                                        }
                                        if (sourceId != null && target != null && target.id != sourceId) {
                                            onConnectPersons(sourceId, target.id)
                                        }
                                        linkDragSourceId = null
                                        linkDragPoint = null
                                    }
                                )
                            }
                            .semantics {
                                contentDescription = "Tarik untuk menghubungkan person"
                            }
                    )
                }
            }

                if (
                    minimapOverview.nodeRects.isNotEmpty() &&
                    shouldShowGraphMinimap(
                        graphWidth = graphWidthPx,
                        graphHeight = graphHeightPx,
                        viewportWorld = exactViewport
                    )
                ) {
                    IdentityFreeGraphMinimap(
                        overview = minimapOverview,
                        graphWidth = graphWidthPx,
                        graphHeight = graphHeightPx,
                        viewportWorld = exactViewport,
                        onNavigate = { worldX, worldY ->
                            val transform = transformState.value
                            val offsets = graphViewportOffsetsForCenter(
                                worldX = worldX,
                                worldY = worldY,
                                scale = transform.scale,
                                viewportWidth = viewportWidthPx,
                                viewportHeight = viewportHeightPx
                            )
                            transformState.value = transform.copy(
                                offsetX = offsets.first,
                                offsetY = offsets.second
                            )
                            viewportInitialized = true
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 64.dp, end = 16.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf(
                        GraphGenerationFilter.ALL to "Semua",
                        GraphGenerationFilter.SAME to "Satu generasi",
                        GraphGenerationFilter.ANCESTORS to "Leluhur",
                        GraphGenerationFilter.DESCENDANTS to "Keturunan"
                    ).forEach { (filter, label) ->
                        FilterChip(
                            selected = generationFilter == filter,
                            onClick = {
                                generationFilter = filter
                                onClearSelection()
                            },
                            label = { Text(label) }
                        )
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

            if (canEditRelationships && inspectedPerson == null) {
                ExtendedFloatingActionButton(
                    onClick = { onAddPersonRequest(null) },
                    icon = {
                        Text(
                            "+",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = { Text("Tambah person") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .testTag("graph-add-person")
                        .semantics {
                            contentDescription =
                                "Tambah person baru. Tekan lama area kosong juga dapat digunakan."
                        }
                )
            }

            val focusedCareTypes = layout.lineageEdges
                .filter { it.type == "CARE" }
                .mapNotNull { it.meta }
                .toSet()
            if (focusedCareTypes.isNotEmpty()) {
                CareRelationshipLegend(
                    types = focusedCareTypes,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                )
            }

            inspectedPerson?.let { person ->
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
                    isGraphCenter = person.personId == centerPersonId,
                    canEditProfile = canEditRelationships,
                    profilePhotoUrl = profilePhotoUrls[person.personId],
                    people = personById,
                    relationships = allRelationships,
                    relationshipPath = relationshipPath,
                    showRelationshipPathInGraph = showRelationshipPathInGraph,
                    onClose = onClearSelection,
                    onOpenProfile = { onOpenPerson(person.personId) },
                    onFocusPerson = { onFocusPerson(person.personId) },
                    onShowRelationshipPath = onShowRelationshipPath,
                    modifier = inspectorModifier
                )
            }
        }
    }
}

@Composable
private fun IdentityFreeGraphMinimap(
    overview: GraphMinimapOverview,
    graphWidth: Float,
    graphHeight: Float,
    viewportWorld: GraphRenderRect,
    onNavigate: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val neutralColor = MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 3.dp,
        modifier = modifier
            .size(width = 168.dp, height = 112.dp)
            .testTag("graph-minimap")
            .semantics {
                contentDescription =
                    "Ikhtisar posisi pohon. Ketuk untuk memindahkan tampilan."
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(overview, graphWidth, graphHeight) {
                    detectTapGestures { tap ->
                        val projection = graphMinimapProjection(
                            graphWidth = graphWidth,
                            graphHeight = graphHeight,
                            minimapWidth = size.width.toFloat(),
                            minimapHeight = size.height.toFloat(),
                            padding = 6.dp.toPx()
                        )
                        val world = minimapWorldPoint(
                            minimapX = tap.x,
                            minimapY = tap.y,
                            projection = projection,
                            graphWidth = graphWidth,
                            graphHeight = graphHeight
                        )
                        onNavigate(world.first, world.second)
                    }
                }
        ) {
            val projection = graphMinimapProjection(
                graphWidth = graphWidth,
                graphHeight = graphHeight,
                minimapWidth = size.width,
                minimapHeight = size.height,
                padding = 6.dp.toPx()
            )
            overview.edgeLines.forEach { line ->
                drawLine(
                    color = neutralColor.copy(alpha = 0.24f),
                    start = Offset(
                        projection.offsetX + line.fromX * projection.scale,
                        projection.offsetY + line.fromY * projection.scale
                    ),
                    end = Offset(
                        projection.offsetX + line.toX * projection.scale,
                        projection.offsetY + line.toY * projection.scale
                    ),
                    strokeWidth = 1.dp.toPx()
                )
            }
            overview.nodeRects.forEach { node ->
                val projected = projectMinimapRect(node, projection)
                drawRect(
                    color = neutralColor.copy(alpha = 0.38f),
                    topLeft = Offset(projected.left, projected.top),
                    size = Size(
                        (projected.right - projected.left).coerceAtLeast(1.dp.toPx()),
                        (projected.bottom - projected.top).coerceAtLeast(1.dp.toPx())
                    )
                )
            }
            val clippedLeft = viewportWorld.left.coerceIn(0f, graphWidth)
            val clippedTop = viewportWorld.top.coerceIn(0f, graphHeight)
            val clippedRight = viewportWorld.right.coerceIn(clippedLeft, graphWidth)
            val clippedBottom = viewportWorld.bottom.coerceIn(clippedTop, graphHeight)
            val viewport = projectMinimapRect(
                GraphRenderRect(
                    left = clippedLeft,
                    top = clippedTop,
                    right = clippedRight,
                    bottom = clippedBottom
                ),
                projection
            )
            drawRect(
                color = neutralColor.copy(alpha = 0.82f),
                topLeft = Offset(viewport.left, viewport.top),
                size = Size(
                    (viewport.right - viewport.left).coerceAtLeast(1.dp.toPx()),
                    (viewport.bottom - viewport.top).coerceAtLeast(1.dp.toPx())
                ),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

@Composable
private fun CareRelationshipLegend(
    types: Set<String>,
    modifier: Modifier = Modifier
) {
    val description = buildList {
        if ("FOSTER" in types) add("Garis dash-titik: pengasuhan sementara")
        if ("GUARDIAN" in types) add("Garis putus dengan lingkaran: perwalian keluarga")
    }.joinToString(". ")
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 3.dp,
        modifier = modifier.semantics {
            contentDescription =
                "$description. Bukan hubungan darah atau penetapan wali legal."
        }
    ) {
        Text(
            "$description\nBukan hubungan darah atau status wali legal.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}

private fun GraphLayout.toExportSnapshot(
    people: Map<String, PersonListItem>
): GraphExportSnapshot {
    val layout = this
    val tiles = layout.nodes.flatMap { it.tiles() }.map { tile ->
        val person = people[tile.id]
        GraphExportTile(
            id = tile.id,
            label = person?.let(::cardDisplayName) ?: tile.label,
            role = tile.role,
            x = tile.rect.x.value,
            y = tile.rect.y.value,
            width = tile.rect.w.value,
            height = tile.rect.h.value,
            gender = person?.gender,
            lifeStatus = person?.lifeStatus,
            age = person
                ?.takeIf { it.lifeStatus == "ALIVE" }
                ?.let { calculateAge(it.birthDate) }
        )
    }
    fun GraphNode.exportLine(type: String, points: Pair<PointDp, PointDp>): GraphExportLine =
        GraphExportLine(
            fromX = points.first.x.value,
            fromY = points.first.y.value,
            toX = points.second.x.value,
            toY = points.second.y.value,
            type = type,
            meta = null
        )
    val spouseLines = layout.nodes.mapNotNull { node ->
        node.spouseLine()?.let { node.exportLine("SPOUSE", it) }
    }
    val lineageLines = layout.lineageEdges.map { edge ->
        GraphExportLine(
            fromX = edge.from.x.value,
            fromY = edge.from.y.value,
            toX = edge.to.x.value,
            toY = edge.to.y.value,
            type = edge.type,
            meta = edge.meta
        )
    }
    val placeholders = layout.nodes.filterIsInstance<SingleParentNode>().map { node ->
        GraphExportPlaceholder(
            label = "Orang tua lain belum tercatat",
            x = node.placeholderRect.x.value,
            y = node.placeholderRect.y.value,
            width = node.placeholderRect.w.value,
            height = node.placeholderRect.h.value
        )
    }
    return GraphExportSnapshot(
        width = layout.width.value,
        height = layout.height.value,
        tiles = tiles,
        spouseLines = spouseLines,
        lineageLines = lineageLines,
        placeholders = placeholders
    )
}

private data class SingleParentNode(
    val parentId: String,
    val childId: String,
    val parentLabel: String,
    val parentRect: RectDp,
    val placeholderRect: RectDp,
    val wrapper: RectDp,
    override val role: String
) : GraphNode(role) {
    override fun bounds(): RectDp = wrapper
    override fun anchorTop(): PointDp = parentRect.topCenter()
    override fun anchorBottom(): PointDp = parentRect.bottomCenter()
    override fun spouseLine(): Pair<PointDp, PointDp>? = null
    override fun tiles(): List<TileRender> = listOf(
        TileRender(
            id = parentId,
            label = parentLabel,
            role = role,
            rect = parentRect,
            isCenter = false
        )
    )

    override fun hitId(worldPx: Offset, density: androidx.compose.ui.unit.Density): String? {
        val left = with(density) { parentRect.left.toPx() }
        val right = with(density) { parentRect.right.toPx() }
        val top = with(density) { parentRect.top.toPx() }
        val bottom = with(density) { parentRect.bottom.toPx() }
        return if (worldPx.x in left..right && worldPx.y in top..bottom) parentId else null
    }

    override fun shift(dx: Dp, dy: Dp): GraphNode = copy(
        parentRect = parentRect.shift(dx, dy),
        placeholderRect = placeholderRect.shift(dx, dy),
        wrapper = wrapper.shift(dx, dy)
    )
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
                        "  ›  ${people[personId]?.let(::cardDisplayName) ?: "Person"}",
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
private fun LargeGraphTextFallback(
    persons: List<PersonListItem>,
    centerPersonId: String,
    renderedTileCount: Int,
    onOpenPerson: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val personById = remember(persons) { persons.associateBy { it.personId } }
    val entries = remember(persons, centerPersonId) {
        orderGraphTextFallbackEntries(
            persons.map { person ->
                GraphTextFallbackEntry(
                    personId = person.personId,
                    displayName = cardDisplayName(person),
                    isCenter = person.personId == centerPersonId
                )
            }
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("large-graph-text-fallback")
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "Pohon besar dibuka sebagai daftar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$renderedTileCount card dari ${persons.size} person melebihi batas aman " +
                    "tampilan visual perangkat. " +
                    "Posisi dan hubungan tetap tersimpan; pilih nama untuk membuka profil.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onBack) {
                Text("Kembali ke keluarga")
            }
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries, key = { it.personId }) { entry ->
                val person = personById[entry.personId]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPerson(entry.personId) }
                        .testTag("large-graph-person-${entry.personId}")
                        .semantics {
                            contentDescription = buildString {
                                append("Buka profil ${entry.displayName}")
                                if (entry.isCenter) append(", fokus saat ini")
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (entry.isCenter) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        }
                    )
                    Text(
                        text = when {
                            entry.isCenter -> "Fokus saat ini"
                            person?.lifeStatus == "DECEASED" -> "Wafat"
                            else -> "Buka profil"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun PersonGraphCard(
    person: PersonListItem,
    profilePhotoUrl: String?,
    selected: Boolean,
    highlighted: Boolean,
    detail: GraphCardDetail,
    onActivate: () -> Unit,
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
    val shortName = cardDisplayName(person)

    Surface(
        modifier = modifier
            .clickable(
                onClickLabel = if (selected) "Buka inspector" else "Pilih person",
                onClick = onActivate
            )
            .semantics {
                contentDescription = buildString {
                    append(person.fullName)
                    if (deceased) append(", telah meninggal")
                    age?.let { append(", $it tahun") }
                }
                this.selected = selected
            },
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        contentColor = contentColor,
        border = BorderStroke(if (selected || highlighted) 2.dp else 1.dp, borderColor),
        shadowElevation = if (selected) 3.dp else 1.dp
    ) {
        when (detail) {
            GraphCardDetail.FULL -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                ProfileAvatar(
                    photoUrl = profilePhotoUrl,
                    gender = person.gender,
                    muted = deceased,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = shortName,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(32.dp)
                )
                Text(
                    text = when {
                        deceased -> "Wafat"
                        age != null -> "$age tahun"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    ),
                    color = contentColor.copy(alpha = 0.76f),
                    maxLines = 1,
                    modifier = Modifier.height(14.dp)
                )
            }

            GraphCardDetail.COMPACT -> Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text(
                    text = shortName,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val status = when {
                    deceased -> "Wafat"
                    age != null -> "$age tahun"
                    else -> null
                }
                if (status != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.76f),
                        maxLines = 1
                    )
                }
            }

            GraphCardDetail.MINIMAL -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Text(
                    text = shortName,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
    isGraphCenter: Boolean,
    canEditProfile: Boolean,
    profilePhotoUrl: String?,
    people: Map<String, PersonListItem>,
    relationships: List<ExportRelationship>,
    relationshipPath: RelationshipPathResponse?,
    showRelationshipPathInGraph: Boolean,
    onClose: () -> Unit,
    onOpenProfile: () -> Unit,
    onFocusPerson: () -> Unit,
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
        .filter { it.isLineageParentChild() && it.toPersonId == person.personId }
        .mapNotNull { people[it.fromPersonId]?.fullName }
    val childNames = relationships
        .filter { it.isLineageParentChild() && it.fromPersonId == person.personId }
        .mapNotNull { people[it.toPersonId]?.fullName }
    val careRelationships = relationships
        .filter {
            it.isCareRelationship() &&
                (it.fromPersonId == person.personId || it.toPersonId == person.personId)
        }
        .mapNotNull { relationship ->
            val isCaregiver = relationship.fromPersonId == person.personId
            val otherId =
                if (isCaregiver) relationship.toPersonId else relationship.fromPersonId
            val otherName = people[otherId]?.fullName ?: return@mapNotNull null
            val role = when (relationship.meta) {
                "FOSTER" ->
                    if (isCaregiver) "Pengasuh $otherName" else "Diasuh oleh $otherName"
                "GUARDIAN" ->
                    if (isCaregiver) "Wali keluarga $otherName"
                    else "Dalam perwalian $otherName"
                else -> return@mapNotNull null
            }
            val period = when {
                relationship.startDate != null && relationship.endDate != null ->
                    "${relationship.startDate}–${relationship.endDate}"
                relationship.startDate != null -> "sejak ${relationship.startDate}"
                relationship.endDate != null -> "hingga ${relationship.endDate}"
                else -> null
            }
            listOfNotNull(role, period, relationship.careContext).joinToString(" · ")
        }
    val partnershipHistory = recordedPartnerships(person.personId, relationships)
        .mapNotNull { relationship ->
            val otherName = people[relationship.otherPersonId(person.personId)]?.fullName
                ?: return@mapNotNull null
            val status = when (relationship.meta) {
                "DIVORCED" -> "bercerai"
                "WIDOWED" -> "berakhir karena meninggal"
                "MARRIED" -> if (isCurrentPartnership(relationship)) {
                    "berlangsung"
                } else {
                    "berakhir"
                }
                else -> "tercatat"
            }
            val period = when {
                relationship.startDate != null && relationship.endDate != null ->
                    "${relationship.startDate}–${relationship.endDate}"
                relationship.startDate != null && isCurrentPartnership(relationship) ->
                    "sejak ${relationship.startDate}"
                relationship.startDate != null -> relationship.startDate
                relationship.endDate != null -> "hingga ${relationship.endDate}"
                else -> null
            }
            listOfNotNull(otherName, status, period).joinToString(" · ")
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
                ProfileAvatar(
                    photoUrl = profilePhotoUrl,
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
                    InspectorValue("Nama panggilan di card", person.nickName)
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
                    InspectorValue(
                        "Riwayat pasangan",
                        partnershipHistory.takeIf { it.isNotEmpty() }?.joinToString("\n")
                    )
                    InspectorValue("Anak", childNames.takeIf { it.isNotEmpty() }?.joinToString())
                    InspectorValue(
                        "Pengasuhan & perwalian keluarga",
                        careRelationships.takeIf { it.isNotEmpty() }?.joinToString("\n")
                    )
                    if (careRelationships.isNotEmpty()) {
                        Text(
                            "Catatan ini tidak menyatakan hubungan darah, hak akses, atau status wali legal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                if (!isGraphCenter) {
                    Text(
                        "Pohon akan ditata ulang dengan ${cardDisplayName(person)} sebagai person utama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!isGraphCenter) {
                        TextButton(onClick = onFocusPerson) {
                            Text("Jadikan ${cardDisplayName(person)} pusat pohon")
                        }
                    }
                    TextButton(onClick = onOpenProfile) {
                        Text(
                            if (canEditProfile) {
                                "✎ Edit profil lengkap"
                            } else {
                                "Lihat profil lengkap"
                            }
                        )
                    }
                }
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
        meta == "FOSTER" && direction == "FORWARD" -> "pengasuh dari $nextName"
        meta == "FOSTER" -> "anak asuh dari $nextName"
        meta == "GUARDIAN" && direction == "FORWARD" -> "wali keluarga dari $nextName"
        meta == "GUARDIAN" -> "dalam perwalian keluarga $nextName"
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

private enum class RowKind { PERSON, COUPLE, SINGLE_PARENT }

private data class RowItem(
    val kind: RowKind,
    val idA: String,
    val idB: String? = null,
    val labelA: String,
    val labelB: String? = null,
    val role: String = "CHILD",
    val primaryPersonId: String? = null,
    val width: Float
)

private data class PlacedItem(val item: RowItem, val x: Float)

internal fun orientedPartnershipPair(
    firstPersonId: String,
    secondPersonId: String,
    swappedPartnershipKeys: Set<String>
): Pair<String, String> =
    if (partnershipPairKey(firstPersonId, secondPersonId) in swappedPartnershipKeys) {
        secondPersonId to firstPersonId
    } else {
        firstPersonId to secondPersonId
    }

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
    margin: Dp,
    swappedPartnershipKeys: Set<String>
): GraphLayout {
    val activeSpouseId = findActiveSpouseId(centerPersonId, relations, allRelationships)
    val centerNode = if (activeSpouseId != null) {
        val (leftPersonId, rightPersonId) = orientedPartnershipPair(
            centerPersonId,
            activeSpouseId,
            swappedPartnershipKeys
        )
        coupleNode(
            leftId = leftPersonId,
            rightId = rightPersonId,
            leftLabel = displayName(leftPersonId),
            rightLabel = displayName(rightPersonId),
            role = "CENTER",
            leftRole = if (leftPersonId == centerPersonId) "CENTER" else "SPOUSE",
            rightRole = if (rightPersonId == centerPersonId) "CENTER" else "SPOUSE",
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
    val centerMemberHorizontalOrder = centerNode.tiles()
        .mapIndexed { index, tile -> tile.id to index }
        .toMap()
    val parentRelationships = if (allRelationships.isNotEmpty()) {
        allRelationships.filter {
            it.isLineageParentChild() && it.toPersonId in centerMemberIds
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
            centerMemberHorizontalOrder[relationship.toPersonId] ?: Int.MAX_VALUE
        }
        .map { it.fromPersonId }
        .distinct()
    val singleParentChildByParent = centerMemberIds.mapNotNull { childId ->
        val recordedParents = parentRelationships
            .filter { it.toPersonId == childId }
            .map { it.fromPersonId }
            .distinct()
        recordedParents.singleOrNull()?.let { parentId -> parentId to childId }
    }.toMap()
    val parentsY = (-rankGapY.value - tileH.value).dp
    val parentItems = buildParentRowItems(
        parentIds = parentIds,
        allRelationships = allRelationships,
        displayName = displayName,
        tileW = tileW,
        spouseGapX = spouseGapX,
        singleParentChildByParent = singleParentChildByParent,
        swappedPartnershipKeys = swappedPartnershipKeys
    )
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
            RowKind.SINGLE_PARENT -> singleParentNode(
                parentId = placed.item.idA,
                childId = requireNotNull(placed.item.idB),
                parentLabel = placed.item.labelA,
                role = "PARENT",
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
            val (leftPersonId, rightPersonId) = orientedPartnershipPair(
                child.id,
                child.spouseId,
                swappedPartnershipKeys
            )
            RowItem(
                kind = RowKind.COUPLE,
                idA = leftPersonId,
                idB = rightPersonId,
                labelA = displayName(leftPersonId),
                labelB = displayName(rightPersonId),
                role = child.role,
                primaryPersonId = child.id,
                width = tileW.value * 2f + spouseGapX.value
            )
        } else {
            RowItem(
                kind = RowKind.PERSON,
                idA = child.id,
                labelA = child.label,
                role = child.role,
                primaryPersonId = child.id,
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
                leftRole = if (placed.item.idA == placed.item.primaryPersonId) {
                    "CHILD"
                } else {
                    "SPOUSE"
                },
                rightRole = if (placed.item.idB == placed.item.primaryPersonId) {
                    "CHILD"
                } else {
                    "SPOUSE"
                },
                x = placed.x.dp,
                y = childrenY,
                tileW = tileW,
                tileH = tileH,
                gap = spouseGapX
            )
            RowKind.SINGLE_PARENT -> error("Single-parent units are only valid in parent rows")
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
    val parentLineageEdges = parentRelationships
        .groupBy { it.toPersonId }
        .values
        .flatMap { relationships ->
            buildParentageEdges(
                childId = relationships.first().toPersonId,
                relationships = relationships,
                visibleNodes = shiftedNodes,
                allRelationships = allRelationships
            )
        }
    val childLineageEdges = completeTree.children.flatMap { child ->
        val to = shiftedNodes.tileRect(child.id)?.topCenter() ?: return@flatMap emptyList()
        val matchingRelationships = allRelationships.filter {
            it.isLineageParentChild() &&
                it.toPersonId == child.id &&
                it.fromPersonId in centerMemberIds
        }
        if (matchingRelationships.isEmpty()) {
            listOf(
                LineageEdge(
                    relationshipIds = setOf("child:${child.id}"),
                    from = shiftedCenter.anchorBottom(),
                    to = to,
                    meta = null
                )
            )
        } else {
            buildParentageEdges(
                childId = child.id,
                relationships = matchingRelationships,
                visibleNodes = shiftedNodes,
                allRelationships = allRelationships
            )
        }
    }
    val siblingLineageEdges = allRelationships.mapNotNull { relationship ->
        if (
            !relationship.isLineageParentChild() ||
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

private fun augmentLayoutWithProgressiveLineage(
    base: GraphLayout,
    relationships: List<ExportRelationship>,
    expandedParentPersonIds: Set<String>,
    expandedChildFamilyKeys: Set<String>,
    expandedPartnershipPersonIds: Set<String>,
    displayName: (String) -> String,
    tileW: Dp,
    tileH: Dp,
    spouseGapX: Dp,
    siblingGapX: Dp,
    rankGapY: Dp,
    margin: Dp,
    swappedPartnershipKeys: Set<String>
): GraphLayout {
    if (relationships.isEmpty()) return base

    val basePersonIds = base.nodes
        .flatMap(GraphNode::tiles)
        .mapTo(mutableSetOf()) { it.id }
    val plan = planProgressiveLineage(
        baseVisiblePersonIds = basePersonIds,
        expandedParentPersonIds = expandedParentPersonIds,
        expandedChildPersonIds = emptySet(),
        expandedPartnershipPersonIds = expandedPartnershipPersonIds,
        expandedChildFamilyKeys = expandedChildFamilyKeys,
        relationships = relationships
    )
    val basePositions = base.nodes
        .flatMap(GraphNode::tiles)
        .associate { tile ->
            tile.id to LineagePlacementRect(
                x = tile.rect.x.value,
                y = tile.rect.y.value,
                width = tile.rect.w.value,
                height = tile.rect.h.value
            )
        }
    val plannedPositions = planProgressivePlacements(
        basePositions = basePositions,
        visiblePersonIds = plan.visiblePersonIds,
        visibleRelationships = plan.visibleRelationships,
        allRelationships = relationships,
        tileWidth = tileW.value,
        tileHeight = tileH.value,
        siblingGap = siblingGapX.value,
        partnershipGap = spouseGapX.value,
        rankGap = rankGapY.value,
        fallbackY = base.center.bounds().y.value,
        swappedPartnershipKeys = swappedPartnershipKeys
    )
    val positions = plannedPositions.mapValues { (_, rect) ->
        RectDp(rect.x.dp, rect.y.dp, rect.width.dp, rect.height.dp)
    }
    val repositionedBaseNodes = base.nodes.map { node ->
        val tile = node.tiles().firstOrNull() ?: return@map node
        val planned = positions[tile.id] ?: return@map node
        node.shift(planned.x - tile.rect.x, planned.y - tile.rect.y)
    }
    fun nodeKey(node: GraphNode): String = node.tiles().map { it.id }.sorted().joinToString("|")
    val repositionedByKey = repositionedBaseNodes.associateBy(::nodeKey)
    val extraNodes = plan.visiblePersonIds
        .filterNot { it in basePersonIds }
        .mapNotNull { personId ->
            val rect = positions[personId] ?: return@mapNotNull null
            personNode(
                id = personId,
                label = displayName(personId),
                role = "BRANCH",
                x = rect.x,
                y = rect.y,
                tileW = tileW,
                tileH = tileH
            )
        }
    val combinedNodes = repositionedBaseNodes + extraNodes
    val parentEdges = plan.visibleRelationships
        .filter { it.isLineageParentChild() }
        .groupBy { it.toPersonId }
        .flatMap { (childId, childRelationships) ->
            val childRect = positions[childId] ?: return@flatMap emptyList()
            val drawableRelationships = childRelationships.filter {
                positions[it.fromPersonId] != null
            }
            if (drawableRelationships.isEmpty()) return@flatMap emptyList()
            val parentageTypes = drawableRelationships.map { it.meta }.distinct()
            if (parentageTypes.size > 1) {
                return@flatMap drawableRelationships.mapNotNull { relationship ->
                    val parentRect = positions[relationship.fromPersonId]
                        ?: return@mapNotNull null
                    LineageEdge(
                        relationshipIds = setOf(relationship.relationshipId),
                        from = parentRect.bottomCenter(),
                        to = childRect.topCenter(),
                        meta = relationship.meta
                    )
                }
            }
            val parentRects = drawableRelationships.mapNotNull {
                positions[it.fromPersonId]
            }
            val parentIds = drawableRelationships.map { it.fromPersonId }.distinct()
            val recordedPartnership = parentIds.size == 2 &&
                plan.visibleRelationships.any { relationship ->
                    relationship.type == "SPOUSE" &&
                        setOf(relationship.fromPersonId, relationship.toPersonId) == parentIds.toSet()
                }
            if (recordedPartnership) {
                val parentAnchor =
                PointDp(
                    x = (
                        parentRects.minOf { it.center().x.value } +
                            parentRects.maxOf { it.center().x.value }
                        ).div(2f).dp,
                    y = parentRects.map { it.center().y.value }.average().toFloat().dp
                )
                listOf(LineageEdge(
                    relationshipIds = drawableRelationships
                        .mapTo(mutableSetOf()) { it.relationshipId },
                    from = parentAnchor,
                    to = childRect.topCenter(),
                    meta = parentageTypes.singleOrNull(),
                    sourceExitY = parentRects.maxOf { it.bottom.value }.dp
                ))
            } else {
                drawableRelationships.mapNotNull { relationship ->
                    val parentRect = positions[relationship.fromPersonId]
                        ?: return@mapNotNull null
                    LineageEdge(
                        relationshipIds = setOf(relationship.relationshipId),
                        from = parentRect.bottomCenter(),
                        to = childRect.topCenter(),
                        meta = relationship.meta
                    )
                }
            }
        }
    val spouseEdges = plan.visibleRelationships.mapNotNull { relationship ->
        if (relationship.type != "SPOUSE") return@mapNotNull null
        val alreadyRenderedAsCouple = combinedNodes.any { node ->
            node is CoupleNode &&
                setOf(node.leftId, node.rightId) ==
                setOf(relationship.fromPersonId, relationship.toPersonId)
        }
        if (alreadyRenderedAsCouple) return@mapNotNull null
        val from = positions[relationship.fromPersonId]?.center() ?: return@mapNotNull null
        val to = positions[relationship.toPersonId]?.center() ?: return@mapNotNull null
        LineageEdge(
            relationshipIds = setOf(relationship.relationshipId),
            from = from,
            to = to,
            meta = relationship.meta,
            type = "SPOUSE"
        )
    }
    val combinedEdges = parentEdges + spouseEdges
    val minX = combinedNodes.minOf { it.bounds().left.value }
    val minY = combinedNodes.minOf { it.bounds().top.value }
    val shiftX = (margin.value - minX).coerceAtLeast(0f).dp
    val shiftY = (margin.value - minY).coerceAtLeast(0f).dp
    val shiftedNodes = combinedNodes.map { it.shift(shiftX, shiftY) }
    val shiftedEdges = combinedEdges.map { edge ->
        edge.copy(
            from = PointDp(edge.from.x + shiftX, edge.from.y + shiftY),
            to = PointDp(edge.to.x + shiftX, edge.to.y + shiftY),
            sourceExitY = edge.sourceExitY + shiftY
        )
    }
    val maxX = shiftedNodes.maxOf { it.bounds().right.value }
    val maxY = shiftedNodes.maxOf { it.bounds().bottom.value }

    fun reposition(nodes: List<GraphNode>): List<GraphNode> = nodes.mapNotNull { original ->
        repositionedByKey[nodeKey(original)]?.shift(shiftX, shiftY)
    }
    return base.copy(
        center = requireNotNull(repositionedByKey[nodeKey(base.center)]).shift(shiftX, shiftY),
        parents = reposition(base.parents),
        siblings = reposition(base.siblings),
        children = reposition(base.children),
        nodes = shiftedNodes,
        lineageEdges = shiftedEdges,
        width = maxOf(base.width + shiftX, maxX.dp + margin),
        height = maxOf(base.height + shiftY, maxY.dp + margin)
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
        if (
            edge.type != "PARENT_CHILD" ||
            !isLineageParentChildMeta(edge.meta)
        ) return 0
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
            to = PointDp(edge.to.x + shiftX, edge.to.y + shiftY),
            sourceExitY = edge.sourceExitY + shiftY
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

internal fun cardDisplayName(person: PersonListItem): String =
    person.nickName
        ?.trim()
        ?.takeIf { it.isNotBlank() && it !in setOf("-", "—") }
        ?: familiarName(person.fullName)

private fun augmentLayoutWithUnconnectedPersons(
    base: GraphLayout,
    persons: List<PersonListItem>,
    relationships: List<ExportRelationship>,
    displayName: (String) -> String,
    tileW: Dp,
    tileH: Dp,
    siblingGapX: Dp,
    rankGapY: Dp,
    margin: Dp,
    preferredPositions: Map<String, GraphPreferredPosition> = emptyMap()
): GraphLayout {
    val visibleIds = base.nodes.flatMap(GraphNode::tiles).mapTo(mutableSetOf()) { it.id }
    val unconnectedIds = unconnectedPersonIds(persons, relationships, visibleIds)
    val unconnected = persons
        .filter { it.personId in unconnectedIds }
        .sortedWith(compareByDescending<PersonListItem> { it.createdAt }.thenBy { it.fullName })
    if (unconnected.isEmpty()) return base

    val columns = 4
    val left = base.nodes.minOf { it.bounds().left.value }
    val top = base.nodes.maxOf { it.bounds().bottom.value } + rankGapY.value + 44f
    val occupied = base.nodes
        .flatMap(GraphNode::tiles)
        .mapTo(mutableListOf()) { tile ->
            LineagePlacementRect(
                x = tile.rect.left.value,
                y = tile.rect.top.value,
                width = tile.rect.w.value,
                height = tile.rect.h.value
            )
        }
    val placements = linkedMapOf<String, LineagePlacementRect>()
    val preferred = unconnected.filter { preferredPositions.containsKey(it.personId) }
    preferred.forEach { person ->
        val desired = requireNotNull(preferredPositions[person.personId])
        val placement = nearestAvailableGraphCardRect(
            preferred = desired,
            occupied = occupied,
            tileWidth = tileW.value,
            tileHeight = tileH.value,
            horizontalStep = tileW.value + siblingGapX.value,
            verticalStep = tileH.value + rankGapY.value,
            margin = margin.value
        )
        placements[person.personId] = placement
        occupied += placement
    }
    unconnected
        .filterNot { preferredPositions.containsKey(it.personId) }
        .forEachIndexed { index, person ->
            val column = index % columns
            val row = index / columns
            val placement = nearestAvailableGraphCardRect(
                preferred = GraphPreferredPosition(
                    centerX = left + column * (tileW.value + siblingGapX.value) +
                        tileW.value / 2f,
                    centerY = top + row * (tileH.value + rankGapY.value) +
                        tileH.value / 2f
                ),
                occupied = occupied,
                tileWidth = tileW.value,
                tileHeight = tileH.value,
                horizontalStep = tileW.value + siblingGapX.value,
                verticalStep = tileH.value + rankGapY.value,
                margin = margin.value
            )
            placements[person.personId] = placement
            occupied += placement
        }
    val nodes = unconnected.map { person ->
        val placement = placements.getValue(person.personId)
        personNode(
            id = person.personId,
            label = displayName(person.personId),
            role = "UNCONNECTED",
            x = placement.left.dp,
            y = placement.top.dp,
            tileW = tileW,
            tileH = tileH
        )
    }
    val combined = base.nodes + nodes
    val maxX = combined.maxOf { it.bounds().right.value }
    val maxY = combined.maxOf { it.bounds().bottom.value }
    return base.copy(
        nodes = combined,
        width = maxOf(base.width, maxX.dp + margin),
        height = maxOf(base.height, maxY.dp + margin)
    )
}

internal fun nearestAvailableGraphCardRect(
    preferred: GraphPreferredPosition,
    occupied: List<LineagePlacementRect>,
    tileWidth: Float,
    tileHeight: Float,
    horizontalStep: Float,
    verticalStep: Float,
    margin: Float
): LineagePlacementRect {
    fun candidate(columnOffset: Int, rowOffset: Int): LineagePlacementRect {
        val centerX = preferred.centerX + columnOffset * horizontalStep
        val centerY = preferred.centerY + rowOffset * verticalStep
        return LineagePlacementRect(
            x = (centerX - tileWidth / 2f).coerceAtLeast(margin),
            y = (centerY - tileHeight / 2f).coerceAtLeast(margin),
            width = tileWidth,
            height = tileHeight
        )
    }

    for (radius in 0..64) {
        val offsets = if (radius == 0) {
            listOf(0 to 0)
        } else {
            buildList {
                for (row in -radius..radius) {
                    for (column in -radius..radius) {
                        if (kotlin.math.abs(column) == radius || kotlin.math.abs(row) == radius) {
                            add(column to row)
                        }
                    }
                }
            }.sortedWith(
                compareBy<Pair<Int, Int>> { (column, row) ->
                    column * column + row * row
                }.thenBy { (_, row) -> kotlin.math.abs(row) }
                    .thenBy { (column, _) -> kotlin.math.abs(column) }
                    .thenBy { (_, row) -> row }
                    .thenBy { (column, _) -> column }
            )
        }
        offsets.forEach { (column, row) ->
            val candidate = candidate(column, row)
            if (occupied.none { it.overlaps(candidate, padding = 8f) }) {
                return candidate
            }
        }
    }

    val fallbackTop = occupied.maxOfOrNull { it.bottom }?.plus(verticalStep) ?: margin
    return LineagePlacementRect(
        x = margin,
        y = fallbackTop,
        width = tileWidth,
        height = tileHeight
    )
}

private fun attachFocusedCareRelationships(
    base: GraphLayout,
    relationships: List<ExportRelationship>,
    focusedPersonId: String
): GraphLayout {
    val positions = base.nodes
        .flatMap(GraphNode::tiles)
        .associate { it.id to it.rect }
    val careEdges = relationships
        .asSequence()
        .filter {
            it.isCareRelationship() &&
                (it.fromPersonId == focusedPersonId || it.toPersonId == focusedPersonId)
        }
        .mapNotNull { relationship ->
            val caregiver = positions[relationship.fromPersonId] ?: return@mapNotNull null
            val recipient = positions[relationship.toPersonId] ?: return@mapNotNull null
            LineageEdge(
                relationshipIds = setOf(relationship.relationshipId),
                from = caregiver.center(),
                to = recipient.center(),
                meta = relationship.meta,
                type = "CARE"
            )
        }
        .toList()
    if (careEdges.isEmpty()) return base
    return base.copy(
        lineageEdges = base.lineageEdges
            .filterNot { it.type == "CARE" } + careEdges
    )
}

internal fun unconnectedPersonIds(
    persons: List<PersonListItem>,
    relationships: List<ExportRelationship>,
    visibleIds: Set<String> = emptySet()
): Set<String> {
    val relatedIds = relationships
        .filterNot { it.isCareRelationship() }
        .flatMapTo(mutableSetOf()) {
            listOf(it.fromPersonId, it.toPersonId)
        }
    return persons.mapNotNullTo(linkedSetOf()) { person ->
        person.personId.takeIf { it !in relatedIds && it !in visibleIds }
    }
}

internal fun findSiblingIds(
    personId: String,
    allRelationships: List<ExportRelationship>,
    persons: List<PersonListItem>
): List<String> {
    if (allRelationships.isEmpty()) return emptyList()
    val parentIds = allRelationships
        .filter { it.isLineageParentChild() && it.toPersonId == personId }
        .map { it.fromPersonId }
        .toSet()
    if (parentIds.isEmpty()) return emptyList()
    val siblingIds = allRelationships
        .filter { it.isLineageParentChild() && it.fromPersonId in parentIds }
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
    spouseGapX: Dp,
    singleParentChildByParent: Map<String, String> = emptyMap(),
    swappedPartnershipKeys: Set<String> = emptySet()
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
            val (leftPersonId, rightPersonId) = orientedPartnershipPair(
                personId,
                spouseId,
                swappedPartnershipKeys
            )
            items += RowItem(
                kind = RowKind.COUPLE,
                idA = leftPersonId,
                idB = rightPersonId,
                labelA = displayName(leftPersonId),
                labelB = displayName(rightPersonId),
                width = tileW.value * 2f + spouseGapX.value
            )
        } else if (personId in singleParentChildByParent) {
            items += RowItem(
                kind = RowKind.SINGLE_PARENT,
                idA = personId,
                idB = singleParentChildByParent.getValue(personId),
                labelA = displayName(personId),
                labelB = "Orang tua lain belum tercatat",
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
    val children = collectChildrenForVisibleParents(
        parentPersonIds = setOfNotNull(centerPersonId, activeSpouseId),
        relations = relations,
        allRelationships = allRelationships
    )

    val orderedChildren = orderChildren(children, persons)
    // Direct children use a stable birth order; progressively opened descendants
    // are positioned by the collision-aware planner.
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

@Composable
private fun ProfileAvatar(
    photoUrl: String?,
    gender: String?,
    muted: Boolean,
    modifier: Modifier = Modifier
) {
    if (photoUrl.isNullOrBlank()) {
        FallbackAvatar(gender = gender, muted = muted, modifier = modifier)
        return
    }
    SubcomposeAsyncImage(
        model = photoUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape),
        loading = {
            FallbackAvatar(gender = gender, muted = muted, modifier = Modifier.fillMaxSize())
        },
        error = {
            FallbackAvatar(gender = gender, muted = muted, modifier = Modifier.fillMaxSize())
        }
    )
}

internal fun familyGenerationLevels(
    centerPersonId: String,
    relationships: List<ExportRelationship>
): Map<String, Int> {
    data class GenerationStep(val personId: String, val delta: Int)

    val adjacency = mutableMapOf<String, MutableList<GenerationStep>>()
    relationships.filterNot { it.isCareRelationship() }.forEach { relationship ->
        val forwardDelta = if (relationship.isLineageParentChild()) 1 else 0
        val reverseDelta = if (relationship.isLineageParentChild()) -1 else 0
        adjacency.getOrPut(relationship.fromPersonId) { mutableListOf() }
            .add(GenerationStep(relationship.toPersonId, forwardDelta))
        adjacency.getOrPut(relationship.toPersonId) { mutableListOf() }
            .add(GenerationStep(relationship.fromPersonId, reverseDelta))
    }

    val levels = mutableMapOf(centerPersonId to 0)
    val queue = ArrayDeque<String>().apply { add(centerPersonId) }
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val currentLevel = levels.getValue(current)
        adjacency[current].orEmpty().forEach { step ->
            if (step.personId !in levels) {
                levels[step.personId] = currentLevel + step.delta
                queue.add(step.personId)
            }
        }
    }
    return levels
}

/**
 * The visible center can be a partnership unit, but its children are still derived
 * from explicit parent-child edges per person. This keeps a child recorded only for
 * one member visible without silently assigning the current partner as co-parent.
 */
private fun collectChildrenForVisibleParents(
    parentPersonIds: Set<String>,
    relations: RelationsResponse,
    allRelationships: List<ExportRelationship>
): List<String> {
    if (allRelationships.isEmpty()) {
        return relations.children.map { it.toPersonId }.distinct()
    }
    val index = LineageRelationshipIndex.from(allRelationships)
    val primaryPersonId = parentPersonIds.firstOrNull() ?: return emptyList()
    return recordedChildrenForPrimaryFamily(
        primaryPersonId = primaryPersonId,
        visibleParentPersonIds = parentPersonIds,
        index = index
    )
}

/**
 * Builds parentage connectors without inferring parentage from partnership:
 * - matching co-parents with a recorded partnership share the ring junction;
 * - co-parents without partnership keep individual anchors (a neutral visual hub);
 * - a single recorded parent starts at that person's card.
 */
private fun buildParentageEdges(
    childId: String,
    relationships: List<ExportRelationship>,
    visibleNodes: List<GraphNode>,
    allRelationships: List<ExportRelationship>
): List<LineageEdge> {
    val childTop = visibleNodes.tileRect(childId)?.topCenter() ?: return emptyList()
    val drawable = relationships
        .distinctBy { it.fromPersonId to it.meta }
        .filter { visibleNodes.tileRect(it.fromPersonId) != null }
    if (drawable.isEmpty()) return emptyList()

    val parentIds = drawable.map { it.fromPersonId }.distinct()
    val parentageTypes = drawable.map { it.meta }.distinct()
    val hasRecordedPartnership = parentIds.size == 2 && allRelationships.any { relationship ->
        relationship.type == "SPOUSE" &&
            setOf(relationship.fromPersonId, relationship.toPersonId) == parentIds.toSet()
    }
    if (parentageTypes.size == 1 && hasRecordedPartnership) {
        val parentRects = parentIds.mapNotNull(visibleNodes::tileRect)
        if (parentRects.size == 2) {
            return listOf(
                LineageEdge(
                    relationshipIds = drawable
                        .mapTo(mutableSetOf()) { it.relationshipId },
                    from = PointDp(
                        x = parentRects.map { it.center().x.value }.average().toFloat().dp,
                        y = parentRects.map { it.center().y.value }.average().toFloat().dp
                    ),
                    to = childTop,
                    meta = parentageTypes.single(),
                    sourceExitY = parentRects.maxOf { it.bottom.value }.dp
                )
            )
        }
    }

    return drawable.mapNotNull { relationship ->
        val parentBottom = visibleNodes.tileRect(relationship.fromPersonId)
            ?.bottomCenter() ?: return@mapNotNull null
        LineageEdge(
            relationshipIds = setOf(relationship.relationshipId),
            from = parentBottom,
            to = childTop,
            meta = relationship.meta
        )
    }
}

private fun collectChildrenForParentGroup(
    parentPersonIds: Set<String>,
    relations: RelationsResponse,
    allRelationships: List<ExportRelationship>
): List<String> {
    if (allRelationships.isEmpty()) {
        // Compatibility path while the full relationship cache is still loading.
        return relations.children.map { it.toPersonId }.distinct()
    }

    val index = LineageRelationshipIndex.from(allRelationships)
    return recordedChildrenForParentGroup(parentPersonIds, index)
}

private fun orderChildren(children: List<String>, persons: List<PersonListItem>): List<String> {
    val personById = persons.associateBy { it.personId }
    val keys = children.distinct().associateWith { id ->
        val person = personById[id]
        val birthDate = person?.birthDate
        ChildSortKey(
            hasBirthDate = if (birthDate == null) 1 else 0,
            birthDate = birthDate ?: "",
            recordedAt = person?.createdAt.orEmpty(),
            personId = id
        )
    }

    return keys.keys.sortedWith { a, b ->
        val ka = keys[a]!!
        val kb = keys[b]!!
        when {
            ka.hasBirthDate != kb.hasBirthDate -> ka.hasBirthDate - kb.hasBirthDate
            ka.birthDate != kb.birthDate -> ka.birthDate.compareTo(kb.birthDate)
            ka.recordedAt != kb.recordedAt -> ka.recordedAt.compareTo(kb.recordedAt)
            else -> ka.personId.compareTo(kb.personId)
        }
    }
}

private data class ChildSortKey(
    val hasBirthDate: Int,
    val birthDate: String,
    val recordedAt: String,
    val personId: String
)

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
    val relationship = latestCurrentPartnership(personId, allRelationships) ?: return null

    return relationship.otherPersonId(personId)
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

private fun singleParentNode(
    parentId: String,
    childId: String,
    parentLabel: String,
    role: String,
    x: Dp,
    y: Dp,
    tileW: Dp,
    tileH: Dp,
    gap: Dp
): GraphNode {
    val parentRect = RectDp(x, y, tileW, tileH)
    val placeholderRect = RectDp(x + tileW + gap, y, tileW, tileH)
    return SingleParentNode(
        parentId = parentId,
        childId = childId,
        parentLabel = parentLabel,
        parentRect = parentRect,
        placeholderRect = placeholderRect,
        wrapper = RectDp(x, y, tileW * 2f + gap, tileH),
        role = role
    )
}

private fun List<GraphNode>.tileRect(personId: String): RectDp? =
    asSequence()
        .flatMap { it.tiles().asSequence() }
        .firstOrNull { it.id == personId }
        ?.rect
