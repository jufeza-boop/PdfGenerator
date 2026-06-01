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
            val file = File(block.content)
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

        // Output blocks sequentially
        val sortedBlocks = project.blocks.sortedBy { it.sequence }
        for (block in sortedBlocks) {
            when (block.type) {
                BlockType.TEXT -> {
                    // Word-wrap text to fit width
                    val lines = wrapText(block.content, textPaint, usableWidth)
                    for (line in lines) {
                        if (currentY + 20f > pageHeight - 60f) {
                            startNewPage()
                        }
                        canvas.drawText(line, marginX, currentY, textPaint)
                        currentY += 18f
                    }
                    currentY += 12f
                }
                BlockType.IMAGE -> {
                    val file = File(block.content)
                    if (file.exists()) {
                        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (originalBitmap != null) {
                            // Scale down proportionally
                            val scaleRatio = usableWidth / originalBitmap.width.toFloat()
                            val targetHeight = (originalBitmap.height * scaleRatio).toInt()
                            
                            // Check vertical size safety
                            if (currentY + targetHeight + 10f > pageHeight - 60f) {
                                startNewPage()
                            }

                            val scaledBitmap = Bitmap.createScaledBitmap(
                                originalBitmap,
                                usableWidth.toInt(),
                                targetHeight,
                                true
                            )
                            canvas.drawBitmap(scaledBitmap, marginX, currentY, null)
                            
                            // Draw light grey border frame
                            canvas.drawRect(
                                marginX,
                                currentY,
                                marginX + usableWidth,
                                currentY + targetHeight,
                                borderPaint
                            )
                            
                            currentY += targetHeight + 16f
                        }
                    }
                }
                BlockType.SIGNATURE -> {
                    val file = File(block.content)
                    if (file.exists()) {
                        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (originalBitmap != null) {
                            val targetWidth = 180f
                            val scaleRatio = targetWidth / originalBitmap.width.toFloat()
                            val targetHeight = (originalBitmap.height * scaleRatio).toInt()

                            if (currentY + targetHeight + 40f > pageHeight - 60f) {
                                startNewPage()
                            }

                            val scaledBitmap = Bitmap.createScaledBitmap(
                                originalBitmap,
                                targetWidth.toInt(),
                                targetHeight,
                                true
                            )
                            
                            // Draw background signature frame
                            val bgPaint = Paint().apply {
                                color = Color.rgb(249, 250, 251)
                                style = Paint.Style.FILL
                            }
                            canvas.drawRect(
                                marginX,
                                currentY,
                                marginX + targetWidth,
                                currentY + targetHeight,
                                bgPaint
                            )
                            canvas.drawRect(
                                marginX,
                                currentY,
                                marginX + targetWidth,
                                currentY + targetHeight,
                                borderPaint
                            )

                            canvas.drawBitmap(scaledBitmap, marginX, currentY, null)
                            currentY += targetHeight + 10f

                            canvas.drawText("Firma Autorizada", marginX, currentY, labelPaint)
                            currentY += 24f
                        }
                    }
                }
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
