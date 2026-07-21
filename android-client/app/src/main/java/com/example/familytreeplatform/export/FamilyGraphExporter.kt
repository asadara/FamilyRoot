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

/** Static export renderer that mirrors the live graph's couple-ring and parent hub language. */
object FamilyGraphExporter {
    fun renderPng(people: List<PersonListItem>, relationships: List<ExportRelationship>): ByteArray {
        val generations = resolveGenerations(people, relationships)
        val height = max(900, 190 + (generations.values.maxOrNull() ?: 0) * 250 + 300)
        val bitmap = Bitmap.createBitmap(1800, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas, bitmap.width.toFloat(), bitmap.height.toFloat(), people, relationships, generations)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    fun renderPdf(people: List<PersonListItem>, relationships: List<ExportRelationship>): ByteArray {
        val document = PdfDocument()
        return try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(1240, 1754, 1).create())
            draw(page.canvas, 1240f, 1754f, people, relationships, resolveGenerations(people, relationships))
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
        val lineageIds = lineage.flatMapTo(mutableSetOf()) { listOf(it.fromPersonId, it.toPersonId) }
        val depth = ids.associateWith { 0 }.toMutableMap()
        repeat(people.size.coerceAtLeast(1)) {
            lineage.forEach { relation ->
                val candidate = depth.getValue(relation.fromPersonId) + 1
                if (candidate > depth.getValue(relation.toPersonId)) depth[relation.toPersonId] = candidate
            }
        }
        relationships.filter { it.type == "SPOUSE" && it.fromPersonId in ids && it.toPersonId in ids }
            .forEach { relation ->
                val fromHasLineage = relation.fromPersonId in lineageIds
                val toHasLineage = relation.toPersonId in lineageIds
                val generation = when {
                    fromHasLineage && !toHasLineage -> depth.getValue(relation.fromPersonId)
                    toHasLineage && !fromHasLineage -> depth.getValue(relation.toPersonId)
                    else -> max(depth.getValue(relation.fromPersonId), depth.getValue(relation.toPersonId))
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
        canvas.drawText("TRêdhAH", 60f, 70f, titlePaint)
        canvas.drawText("Merangkai jejak, menyatukan trah  |  ${people.size} person  |  ${relationships.size} hubungan", 60f, 108f, subtitlePaint)
        if (people.isEmpty()) {
            canvas.drawText("Belum ada person dalam silsilah ini", 60f, 180f, subtitlePaint)
            return
        }

        val grouped = people.groupBy { generations[it.personId] ?: 0 }.toSortedMap()
        val positions = mutableMapOf<String, RectF>()
        val top = 160f
        val rowHeight = ((height - top - 60f) / grouped.size.coerceAtLeast(1)).coerceAtMost(250f)
        grouped.entries.forEachIndexed { rowIndex, entry ->
            val units = buildLayoutUnits(entry.value, relationships)
            val baseCardWidth = 280f
            val unitGap = 32f
            val spouseGap = 18f
            val desiredWidth = units.sumOf { unit ->
                if (unit.people.size == 2) (baseCardWidth * 2f + spouseGap).toDouble() else baseCardWidth.toDouble()
            }.toFloat() + unitGap * (units.size - 1).coerceAtLeast(0)
            val scale = min(1f, (width - 120f) / desiredWidth.coerceAtLeast(1f))
            val cardWidth = (baseCardWidth * scale).coerceAtLeast(110f)
            val actualUnitGap = (unitGap * scale).coerceAtLeast(12f)
            val actualSpouseGap = (spouseGap * scale).coerceAtLeast(10f)
            val unitWidths = units.map { unit ->
                if (unit.people.size == 2) cardWidth * 2f + actualSpouseGap else cardWidth
            }
            val totalWidth = unitWidths.sum() + actualUnitGap * (units.size - 1).coerceAtLeast(0)
            var x = (width - totalWidth) / 2f
            val y = top + rowIndex * rowHeight
            units.forEachIndexed { unitIndex, unit ->
                unit.people.forEachIndexed { personIndex, person ->
                    val left = x + personIndex * (cardWidth + actualSpouseGap)
                    positions[person.personId] = RectF(left, y, left + cardWidth, y + 132f)
                }
                x += unitWidths[unitIndex] + actualUnitGap
            }
        }

        val lineagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(80, 105, 94)
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        val spousePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(155, 116, 66)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        relationships.filter { it.type == "SPOUSE" && it.fromPersonId in positions && it.toPersonId in positions }
            .forEach { relation ->
                val first = positions.getValue(relation.fromPersonId)
                val second = positions.getValue(relation.toPersonId)
                val centerX = (first.centerX() + second.centerX()) / 2f
                val centerY = (first.centerY() + second.centerY()) / 2f
                canvas.drawCircle(centerX - 7f, centerY, 13f, spousePaint)
                canvas.drawCircle(centerX + 7f, centerY, 13f, spousePaint)
            }

        relationships.filter { it.type == "PARENT_CHILD" && it.fromPersonId in positions && it.toPersonId in positions }
            .groupBy { child ->
                relationships.filter { it.type == "PARENT_CHILD" && it.toPersonId == child.toPersonId }
                    .map { it.fromPersonId }.filter { it in positions }.distinct().sorted().joinToString("|")
            }
            .values.forEach { groupedRelations ->
                val parentIds = groupedRelations.flatMap { child ->
                    relationships.filter { it.type == "PARENT_CHILD" && it.toPersonId == child.toPersonId }
                        .map { it.fromPersonId }
                }.filter { it in positions }.distinct()
                val parents = parentIds.mapNotNull { positions[it] }
                val children = groupedRelations.mapNotNull { positions[it.toPersonId] }.distinctBy { it.left to it.top }
                if (parents.isEmpty() || children.isEmpty()) return@forEach
                val parentJoinY = parents.maxOf { it.bottom } + 24f
                val childJoinY = children.minOf { it.top } - 24f
                val parentXs = parents.map { it.centerX() }.sorted()
                val childXs = children.map { it.centerX() }.sorted()
                parents.forEach { parent -> canvas.drawLine(parent.centerX(), parent.bottom, parent.centerX(), parentJoinY, lineagePaint) }
                if (parentXs.size > 1) canvas.drawLine(parentXs.first(), parentJoinY, parentXs.last(), parentJoinY, lineagePaint)
                canvas.drawLine(parentXs.average().toFloat(), parentJoinY, parentXs.average().toFloat(), childJoinY, lineagePaint)
                if (childXs.size > 1) canvas.drawLine(childXs.first(), childJoinY, childXs.last(), childJoinY, lineagePaint)
                children.forEach { child -> canvas.drawLine(child.centerX(), childJoinY, child.centerX(), child.top, lineagePaint) }
            }

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(250, 248, 243) }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(92, 109, 98)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 35, 55)
            textSize = 22f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }
        people.forEach { person ->
            val rect = positions.getValue(person.personId)
            canvas.drawRoundRect(rect, 18f, 18f, cardPaint)
            canvas.drawRoundRect(rect, 18f, 18f, borderPaint)
            val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(226, 235, 226) }
            val avatarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(52, 82, 62)
                textSize = 18f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            val avatarX = rect.centerX()
            val avatarY = rect.top + 30f
            canvas.drawCircle(avatarX, avatarY, 17f, avatarPaint)
            val initials = person.fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.first().uppercase() }
            canvas.drawText(initials.ifBlank { "?" }, avatarX, avatarY + 6f, avatarTextPaint)
            val maxChars = max(8, (rect.width() / 13f).toInt())
            val name = person.fullName.let { if (it.length > maxChars) "${it.take(maxChars - 1)}…" else it }
            canvas.drawText(name, avatarX, rect.top + 66f, namePaint)
            val detail = listOfNotNull(person.birthDate, person.lifeStatus).joinToString("  |  ")
            canvas.drawText(detail.take(maxChars + 5), avatarX, rect.top + 91f, detailPaint)
            canvas.drawText("Generasi ${generations[person.personId] ?: 0}", avatarX, rect.top + 115f, detailPaint)
        }
    }

    private fun buildLayoutUnits(members: List<PersonListItem>, relationships: List<ExportRelationship>): List<LayoutUnit> {
        val byId = members.associateBy { it.personId }
        val remaining = members.mapTo(linkedSetOf()) { it.personId }
        val units = mutableListOf<LayoutUnit>()
        relationships.filter {
            it.type == "SPOUSE" && it.endDate == null && it.meta != "DIVORCED" &&
                it.fromPersonId in remaining && it.toPersonId in remaining
        }.forEach { relation ->
            if (relation.fromPersonId !in remaining || relation.toPersonId !in remaining) return@forEach
            units += LayoutUnit(listOf(byId.getValue(relation.fromPersonId), byId.getValue(relation.toPersonId)))
            remaining.remove(relation.fromPersonId)
            remaining.remove(relation.toPersonId)
        }
        remaining.map(byId::getValue).sortedBy { it.fullName }.forEach { units += LayoutUnit(listOf(it)) }
        return units.sortedBy { unit -> unit.people.minOf { it.fullName } }
    }
}
