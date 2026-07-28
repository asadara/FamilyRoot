package com.example.familytreeplatform.feature.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphRenderPolicyTest {
    @Test
    fun `viewport inversion includes configurable world overscan`() {
        val viewport = visibleWorldRect(
            scale = 2f,
            offsetX = -200f,
            offsetY = -100f,
            viewportWidth = 800f,
            viewportHeight = 600f,
            overscanWorld = 50f
        )

        assertEquals(50f, viewport.left)
        assertEquals(0f, viewport.top)
        assertEquals(550f, viewport.right)
        assertEquals(400f, viewport.bottom)
    }

    @Test
    fun `intersection keeps crossing and boundary tiles but rejects distant tiles`() {
        val viewport = GraphRenderRect(100f, 100f, 500f, 400f)

        assertTrue(GraphRenderRect(50f, 150f, 550f, 160f).intersects(viewport))
        assertTrue(GraphRenderRect(500f, 400f, 540f, 440f).intersects(viewport))
        assertFalse(GraphRenderRect(501f, 401f, 540f, 440f).intersects(viewport))
    }

    @Test
    fun `card detail follows stable zoom thresholds`() {
        assertEquals(GraphCardDetail.MINIMAL, graphCardDetail(0.5f))
        assertEquals(GraphCardDetail.COMPACT, graphCardDetail(0.62f))
        assertEquals(GraphCardDetail.COMPACT, graphCardDetail(0.85f))
        assertEquals(GraphCardDetail.FULL, graphCardDetail(0.86f))
    }

    @Test
    fun `text fallback starts only above interactive device budget`() {
        assertEquals(GraphRenderMode.INTERACTIVE, graphRenderMode(800))
        assertEquals(GraphRenderMode.TEXT_FALLBACK, graphRenderMode(801))
        assertEquals(
            GraphRenderMode.TEXT_FALLBACK,
            graphRenderMode(tileCount = 11, maxInteractiveTiles = 10)
        )
    }

    @Test
    fun `text fallback ordering pins center then sorts deterministically`() {
        val ordered = orderGraphTextFallbackEntries(
            listOf(
                GraphTextFallbackEntry("3", "Zahra", isCenter = false),
                GraphTextFallbackEntry("2", "budi", isCenter = false),
                GraphTextFallbackEntry("1", "Aji", isCenter = true),
                GraphTextFallbackEntry("4", "Budi", isCenter = false)
            )
        )

        assertEquals(listOf("1", "2", "4", "3"), ordered.map { it.personId })
    }
}
