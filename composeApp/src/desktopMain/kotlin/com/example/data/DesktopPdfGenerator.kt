package com.example.data

import com.lowagie.text.*
import com.lowagie.text.pdf.*
import com.lowagie.text.pdf.draw.LineSeparator
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.awt.Color

/**
 * High-fidelity PDF Generator for Desktop using OpenPDF.
 * This implementation aims for pixel-perfect parity with the Android native drawing logic.
 */
class DesktopPdfGenerator : PdfGenerator {

    override suspend fun generatePdf(
        project: ProjectWithBlocks,
        exportMode: PdfExportMode,
        singleVisitId: Long?
    ): String {
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val pdfFile = File(tempDir, "project_report_${project.project.id}.pdf")
        
        // A4 margins matching Android's marginX = 54f
        val document = Document(PageSize.A4, 54f, 54f, 80f, 60f)
        val writer = PdfWriter.getInstance(document, FileOutputStream(pdfFile))
        
        // Setup Header and Pagination
        if (project.project.showHeaderBox) {
            writer.pageEvent = object : PdfPageEventHelper() {
                override fun onEndPage(writer: PdfWriter, document: Document) {
                    drawHeader(writer, document, project)
                }
            }
        }

        document.open()

        // 1. Category Label (e.g. "REPORTE DE PROYECTO")
        if (project.project.showHeaderLabel) {
            val label = project.project.reportLabel.ifBlank { "REPORTE DE PROYECTO" }
            val labelFont = Font(Font.HELVETICA, 9f, Font.NORMAL, Color(156, 163, 175))
            document.add(Paragraph(label.uppercase(), labelFont))
        }

        // 2. Project Title
        if (project.project.showHeaderTitle) {
            val titleFont = Font(Font.HELVETICA, 22f, Font.BOLD, Color(31, 41, 55))
            document.add(Paragraph(project.project.name.uppercase(), titleFont))
        }

        // 3. Metadata Date
        if (project.project.showHeaderDate) {
            val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())
            val dateText = "Fecha de creación: " + sdf.format(Date(project.project.createdAt))
            val subFont = Font(Font.HELVETICA, 11f, Font.NORMAL, Color(107, 114, 128))
            document.add(Paragraph(dateText, subFont))
        }

        document.add(Paragraph(" "))
        
        // Horizontal Separator Line
        val line = LineSeparator()
        line.lineColor = Color(229, 231, 235)
        document.add(line)
        document.add(Paragraph(" "))

        // 4. Render Dynamic Blocks
        val sortedBlocks = getSortedBlocks(project, exportMode, singleVisitId)
        for (block in sortedBlocks) {
            addBlockToDocument(document, block)
        }

