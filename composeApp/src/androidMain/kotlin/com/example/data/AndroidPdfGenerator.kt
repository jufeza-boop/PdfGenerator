package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidPdfGenerator(private val context: Context) : PdfGenerator {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val tableAdapter = moshi.adapter(TableBlockContent::class.java)
    private val checklistAdapter = moshi.adapter(ChecklistBlockContent::class.java)
    private val checklistTableAdapter = moshi.adapter(ChecklistTableBlockContent::class.java)

    override suspend fun generatePdf(
        project: ProjectWithBlocks,
        exportMode: PdfExportMode,
        singleVisitId: Long?
    ): String {
        val pdfFile = File(context.cacheDir, "project_report_${project.project.id}.pdf")
        if (pdfFile.exists()) {
            pdfFile.delete()
        }

        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 width
        val pageHeight = 842 // A4 height
        
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas
        
        // Define beautiful styling colors
        val titlePaint = Paint().apply {
            color = Color.rgb(31, 41, 55)
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(107, 114, 128)
            textSize = 11f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.rgb(55, 65, 81)
            textSize = 12f
            isAntiAlias = true
        }

        val labelPaint = Paint().apply {
            color = Color.rgb(156, 163, 175)
            textSize = 9f
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.rgb(229, 231, 235)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val marginX = 54f
        val usableWidth = pageWidth - (2 * marginX)

        val showHeaderBox = project.project.showHeaderBox
        val contentStartY = if (showHeaderBox) 105f else 60f
        val contentEndY = pageHeight - 60f

        val mainTitlePaint = Paint().apply {
            color = Color.rgb(31, 41, 55)
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val footerTextPaint = Paint().apply {
            color = Color.rgb(107, 114, 128)
            textSize = 9f
            isAntiAlias = true
        }

        val tableHeadPaint = Paint().apply {
            color = Color.rgb(17, 24, 39)
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val tableCellPaint = Paint().apply {
            color = Color.rgb(55, 65, 81)
            textSize = 10f
            isAntiAlias = true
        }

        val tableBgPaint = Paint().apply {
            color = Color.rgb(243, 244, 246)
            style = Paint.Style.FILL
        }

        val checkboxPaint = Paint().apply {
            color = Color.rgb(37, 99, 235)
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val checklistTextPaint = Paint().apply {
            color = Color.rgb(31, 41, 55)
            textSize = 11f
            isAntiAlias = true
        }

        fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
            val result = mutableListOf<String>()
            val lines = text.split("\n")
            for (line in lines) {
                val words = line.split(" ")
                val currentLine = StringBuilder()
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    val width = paint.measureText(testLine)
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

        fun drawCompanyHeader(canvas: Canvas, pageNum: Int, totalPages: Int, proj: ProjectEntity) {
            if (!proj.showHeaderBox) return
            val startX = 40f
            val endX = 555f
            val topY = 35f
            val bottomY = 85f
            val boxPaint = Paint().apply {
                color = Color.rgb(229, 231, 235)
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
                isAntiAlias = true
            }
            val fillPaint = Paint().apply {
                color = Color.rgb(243, 244, 246)
                style = Paint.Style.FILL
            }
            canvas.drawRect(240f, topY, 465f, bottomY, fillPaint)
            canvas.drawRect(startX, topY, endX, bottomY, boxPaint)
            canvas.drawLine(240f, topY, 240f, bottomY, boxPaint)
            canvas.drawLine(465f, topY, 465f, bottomY, boxPaint)
            val compTitlePaint = Paint().apply {
                color = Color.rgb(154, 102, 64)
                textSize = 10f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val compSubPaint = Paint().apply {
                color = Color.rgb(107, 114, 128)
                textSize = 5.5f
                isAntiAlias = true
            }
            canvas.drawText(proj.headerCompany.ifBlank { "Nombre de la empresa" }, startX + 10f, topY + 16f, compTitlePaint)
            val subLines = proj.headerCompanySub.split("\n")
            var subY = topY + 26f
            for (line in subLines) {
                if (line.isNotBlank()) {
                    canvas.drawText(line.trim(), startX + 10f, subY, compSubPaint)
                    subY += 8f
                }
            }
            val centerPaint = Paint().apply {
                color = Color.rgb(17, 24, 39)
                textSize = 12f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(proj.headerTitle.ifBlank { "INFORME DE VISITA A OBRA" }.uppercase(Locale.getDefault()), 240f + 112.5f, topY + 29f, centerPaint)
            val pagePaint = Paint().apply {
                color = Color.rgb(55, 65, 81)
                textSize = 10f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Página $pageNum de $totalPages", 465f + 45f, topY + 28f, pagePaint)
        }

        fun getRequiredHeight(currBlock: ContentBlockEntity, colWidth: Float): Float {
            return when (currBlock.type) {
                BlockType.TITLE -> 30f
                BlockType.FOOTER -> 20f
                BlockType.TEXT -> {
                    val lines = wrapText(currBlock.content, textPaint, colWidth)
                    lines.size * 18f + 12f
                }
                BlockType.IMAGE -> {
                    val file = File(currBlock.content)
                    if (file.exists()) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, options)
                        val originalWidth = options.outWidth.toFloat()
                        val originalHeight = options.outHeight.toFloat()
                        if (originalWidth > 0) {
                            val scaleRatio = colWidth / originalWidth
                            originalHeight * scaleRatio + 20f
                        } else 40f
                    } else 40f
                }
                BlockType.SIGNATURE -> {
                    val file = File(currBlock.content.split("|")[0])
                    if (file.exists()) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, options)
                        val originalWidth = options.outWidth.toFloat()
                        val originalHeight = options.outHeight.toFloat()
                        if (originalWidth > 0) {
                            val targetWidth = minOf(colWidth, 180f)
                            val scaleRatio = targetWidth / originalWidth
                            originalHeight * scaleRatio + 55f
                        } else 80f
                    } else 115f
                }
                BlockType.TABLE -> {
                    val content = try { tableAdapter.fromJson(currBlock.content) ?: TableBlockContent() } catch(e: Exception) { TableBlockContent() }
                    (content.rows.size + (if (content.headers.isNotEmpty()) 1 else 0)) * 22f + (if (content.title.isNotBlank()) 25f else 0f) + 15f
                }
                BlockType.CHECKLIST -> {
                    val content = try { checklistAdapter.fromJson(currBlock.content) ?: ChecklistBlockContent() } catch(e: Exception) { ChecklistBlockContent() }
                    content.items.size * 20f + (if (content.title.isNotBlank()) 25f else 0f) + 15f
                }
                BlockType.CHECKLIST_TABLE -> {
                    val content = try { checklistTableAdapter.fromJson(currBlock.content) ?: ChecklistTableBlockContent() } catch(e: Exception) { ChecklistTableBlockContent() }
                    (content.rows.size + 1) * 22f + (if (content.title.isNotBlank()) 25f else 0f) + 15f
                }
            }
        }

        fun drawBlock(currBlock: ContentBlockEntity, x: Float, colWidth: Float, startY: Float): Float {
            var y = startY
            when (currBlock.type) {
                BlockType.TITLE -> {
                    canvas.drawText(currBlock.content, x, y + 15f, mainTitlePaint)
                    y += 25f
                }
                BlockType.FOOTER -> {
                    canvas.drawText(currBlock.content, x, y + 10f, footerTextPaint)
                    y += 18f
                }
                BlockType.TEXT -> {
                    val lines = wrapText(currBlock.content, textPaint, colWidth)
                    for (line in lines) {
                        canvas.drawText(line, x, y + 12f, textPaint)
                        y += 18f
                    }
                    y += 10f
                }
                BlockType.IMAGE -> {
                    val file = File(currBlock.content)
                    if (file.exists()) {
                        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (originalBitmap != null) {
                            val scaleRatio = colWidth / originalBitmap.width.toFloat()
                            val targetHeight = (originalBitmap.height * scaleRatio).toInt()
                            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, colWidth.toInt(), targetHeight, true)
                            canvas.drawBitmap(scaledBitmap, x, y, null)
                            canvas.drawRect(x, y, x + colWidth, y + targetHeight, borderPaint)
                            y += targetHeight + 16f
                        }
                    }
                }
                BlockType.SIGNATURE -> {
                    val parts = currBlock.content.split("|")
                    val filePath = parts[0]
                    val signatureLabel = parts.getOrNull(1)?.ifBlank { null } ?: "Firma de Validación"
                    val signatureSubtitle = parts.getOrNull(2)?.ifBlank { null } ?: "Firma Autorizada"
                    val file = File(filePath)
                    if (file.exists()) {
                        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (originalBitmap != null) {
                            val targetWidth = minOf(colWidth, 180f)
                            val scaleRatio = targetWidth / originalBitmap.width.toFloat()
                            val targetHeight = (originalBitmap.height * scaleRatio).toInt()
                            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth.toInt(), targetHeight, true)
                            val bgPaint = Paint().apply { color = Color.rgb(249, 250, 251); style = Paint.Style.FILL }
                            canvas.drawRect(x, y, x + targetWidth, y + targetHeight, bgPaint)
                            canvas.drawRect(x, y, x + targetWidth, y + targetHeight, borderPaint)
                            canvas.drawBitmap(scaledBitmap, x, y, null)
                            val textY = y + targetHeight + 14f
                            val boldLabelPaint = Paint().apply { color = Color.rgb(31, 41, 55); textSize = 11f; isFakeBoldText = true; isAntiAlias = true }
                            canvas.drawText(signatureLabel, x, textY, boldLabelPaint)
                            canvas.drawText(signatureSubtitle, x, textY + 14f, labelPaint)
                            y += targetHeight + 35f
                        }
                    } else {
                        val targetWidth = minOf(colWidth, 180f)
                        val targetHeight = 80f
                        val bgPaint = Paint().apply { color = Color.rgb(249, 250, 251); style = Paint.Style.FILL }
                        canvas.drawRect(x, y, x + targetWidth, y + targetHeight, bgPaint)
                        canvas.drawRect(x, y, x + targetWidth, y + targetHeight, borderPaint)
                        val linePaint = Paint().apply { color = Color.rgb(209, 213, 219); strokeWidth = 1f; style = Paint.Style.STROKE }
                        canvas.drawLine(x + 10f, y + targetHeight - 15f, x + targetWidth - 10f, y + targetHeight - 15f, linePaint)
                        val textY = y + targetHeight + 14f
                        val boldLabelPaint = Paint().apply { color = Color.rgb(31, 41, 55); textSize = 10f; isFakeBoldText = true; isAntiAlias = true }
                        canvas.drawText(signatureLabel, x, textY, boldLabelPaint)
                        canvas.drawText(signatureSubtitle, x, textY + 14f, labelPaint)
                        y += targetHeight + 35f
                    }
                }
                BlockType.TABLE -> {
                    val content = try { tableAdapter.fromJson(currBlock.content) ?: TableBlockContent() } catch(e: Exception) { TableBlockContent() }
                    if (content.rows.isNotEmpty()) {
                        val numCols = if (content.headers.isNotEmpty()) content.headers.size else content.rows[0].size
                        val colW = colWidth / numCols.toFloat()
                        
                        var cellY = y
                        if (content.title.isNotBlank()) {
                            canvas.drawText(content.title, x, cellY + 15f, tableHeadPaint)
                            cellY += 32f // Aumentado de 25f para dar más espacio bajo el título
                        }

                        content.rows.forEachIndexed { rowIndex, row ->
                            val isHeader = rowIndex == 0 && content.headers.isNotEmpty()
                            if (isHeader) canvas.drawRect(x, cellY, x + colWidth, cellY + 22f, tableBgPaint)
                            canvas.drawRect(x, cellY, x + colWidth, cellY + 22f, borderPaint)
                            
                            row.forEachIndexed { colIndex, cellText ->
                                if (colIndex < numCols) {
                                    val cellX = x + colIndex * colW
                                    if (colIndex > 0) canvas.drawLine(cellX, cellY, cellX, cellY + 22f, borderPaint)
                                    val tPaint = if (isHeader) tableHeadPaint else tableCellPaint
                                    canvas.drawText(cellText.trim(), cellX + 6f, cellY + 15f, tPaint)
                                }
                            }
                            cellY += 22f
                        }
                        y = cellY + 10f
                    }
                }
                BlockType.CHECKLIST -> {
                    val content = try { checklistAdapter.fromJson(currBlock.content) ?: ChecklistBlockContent() } catch(e: Exception) { ChecklistBlockContent() }
                    var cellY = y
                    if (content.title.isNotBlank()) {
                        canvas.drawText(content.title, x, cellY + 15f, tableHeadPaint)
                        cellY += 32f
                    }
                    
                    val boxPaint = Paint().apply {
                        color = Color.BLACK
                        style = Paint.Style.STROKE
                        strokeWidth = 0.5f // Thin professional border
                        isAntiAlias = true
                    }
                    
                    content.items.forEach { item ->
                        val boxSize = 8.5f // Proportional to text
                        val boxX = x + 2f
                        val boxY = cellY + 4f
                        
                        // Draw thin black square box
                        canvas.drawRect(boxX, boxY, boxX + boxSize, boxY + boxSize, boxPaint)
                        
                        if (item.checked) {
                            // Clean black centered X
                            canvas.drawText("X", boxX + boxSize/2f, boxY + boxSize - 1.5f, Paint().apply {
                                color = Color.BLACK
                                textAlign = Paint.Align.CENTER
                                textSize = 8f
                                isFakeBoldText = true
                                isAntiAlias = true
                            })
                        }
                        
                        canvas.drawText(item.text, x + 16f, cellY + 13f, checklistTextPaint.apply {
                            color = Color.rgb(55, 65, 81)
                        })
                        cellY += 18f
                    }
                    y = cellY + 10f
                }
                BlockType.CHECKLIST_TABLE -> {
                    val content = try { checklistTableAdapter.fromJson(currBlock.content) ?: ChecklistTableBlockContent() } catch(e: Exception) { ChecklistTableBlockContent() }
                    if (content.rows.isNotEmpty()) {
                        val statusCols = content.headers
                        val numStatus = statusCols.size
                        val textColW = colWidth * 0.65f
                        val statusColW = (colWidth * 0.35f) / numStatus.coerceAtLeast(1)
                        
                        var cellY = y
                        if (content.title.isNotBlank()) {
                            canvas.drawText(content.title, x, cellY + 15f, tableHeadPaint)
                            cellY += 32f // Aumentado de 25f
                        }

                        // Header
                        canvas.drawRect(x, cellY, x + colWidth, cellY + 22f, tableBgPaint)
                        canvas.drawRect(x, cellY, x + colWidth, cellY + 22f, borderPaint)
                        canvas.drawText("Comprobaciones", x + 6f, cellY + 15f, tableHeadPaint.apply { textAlign = Paint.Align.LEFT })
                        
                        statusCols.forEachIndexed { idx, colName ->
                            val statusX = x + textColW + idx * statusColW
                            canvas.drawLine(statusX, cellY, statusX, cellY + 22f, borderPaint)
                            canvas.drawText(colName, statusX + statusColW/2, cellY + 15f, tableHeadPaint.apply { textAlign = Paint.Align.CENTER })
                        }
                        cellY += 22f
                        
                        // Rows
                        content.rows.forEach { row ->
                            canvas.drawRect(x, cellY, x + colWidth, cellY + 22f, borderPaint)
                            canvas.drawText(row.text, x + 6f, cellY + 15f, tableCellPaint.apply { textAlign = Paint.Align.LEFT })
                            
                            statusCols.forEachIndexed { idx, _ ->
                                val statusX = x + textColW + idx * statusColW
                                canvas.drawLine(statusX, cellY, statusX, cellY + 22f, borderPaint)
                                
                                val centerX = statusX + statusColW/2
                                val centerY = cellY + 11f
                                
                                if (row.selectedIndex == idx) {
                                    canvas.drawText("X", centerX, centerY + 4f, tableHeadPaint.apply { textAlign = Paint.Align.CENTER })
                                }
                                canvas.drawRect(centerX - 6f, centerY - 6f, centerX + 6f, centerY + 6f, borderPaint)
                            }
                            cellY += 22f
                        }
                        y = cellY + 10f
                    }
                }
            }
            return y
        }

        val sortedBlocks = when (exportMode) {
            PdfExportMode.COMMON_ONLY -> project.blocks.filter { it.visitId == null || it.visitId == 0L }.sortedBy { it.sequence }
            PdfExportMode.SINGLE_VISIT -> {
                val list = mutableListOf<ContentBlockEntity>()
                list.addAll(project.blocks.filter { it.visitId == null || it.visitId == 0L }.sortedBy { it.sequence })
                project.visits.find { it.id == singleVisitId }?.let { visit ->
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    list.add(ContentBlockEntity(id = -999, projectId = project.project.id, type = BlockType.TITLE, content = "VISITA: ${visit.title} (${sdf.format(Date(visit.date))})", sequence = -1))
                    if (visit.notes.isNotBlank()) list.add(ContentBlockEntity(id = -200L - visit.id, projectId = project.project.id, type = BlockType.TEXT, content = "Notas de reunión o incidencias:\n" + visit.notes, sequence = -1))
                    list.addAll(project.blocks.filter { it.visitId == singleVisitId }.sortedBy { it.sequence })
                }
                list
            }
            PdfExportMode.FULL_REPORT -> {
                val list = mutableListOf<ContentBlockEntity>()
                list.addAll(project.blocks.filter { it.visitId == null || it.visitId == 0L }.sortedBy { it.sequence })
                project.visits.sortedBy { it.date }.forEach { v ->
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    list.add(ContentBlockEntity(id = -100L - v.id, projectId = project.project.id, type = BlockType.TITLE, content = "VISITA: ${v.title.uppercase(Locale.getDefault())} (${sdf.format(Date(v.date))})", sequence = -1))
                    if (v.notes.isNotBlank()) list.add(ContentBlockEntity(id = -200L - v.id, projectId = project.project.id, type = BlockType.TEXT, content = "Notas de reunión o incidencias:\n" + v.notes, sequence = -1))
                    list.addAll(project.blocks.filter { it.visitId == v.id }.sortedBy { it.sequence })
                }
                list
            }
        }

        var dryPageCount = 1
        var dryY = contentStartY
        if (project.project.showHeaderLabel) dryY += 24f
        if (project.project.showHeaderTitle) dryY += 20f
        if (project.project.showHeaderDate) dryY += 15f
        dryY += 35f
        var dryIndex = 0
        while (dryIndex < sortedBlocks.size) {
            val block = sortedBlocks[dryIndex]
            if (block.isHalfWidth) {
                val nextBlock = sortedBlocks.getOrNull(dryIndex + 1)
                if (nextBlock != null && nextBlock.isHalfWidth) {
                    val colWidth = usableWidth / 2f - 6f
                    val maxH = maxOf(getRequiredHeight(block, colWidth), getRequiredHeight(nextBlock, colWidth))
                    if (dryY + maxH > contentEndY) { dryPageCount++; dryY = contentStartY }
                    dryY += maxH; dryIndex += 2
                } else {
                    val h = getRequiredHeight(block, usableWidth / 2f - 6f)
                    if (dryY + h > contentEndY) { dryPageCount++; dryY = contentStartY }
                    dryY += h; dryIndex += 1
                }
            } else {
                val h = getRequiredHeight(block, usableWidth)
                if (dryY + h > contentEndY) { dryPageCount++; dryY = contentStartY }
                dryY += h; dryIndex += 1
            }
        }
        val totalPages = dryPageCount

        if (showHeaderBox) drawCompanyHeader(canvas, pageNumber, totalPages, project.project)
        var currentY = contentStartY
        if (project.project.showHeaderLabel) { canvas.drawText(project.project.reportLabel.ifBlank { "REPORTE DE PROYECTO" }.uppercase(Locale.getDefault()), marginX, currentY, labelPaint); currentY += 24f }
        if (project.project.showHeaderTitle) { canvas.drawText(project.project.name.uppercase(Locale.getDefault()), marginX, currentY, titlePaint); currentY += 20f }
        if (project.project.showHeaderDate) { val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()); canvas.drawText("Fecha de creación: " + sdf.format(Date(project.project.createdAt)), marginX, currentY, subtitlePaint); currentY += 15f }
        canvas.drawLine(marginX, currentY, pageWidth - marginX, currentY, borderPaint); currentY += 35f

        fun startNewPage() {
            pdfDocument.finishPage(currentPage)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            currentPage = pdfDocument.startPage(pageInfo)
            canvas = currentPage.canvas
            if (showHeaderBox) drawCompanyHeader(canvas, pageNumber, totalPages, project.project)
            currentY = contentStartY
        }

        var bIndex = 0
        while (bIndex < sortedBlocks.size) {
            val block = sortedBlocks[bIndex]
            if (block.isHalfWidth) {
                val nextBlock = sortedBlocks.getOrNull(bIndex + 1)
                if (nextBlock != null && nextBlock.isHalfWidth) {
                    val colWidth = usableWidth / 2f - 6f
                    val maxH = maxOf(getRequiredHeight(block, colWidth), getRequiredHeight(nextBlock, colWidth))
                    if (currentY + maxH > contentEndY) startNewPage()
                    val finishY1 = drawBlock(block, marginX, colWidth, currentY)
                    val finishY2 = drawBlock(nextBlock, marginX + colWidth + 12f, colWidth, currentY)
                    currentY = maxOf(finishY1, finishY2); bIndex += 2
                } else {
                    val colWidth = usableWidth / 2f - 6f
                    val h = getRequiredHeight(block, colWidth)
                    if (currentY + h > contentEndY) startNewPage()
                    currentY = drawBlock(block, marginX, colWidth, currentY); bIndex += 1
                }
            } else {
                val h = getRequiredHeight(block, usableWidth)
                if (currentY + h > contentEndY) startNewPage()
                currentY = drawBlock(block, marginX, usableWidth, currentY); bIndex += 1
            }
        }
        pdfDocument.finishPage(currentPage)
        FileOutputStream(pdfFile).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return pdfFile.absolutePath
    }
}
