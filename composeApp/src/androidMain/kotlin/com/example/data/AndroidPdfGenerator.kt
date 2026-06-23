package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream

class AndroidPdfGenerator(private val context: Context) : PdfGenerator {

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

        // Implement TextMeasurer natively
        val textMeasurer = object : TextMeasurer {
            override fun measureTextWidth(text: String, fontSize: Float, isBold: Boolean): Float {
                val paint = Paint().apply {
                    textSize = fontSize
                    isFakeBoldText = isBold
                    isAntiAlias = true
                }
                return paint.measureText(text)
            }

            override fun getFontHeight(fontSize: Float, isBold: Boolean): Float {
                val paint = Paint().apply {
                    textSize = fontSize
                    isFakeBoldText = isBold
                    isAntiAlias = true
                }
                val metrics = paint.fontMetrics
                return metrics.descent - metrics.ascent
            }
        }

        // Implement image size retrieval natively
        val imageSizeProvider: (String) -> Pair<Float, Float> = { path ->
            val file = File(path)
            if (file.exists()) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                options.outWidth.toFloat() to options.outHeight.toFloat()
            } else {
                0f to 0f
            }
        }

        // Run the shared layout engine
        val engine = PdfLayoutEngine(textMeasurer, imageSizeProvider)
        val renderedPages = engine.layoutPdf(project, exportMode, singleVisitId)

        // Draw instructions onto the Canvas for each page
        for (page in renderedPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, page.pageNumber).create()
            val currentPage = pdfDocument.startPage(pageInfo)
            val canvas = currentPage.canvas

            for (instruction in page.instructions) {
                when (instruction) {
                    is DrawInstruction.Text -> {
                        val paint = Paint().apply {
                            color = Color.rgb(
                                (instruction.colorRgb shr 16) and 0xFF,
                                (instruction.colorRgb shr 8) and 0xFF,
                                instruction.colorRgb and 0xFF
                            )
                            textSize = instruction.fontSize
                            isFakeBoldText = instruction.isBold
                            textSkewX = if (instruction.isItalic) -0.25f else 0f
                            isAntiAlias = true
                            textAlign = when (instruction.align) {
                                DrawAlign.LEFT -> Paint.Align.LEFT
                                DrawAlign.CENTER -> Paint.Align.CENTER
                                DrawAlign.RIGHT -> Paint.Align.RIGHT
                            }
                        }
                        canvas.drawText(instruction.text, instruction.x, instruction.y, paint)
                    }
                    is DrawInstruction.Line -> {
                        val paint = Paint().apply {
                            color = Color.rgb(
                                (instruction.colorRgb shr 16) and 0xFF,
                                (instruction.colorRgb shr 8) and 0xFF,
                                instruction.colorRgb and 0xFF
                            )
                            strokeWidth = instruction.strokeWidth
                            style = Paint.Style.STROKE
                            isAntiAlias = true
                        }
                        canvas.drawLine(instruction.x1, instruction.y1, instruction.x2, instruction.y2, paint)
                    }
                    is DrawInstruction.Rect -> {
                        val paint = Paint().apply {
                            color = Color.rgb(
                                (instruction.colorRgb shr 16) and 0xFF,
                                (instruction.colorRgb shr 8) and 0xFF,
                                instruction.colorRgb and 0xFF
                            )
                            strokeWidth = instruction.strokeWidth
                            style = if (instruction.isFill) Paint.Style.FILL else Paint.Style.STROKE
                            isAntiAlias = true
                        }
                        canvas.drawRect(instruction.x1, instruction.y1, instruction.x2, instruction.y2, paint)
                    }
                    is DrawInstruction.Image -> {
                        val file = File(instruction.path)
                        if (file.exists()) {
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            if (bitmap != null) {
                                val w = instruction.w.coerceAtLeast(1f).toInt()
                                val h = instruction.h.coerceAtLeast(1f).toInt()
                                val scaled = bitmap.scale(w, h, true)
                                canvas.drawBitmap(scaled, instruction.x, instruction.y, null)
                            }
                        }
                    }
                }
            }

            pdfDocument.finishPage(currentPage)
        }

        // Save PDF to output stream
        FileOutputStream(pdfFile).use { output ->
            pdfDocument.writeTo(output)
        }
        pdfDocument.close()

        return pdfFile.absolutePath
    }
}
