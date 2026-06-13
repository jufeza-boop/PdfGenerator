package com.example.data

import com.lowagie.text.*
import com.lowagie.text.pdf.*
import com.lowagie.text.pdf.draw.LineSeparator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val tableAdapter = moshi.adapter(TableBlockContent::class.java)
    private val checklistAdapter = moshi.adapter(ChecklistBlockContent::class.java)
    private val checklistTableAdapter = moshi.adapter(ChecklistTableBlockContent::class.java)

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
        val maxWidth = document.right() - document.left()
        
        var i = 0
        while (i < sortedBlocks.size) {
            val block = sortedBlocks[i]
            if (block.isHalfWidth) {
                val nextBlock = sortedBlocks.getOrNull(i + 1)
                if (nextBlock != null && nextBlock.isHalfWidth) {
                    // Create 2-column container table
                    val container = PdfPTable(2)
                    container.widthPercentage = 100f
                    container.setWidths(floatArrayOf(0.5f, 0.5f))
                    
                    val cell1 = PdfPCell()
                    cell1.border = Rectangle.NO_BORDER
                    cell1.setPaddingRight(6f)
                    addBlockToContainer(cell1, block, maxWidth / 2f - 6f)
                    container.addCell(cell1)
                    
                    val cell2 = PdfPCell()
                    cell2.border = Rectangle.NO_BORDER
                    cell2.setPaddingLeft(6f)
                    addBlockToContainer(cell2, nextBlock, maxWidth / 2f - 6f)
                    container.addCell(cell2)
                    
                    document.add(container)
                    document.add(Paragraph(" "))
                    i += 2
                } else {
                    // Single half-width block
                    val container = PdfPTable(2)
                    container.widthPercentage = 100f
                    container.setWidths(floatArrayOf(0.5f, 0.5f))
                    
                    val cell1 = PdfPCell()
                    cell1.border = Rectangle.NO_BORDER
                    cell1.setPaddingRight(6f)
                    addBlockToContainer(cell1, block, maxWidth / 2f - 6f)
                    container.addCell(cell1)
                    
                    val cell2 = PdfPCell()
                    cell2.border = Rectangle.NO_BORDER
                    container.addCell(cell2)
                    
                    document.add(container)
                    document.add(Paragraph(" "))
                    i += 1
                }
            } else {
                addBlockToContainer(document, block, maxWidth)
                i += 1
            }
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

    private fun addBlockToContainer(container: Any, block: ContentBlockEntity, availableWidth: Float) {
        fun addToContainer(element: Element) {
            when (container) {
                is Document -> container.add(element)
                is PdfPCell -> container.addElement(element)
                else -> throw IllegalArgumentException("Unsupported container type: ${container::class.simpleName}")
            }
        }

        when (block.type) {
            BlockType.TITLE -> {
                val font = Font(Font.HELVETICA, 16f, Font.BOLD, Color(31, 41, 55))
                addToContainer(Paragraph(block.content, font))
                addToContainer(Paragraph(" "))
            }
            BlockType.TEXT -> {
                val font = Font(Font.HELVETICA, 12f, Font.NORMAL, Color(55, 65, 81))
                addToContainer(Paragraph(block.content, font))
                addToContainer(Paragraph(" "))
            }
            BlockType.IMAGE -> {
                try {
                    val img = Image.getInstance(block.content)
                    img.scaleToFit(availableWidth, 400f)
                    img.alignment = Image.ALIGN_CENTER
                    addToContainer(img)
                    addToContainer(Paragraph(" "))
                } catch (e: Exception) {
                    addToContainer(Paragraph("[Error cargando imagen]", Font(Font.HELVETICA, 10f, Font.ITALIC, Color.RED)))
                }
            }
            BlockType.TABLE -> {
                val content = try { tableAdapter.fromJson(block.content) ?: TableBlockContent() } catch(e: Exception) { TableBlockContent() }
                if (content.rows.isNotEmpty()) {
                    if (content.title.isNotBlank()) {
                        val t = Paragraph(content.title, Font(Font.HELVETICA, 12f, Font.BOLD, Color(31, 41, 55)))
                        t.spacingAfter = 10f
                        addToContainer(t)
                    }
                    val numCols = if (content.headers.isNotEmpty()) content.headers.size else content.rows[0].size
                    val pdfTable = PdfPTable(numCols)
                    pdfTable.widthPercentage = 100f
                    
                    content.rows.forEachIndexed { index, row ->
                        val isHeader = index == 0 && content.headers.isNotEmpty()
                        row.forEach { cellText ->
                            val cell = PdfPCell(Phrase(cellText.trim(), if (isHeader) Font(Font.HELVETICA, 11f, Font.BOLD) else Font(Font.HELVETICA, 10f)))
                            if (isHeader) cell.backgroundColor = Color(243, 244, 246)
                            cell.borderColor = Color(229, 231, 235)
                            pdfTable.addCell(cell)
                        }
                    }
                    addToContainer(pdfTable)
                    addToContainer(Paragraph(" "))
                }
            }
            BlockType.CHECKLIST -> {
                val content = try { checklistAdapter.fromJson(block.content) ?: ChecklistBlockContent() } catch(e: Exception) { ChecklistBlockContent() }
                if (content.title.isNotBlank()) {
                    val t = Paragraph(content.title, Font(Font.HELVETICA, 12f, Font.BOLD, Color(31, 41, 55)))
                    t.spacingAfter = 10f
                    addToContainer(t)
                }
                
                content.items.forEach { item ->
                    val itemTable = PdfPTable(2)
                    itemTable.widthPercentage = 100f
                    itemTable.setWidths(floatArrayOf(0.08f, 0.92f))
                    
                    val boxCell = PdfPCell()
                    boxCell.fixedHeight = 10f
                    boxCell.border = Rectangle.BOX
                    boxCell.borderWidth = 0.5f
                    boxCell.borderColor = Color.BLACK
                    boxCell.horizontalAlignment = Element.ALIGN_CENTER
                    boxCell.verticalAlignment = Element.ALIGN_MIDDLE
                    boxCell.setPadding(0f)
                    
                    if (item.checked) {
                        val checkFont = Font(Font.HELVETICA, 8f, Font.BOLD, Color.BLACK)
                        boxCell.phrase = Phrase("X", checkFont)
                    }
                    itemTable.addCell(boxCell)
                    
                    val font = Font(Font.HELVETICA, 11f, Font.NORMAL, Color(55, 65, 81))
                    val textCell = PdfPCell(Phrase(item.text, font))
                    textCell.border = Rectangle.NO_BORDER
                    textCell.paddingLeft = 8f
                    textCell.paddingBottom = 4f
                    textCell.verticalAlignment = Element.ALIGN_MIDDLE
                    itemTable.addCell(textCell)
                    
                    addToContainer(itemTable)
                }
                addToContainer(Paragraph(" "))
            }
            BlockType.CHECKLIST_TABLE -> {
                val content = try { checklistTableAdapter.fromJson(block.content) ?: ChecklistTableBlockContent() } catch(e: Exception) { ChecklistTableBlockContent() }
                if (content.rows.isNotEmpty()) {
                    if (content.title.isNotBlank()) {
                        val t = Paragraph(content.title, Font(Font.HELVETICA, 12f, Font.BOLD, Color(31, 41, 55)))
                        t.spacingAfter = 10f
                        addToContainer(t)
                    }
                    val statusCols = content.headers
                    val numCols = 1 + statusCols.size
                    val pdfTable = PdfPTable(numCols)
                    pdfTable.widthPercentage = 100f
                    val widths = FloatArray(numCols); widths[0] = 0.65f; val sw = 0.35f/statusCols.size; for(i in 1 until numCols) widths[i] = sw
                    pdfTable.setWidths(widths)

                    val hFont = Font(Font.HELVETICA, 10f, Font.BOLD, Color.BLACK)
                    val hCell = PdfPCell(Phrase("Comprobación", hFont))
                    hCell.backgroundColor = Color(243, 244, 246); hCell.borderColor = Color(200, 200, 200); hCell.setPadding(5f)
                    pdfTable.addCell(hCell)
                    statusCols.forEach { h -> 
                        val c = PdfPCell(Phrase(h, hFont))
                        c.backgroundColor = Color(243, 244, 246); c.borderColor = Color(200, 200, 200)
                        c.horizontalAlignment = Element.ALIGN_CENTER; c.verticalAlignment = Element.ALIGN_MIDDLE
                        pdfTable.addCell(c)
                    }

                    content.rows.forEach { row ->
                        val tCell = PdfPCell(Phrase(row.text, Font(Font.HELVETICA, 10f, Font.NORMAL, Color(55, 65, 81))))
                        tCell.borderColor = Color(200, 200, 200); tCell.setPadding(5f); pdfTable.addCell(tCell)
                        
                        statusCols.forEachIndexed { idx, _ ->
                            val sCell = PdfPCell()
                            sCell.borderColor = Color(200, 200, 200)
                            sCell.horizontalAlignment = Element.ALIGN_CENTER
                            sCell.verticalAlignment = Element.ALIGN_MIDDLE
                            sCell.setPadding(0f)
                            
                            val innerTable = PdfPTable(1)
                            innerTable.totalWidth = 12f
                            innerTable.isLockedWidth = true
                            
                            val box = PdfPCell()
                            box.fixedHeight = 12f
                            box.border = Rectangle.BOX
                            box.borderWidth = 1f
                            box.borderColor = Color(200, 200, 200)
                            box.horizontalAlignment = Element.ALIGN_CENTER
                            box.verticalAlignment = Element.ALIGN_MIDDLE
                            box.setPadding(0f)
                            
                            if (row.selectedIndex == idx) {
                                box.phrase = Phrase("X", Font(Font.HELVETICA, 8f, Font.BOLD, Color.BLACK))
                            }
                            
                            innerTable.addCell(box)
                            sCell.addElement(innerTable)
                            pdfTable.addCell(sCell)
                        }
                    }
                    addToContainer(pdfTable)
                    addToContainer(Paragraph(" "))
                }
            }
            BlockType.SIGNATURE -> {
                val parts = block.content.split("|")
                val path = parts[0]
                val label = parts.getOrNull(1) ?: "Firma de Validación"
                val sub = parts.getOrNull(2) ?: "Firma Autorizada"
                
                try {
                    val img = Image.getInstance(path)
                    img.scaleToFit(minOf(availableWidth, 150f), 80f)
                    addToContainer(img)
                } catch (e: Exception) {
                    val line = LineSeparator()
                    line.offset = -5f
                    line.lineWidth = 1f
                    line.percentage = 30f
                    line.alignment = Element.ALIGN_LEFT
                    addToContainer(line)
                }
                
                addToContainer(Paragraph(label, Font(Font.HELVETICA, 11f, Font.BOLD, Color(31, 41, 55))))
                addToContainer(Paragraph(sub, Font(Font.HELVETICA, 9f, Font.NORMAL, Color(156, 163, 175))))
                addToContainer(Paragraph(" "))
            }
            BlockType.FOOTER -> {
                addToContainer(Paragraph(block.content, Font(Font.HELVETICA, 9f, Font.ITALIC, Color(107, 114, 128))))
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
