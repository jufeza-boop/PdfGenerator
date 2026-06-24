package com.example.data

import com.lowagie.text.Document
import com.lowagie.text.Image
import com.lowagie.text.PageSize
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfContentByte
import com.lowagie.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream

class DesktopPdfGenerator : PdfGenerator {

    override suspend fun generatePdf(
        project: ProjectData,
        exportMode: PdfExportMode,
        singleVisitId: String?
    ): String {
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val pdfFile = File(tempDir, "project_report_${project.uuid}.pdf")
        if (pdfFile.exists()) {
            pdfFile.delete()
        }

        val document = Document(PageSize.A4, 0f, 0f, 0f, 0f)
        val writer = PdfWriter.getInstance(document, FileOutputStream(pdfFile))
        document.open()

        val cb = writer.directContent

        val baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
        val baseFontBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
        val baseFontItalic = BaseFont.createFont(BaseFont.HELVETICA_OBLIQUE, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
        val baseFontBoldItalic = BaseFont.createFont(BaseFont.HELVETICA_BOLDOBLIQUE, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)

        // Implement TextMeasurer natively
        val textMeasurer = object : TextMeasurer {
            override fun measureTextWidth(text: String, fontSize: Float, isBold: Boolean): Float {
                val font = if (isBold) baseFontBold else baseFont
                return font.getWidthPoint(text, fontSize)
            }

            override fun getFontHeight(fontSize: Float, isBold: Boolean): Float {
                return fontSize
            }
        }

        // Implement image size retrieval natively
        val imageSizeProvider: (String) -> Pair<Float, Float> = { path ->
            val file = File(path)
            if (file.exists()) {
                try {
                    val img = Image.getInstance(path)
                    img.width to img.height
                } catch (e: Exception) {
                    0f to 0f
                }
            } else {
                0f to 0f
            }
        }

        // Run the shared layout engine
        val engine = PdfLayoutEngine(textMeasurer, imageSizeProvider)
        val renderedPages = engine.layoutPdf(project, exportMode, singleVisitId)

        // Draw instructions using PdfContentByte for each page
        for (pageIndex in renderedPages.indices) {
            val page = renderedPages[pageIndex]
            if (pageIndex > 0) {
                document.newPage()
            }

            for (instruction in page.instructions) {
                when (instruction) {
                    is DrawInstruction.Text -> {
                        cb.beginText()
                        val font = when {
                            instruction.isBold && instruction.isItalic -> baseFontBoldItalic
                            instruction.isBold -> baseFontBold
                            instruction.isItalic -> baseFontItalic
                            else -> baseFont
                        }
                        cb.setFontAndSize(font, instruction.fontSize)
                        val r = (instruction.colorRgb shr 16) and 0xFF
                        val g = (instruction.colorRgb shr 8) and 0xFF
                        val b = instruction.colorRgb and 0xFF
                        cb.setRGBColorFill(r, g, b)

                        // Invert Y coordinate: 842f - y
                        val drawY = 842f - instruction.y
                        when (instruction.align) {
                            DrawAlign.LEFT -> cb.showTextAligned(PdfContentByte.ALIGN_LEFT, instruction.text, instruction.x, drawY, 0f)
                            DrawAlign.CENTER -> cb.showTextAligned(PdfContentByte.ALIGN_CENTER, instruction.text, instruction.x, drawY, 0f)
                            DrawAlign.RIGHT -> cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, instruction.text, instruction.x, drawY, 0f)
                        }
                        cb.endText()
                    }
                    is DrawInstruction.Line -> {
                        cb.setLineWidth(instruction.strokeWidth)
                        val r = (instruction.colorRgb shr 16) and 0xFF
                        val g = (instruction.colorRgb shr 8) and 0xFF
                        val b = instruction.colorRgb and 0xFF
                        cb.setRGBColorStroke(r, g, b)
                        cb.moveTo(instruction.x1, 842f - instruction.y1)
                        cb.lineTo(instruction.x2, 842f - instruction.y2)
                        cb.stroke()
                    }
                    is DrawInstruction.Rect -> {
                        cb.setLineWidth(instruction.strokeWidth)
                        val r = (instruction.colorRgb shr 16) and 0xFF
                        val g = (instruction.colorRgb shr 8) and 0xFF
                        val b = instruction.colorRgb and 0xFF

                        // Rectangle bounds inversion: Y2 is bottom, height is positive difference
                        val rectY = 842f - instruction.y2
                        val rectW = instruction.x2 - instruction.x1
                        val rectH = instruction.y2 - instruction.y1

                        if (instruction.isFill) {
                            cb.setRGBColorFill(r, g, b)
                            cb.rectangle(instruction.x1, rectY, rectW, rectH)
                            cb.fill()
                        } else {
                            cb.setRGBColorStroke(r, g, b)
                            cb.rectangle(instruction.x1, rectY, rectW, rectH)
                            cb.stroke()
                        }
                    }
                    is DrawInstruction.Image -> {
                        val file = File(instruction.path)
                        if (file.exists()) {
                            try {
                                val img = Image.getInstance(instruction.path)
                                img.scaleAbsolute(instruction.w, instruction.h)
                                img.setAbsolutePosition(instruction.x, 842f - instruction.y - instruction.h)
                                cb.addImage(img)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }

        document.close()
        return pdfFile.absolutePath
    }
}
