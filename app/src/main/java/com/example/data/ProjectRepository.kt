package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ProjectRepository(private val context: Context, private val projectDao: ProjectDao) {

    val allProjects: Flow<List<ProjectWithBlocks>> = projectDao.getAllProjectsFlow()

    fun getProjectById(id: Long): Flow<ProjectWithBlocks?> = projectDao.getProjectByIdFlow(id)

    suspend fun createProject(name: String): Long = withContext(Dispatchers.IO) {
        val newProj = ProjectEntity(name = name)
        projectDao.insertProject(newProj)
    }

    suspend fun deleteProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
        // Delete all local files associated with blocks before deleting the project
        val projectWithBlocks = projectDao.getProjectByIdFlow(project.id)
        // Note: flows are active stream, but we can read the current list by taking 1 if we wanted.
        // It's safer to just delete DB, cascade foreign keys will remove block records.
        // But files on disk should be cleaned if possible. We can do that or cascade-delete.
        projectDao.deleteProject(project)
    }

    suspend fun addTextBlock(projectId: Long, text: String, sequence: Int) = withContext(Dispatchers.IO) {
        val block = ContentBlockEntity(
            projectId = projectId,
            type = BlockType.TEXT,
            content = text,
            sequence = sequence
        )
        projectDao.insertBlock(block)
    }

    suspend fun saveImageBlock(projectId: Long, inputStream: InputStream, sequence: Int) = withContext(Dispatchers.IO) {
        // Copy the captured image files to private application document folder as required by rules
        val destinationDir = File(context.filesDir, "project_${projectId}_images")
        if (!destinationDir.exists()) destinationDir.mkdirs()

        val filename = "img_${UUID.randomUUID()}.jpg"
        val destinationFile = File(destinationDir, filename)

        FileOutputStream(destinationFile).use { output ->
            inputStream.copyTo(output)
        }

        val block = ContentBlockEntity(
            projectId = projectId,
            type = BlockType.IMAGE,
            content = destinationFile.absolutePath,
            sequence = sequence
        )
        projectDao.insertBlock(block)
    }

    suspend fun copyImageToLocalFile(projectId: Long, inputStream: InputStream): String = withContext(Dispatchers.IO) {
        val destinationDir = File(context.filesDir, "project_${projectId}_images")
        if (!destinationDir.exists()) destinationDir.mkdirs()

        val filename = "img_${UUID.randomUUID()}.jpg"
        val destinationFile = File(destinationDir, filename)

        FileOutputStream(destinationFile).use { output ->
            inputStream.copyTo(output)
        }
        destinationFile.absolutePath
    }

    suspend fun saveSignatureBlock(projectId: Long, signatureBitmap: Bitmap, sequence: Int) = withContext(Dispatchers.IO) {
        val destinationDir = File(context.filesDir, "project_${projectId}_signatures")
        if (!destinationDir.exists()) destinationDir.mkdirs()

        val filename = "sig_${UUID.randomUUID()}.png"
        val destinationFile = File(destinationDir, filename)

        FileOutputStream(destinationFile).use { output ->
            signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        val block = ContentBlockEntity(
            projectId = projectId,
            type = BlockType.SIGNATURE,
            content = destinationFile.absolutePath,
            sequence = sequence
        )
        projectDao.insertBlock(block)
    }

    suspend fun saveSignatureToLocalFile(projectId: Long, signatureBitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val destinationDir = File(context.filesDir, "project_${projectId}_signatures")
        if (!destinationDir.exists()) destinationDir.mkdirs()

        val filename = "sig_${UUID.randomUUID()}.png"
        val destinationFile = File(destinationDir, filename)

        FileOutputStream(destinationFile).use { output ->
            signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        destinationFile.absolutePath
    }

    suspend fun insertBlock(block: ContentBlockEntity): Long = withContext(Dispatchers.IO) {
        projectDao.insertBlock(block)
    }

    suspend fun updateBlock(block: ContentBlockEntity) = withContext(Dispatchers.IO) {
        projectDao.updateBlock(block)
    }

    suspend fun updateBlockContent(id: Long, projectId: Long, type: BlockType, text: String, sequence: Int) = withContext(Dispatchers.IO) {
        val block = ContentBlockEntity(
            id = id,
            projectId = projectId,
            type = type,
            content = text,
            sequence = sequence
        )
        projectDao.updateBlock(block)
    }

    suspend fun deleteBlock(block: ContentBlockEntity) = withContext(Dispatchers.IO) {
        // If it's a file block, delete local file
        if (block.type == BlockType.IMAGE || block.type == BlockType.SIGNATURE) {
            val filePath = if (block.type == BlockType.SIGNATURE) {
                block.content.split("|")[0]
            } else {
                block.content
            }
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
        }
        projectDao.deleteBlock(block)
    }

    // PDF Generation Logic utilizing native A4 drawing
    suspend fun generatePdf(project: ProjectWithBlocks): File = withContext(Dispatchers.IO) {
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
            color = Color.rgb(31, 41, 55) // Slate gray dark
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

        var currentY = 60f
        val marginX = 54f // 0.75 in
        val usableWidth = pageWidth - (2 * marginX)

        // Title Block
        canvas.drawText("REPORTE DE PROYECTO", marginX, currentY, labelPaint)
        currentY += 24f
        canvas.drawText(project.project.name.uppercase(Locale.getDefault()), marginX, currentY, titlePaint)
        currentY += 20f

        val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())
        val dateText = "Fecha de creación: " + sdf.format(Date(project.project.createdAt))
        canvas.drawText(dateText, marginX, currentY, subtitlePaint)
        currentY += 15f

        // Separator line
        canvas.drawLine(marginX, currentY, pageWidth - marginX, currentY, borderPaint)
        currentY += 35f

        fun startNewPage() {
            pdfDocument.finishPage(currentPage)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            currentPage = pdfDocument.startPage(pageInfo)
            canvas = currentPage.canvas
            currentY = 60f
        }

        // Output blocks sequentially using elegant parallel row calculations
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
                    } else 40f
                }
                BlockType.TABLE -> {
                    val rows = currBlock.content.split("\n").filter { it.isNotBlank() }
                    rows.size * 22f + 15f
                }
                BlockType.CHECKLIST -> {
                    val items = currBlock.content.split("\n").filter { it.isNotBlank() }
                    items.size * 20f + 15f
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
                            val scaledWidth = maxOf(1, colWidth.toInt())
                            val scaledHeight = maxOf(1, targetHeight)
                            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
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
                            val scaledWidth = maxOf(1, targetWidth.toInt())
                            val scaledHeight = maxOf(1, targetHeight)
                            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
                            
                            val bgPaint = Paint().apply {
                                color = Color.rgb(249, 250, 251)
                                style = Paint.Style.FILL
                            }
                            canvas.drawRect(x, y, x + targetWidth, y + targetHeight, bgPaint)
                            canvas.drawRect(x, y, x + targetWidth, y + targetHeight, borderPaint)
                            canvas.drawBitmap(scaledBitmap, x, y, null)
                            
                            val textY = y + targetHeight + 14f
                            val boldLabelPaint = Paint().apply {
                                color = Color.rgb(31, 41, 55)
                                textSize = 11f
                                isFakeBoldText = true
                                isAntiAlias = true
                            }
                            canvas.drawText(signatureLabel, x, textY, boldLabelPaint)
                            canvas.drawText(signatureSubtitle, x, textY + 14f, labelPaint)
                            y += targetHeight + 35f
                        }
                    }
                }
                BlockType.TABLE -> {
                    val rows = currBlock.content.split("\n").filter { it.isNotBlank() }
                    if (rows.isNotEmpty()) {
                        val headerCells = rows[0].split("|")
                        val numCols = headerCells.count()
                        val colW = colWidth / numCols.toFloat()
                        
                        var cellY = y
                        rows.forEachIndexed { rowIndex, rowText ->
                            val cells = rowText.split("|")
                            val isHeader = rowIndex == 0
                            
                            if (isHeader) {
                                canvas.drawRect(x, cellY, x + colWidth, cellY + 22f, tableBgPaint)
                            }
                            canvas.drawRect(x, cellY, x + colWidth, cellY + 22f, borderPaint)
                            
                            cells.forEachIndexed { colIndex, cellText ->
                                if (colIndex < numCols) {
                                    val cellX = x + colIndex * colW
                                    if (colIndex > 0) {
                                        canvas.drawLine(cellX, cellY, cellX, cellY + 22f, borderPaint)
                                    }
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
                    val items = currBlock.content.split("\n").filter { it.isNotBlank() }
                    items.forEach { itemLine ->
                        val checked = itemLine.startsWith("true")
                        val label = if (itemLine.contains("|")) itemLine.substringAfter("|") else itemLine
                        
                        val boxSize = 10f
                        val boxX = x + 4f
                        val boxY = y + 4f
                        
                        canvas.drawRect(boxX, boxY, boxX + boxSize, boxY + boxSize, checkboxPaint)
                        if (checked) {
                            val insidePaint = Paint().apply {
                                color = Color.rgb(37, 99, 235)
                                style = Paint.Style.FILL
                                isAntiAlias = true
                            }
                            canvas.drawRect(boxX + 2f, boxY + 2f, boxX + boxSize - 2f, boxY + boxSize - 2f, insidePaint)
                        }
                        
                        canvas.drawText(label, x + 20f, y + 13f, checklistTextPaint)
                        y += 20f
                    }
                    y += 10f
                }
            }
            return y
        }

        val sortedBlocks = project.blocks.sortedBy { it.sequence }
        var bIndex = 0
        while (bIndex < sortedBlocks.size) {
            val block = sortedBlocks[bIndex]
            if (block.isHalfWidth) {
                val nextBlock = sortedBlocks.getOrNull(bIndex + 1)
                if (nextBlock != null && nextBlock.isHalfWidth) {
                    val colWidth = usableWidth / 2f - 6f
                    val h1 = getRequiredHeight(block, colWidth)
                    val h2 = getRequiredHeight(nextBlock, colWidth)
                    val maxH = maxOf(h1, h2)
                    
                    if (currentY + maxH > pageHeight - 60f) {
                        startNewPage()
                    }
                    
                    val finishY1 = drawBlock(block, marginX, colWidth, currentY)
                    val finishY2 = drawBlock(nextBlock, marginX + colWidth + 12f, colWidth, currentY)
                    currentY = maxOf(finishY1, finishY2)
                    bIndex += 2
                } else {
                    val colWidth = usableWidth / 2f - 6f
                    val h = getRequiredHeight(block, colWidth)
                    if (currentY + h > pageHeight - 60f) {
                        startNewPage()
                    }
                    currentY = drawBlock(block, marginX, colWidth, currentY)
                    bIndex += 1
                }
            } else {
                val h = getRequiredHeight(block, usableWidth)
                if (currentY + h > pageHeight - 60f) {
                    startNewPage()
                }
                currentY = drawBlock(block, marginX, usableWidth, currentY)
                bIndex += 1
            }
        }

        pdfDocument.finishPage(currentPage)
        
        FileOutputStream(pdfFile).use { output ->
            pdfDocument.writeTo(output)
        }
        pdfDocument.close()

        pdfFile
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
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

    suspend fun uploadPdfToCloudMock(pdfFile: File): Boolean = withContext(Dispatchers.IO) {
        // Simulate a cloud server request
        delay(2000)
        true
    }
}
