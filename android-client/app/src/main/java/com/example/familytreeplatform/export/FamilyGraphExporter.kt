package com.example.familytreeplatform.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

private data class LayoutUnit(val people: List<PersonListItem>)

object FamilyGraphExporter {
    fun renderPng(
        people: List<PersonListItem>,
        relationships: List<ExportRelationship>
    ): ByteArray {
        val generations = resolveGenerations(people, relationships)
        val height = max(900, 180 + (generations.values.maxOrNull() ?: 0) * 230 + 260)
        val bitmap = Bitmap.createBitmap(1800, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas, bitmap.width.toFloat(), bitmap.height.toFloat(), people, relationships, generations)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    fun renderPdf(
        people: List<PersonListItem>,
        relationships: List<ExportRelationship>
    ): ByteArray {
        val document = PdfDocument()
        return try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(1240, 1754, 1).create())
            draw(
                page.canvas,
                1240f,
                1754f,
                people,
                relationships,
                resolveGenerations(people, relationships)
            )
            document.finishPage(page)
            ByteArrayOutputStream().use { output ->
                document.writeTo(output)
                output.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    internal fun resolveGenerations(
        people: List<PersonListItem>,
        relationships: List<ExportRelationship>
    ): Map<String, Int> {
        val ids = people.mapTo(linkedSetOf()) { it.personId }
        val lineage = relationships.filter {
            it.type == "PARENT_CHILD" && it.fromPersonId in ids && it.toPersonId in ids
        }
        val lineageIds = lineage.flatMapTo(mutableSetOf()) {
            listOf(it.fromPersonId, it.toPersonId)
        }
        val depth = ids.associateWith { 0 }.toMutableMap()

        repeat(people.size.coerceAtLeast(1)) {
            lineage.forEach { relation ->
                val candidate = depth.getValue(relation.fromPersonId) + 1
                if (candidate > depth.getValue(relation.toPersonId)) {
                    depth[relation.toPersonId] = candidate
                }
            }
        }

        relationships
            .filter { it.type == "SPOUSE" && it.fromPersonId in ids && it.toPersonId in ids }
            .forEach { relation ->
                val fromHasLineage = relation.fromPersonId in lineageIds
                val toHasLineage = relation.toPersonId in lineageIds
                val generation = when {
                    fromHasLineage && !toHasLineage -> depth.getValue(relation.fromPersonId)
                    toHasLineage && !fromHasLineage -> depth.getValue(relation.toPersonId)
                    else -> max(
                        depth.getValue(relation.fromPersonId),
                        depth.getValue(relation.toPersonId)
                    )
                }
                depth[relation.fromPersonId] = generation
                depth[relation.toPersonId] = generation
            }
        return depth
    }

    private fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        people: List<PersonListItem>,
        relationships: List<ExportRelationship>,
        generations: Map<String, Int>
    ) {
        canvas.drawColor(Color.WHITE)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 45, 70)
            textSize = 44f
            isFakeBoldText = true
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 22f
        }
        canvas.drawText("Silsilah TRêdhAH", 60f, 70f, titlePaint)
        canvas.drawText("${people.size} people • ${relationships.size} relationships", 60f, 108f, subtitlePaint)

        if (people.isEmpty()) {
            canvas.drawText("No family members", 60f, 180f, subtitlePaint)
            return
        }

        val grouped = people.groupBy { generations[it.personId] ?: 0 }.toSortedMap()
        val positions = mutableMapOf<String, RectF>()
        val top = 160f
        val rowHeight = ((height - top - 50f) / grouped.size.coerceAtLeast(1)).coerceAtMost(230f)
        grouped.entries.forEachIndexed { rowIndex, entry ->
            val units = buildLayoutUnits(entry.value, relationships)
            val baseCardWidth = 280f
            val baseUnitGap = 32f
            val baseSpouseGap = 18f
            val desiredWidth = units.sumOf { unit ->
                if (unit.people.size == 2) {
                    (baseCardWidth * 2f + baseSpouseGap).toDouble()
                } else {
                    baseCardWidth.toDouble()
                }
            }.toFloat() + baseUnitGap * (units.size - 1).coerceAtLeast(0)
            val scale = min(1f, (width - 120f) / desiredWidth.coerceAtLeast(1f))
            val cardWidth = (baseCardWidth * scale).coerceAtLeast(110f)
            val unitGap = (baseUnitGap * scale).coerceAtLeast(12f)
            val spouseGap = (baseSpouseGap * scale).coerceAtLeast(10f)
            val unitWidths = units.map { unit ->
                if (unit.people.size == 2) cardWidth * 2f + spouseGap else cardWidth
            }
            val totalWidth = unitWidths.sum() + unitGap * (units.size - 1).coerceAtLeast(0)
            var x = (width - totalWidth) / 2f
            val y = top + rowIndex * rowHeight
            units.forEachIndexed { unitIndex, unit ->
                unit.people.forEachIndexed { personIndex, person ->
                    val left = x + personIndex * (cardWidth + spouseGap)
                    positions[person.personId] = RectF(left, y, left + cardWidth, y + 112f)
                }
                x += unitWidths[unitIndex] + unitGap
            }
        }

