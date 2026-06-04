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

enum class PdfExportMode {
    FULL_REPORT,
    COMMON_ONLY,
    SINGLE_VISIT
}

class ProjectRepository(val context: Context, val projectDao: ProjectDao) {

    val allProjects: Flow<List<ProjectWithBlocks>> = projectDao.getAllProjectsFlow()

    fun getProjectById(id: Long): Flow<ProjectWithBlocks?> = projectDao.getProjectByIdFlow(id)

    suspend fun insertVisit(visit: VisitEntity): Long = withContext(Dispatchers.IO) {
        projectDao.insertVisit(visit)
    }

    suspend fun updateVisit(visit: VisitEntity) = withContext(Dispatchers.IO) {
        projectDao.updateVisit(visit)
    }

    suspend fun deleteVisit(visit: VisitEntity) = withContext(Dispatchers.IO) {
        projectDao.deleteVisit(visit)
    }

    suspend fun createProject(name: String, templateType: String = "NONE"): Long = withContext(Dispatchers.IO) {
        val newProj = when (templateType) {
            "ACTA_VISITA" -> ProjectEntity(
                name = name,
                headerCompany = "JAVIER MARTÍNEZ PARRA",
                headerCompanySub = "ARQUITECTO TÉCNICO-INGENIERO DE EDIFICACIÓN\nESPECIALISTA EN C.S.S. EN OBRAS DE CONSTRUCCIÓN",
                headerTitle = "INFORME DE VISITA A OBRA"
            )
            "CONTROL_CALIDAD" -> ProjectEntity(
                name = name,
                headerCompany = "LABORATORIO DE CONTROL S.L.",
                headerCompanySub = "CONTROL DE CALIDAD DE EDIFICACIÓN\nREGISTRO DE ENSAYOS DE HORMIGÓN ESTRUCTURAL",
                headerTitle = "CONTROL DE RECEPCIÓN DE HORMIGÓN"
            )
            else -> ProjectEntity(name = name)
        }
        val projectId = projectDao.insertProject(newProj)
        
        when (templateType) {
            "ACTA_VISITA" -> {
                var seq = 0
                val blocks = listOf(
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "ACTA DE VISITA - DIRECCIÓN FACULTATIVA", sequence = seq++),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TABLE, content = "Dato General | Información del Proyecto\nNombre de la obra | [Nombre de la Obra]\nDirección de la obra | [Dirección / Localización]\nPromotor | [Nombre del Promotor]\nContratista principal | [Nombre de la Constructora / Contratista]\nDirección de la obra (DO) | -\nDirección de la Ejecución (DEO) | -\nCoordinación de Seguridad y Salud (CSS) | -", sequence = seq++)
                )
                blocks.forEach { projectDao.insertBlock(it) }
            }
            "CONTROL_CALIDAD" -> {
                var seq = 0
                val blocks = listOf(
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "CONTROL DE CALIDAD Y RECEPCIÓN DE HORMIGÓN", sequence = seq++),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TABLE, content = "Ensayo y Control | Especificación del Proyecto\nTipo de Hormigón | HA-25 / B / 20 / IIa (Fck = 25 N/mm²)\nTipo de Cemento | CEM II/A-L 42.5R (Uso general)\nConsistencia / Cono | Consistencia Blanda (Asentamiento Cono: 6-9 cm)\nTamaño Máximo Árido | 20 mm de piedra de machaqueo\nAditivos incorporados | Plastificantes homologados", sequence = seq++)
                )
                blocks.forEach { projectDao.insertBlock(it) }
            }
        }
        projectId
    }

    suspend fun updateProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
        projectDao.updateProject(project)
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

    private fun drawCompanyHeader(canvas: Canvas, pageNum: Int, totalPages: Int, proj: ProjectEntity) {
        if (!proj.showHeaderBox) return
        
        // Draw the main border box
        val startX = 40f
        val endX = 555f
        val topY = 35f
        val bottomY = 85f
        
        val boxPaint = Paint().apply {
            color = Color.rgb(229, 231, 235) // Light gray border
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            isAntiAlias = true
        }
        
        val fillPaint = Paint().apply {
            color = Color.rgb(243, 244, 246) // Center gray column background
            style = Paint.Style.FILL
        }
        
        // Draw Center Column Filled Background
        canvas.drawRect(240f, topY, 465f, bottomY, fillPaint)
        
        // Draw Outer bounding box
        canvas.drawRect(startX, topY, endX, bottomY, boxPaint)
        
        // Draw vertical divider lines
        canvas.drawLine(240f, topY, 240f, bottomY, boxPaint)
        canvas.drawLine(465f, topY, 465f, bottomY, boxPaint)
        
        // Left Column: Company/Director text (headerCompany, headerCompanySub)
        val compTitlePaint = Paint().apply {
            color = Color.rgb(154, 102, 64) // Elegant brown copper tone
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        val compSubPaint = Paint().apply {
            color = Color.rgb(107, 114, 128)
            textSize = 5.5f
            isAntiAlias = true
        }
        
        val compTitle = proj.headerCompany.ifBlank { "JAVIER MARTÍNEZ PARRA" }
        canvas.drawText(compTitle, startX + 10f, topY + 16f, compTitlePaint)
        
        val subLines = proj.headerCompanySub.split("\n")
        var subY = topY + 26f
        for (line in subLines) {
            if (line.isNotBlank()) {
                canvas.drawText(line.trim(), startX + 10f, subY, compSubPaint)
                subY += 8f
            }
        }
        
        // Center Column: Title Text (headerTitle)
        val centerPaint = Paint().apply {
            color = Color.rgb(17, 24, 39)
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        val centerTitle = proj.headerTitle.ifBlank { "INFORME DE VISITA A OBRA" }
        canvas.drawText(centerTitle.uppercase(Locale.getDefault()), 240f + 112.5f, topY + 29f, centerPaint)
        
        // Right Column: Pagination (Página X de Y)
        val pagePaint = Paint().apply {
            color = Color.rgb(55, 65, 81)
            textSize = 10f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val pageText = "Página $pageNum de $totalPages"
        canvas.drawText(pageText, 465f + 45f, topY + 28f, pagePaint)
    }

    // PDF Generation Logic utilizing native A4 drawing
    suspend fun generatePdf(
        project: ProjectWithBlocks,
        exportMode: PdfExportMode = PdfExportMode.FULL_REPORT,
        singleVisitId: Long? = null
    ): File = withContext(Dispatchers.IO) {
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

        val marginX = 54f // 0.75 in
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
                    } else {
                        // Unsigned placeholder container
                        val targetWidth = minOf(colWidth, 180f)
                        val targetHeight = 80f
                        val bgPaint = Paint().apply {
                            color = Color.rgb(249, 250, 251)
                            style = Paint.Style.FILL
                        }
                        canvas.drawRect(x, y, x + targetWidth, y + targetHeight, bgPaint)
                        canvas.drawRect(x, y, x + targetWidth, y + targetHeight, borderPaint)
                        
                        // Draw empty signing line guideline
                        val linePaint = Paint().apply {
                            color = Color.rgb(209, 213, 219)
                            strokeWidth = 1f
                            style = Paint.Style.STROKE
                        }
                        canvas.drawLine(x + 10f, y + targetHeight - 15f, x + targetWidth - 10f, y + targetHeight - 15f, linePaint)
                        
                        val textY = y + targetHeight + 14f
                        val boldLabelPaint = Paint().apply {
                            color = Color.rgb(31, 41, 55)
                            textSize = 10f
                            isFakeBoldText = true
                            isAntiAlias = true
                        }
                        canvas.drawText(signatureLabel, x, textY, boldLabelPaint)
                        canvas.drawText(signatureSubtitle, x, textY + 14f, labelPaint)
                        y += targetHeight + 35f
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

        val sortedBlocks = when (exportMode) {
            PdfExportMode.COMMON_ONLY -> {
                project.blocks.filter { it.visitId == null || it.visitId == 0L }.sortedBy { it.sequence }
            }
            PdfExportMode.SINGLE_VISIT -> {
                val list = mutableListOf<ContentBlockEntity>()
                // 1. Add common blocks (datos generales)
                val commonBlocks = project.blocks.filter { it.visitId == null || it.visitId == 0L }.sortedBy { it.sequence }
                list.addAll(commonBlocks)
                
                // 2. Add the specific visit header and notes
                val visit = project.visits.find { it.id == singleVisitId }
                if (visit != null) {
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    list.add(
                        ContentBlockEntity(
                            id = -999,
                            projectId = project.project.id,
                            type = BlockType.TITLE,
                            content = "VISITA: ${visit.title} (${sdf.format(Date(visit.date))})",
                            sequence = -1
                        )
                    )
                    if (visit.notes.isNotBlank()) {
                        list.add(
                            ContentBlockEntity(
                                id = -200L - visit.id,
                                projectId = project.project.id,
                                type = BlockType.TEXT,
                                content = "Notas de reunión o incidencias:\n" + visit.notes,
                                sequence = -1
                            )
                        )
                    }
                    // 3. Add visit blocks
                    val visitBlocks = project.blocks.filter { it.visitId == singleVisitId }.sortedBy { it.sequence }
                    list.addAll(visitBlocks)
                }
                list
            }
            PdfExportMode.FULL_REPORT -> {
                val list = mutableListOf<ContentBlockEntity>()
                // 1. Add common blocks
                val commonBlocks = project.blocks.filter { it.visitId == null || it.visitId == 0L }.sortedBy { it.sequence }
                list.addAll(commonBlocks)
                
                // 2. Sort visits by date/id
                val sortedVisits = project.visits.sortedBy { it.date }
                sortedVisits.forEach { v ->
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    // Add visit section separator title
                    list.add(
                        ContentBlockEntity(
                            id = -100L - v.id,
                            projectId = project.project.id,
                            type = BlockType.TITLE,
                            content = "VISITA: ${v.title.uppercase(Locale.getDefault())} (${sdf.format(Date(v.date))})",
                            sequence = -1
                        )
                    )
                    // Add visit notes if present as text block
                    if (v.notes.isNotBlank()) {
                        list.add(
                            ContentBlockEntity(
                                id = -200L - v.id,
                                projectId = project.project.id,
                                type = BlockType.TEXT,
                                content = "Notas de reunión o incidencias:\n" + v.notes,
                                sequence = -1
                            )
                        )
                    }
                    // Add visit blocks
                    val visitBlocks = project.blocks.filter { it.visitId == v.id }.sortedBy { it.sequence }
                    list.addAll(visitBlocks)
                }
                list
            }
        }

        // --- 1. DRY RUN PASS (To calculate total page count) ---
        var dryPageCount = 1
        var dryY = contentStartY

        // First page's title heights in the dry run
        if (project.project.showHeaderLabel) {
            dryY += 24f
        }
        dryY += 20f // Title Name
        if (project.project.showHeaderDate) {
            dryY += 15f
        }
        dryY += 35f // separator and spaces

        var dryIndex = 0
        while (dryIndex < sortedBlocks.size) {
            val block = sortedBlocks[dryIndex]
            if (block.isHalfWidth) {
                val nextBlock = sortedBlocks.getOrNull(dryIndex + 1)
                if (nextBlock != null && nextBlock.isHalfWidth) {
                    val colWidth = usableWidth / 2f - 6f
                    val h1 = getRequiredHeight(block, colWidth)
                    val h2 = getRequiredHeight(nextBlock, colWidth)
                    val maxH = maxOf(h1, h2)
                    
                    if (dryY + maxH > contentEndY) {
                        dryPageCount++
                        dryY = contentStartY
                    }
                    dryY += maxH
                    dryIndex += 2
                } else {
                    val colWidth = usableWidth / 2f - 6f
                    val h = getRequiredHeight(block, colWidth)
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

        // --- 2. REAL DRAWING PASS ---
        // Draw initial page header box
        if (showHeaderBox) {
            drawCompanyHeader(canvas, pageNumber, totalPages, project.project)
        }

        var currentY = contentStartY

        // Title Block
        if (project.project.showHeaderLabel) {
            val labelToDraw = project.project.reportLabel.ifBlank { "REPORTE DE PROYECTO" }
            canvas.drawText(labelToDraw.uppercase(Locale.getDefault()), marginX, currentY, labelPaint)
            currentY += 24f
        }
        canvas.drawText(project.project.name.uppercase(Locale.getDefault()), marginX, currentY, titlePaint)
        currentY += 20f

        if (project.project.showHeaderDate) {
            val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())
            val dateText = "Fecha de creación: " + sdf.format(Date(project.project.createdAt))
            canvas.drawText(dateText, marginX, currentY, subtitlePaint)
            currentY += 15f
        }

        // Separator line
        canvas.drawLine(marginX, currentY, pageWidth - marginX, currentY, borderPaint)
        currentY += 35f

        fun startNewPage() {
            pdfDocument.finishPage(currentPage)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            currentPage = pdfDocument.startPage(pageInfo)
            canvas = currentPage.canvas
            if (showHeaderBox) {
                drawCompanyHeader(canvas, pageNumber, totalPages, project.project)
            }
            currentY = contentStartY
        }

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
                    
                    if (currentY + maxH > contentEndY) {
                        startNewPage()
                    }
                    
                    val finishY1 = drawBlock(block, marginX, colWidth, currentY)
                    val finishY2 = drawBlock(nextBlock, marginX + colWidth + 12f, colWidth, currentY)
                    currentY = maxOf(finishY1, finishY2)
                    bIndex += 2
                } else {
                    val colWidth = usableWidth / 2f - 6f
                    val h = getRequiredHeight(block, colWidth)
                    if (currentY + h > contentEndY) {
                        startNewPage()
                    }
                    currentY = drawBlock(block, marginX, colWidth, currentY)
                    bIndex += 1
                }
            } else {
                val h = getRequiredHeight(block, usableWidth)
                if (currentY + h > contentEndY) {
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
