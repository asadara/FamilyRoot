package com.example.familytreeplatform.feature.graph

internal const val MAX_INTERACTIVE_GRAPH_TILES = 800

internal enum class GraphCardDetail {
    MINIMAL,
    COMPACT,
    FULL
}

internal enum class GraphRenderMode {
    INTERACTIVE,
    TEXT_FALLBACK
}

internal data class GraphRenderRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(right >= left) { "right must not be smaller than left" }
        require(bottom >= top) { "bottom must not be smaller than top" }
    }

    fun intersects(other: GraphRenderRect): Boolean =
        right >= other.left &&
            left <= other.right &&
            bottom >= other.top &&
            top <= other.bottom

    fun contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom
}

internal data class GraphTextFallbackEntry(
    val personId: String,
    val displayName: String,
    val isCenter: Boolean
)

/**
 * Identity-free minimap geometry. Deliberately contains no person or relationship
 * identifiers, labels, media, life data, or semantic relationship metadata.
 */
internal data class GraphMinimapOverview(
    val nodeRects: List<GraphRenderRect>,
    val edgeLines: List<GraphMinimapLine>
)

internal data class GraphMinimapLine(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float
)

internal data class GraphMinimapProjection(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

internal fun graphRenderMode(
    tileCount: Int,
    maxInteractiveTiles: Int = MAX_INTERACTIVE_GRAPH_TILES
): GraphRenderMode {
    require(tileCount >= 0) { "tileCount must not be negative" }
    require(maxInteractiveTiles > 0) { "maxInteractiveTiles must be positive" }
    return if (tileCount > maxInteractiveTiles) {
        GraphRenderMode.TEXT_FALLBACK
    } else {
        GraphRenderMode.INTERACTIVE
    }
}

internal fun graphCardDetail(scale: Float): GraphCardDetail = when {
    scale < 0.62f -> GraphCardDetail.MINIMAL
    scale < 0.86f -> GraphCardDetail.COMPACT
    else -> GraphCardDetail.FULL
}

internal fun visibleWorldRect(
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    overscanWorld: Float = 0f
): GraphRenderRect {
    require(scale > 0f) { "scale must be positive" }
    require(viewportWidth >= 0f) { "viewportWidth must not be negative" }
    require(viewportHeight >= 0f) { "viewportHeight must not be negative" }
    require(overscanWorld >= 0f) { "overscanWorld must not be negative" }
    return GraphRenderRect(
        left = -offsetX / scale - overscanWorld,
        top = -offsetY / scale - overscanWorld,
        right = (viewportWidth - offsetX) / scale + overscanWorld,
        bottom = (viewportHeight - offsetY) / scale + overscanWorld
    )
}

internal fun orderGraphTextFallbackEntries(
    entries: List<GraphTextFallbackEntry>
): List<GraphTextFallbackEntry> = entries.sortedWith(
    compareByDescending<GraphTextFallbackEntry> { it.isCenter }
        .thenBy { it.displayName.trim().lowercase() }
        .thenBy { it.personId }
)

internal fun graphMinimapProjection(
    graphWidth: Float,
    graphHeight: Float,
    minimapWidth: Float,
    minimapHeight: Float,
    padding: Float = 0f
): GraphMinimapProjection {
    require(graphWidth > 0f && graphHeight > 0f) { "graph dimensions must be positive" }
    require(minimapWidth > 0f && minimapHeight > 0f) {
        "minimap dimensions must be positive"
    }
    require(padding >= 0f) { "padding must not be negative" }
    val availableWidth = (minimapWidth - padding * 2f).coerceAtLeast(1f)
    val availableHeight = (minimapHeight - padding * 2f).coerceAtLeast(1f)
    val scale = minOf(availableWidth / graphWidth, availableHeight / graphHeight)
    return GraphMinimapProjection(
        scale = scale,
        offsetX = (minimapWidth - graphWidth * scale) / 2f,
        offsetY = (minimapHeight - graphHeight * scale) / 2f
    )
}

internal fun projectMinimapRect(
    world: GraphRenderRect,
    projection: GraphMinimapProjection
): GraphRenderRect = GraphRenderRect(
    left = projection.offsetX + world.left * projection.scale,
    top = projection.offsetY + world.top * projection.scale,
    right = projection.offsetX + world.right * projection.scale,
    bottom = projection.offsetY + world.bottom * projection.scale
)

internal fun minimapWorldPoint(
    minimapX: Float,
    minimapY: Float,
    projection: GraphMinimapProjection,
    graphWidth: Float,
    graphHeight: Float
): Pair<Float, Float> {
    require(projection.scale > 0f) { "projection scale must be positive" }
    return (
        (minimapX - projection.offsetX) / projection.scale
        ).coerceIn(0f, graphWidth) to (
        (minimapY - projection.offsetY) / projection.scale
        ).coerceIn(0f, graphHeight)
}

internal fun graphViewportOffsetsForCenter(
    worldX: Float,
    worldY: Float,
    scale: Float,
    viewportWidth: Float,
    viewportHeight: Float
): Pair<Float, Float> {
    require(scale > 0f) { "scale must be positive" }
    require(viewportWidth >= 0f) { "viewportWidth must not be negative" }
    require(viewportHeight >= 0f) { "viewportHeight must not be negative" }
    return (
        viewportWidth / 2f - worldX * scale
        ) to (
        viewportHeight / 2f - worldY * scale
        )
}

internal fun shouldShowGraphMinimap(
    graphWidth: Float,
    graphHeight: Float,
    viewportWorld: GraphRenderRect
): Boolean =
    graphWidth > viewportWorld.right - viewportWorld.left + 1f ||
        graphHeight > viewportWorld.bottom - viewportWorld.top + 1f
