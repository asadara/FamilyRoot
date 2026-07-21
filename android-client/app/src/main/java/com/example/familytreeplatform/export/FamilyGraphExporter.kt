package com.example.familytreeplatform.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.familytreeplatform.GraphExportLine
import com.example.familytreeplatform.GraphExportPlaceholder
import com.example.familytreeplatform.GraphExportSnapshot
import com.example.familytreeplatform.GraphExportTile
import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

private data class LayoutUnit(val people: List<PersonListItem>)

/** Static export renderer that mirrors the live graph's couple-ring and parent hub language. */
object FamilyGraphExporter {
    internal fun renderPng(snapshot: GraphExportSnapshot): ByteArray {
        val bitmap = Bitmap.createBitmap(1800, 1400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawSnapshot(canvas, bitmap.width.toFloat(), bitmap.height.toFloat(), snapshot)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    internal fun renderPdf(snapshot: GraphExportSnapshot): ByteArray {
        val document = PdfDocument()
        return try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(1240, 1754, 1).create())
            drawSnapshot(page.canvas, 1240f, 1754f, snapshot)
            document.finishPage(page)
            ByteArrayOutputStream().use { output ->
                document.writeTo(output)
                output.toByteArray()
            }
        } finally {
            document.close()
        }
    }

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

    private fun drawSnapshot(
        canvas: Canvas,
        width: Float,
        height: Float,
        snapshot: GraphExportSnapshot
    ) {
        canvas.drawColor(Color.WHITE)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 45, 70)
            textSize = 40f
            isFakeBoldText = true
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 20f
        }
        canvas.drawText("TR\u00eadhAH", 48f, 58f, titlePaint)
        canvas.drawText("Ekspor workspace pohon keluarga", 48f, 90f, subtitlePaint)
        if (snapshot.tiles.isEmpty()) return

        val marginX = 42f
        val top = 120f
        val availableWidth = width - marginX * 2f
        val availableHeight = height - top - 42f
        val scale = min(availableWidth / snapshot.width.coerceAtLeast(1f), availableHeight / snapshot.height.coerceAtLeast(1f))
        val offsetX = (width - snapshot.width * scale) / 2f
        canvas.save()
        canvas.translate(offsetX, top)
        canvas.scale(scale, scale)

        val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 108, 105)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f / scale
            strokeCap = Paint.Cap.ROUND
        }
        val spousePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(155, 116, 66)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f / scale
        }
        val partnershipLines = snapshotPartnershipLines(snapshot)
        partnershipLines.forEach { line ->
            val centerX = (line.fromX + line.toX) / 2f
            val centerY = (line.fromY + line.toY) / 2f
            val separation = if (line.meta == "DIVORCED") 10f else 5f
            val markerPaint = Paint(spousePaint).apply {
                if (line.meta == "WIDOWED") alpha = 122
            }
            canvas.drawCircle(centerX - separation, centerY, 7f, markerPaint)
            canvas.drawCircle(centerX + separation, centerY, 7f, markerPaint)
        }
        snapshot.lineageLines.filterNot { it.type == "SPOUSE" }.forEach { line ->
            val hubY = line.fromY + (line.toY - line.fromY) / 2f
            canvas.drawLine(line.fromX, line.fromY, line.fromX, hubY, connectorPaint)
            canvas.drawLine(line.fromX, hubY, line.toX, hubY, connectorPaint)
            canvas.drawLine(line.toX, hubY, line.toX, line.toY, connectorPaint)
        }

        snapshot.tiles.forEach { tile -> drawSnapshotTile(canvas, tile, connectorPaint) }
        snapshot.placeholders.forEach { placeholder ->
            drawSnapshotPlaceholder(canvas, placeholder, connectorPaint)
        }
        canvas.restore()
    }

    internal fun snapshotPartnershipLines(snapshot: GraphExportSnapshot): List<GraphExportLine> =
        (snapshot.spouseLines + snapshot.lineageLines.filter { it.type == "SPOUSE" })
            .distinctBy { line ->
            listOf(line.fromX to line.fromY, line.toX to line.toY)
                .sortedWith(compareBy<Pair<Float, Float>> { it.first }.thenBy { it.second })
        }

    private fun drawSnapshotPlaceholder(
        canvas: Canvas,
        placeholder: GraphExportPlaceholder,
        borderPaint: Paint
    ) {
        val rect = RectF(
            placeholder.x,
            placeholder.y,
            placeholder.x + placeholder.width,
            placeholder.y + placeholder.height
        )
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(246, 245, 241) }
        val dashedBorder = Paint(borderPaint).apply {
            color = Color.rgb(145, 145, 138)
            pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        }
        canvas.drawRoundRect(rect, 14f, 14f, fillPaint)
        canvas.drawRoundRect(rect, 14f, 14f, dashedBorder)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(95, 95, 90)
            textSize = 15f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("+", rect.centerX(), rect.centerY() - 8f, textPaint.apply {
            textSize = 24f
        })
        textPaint.textSize = 13f
        canvas.drawText("Orang tua lain", rect.centerX(), rect.centerY() + 16f, textPaint)
        canvas.drawText("belum tercatat", rect.centerX(), rect.centerY() + 34f, textPaint)
    }

    private fun drawSnapshotTile(canvas: Canvas, tile: GraphExportTile, borderPaint: Paint) {
        val rect = RectF(tile.x, tile.y, tile.x + tile.width, tile.y + tile.height)
        val deceased = tile.lifeStatus == "DECEASED"
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (deceased) Color.rgb(232, 229, 221) else Color.rgb(255, 251, 240)
        }
        val cardBorder = Paint(borderPaint).apply {
            color = if (deceased) Color.rgb(141, 137, 128) else Color.rgb(122, 111, 91)
        }
        canvas.drawRoundRect(rect, 14f, 14f, fillPaint)
        canvas.drawRoundRect(rect, 14f, 14f, cardBorder)

        val avatarBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (deceased) Color.rgb(214, 210, 201) else Color.rgb(247, 228, 174)
        }
        val avatarForeground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (deceased) Color.rgb(126, 123, 117) else Color.rgb(113, 78, 26)
            style = Paint.Style.FILL
        }
        val centerX = rect.centerX()
        val avatarCenterY = rect.top + 42f
        val avatarRadius = 28f
        canvas.drawCircle(centerX, avatarCenterY, avatarRadius, avatarBackground)
        canvas.drawCircle(centerX, avatarCenterY - 8f, 8f, avatarForeground)
        canvas.drawOval(
            RectF(
                centerX - 15f,
                avatarCenterY + 3f,
                centerX + 15f,
                avatarCenterY + 20f
            ),
            avatarForeground
        )
        if (tile.gender == "FEMALE") {
            val hairPaint = Paint(avatarForeground).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawArc(
                RectF(
                    centerX - 11f,
                    avatarCenterY - 17f,
                    centerX + 11f,
                    avatarCenterY + 5f
                ),
                185f,
                170f,
                false,
                hairPaint
            )
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (deceased) Color.rgb(88, 85, 79) else Color.rgb(48, 37, 23)
            textSize = 14f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val nameLines = twoLineText(tile.label, namePaint, rect.width() - 24f)
        val nameStartY = rect.top + 91f
        nameLines.forEachIndexed { index, line ->
            canvas.drawText(line, centerX, nameStartY + index * 17f, namePaint)
        }
        tile.age?.let { age ->
            val agePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (deceased) Color.rgb(105, 102, 96) else Color.rgb(92, 78, 57)
                textSize = 12f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("$age tahun", centerX, rect.bottom - 14f, agePaint)
        }
    }

    private fun twoLineText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return listOf("?")
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else if (lines.isEmpty()) {
                lines += current
                current = word
            } else {
                current += " $word"
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines.take(2).mapIndexed { index, line ->
            if (index == 1 && lines.size > 2 || paint.measureText(line) > maxWidth) {
                var shortened = line
                while (shortened.isNotEmpty() && paint.measureText("$shortened…") > maxWidth) {
                    shortened = shortened.dropLast(1)
                }
                "$shortened…"
            } else {
                line
            }
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
        canvas.drawText("TR\u00eadhAH", 60f, 70f, titlePaint)
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