        val lineagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 105, 180)
            strokeWidth = 5f
        }
        val spousePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(135, 90, 150)
            strokeWidth = 4f
        }

        relationships
            .filter { it.type == "SPOUSE" }
            .forEach { relation ->
                val first = positions[relation.fromPersonId] ?: return@forEach
                val second = positions[relation.toPersonId] ?: return@forEach
                val left = if (first.left <= second.left) first else second
                val right = if (left === first) second else first
                canvas.drawLine(left.right, left.centerY(), right.left, right.centerY(), spousePaint)
            }

        relationships
            .filter { it.type == "PARENT_CHILD" }
            .groupBy { it.toPersonId }
            .forEach { (childId, relations) ->
                val child = positions[childId] ?: return@forEach
                val parents = relations.mapNotNull { positions[it.fromPersonId] }.distinctBy { it.left to it.top }
                if (parents.isEmpty()) return@forEach
                val targetX = child.centerX()
                if (parents.size == 1) {
                    val parent = parents.single()
                    val midY = (parent.bottom + child.top) / 2f
                    canvas.drawLine(parent.centerX(), parent.bottom, parent.centerX(), midY, lineagePaint)
                    canvas.drawLine(parent.centerX(), midY, targetX, midY, lineagePaint)
                    canvas.drawLine(targetX, midY, targetX, child.top, lineagePaint)
                } else {
                    val joinY = parents.maxOf { it.bottom } + 24f
                    val parentXs = parents.map { it.centerX() }
                    parents.forEach { parent ->
                        canvas.drawLine(parent.centerX(), parent.bottom, parent.centerX(), joinY, lineagePaint)
                    }
                    canvas.drawLine(parentXs.min(), joinY, parentXs.max(), joinY, lineagePaint)
                    val sourceX = (parentXs.min() + parentXs.max()) / 2f
                    val midY = (joinY + child.top) / 2f
                    canvas.drawLine(sourceX, joinY, sourceX, midY, lineagePaint)
                    canvas.drawLine(sourceX, midY, targetX, midY, lineagePaint)
                    canvas.drawLine(targetX, midY, targetX, child.top, lineagePaint)
                }
            }

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 243, 252) }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 105, 180)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 35, 55)
            textSize = 25f
            isFakeBoldText = true
        }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 19f
        }
        people.forEach { person ->
            val rect = positions.getValue(person.personId)
            canvas.drawRoundRect(rect, 14f, 14f, cardPaint)
            canvas.drawRoundRect(rect, 14f, 14f, borderPaint)
            val maxChars = max(8, (rect.width() / 14f).toInt())
            val name = person.fullName.let { if (it.length > maxChars) "${it.take(maxChars - 1)}…" else it }
            canvas.drawText(name, rect.left + 12f, rect.top + 38f, namePaint)
            val detail = listOfNotNull(person.birthDate, person.lifeStatus).joinToString(" • ")
            canvas.drawText(detail.take(maxChars + 5), rect.left + 12f, rect.top + 76f, detailPaint)
            canvas.drawText(
                "Generation ${generations[person.personId] ?: 0}",
                rect.left + 12f,
                rect.top + 101f,
                detailPaint
            )
        }
    }

    private fun buildLayoutUnits(
        members: List<PersonListItem>,
        relationships: List<ExportRelationship>
    ): List<LayoutUnit> {
        val byId = members.associateBy { it.personId }
        val remaining = members.mapTo(linkedSetOf()) { it.personId }
        val units = mutableListOf<LayoutUnit>()
        relationships
            .filter {
                it.type == "SPOUSE" &&
                    it.endDate == null &&
                    it.meta != "DIVORCED" &&
                    it.fromPersonId in remaining &&
                    it.toPersonId in remaining
            }
            .forEach { relation ->
                if (relation.fromPersonId !in remaining || relation.toPersonId !in remaining) return@forEach
                units += LayoutUnit(listOf(byId.getValue(relation.fromPersonId), byId.getValue(relation.toPersonId)))
                remaining.remove(relation.fromPersonId)
                remaining.remove(relation.toPersonId)
            }
        remaining
            .map(byId::getValue)
            .sortedBy { it.fullName }
            .forEach { units += LayoutUnit(listOf(it)) }
        return units.sortedBy { unit -> unit.people.minOf { it.fullName } }
    }
}
