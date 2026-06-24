package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface TextMeasurer {
    fun measureTextWidth(text: String, fontSize: Float, isBold: Boolean): Float
    fun getFontHeight(fontSize: Float, isBold: Boolean): Float
}

enum class DrawAlign { LEFT, CENTER, RIGHT }

sealed class DrawInstruction {
    data class Text(
        val text: String,
        val x: Float,
        val y: Float,
        val fontSize: Float,
        val isBold: Boolean,
        val isItalic: Boolean = false,
        val colorRgb: Int = 0x1F2937,
        val align: DrawAlign = DrawAlign.LEFT
    ) : DrawInstruction()

    data class Line(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val colorRgb: Int = 0xE5E7EB,
        val strokeWidth: Float = 1f
    ) : DrawInstruction()

    data class Rect(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val colorRgb: Int,
        val isFill: Boolean = false,
        val strokeWidth: Float = 1f
    ) : DrawInstruction()

    data class Image(
        val path: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float
    ) : DrawInstruction()
}

data class RenderedPage(
    val pageNumber: Int,
    val instructions: List<DrawInstruction>
)

class PdfLayoutEngine(
    private val textMeasurer: TextMeasurer,
    private val imageSizeProvider: (String) -> Pair<Float, Float>
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val tableAdapter = moshi.adapter(TableBlockContent::class.java)
    private val checklistAdapter = moshi.adapter(ChecklistBlockContent::class.java)
    private val checklistTableAdapter = moshi.adapter(ChecklistTableBlockContent::class.java)

    private val marginX = 54f
    private val pageWidth = 595f
    private val pageHeight = 842f
    private val usableWidth = pageWidth - (2 * marginX)

    fun wrapText(text: String, fontSize: Float, isBold: Boolean, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val lines = text.split("\n")
        for (line in lines) {
            val words = line.split(" ")
            val currentLine = StringBuilder()
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                val width = textMeasurer.measureTextWidth(testLine, fontSize, isBold)
                if (width < maxWidth) {
                    currentLine.append(if (currentLine.isEmpty()) "" else " ").append(word)
                } else {
                    result.add(currentLine.toString())
                    currentLine.clear()
                    currentLine.append(word)
                }
            }
            if (currentLine.isNotEmpty()) {
                result.add(currentLine.toString())
            }
        }
        return result
    }

    fun getRequiredHeight(block: BlockData, colWidth: Float): Float {
        return when (block.type) {
            BlockType.TITLE.name -> 30f
            BlockType.FOOTER.name -> 20f
            BlockType.TEXT.name -> {
                val lines = wrapText(block.content, 12f, false, colWidth)
                lines.size * 18f + 12f
            }
            BlockType.IMAGE.name -> {
                val originalSize = imageSizeProvider(block.content)
                val originalWidth = originalSize.first
                val originalHeight = originalSize.second
                if (originalWidth > 0) {
                    val scaleRatio = colWidth / originalWidth
                    originalHeight * scaleRatio + 20f
                } else 40f
            }
            BlockType.SIGNATURE.name -> {
                val parts = block.content.split("|")
                val filePath = parts[0]
                val originalSize = imageSizeProvider(filePath)
                val originalWidth = originalSize.first
                val originalHeight = originalSize.second
                if (originalWidth > 0) {
                    val targetWidth = minOf(colWidth, 180f)
                    val scaleRatio = targetWidth / originalWidth
                    originalHeight * scaleRatio + 55f
                } else 115f
            }
            BlockType.TABLE.name -> {
                val content = try { tableAdapter.fromJson(block.content) ?: TableBlockContent() } catch (e: Exception) { TableBlockContent() }
                (content.rows.size + (if (content.headers.isNotEmpty()) 1 else 0)) * 22f + (if (content.title.isNotBlank()) 32f else 0f) + 15f
            }
            BlockType.CHECKLIST.name -> {
                val content = try { checklistAdapter.fromJson(block.content) ?: ChecklistBlockContent() } catch (e: Exception) { ChecklistBlockContent() }
                content.items.size * 18f + (if (content.title.isNotBlank()) 32f else 0f) + 15f
            }
            BlockType.CHECKLIST_TABLE.name -> {
                val content = try { checklistTableAdapter.fromJson(block.content) ?: ChecklistTableBlockContent() } catch (e: Exception) { ChecklistTableBlockContent() }
                (content.rows.size + 1) * 22f + (if (content.title.isNotBlank()) 32f else 0f) + 15f
            }
            else -> 0f
        }
    }

    fun layoutPdf(
        project: ProjectData,
        exportMode: PdfExportMode,
        singleVisitId: String?
    ): List<RenderedPage> {
        val showHeaderBox = project.showHeaderBox
        val contentStartY = if (showHeaderBox) 105f else 60f
        val contentEndY = pageHeight - 60f

        // 1. Prepare sorted blocks list (including formatted Visit items)
        val sortedBlocks = when (exportMode) {
            PdfExportMode.COMMON_ONLY -> project.blocks.filter { it.visitUuid == null }.sortedBy { it.sequence }
            PdfExportMode.SINGLE_VISIT -> {
                val list = mutableListOf<BlockData>()
                list.addAll(project.blocks.filter { it.visitUuid == null }.sortedBy { it.sequence })
                project.visits.find { it.uuid == singleVisitId }?.let { visit ->
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    list.add(BlockData(uuid = "temp_title_${visit.uuid}", type = BlockType.TITLE.name, content = "VISITA: ${visit.title} (${sdf.format(Date(visit.date))})", sequence = -1))
                    if (visit.notes.isNotBlank()) {
                        list.add(BlockData(uuid = "temp_notes_${visit.uuid}", type = BlockType.TEXT.name, content = "Notas de reunión o incidencias:\n" + visit.notes, sequence = -1))
                    }
                    list.addAll(project.blocks.filter { it.visitUuid == singleVisitId }.sortedBy { it.sequence })
                }
                list
            }
            PdfExportMode.FULL_REPORT -> {
                val list = mutableListOf<BlockData>()
                list.addAll(project.blocks.filter { it.visitUuid == null }.sortedBy { it.sequence })
                project.visits.sortedBy { it.date }.forEach { v ->
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    list.add(BlockData(uuid = "temp_title_${v.uuid}", type = BlockType.TITLE.name, content = "VISITA: ${v.title.uppercase(Locale.getDefault())} (${sdf.format(Date(v.date))})", sequence = -1))
                    if (v.notes.isNotBlank()) {
                        list.add(BlockData(uuid = "temp_notes_${v.uuid}", type = BlockType.TEXT.name, content = "Notas de reunión o incidencias:\n" + v.notes, sequence = -1))
                    }
                    list.addAll(project.blocks.filter { it.visitUuid == v.uuid }.sortedBy { it.sequence })
                }
                list
            }
        }

        // 2. Dry run: calculate total pages count
        var dryPageCount = 1
        var dryY = contentStartY
        val proj = project
        if (proj.showHeaderLabel) dryY += 24f
        if (proj.showHeaderTitle) dryY += 20f
        if (proj.showHeaderDate) dryY += 15f
        dryY += 35f // horizontal line and spaces

        var dryIndex = 0
        while (dryIndex < sortedBlocks.size) {
            val block = sortedBlocks[dryIndex]
            if (block.isHalfWidth) {
                val nextBlock = sortedBlocks.getOrNull(dryIndex + 1)
                if (nextBlock != null && nextBlock.isHalfWidth) {
                    val colWidth = usableWidth / 2f - 6f
                    val maxH = maxOf(getRequiredHeight(block, colWidth), getRequiredHeight(nextBlock, colWidth))
                    if (dryY + maxH > contentEndY) {
                        dryPageCount++
                        dryY = contentStartY
                    }
                    dryY += maxH
                    dryIndex += 2
                } else {
                    val h = getRequiredHeight(block, usableWidth / 2f - 6f)
                    if (dryY + h > contentEndY) {
                        dryPageCount++
                        dryY = contentStartY
                    }
                    dryY += h
                    dryIndex += 1
                }
            } else {
                val h = getRequiredHeight(block, usableWidth)
                if (dryY + h > contentEndY) {
                    dryPageCount++
                    dryY = contentStartY
                }
                dryY += h
                dryIndex += 1
            }
        }
        val totalPages = dryPageCount

        // 3. Render layout: generate list of drawing instructions page by page
        val pages = mutableListOf<RenderedPage>()
        var currentPageNumber = 1
        var currentInstructions = mutableListOf<DrawInstruction>()
        var currentY = contentStartY

        fun appendHeader() {
            if (!showHeaderBox) return
            val startX = 40f
            val endX = 555f
            val topY = 35f
            val bottomY = 85f

            // Outer box & division lines
            currentInstructions.add(DrawInstruction.Rect(240f, topY, 465f, bottomY, 0xF3F4F6, isFill = true))
            currentInstructions.add(DrawInstruction.Rect(startX, topY, endX, bottomY, 0xE5E7EB, isFill = false, strokeWidth = 1.2f))
            currentInstructions.add(DrawInstruction.Line(240f, topY, 240f, bottomY, 0xE5E7EB, strokeWidth = 1.2f))
            currentInstructions.add(DrawInstruction.Line(465f, topY, 465f, bottomY, 0xE5E7EB, strokeWidth = 1.2f))

            // Company Title and Sub-Lines
            currentInstructions.add(DrawInstruction.Text(proj.headerCompany.ifBlank { "Nombre de la empresa" }, startX + 10f, topY + 16f, 10f, isBold = true, colorRgb = 0x9A6640))
            val subLines = proj.headerCompanySub.split("\n")
            var subY = topY + 26f
            for (line in subLines) {
                if (line.isNotBlank()) {
                    currentInstructions.add(DrawInstruction.Text(line.trim(), startX + 10f, subY, 5.5f, isBold = false, colorRgb = 0x6B7280))
                    subY += 8f
                }
            }

            // Report Title centered
            currentInstructions.add(DrawInstruction.Text(proj.headerTitle.ifBlank { "INFORME DE VISITA A OBRA" }.uppercase(Locale.getDefault()), 240f + 112.5f, topY + 29f, 12f, isBold = true, colorRgb = 0x111827, align = DrawAlign.CENTER))

            // Pagination
            currentInstructions.add(DrawInstruction.Text("Página $currentPageNumber de $totalPages", 465f + 45f, topY + 28f, 10f, isBold = false, colorRgb = 0x374151, align = DrawAlign.CENTER))
        }

        fun finalizePage() {
            pages.add(RenderedPage(currentPageNumber, currentInstructions))
            currentInstructions = mutableListOf()
        }

        fun startNewPage() {
            finalizePage()
            currentPageNumber++
            appendHeader()
            currentY = contentStartY
        }

        // Draw initial page header and project titles
        appendHeader()
        if (proj.showHeaderLabel) {
            currentInstructions.add(DrawInstruction.Text(proj.reportLabel.ifBlank { "REPORTE DE PROYECTO" }.uppercase(Locale.getDefault()), marginX, currentY, 9f, isBold = false, colorRgb = 0x9CA3AF))
            currentY += 24f
        }
        if (proj.showHeaderTitle) {
            currentInstructions.add(DrawInstruction.Text(proj.name.uppercase(Locale.getDefault()), marginX, currentY, 22f, isBold = true, colorRgb = 0x1F2937))
            currentY += 20f
        }
        if (proj.showHeaderDate) {
            val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())
            val dateText = "Fecha de creación: " + sdf.format(Date(proj.createdAt))
            currentInstructions.add(DrawInstruction.Text(dateText, marginX, currentY, 11f, isBold = false, colorRgb = 0x6B7280))
            currentY += 15f
        }
        currentInstructions.add(DrawInstruction.Line(marginX, currentY, pageWidth - marginX, currentY, 0xE5E7EB, strokeWidth = 1f))
        currentY += 35f

        fun drawBlock(block: BlockData, x: Float, width: Float, startY: Float): Float {
            var y = startY
            when (block.type) {
                BlockType.TITLE.name -> {
                    currentInstructions.add(DrawInstruction.Text(block.content, x, y + 15f, 16f, isBold = true, colorRgb = 0x1F2937))
                    y += 25f
                }
                BlockType.FOOTER.name -> {
                    currentInstructions.add(DrawInstruction.Text(block.content, x, y + 10f, 9f, isBold = false, isItalic = true, colorRgb = 0x6B7280))
                    y += 18f
                }
                BlockType.TEXT.name -> {
                    val lines = wrapText(block.content, 12f, false, width)
                    for (line in lines) {
                        currentInstructions.add(DrawInstruction.Text(line, x, y + 12f, 12f, isBold = false, colorRgb = 0x374151))
                        y += 18f
                    }
                    y += 10f
                }
                BlockType.IMAGE.name -> {
                    val size = imageSizeProvider(block.content)
                    val originalWidth = size.first
                    val originalHeight = size.second
                    if (originalWidth > 0) {
                        val scaleRatio = width / originalWidth
                        val targetHeight = originalHeight * scaleRatio
                        currentInstructions.add(DrawInstruction.Image(block.content, x, y, width, targetHeight))
                        currentInstructions.add(DrawInstruction.Rect(x, y, x + width, y + targetHeight, 0xE5E7EB, isFill = false, strokeWidth = 1f))
                        y += targetHeight + 16f
                    }
                }
                BlockType.SIGNATURE.name -> {
                    val parts = block.content.split("|")
                    val filePath = parts[0]
                    val signatureLabel = parts.getOrNull(1)?.ifBlank { null } ?: "Firma de Validación"
                    val signatureSubtitle = parts.getOrNull(2)?.ifBlank { null } ?: "Firma Autorizada"
                    val size = imageSizeProvider(filePath)
                    val originalWidth = size.first
                    val originalHeight = size.second
                    val targetWidth = minOf(width, 180f)

                    if (originalWidth > 0) {
                        val scaleRatio = targetWidth / originalWidth
                        val targetHeight = originalHeight * scaleRatio
                        currentInstructions.add(DrawInstruction.Rect(x, y, x + targetWidth, y + targetHeight, 0xF9FAFB, isFill = true))
                        currentInstructions.add(DrawInstruction.Rect(x, y, x + targetWidth, y + targetHeight, 0xE5E7EB, isFill = false, strokeWidth = 1f))
                        currentInstructions.add(DrawInstruction.Image(filePath, x, y, targetWidth, targetHeight))
                        val textY = y + targetHeight + 14f
                        currentInstructions.add(DrawInstruction.Text(signatureLabel, x, textY, 11f, isBold = true, colorRgb = 0x1F2937))
                        currentInstructions.add(DrawInstruction.Text(signatureSubtitle, x, textY + 14f, 9f, isBold = false, colorRgb = 0x9CA3AF))
                        y += targetHeight + 35f
                    } else {
                        val targetHeight = 80f
                        currentInstructions.add(DrawInstruction.Rect(x, y, x + targetWidth, y + targetHeight, 0xF9FAFB, isFill = true))
                        currentInstructions.add(DrawInstruction.Rect(x, y, x + targetWidth, y + targetHeight, 0xE5E7EB, isFill = false, strokeWidth = 1f))
                        currentInstructions.add(DrawInstruction.Line(x + 10f, y + targetHeight - 15f, x + targetWidth - 10f, y + targetHeight - 15f, 0xD1D5DB, strokeWidth = 1f))
                        val textY = y + targetHeight + 14f
                        currentInstructions.add(DrawInstruction.Text(signatureLabel, x, textY, 10f, isBold = true, colorRgb = 0x1F2937))
                        currentInstructions.add(DrawInstruction.Text(signatureSubtitle, x, textY + 14f, 9f, isBold = false, colorRgb = 0x9CA3AF))
                        y += targetHeight + 35f
                    }
                }
                BlockType.TABLE.name -> {
                    val content = try { tableAdapter.fromJson(block.content) ?: TableBlockContent() } catch (e: Exception) { TableBlockContent() }
                    if (content.rows.isNotEmpty()) {
                        val numCols = if (content.headers.isNotEmpty()) content.headers.size else content.rows[0].size
                        val colW = width / numCols.toFloat()
                        var cellY = y
                        if (content.title.isNotBlank()) {
                            currentInstructions.add(DrawInstruction.Text(content.title, x, cellY + 15f, 11f, isBold = true, colorRgb = 0x111827))
                            cellY += 32f
                        }

                        content.rows.forEachIndexed { rowIndex, row ->
                            val isHeader = rowIndex == 0 && content.headers.isNotEmpty()
                            if (isHeader) {
                                currentInstructions.add(DrawInstruction.Rect(x, cellY, x + width, cellY + 22f, 0xF3F4F6, isFill = true))
                            }
                            currentInstructions.add(DrawInstruction.Rect(x, cellY, x + width, cellY + 22f, 0xE5E7EB, isFill = false, strokeWidth = 1f))
                            
                            row.forEachIndexed { colIndex, cellText ->
                                if (colIndex < numCols) {
                                    val cellX = x + colIndex * colW
                                    if (colIndex > 0) {
                                        currentInstructions.add(DrawInstruction.Line(cellX, cellY, cellX, cellY + 22f, 0xE5E7EB, strokeWidth = 1f))
                                    }
                                    currentInstructions.add(DrawInstruction.Text(cellText.trim(), cellX + 6f, cellY + 15f, 10f, isBold = isHeader, colorRgb = if (isHeader) 0x111827 else 0x374151))
                                }
                            }
                            cellY += 22f
                        }
                        y = cellY + 10f
                    }
                }
                BlockType.CHECKLIST.name -> {
                    val content = try { checklistAdapter.fromJson(block.content) ?: ChecklistBlockContent() } catch (e: Exception) { ChecklistBlockContent() }
                    var cellY = y
                    if (content.title.isNotBlank()) {
                        currentInstructions.add(DrawInstruction.Text(content.title, x, cellY + 15f, 11f, isBold = true, colorRgb = 0x111827))
                        cellY += 32f
                    }

                    content.items.forEach { item ->
                        val boxSize = 8.5f
                        val boxX = x + 2f
                        val boxY = cellY + 4f

                        currentInstructions.add(DrawInstruction.Rect(boxX, boxY, boxX + boxSize, boxY + boxSize, 0x000000, isFill = false, strokeWidth = 0.5f))
                        if (item.checked) {
                            currentInstructions.add(DrawInstruction.Text("X", boxX + boxSize/2f, boxY + boxSize - 1.5f, 8f, isBold = true, colorRgb = 0x000000, align = DrawAlign.CENTER))
                        }
                        currentInstructions.add(DrawInstruction.Text(item.text, x + 16f, cellY + 13f, 11f, isBold = false, colorRgb = 0x374151))
                        cellY += 18f
                    }
                    y = cellY + 10f
                }
                BlockType.CHECKLIST_TABLE.name -> {
                    val content = try { checklistTableAdapter.fromJson(block.content) ?: ChecklistTableBlockContent() } catch (e: Exception) { ChecklistTableBlockContent() }
                    if (content.rows.isNotEmpty()) {
                        val statusCols = content.headers
                        val numStatus = statusCols.size
                        val textColW = width * 0.65f
                        val statusColW = (width * 0.35f) / numStatus.coerceAtLeast(1)
                        var cellY = y
                        if (content.title.isNotBlank()) {
                            currentInstructions.add(DrawInstruction.Text(content.title, x, cellY + 15f, 11f, isBold = true, colorRgb = 0x111827))
                            cellY += 32f
                        }

                        // Header
                        currentInstructions.add(DrawInstruction.Rect(x, cellY, x + width, cellY + 22f, 0xF3F4F6, isFill = true))
                        currentInstructions.add(DrawInstruction.Rect(x, cellY, x + width, cellY + 22f, 0xE5E7EB, isFill = false, strokeWidth = 1f))
                        currentInstructions.add(DrawInstruction.Text("Comprobaciones", x + 6f, cellY + 15f, 11f, isBold = true, colorRgb = 0x111827))
                        statusCols.forEachIndexed { idx, colName ->
                            val statusX = x + textColW + idx * statusColW
                            currentInstructions.add(DrawInstruction.Line(statusX, cellY, statusX, cellY + 22f, 0xE5E7EB, strokeWidth = 1f))
                            currentInstructions.add(DrawInstruction.Text(colName, statusX + statusColW/2f, cellY + 15f, 11f, isBold = true, colorRgb = 0x111827, align = DrawAlign.CENTER))
                        }
                        cellY += 22f

                        // Rows
                        content.rows.forEach { row ->
                            currentInstructions.add(DrawInstruction.Rect(x, cellY, x + width, cellY + 22f, 0xE5E7EB, isFill = false, strokeWidth = 1f))
                            currentInstructions.add(DrawInstruction.Text(row.text, x + 6f, cellY + 15f, 10f, isBold = false, colorRgb = 0x374151))
                            statusCols.forEachIndexed { idx, _ ->
                                val statusX = x + textColW + idx * statusColW
                                currentInstructions.add(DrawInstruction.Line(statusX, cellY, statusX, cellY + 22f, 0xE5E7EB, strokeWidth = 1f))
                                val centerX = statusX + statusColW/2f
                                val centerY = cellY + 11f

                                if (row.selectedIndex == idx) {
                                    currentInstructions.add(DrawInstruction.Text("X", centerX, centerY + 4f, 10f, isBold = true, colorRgb = 0x111827, align = DrawAlign.CENTER))
                                }
                                currentInstructions.add(DrawInstruction.Rect(centerX - 6f, centerY - 6f, centerX + 6f, centerY + 6f, 0xE5E7EB, isFill = false, strokeWidth = 1f))
                            }
                            cellY += 22f
                        }
                        y = cellY + 10f
                    }
                }
            }
            return y
        }

        var i = 0
        while (i < sortedBlocks.size) {
            val block = sortedBlocks[i]
            if (block.isHalfWidth) {
                val nextBlock = sortedBlocks.getOrNull(i + 1)
                if (nextBlock != null && nextBlock.isHalfWidth) {
                    val colWidth = usableWidth / 2f - 6f
                    val h1 = getRequiredHeight(block, colWidth)
                    val h2 = getRequiredHeight(nextBlock, colWidth)
                    val maxH = maxOf(h1, h2)

                    if (currentY + maxH > contentEndY) {
                        startNewPage()
                    }

                    drawBlock(block, marginX, colWidth, currentY)
                    drawBlock(nextBlock, marginX + colWidth + 12f, colWidth, currentY)
                    currentY += maxH
                    i += 2
                } else {
                    val colWidth = usableWidth / 2f - 6f
                    val h = getRequiredHeight(block, colWidth)
                    if (currentY + h > contentEndY) {
                        startNewPage()
                    }
                    drawBlock(block, marginX, colWidth, currentY)
                    currentY += h
                    i += 1
                }
            } else {
                val h = getRequiredHeight(block, usableWidth)
                if (currentY + h > contentEndY) {
                    startNewPage()
                }
                drawBlock(block, marginX, usableWidth, currentY)
                currentY += h
                i += 1
            }
        }

        finalizePage()
        return pages
    }
}
