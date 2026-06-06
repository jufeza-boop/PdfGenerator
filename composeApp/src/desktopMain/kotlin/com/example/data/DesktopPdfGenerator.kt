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

class DesktopPdfGenerator : PdfGenerator {

    override suspend fun generatePdf(
        project: ProjectWithBlocks,
        exportMode: PdfExportMode,
        singleVisitId: Long?
    ): String {
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val pdfFile = File(tempDir, "project_report_${project.project.id}.pdf")
        
        val document = Document(PageSize.A4, 54f, 54f, 80f, 60f)
        val writer = PdfWriter.getInstance(document, FileOutputStream(pdfFile))
        
        if (project.project.showHeaderBox) {
            writer.pageEvent = object : PdfPageEventHelper() {
                override fun onEndPage(writer: PdfWriter, document: Document) {
                    drawHeader(writer, document, project)
                }
            }
        }

        document.open()

        if (project.project.showHeaderLabel) {
            val label = project.project.reportLabel.ifBlank { "REPORTE DE PROYECTO" }
            val labelFont = Font(Font.HELVETICA, 9f, Font.NORMAL, Color.LIGHT_GRAY)
            document.add(Paragraph(label.uppercase(), labelFont))
        }

        val titleFont = Font(Font.HELVETICA, 22f, Font.BOLD, Color.DARK_GRAY)
        document.add(Paragraph(project.project.name.uppercase(), titleFont))

        if (project.project.showHeaderDate) {
            val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())
            val dateText = "Fecha de creación: " + sdf.format(Date(project.project.createdAt))
            val subFont = Font(Font.HELVETICA, 11f, Font.NORMAL, Color.GRAY)
            document.add(Paragraph(dateText, subFont))
        }

        document.add(Paragraph(" "))
        
        val line = LineSeparator()
        line.lineColor = Color.LIGHT_GRAY
        document.add(line)
        document.add(Paragraph(" "))

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
        
        val leftCell = PdfPCell()
        leftCell.borderColor = Color.LIGHT_GRAY
        leftCell.setPadding(6f)
        
        val compFont = Font(Font.HELVETICA, 10f, Font.BOLD, Color(154, 102, 64))
        leftCell.addElement(Paragraph(proj.headerCompany.ifBlank { "JAVIER MARTÍNEZ PARRA" }, compFont))
        
        val subFont = Font(Font.HELVETICA, 5.5f, Font.NORMAL, Color.GRAY)
        leftCell.addElement(Paragraph(proj.headerCompanySub, subFont))
        table.addCell(leftCell)
        
        val centerCell = PdfPCell()
        centerCell.borderColor = Color.LIGHT_GRAY
        centerCell.backgroundColor = Color(243, 244, 246)
        centerCell.setPadding(6f)
        
        val titleFont = Font(Font.HELVETICA, 10f, Font.BOLD, Color.DARK_GRAY)
        val p = Paragraph(proj.headerTitle.ifBlank { "INFORME DE VISITA A OBRA" }.uppercase(), titleFont)
        p.alignment = Element.ALIGN_CENTER
        centerCell.addElement(p)
        table.addCell(centerCell)
        
        val rightCell = PdfPCell()
        rightCell.borderColor = Color.LIGHT_GRAY
        rightCell.setPadding(6f)
        
        val pageFont = Font(Font.HELVETICA, 8.5f, Font.NORMAL, Color.DARK_GRAY)
        val pageText = Paragraph("Pág. ${writer.pageNumber}", pageFont)
        pageText.alignment = Element.ALIGN_CENTER
        rightCell.addElement(pageText)
        table.addCell(rightCell)
        
        table.writeSelectedRows(0, -1, document.left(), document.top() + 65f, cb)
    }

    private fun addBlockToDocument(document: Document, block: ContentBlockEntity) {
        when (block.type) {
            BlockType.TITLE -> {
                val font = Font(Font.HELVETICA, 16f, Font.BOLD, Color.DARK_GRAY)
                document.add(Paragraph(block.content, font))
                document.add(Paragraph(" "))
            }
            BlockType.TEXT -> {
                val font = Font(Font.HELVETICA, 12f, Font.NORMAL, Color.DARK_GRAY)
                document.add(Paragraph(block.content, font))
                document.add(Paragraph(" "))
            }
            BlockType.IMAGE -> {
                try {
                    val img = Image.getInstance(block.content)
                    img.scaleToFit(document.right() - document.left(), 300f)
                    img.alignment = Image.ALIGN_CENTER
                    document.add(img)
                    document.add(Paragraph(" "))
                } catch (e: Exception) {
                    document.add(Paragraph("[Error cargando imagen]"))
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
                val label = parts.getOrNull(1) ?: "Firma"
                val sub = parts.getOrNull(2) ?: ""
                
                try {
                    val img = Image.getInstance(path)
                    img.scaleToFit(150f, 80f)
                    document.add(img)
                } catch (e: Exception) {}
                
                document.add(Paragraph(label, Font(Font.HELVETICA, 10f, Font.BOLD)))
                if (sub.isNotBlank()) document.add(Paragraph(sub, Font(Font.HELVETICA, 9f)))
                document.add(Paragraph(" "))
            }
            BlockType.CHECKLIST -> {
                val items = block.content.split("\n").filter { it.isNotBlank() }
                items.forEach { line ->
                    val checked = line.startsWith("true")
                    val text = if (line.contains("|")) line.substringAfter("|") else line
                    val prefix = if (checked) "[X] " else "[ ] "
                    document.add(Paragraph(prefix + text, Font(Font.HELVETICA, 11f)))
                }
                document.add(Paragraph(" "))
            }
            BlockType.FOOTER -> {
                document.add(Paragraph(block.content, Font(Font.HELVETICA, 9f, Font.ITALIC, Color.GRAY)))
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