        document.close()
        return pdfFile.absolutePath
    }

    private fun drawHeader(writer: PdfWriter, document: Document, project: ProjectWithBlocks) {
        val cb = writer.directContent
        val proj = project.project
        
        val table = PdfPTable(3)
        table.totalWidth = document.right() - document.left()
        table.setWidths(floatArrayOf(0.40f, 0.42f, 0.18f))
        
        // Left Column: Branding
        val leftCell = PdfPCell()
        leftCell.borderColor = Color(229, 231, 235)
        leftCell.setPadding(6f)
        
        val compFont = Font(Font.HELVETICA, 10f, Font.BOLD, Color(154, 102, 64))
        leftCell.addElement(Paragraph(proj.headerCompany.ifBlank { "Nombre de la empresa" }, compFont))
        
        val subFont = Font(Font.HELVETICA, 5.5f, Font.NORMAL, Color(107, 114, 128))
        val subLines = proj.headerCompanySub.split("\n")
        subLines.forEach { if(it.isNotBlank()) leftCell.addElement(Paragraph(it.trim(), subFont)) }
        table.addCell(leftCell)
        
        // Center Column: Document Title
        val centerCell = PdfPCell()
        centerCell.borderColor = Color(229, 231, 235)
        centerCell.backgroundColor = Color(243, 244, 246)
        centerCell.setPadding(6f)
        centerCell.verticalAlignment = Element.ALIGN_MIDDLE
        
        val titleFont = Font(Font.HELVETICA, 10f, Font.BOLD, Color(17, 24, 39))
        val p = Paragraph(proj.headerTitle.ifBlank { "INFORME DE VISITA A OBRA" }.uppercase(), titleFont)
        p.alignment = Element.ALIGN_CENTER
        centerCell.addElement(p)
        table.addCell(centerCell)
        
        // Right Column: Pagination
        val rightCell = PdfPCell()
        rightCell.borderColor = Color(229, 231, 235)
        rightCell.setPadding(6f)
        rightCell.verticalAlignment = Element.ALIGN_MIDDLE
        
        val pageFont = Font(Font.HELVETICA, 8.5f, Font.NORMAL, Color(75, 85, 99))
        val pageText = Paragraph("Pág. ${writer.pageNumber}", pageFont)
        pageText.alignment = Element.ALIGN_CENTER
        rightCell.addElement(pageText)
        table.addCell(rightCell)
        
        // Position at the very top
        table.writeSelectedRows(0, -1, document.left(), document.top() + 65f, cb)
    }

    private fun addBlockToDocument(document: Document, block: ContentBlockEntity) {
        when (block.type) {
            BlockType.TITLE -> {
                val font = Font(Font.HELVETICA, 16f, Font.BOLD, Color(31, 41, 55))
                document.add(Paragraph(block.content, font))
                document.add(Paragraph(" "))
            }
            BlockType.TEXT -> {
                val font = Font(Font.HELVETICA, 12f, Font.NORMAL, Color(55, 65, 81))
                document.add(Paragraph(block.content, font))
                document.add(Paragraph(" "))
            }
            BlockType.IMAGE -> {
                try {
                    val img = Image.getInstance(block.content)
                    val maxWidth = document.right() - document.left()
                    img.scaleToFit(if (block.isHalfWidth) maxWidth/2 - 10f else maxWidth, 400f)
                    img.alignment = Image.ALIGN_CENTER
                    document.add(img)
                    document.add(Paragraph(" "))
                } catch (e: Exception) {
                    document.add(Paragraph("[Error cargando imagen]", Font(Font.HELVETICA, 10f, Font.ITALIC, Color.RED)))
                }
            }
            BlockType.TABLE -> {
                val rows = block.content.split("\n").filter { it.isNotBlank() }
                if (rows.isNotEmpty()) {
                    val cols = rows[0].split("|").size
                    val pdfTable = PdfPTable(cols)
                    pdfTable.widthPercentage = 100f
                    
                    rows.forEachIndexed { index, rowText ->
                        val cells = rowText.split("|")
                        cells.forEach { cellText ->
                            val cell = PdfPCell(Phrase(cellText.trim(), if (index == 0) Font(Font.HELVETICA, 11f, Font.BOLD) else Font(Font.HELVETICA, 10f)))
                            if (index == 0) cell.backgroundColor = Color(243, 244, 246)
                            cell.borderColor = Color(229, 231, 235)
                            pdfTable.addCell(cell)
                        }
                    }
                    document.add(pdfTable)
                    document.add(Paragraph(" "))
                }
            }
            BlockType.SIGNATURE -> {
                val parts = block.content.split("|")
                val path = parts[0]
                val label = parts.getOrNull(1) ?: "Firma de Validación"
                val sub = parts.getOrNull(2) ?: "Firma Autorizada"
                
                try {
                    val img = Image.getInstance(path)
                    img.scaleToFit(150f, 80f)
                    document.add(img)
                } catch (e: Exception) {
                    // Placeholder for signature line if file missing
                    val line = LineSeparator()
                    line.offset = -5f
                    line.lineWidth = 1f
                    line.percentage = 30f
                    line.alignment = Element.ALIGN_LEFT
                    document.add(line)
                }
                
                document.add(Paragraph(label, Font(Font.HELVETICA, 11f, Font.BOLD, Color(31, 41, 55))))
                document.add(Paragraph(sub, Font(Font.HELVETICA, 9f, Font.NORMAL, Color(156, 163, 175))))
                document.add(Paragraph(" "))
            }
            BlockType.CHECKLIST -> {
                if (block.content.startsWith("TABLE|")) {
                    val lines = block.content.split("\n").filter { it.isNotBlank() }
                    if (lines.isNotEmpty()) {
                        val headerParts = lines[0].split("|")
                        val statusCols = headerParts.drop(1)
                        val numCols = 1 + statusCols.size
                        
                        val pdfTable = PdfPTable(numCols)
                        pdfTable.widthPercentage = 100f
                        // Set widths: 60% for text, rest shared
                        val widths = FloatArray(numCols)
                        widths[0] = 0.6f
                        val statusW = 0.4f / statusCols.size
                        for (i in 1 until numCols) widths[i] = statusW
                        pdfTable.setWidths(widths)
                        
                        // Header
                        val hCell = PdfPCell(Phrase("Comprobaciones", Font(Font.HELVETICA, 11f, Font.BOLD)))
                        hCell.backgroundColor = Color(243, 244, 246)
                        hCell.borderColor = Color(229, 231, 235)
                        pdfTable.addCell(hCell)
                        
                        statusCols.forEach { colName ->
                            val c = PdfPCell(Phrase(colName, Font(Font.HELVETICA, 10f, Font.BOLD)))
                            c.backgroundColor = Color(243, 244, 246)
                            c.borderColor = Color(229, 231, 235)
                            c.horizontalAlignment = Element.ALIGN_CENTER
                            pdfTable.addCell(c)
                        }
                        
                        // Rows
                        lines.drop(1).forEach { line ->
                            val parts = line.split("|")
                            val text = parts.getOrNull(0) ?: ""
                            val selectedIdx = parts.getOrNull(1)?.toIntOrNull() ?: -1
                            
                            val tCell = PdfPCell(Phrase(text, Font(Font.HELVETICA, 10f)))
                            tCell.borderColor = Color(229, 231, 235)
                            pdfTable.addCell(tCell)
                            
                            statusCols.forEachIndexed { idx, _ ->
                                val check = if (selectedIdx == idx) "X" else ""
                                val c = PdfPCell(Phrase(check, Font(Font.HELVETICA, 10f, Font.BOLD)))
                                c.borderColor = Color(229, 231, 235)
                                c.horizontalAlignment = Element.ALIGN_CENTER
                                pdfTable.addCell(c)
                            }
                        }
                        document.add(pdfTable)
                        document.add(Paragraph(" "))
                    }
                } else {
                    val items = block.content.split("\n").filter { it.isNotBlank() }
                    items.forEach { line ->
                        val checked = line.startsWith("true")
                        val text = if (line.contains("|")) line.substringAfter("|") else line
                        val prefix = if (checked) "✓ " else "☐ "
                        val font = if (checked) Font(Font.HELVETICA, 11f, Font.STRIKETHRU, Color.GRAY) else Font(Font.HELVETICA, 11f)
                        document.add(Paragraph(prefix + text, font))
                    }
                    document.add(Paragraph(" "))
                }
            }
            BlockType.FOOTER -> {
                document.add(Paragraph(block.content, Font(Font.HELVETICA, 9f, Font.ITALIC, Color(107, 114, 128))))
            }
        }
    }

    private fun getSortedBlocks(project: ProjectWithBlocks, mode: PdfExportMode, singleId: Long?): List<ContentBlockEntity> {
        val list = mutableListOf<ContentBlockEntity>()
        val common = project.blocks.filter { it.visitId == null || it.visitId == 0L }.sortedBy { it.sequence }
        
        when (mode) {
            PdfExportMode.COMMON_ONLY -> list.addAll(common)
            PdfExportMode.SINGLE_VISIT -> {
                list.addAll(common)
                project.visits.find { it.id == singleId }?.let { v ->
                    list.add(ContentBlockEntity(type = BlockType.TITLE, content = "VISITA: ${v.title}", sequence = -1, projectId = project.project.id))
                    list.addAll(project.blocks.filter { it.visitId == v.id }.sortedBy { it.sequence })
                }
            }
            PdfExportMode.FULL_REPORT -> {
                list.addAll(common)
                project.visits.sortedBy { it.date }.forEach { v ->
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    list.add(ContentBlockEntity(type = BlockType.TITLE, content = "VISITA: ${v.title} (${sdf.format(Date(v.date))})", sequence = -1, projectId = project.project.id))
                    list.addAll(project.blocks.filter { it.visitId == v.id }.sortedBy { it.sequence })
                }
            }
        }
        return list
    }
}
