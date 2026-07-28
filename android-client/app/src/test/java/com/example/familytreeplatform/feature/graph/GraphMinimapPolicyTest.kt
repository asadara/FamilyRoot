package com.example.familytreeplatform.feature.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphMinimapPolicyTest {
    @Test
    fun `projection preserves aspect ratio and centers graph`() {
        val projection = graphMinimapProjection(
            graphWidth = 1_000f,
            graphHeight = 500f,
            minimapWidth = 200f,
            minimapHeight = 120f,
            padding = 10f
        )

        assertEquals(0.18f, projection.scale, 0.0001f)
        assertEquals(10f, projection.offsetX, 0.0001f)
        assertEquals(15f, projection.offsetY, 0.0001f)
    }

    @Test
    fun `tap converts to bounded world position`() {
        val projection = graphMinimapProjection(
            graphWidth = 1_000f,
            graphHeight = 500f,
            minimapWidth = 200f,
            minimapHeight = 100f
        )

        assertEquals(
            500f to 250f,
            minimapWorldPoint(100f, 50f, projection, 1_000f, 500f)
        )
        assertEquals(
            0f to 0f,
            minimapWorldPoint(-50f, -50f, projection, 1_000f, 500f)
        )
    }

    @Test
    fun `pan navigation centers selected world point without changing scale`() {
        assertEquals(
            -800f to -450f,
            graphViewportOffsetsForCenter(
                worldX = 600f,
                worldY = 350f,
                scale = 2f,
                viewportWidth = 800f,
                viewportHeight = 500f
            )
        )
    }

    @Test
    fun `minimap only appears when graph extends beyond viewport`() {
        assertFalse(
            shouldShowGraphMinimap(
                graphWidth = 500f,
                graphHeight = 300f,
                viewportWorld = GraphRenderRect(0f, 0f, 500f, 300f)
            )
        )
        assertTrue(
            shouldShowGraphMinimap(
                graphWidth = 1_000f,
                graphHeight = 600f,
                viewportWorld = GraphRenderRect(100f, 100f, 600f, 400f)
            )
        )
    }

    @Test
    fun `overview geometry exposes no identity or semantic fields`() {
        val overviewFields = GraphMinimapOverview::class.java.declaredFields
            .map { it.name.lowercase() }
        val lineFields = GraphMinimapLine::class.java.declaredFields
            .map { it.name.lowercase() }
        val forbidden = listOf(
            "id",
            "name",
            "photo",
            "age",
            "status",
            "gender",
            "meta",
            "type",
            "count"
        )

        forbidden.forEach { term ->
            assertTrue(overviewFields.none { term in it })
            assertTrue(lineFields.none { term in it })
        }
    }
}
