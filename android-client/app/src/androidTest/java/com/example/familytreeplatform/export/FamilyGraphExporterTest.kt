package com.example.familytreeplatform.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.familytreeplatform.GraphExportLine
import com.example.familytreeplatform.GraphExportPlaceholder
import com.example.familytreeplatform.GraphExportSnapshot
import com.example.familytreeplatform.GraphExportTile
import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class FamilyGraphExporterTest {
    private val person = PersonListItem(
        personId = "person-1",
        fullName = "Siti Aminah",
        createdAt = "2026-01-01T00:00:00.000Z",
        lifeStatus = "ALIVE"
    )

    @Test
    fun pngHasExpectedSignature() {
        val bytes = FamilyGraphExporter.renderPng(listOf(person), emptyList())
        assertEquals(0x89, bytes[0].toInt() and 0xff)
        assertEquals('P'.code, bytes[1].toInt())
        assertTrue(bytes.size > 1_000)
    }

    @Test
    fun pdfHasExpectedHeader() {
        val bytes = FamilyGraphExporter.renderPdf(listOf(person), emptyList())
        assertEquals("%PDF", bytes.take(4).map(Byte::toInt).map(Int::toChar).joinToString(""))
        assertTrue(bytes.size > 1_000)
    }

    @Test
    fun workspaceSnapshotIsTheSourceForPngExport() {
        val snapshot = GraphExportSnapshot(
            width = 420f,
            height = 520f,
            tiles = listOf(
                GraphExportTile("parent", "Budi", "CENTER", 150f, 50f, 120f, 152f),
                GraphExportTile("child", "Raka", "CHILD", 150f, 300f, 120f, 152f)
            ),
            spouseLines = emptyList(),
            lineageLines = listOf(
                GraphExportLine(210f, 202f, 210f, 300f, "PARENT_CHILD", "BIOLOGICAL")
            ),
            placeholders = listOf(
                GraphExportPlaceholder(
                    label = "Orang tua lain belum tercatat",
                    x = 282f,
                    y = 50f,
                    width = 120f,
                    height = 152f
                )
            )
        )

        val bytes = FamilyGraphExporter.renderPng(snapshot)

        assertEquals(0x89, bytes[0].toInt() and 0xff)
        assertTrue(bytes.size > 1_000)
    }

    @Test
    fun progressiveAndAtomicPartnershipMarkersShareTheExportRingRenderer() {
        val atomic = GraphExportLine(10f, 20f, 40f, 20f, "SPOUSE", null)
        val progressive = GraphExportLine(60f, 20f, 90f, 20f, "SPOUSE", "DIVORCED")
        val lineage = GraphExportLine(20f, 40f, 20f, 80f, "PARENT_CHILD", "BIOLOGICAL")
        val snapshot = GraphExportSnapshot(
            width = 100f,
            height = 100f,
            tiles = emptyList(),
            spouseLines = listOf(atomic),
            lineageLines = listOf(progressive, lineage)
        )

        assertEquals(
            listOf(atomic, progressive),
            FamilyGraphExporter.snapshotPartnershipLines(snapshot)
        )
    }

    @Test
    fun spouseInheritsLineageGeneration() {
        fun member(id: String, name: String) = PersonListItem(
            personId = id,
            fullName = name,
            createdAt = "2026-01-01T00:00:00.000Z",
            lifeStatus = "ALIVE"
        )
        fun relation(id: String, type: String, from: String, to: String) = ExportRelationship(
            relationshipId = id,
            type = type,
            fromPersonId = from,
            toPersonId = to,
            meta = if (type == "SPOUSE") "MARRIED" else "BIOLOGICAL",
            createdAt = "2026-01-01T00:00:00.000Z"
        )
        val people = listOf(
            member("hadi", "Hadi"),
            member("budi", "Budi"),
            member("siti", "Siti"),
            member("raka", "Raka"),
            member("alya", "Alya")
        )
        val relationships = listOf(
            relation("1", "PARENT_CHILD", "hadi", "budi"),
            relation("2", "PARENT_CHILD", "budi", "raka"),
            relation("3", "PARENT_CHILD", "siti", "raka"),
            relation("4", "SPOUSE", "budi", "siti"),
            relation("5", "SPOUSE", "raka", "alya")
        )

        val generations = FamilyGraphExporter.resolveGenerations(people, relationships)

        assertEquals(1, generations["budi"])
        assertEquals(1, generations["siti"])
        assertEquals(2, generations["raka"])
        assertEquals(2, generations["alya"])
    }

    @Test
    fun pngExportStaysWithinPhysicalDeviceBudget() {
        val people = (0 until 60).map { index ->
            PersonListItem(
                personId = "person-$index",
                fullName = "Family Member $index",
                createdAt = "2026-07-18T00:00:00.000Z",
                lifeStatus = "ALIVE"
            )
        }

        var byteCount = 0
        val elapsedMs = measureTimeMillis {
            byteCount = FamilyGraphExporter.renderPng(people, emptyList()).size
        }

        assertTrue("PNG export took ${elapsedMs}ms (budget 8000ms)", elapsedMs <= 8_000)
        assertTrue(byteCount > 1_000)
    }
}
