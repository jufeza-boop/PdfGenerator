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
        project: ProjectData,
        exportMode: PdfExportMode,
        singleVisitId: String?
    ): String {
        val pdfFile = File(context.cacheDir, "project_report_${project.uuid}.pdf")
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
                var w = options.outWidth.toFloat()
                var h = options.outHeight.toFloat()
                try {
                    val exif = android.media.ExifInterface(file.absolutePath)
                    val orientation = exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                    if (orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90 ||
                        orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270 ||
                        orientation == android.media.ExifInterface.ORIENTATION_TRANSPOSE ||
                        orientation == android.media.ExifInterface.ORIENTATION_TRANSVERSE
                    ) {
                        val temp = w
                        w = h
                        h = temp
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                w to h
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
                            try {
                                // 1. Detectar orientación primero para ajustar las dimensiones requeridas
                                val exif = android.media.ExifInterface(file.absolutePath)
                                val orientation = exif.getAttributeInt(
                                    android.media.ExifInterface.TAG_ORIENTATION,
                                    android.media.ExifInterface.ORIENTATION_NORMAL
                                )
                                val isSwapped = orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90 ||
                                        orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270 ||
                                        orientation == android.media.ExifInterface.ORIENTATION_TRANSPOSE ||
                                        orientation == android.media.ExifInterface.ORIENTATION_TRANSVERSE

                                // 2. Calcular dimensiones en base al doble del tamaño del PDF (para 150+ DPI de excelente calidad visual)
                                val targetW = (instruction.w * 2).toInt().coerceAtLeast(1)
                                val targetH = (instruction.h * 2).toInt().coerceAtLeast(1)
                                val reqWidth = if (isSwapped) targetH else targetW
                                val reqHeight = if (isSwapped) targetW else targetH

                                // 3. Obtener dimensiones de la imagen original en disco sin cargarla a memoria
                                val options = BitmapFactory.Options().apply {
                                    inJustDecodeBounds = true
                                }
                                BitmapFactory.decodeFile(file.absolutePath, options)

                                // 4. Configurar el factor de submuestreo (inSampleSize)
                                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                                options.inJustDecodeBounds = false
                                options.inScaled = false

                                // 5. Decodificar el bitmap de tamaño optimizado
                                var bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                                if (bitmap != null) {
                                    // 6. Aplicar la rotación/espejo necesaria de EXIF
                                    val matrix = android.graphics.Matrix()
                                    var needsRotation = true
                                    when (orientation) {
                                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                                        android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                                        android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                                        android.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                                            matrix.postRotate(90f)
                                            matrix.postScale(-1f, 1f)
                                        }
                                        android.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                                            matrix.postRotate(270f)
                                            matrix.postScale(-1f, 1f)
                                        }
                                        else -> needsRotation = false
                                    }

                                    if (needsRotation) {
                                        val rotatedBitmap = Bitmap.createBitmap(
                                            bitmap,
                                            0,
                                            0,
                                            bitmap.width,
                                            bitmap.height,
                                            matrix,
                                            true
                                        )
                                        if (rotatedBitmap != bitmap) {
                                            bitmap.recycle()
                                            bitmap = rotatedBitmap
                                        }
                                    }

                                    // 7. Escalar al doble del tamaño de destino en el PDF (para excelente nitidez de impresión)
                                    val scaled = bitmap.scale(targetW, targetH, true)

                                    // 8. Definir el rectángulo de destino en puntos del PDF
                                    val destRect = android.graphics.RectF(
                                        instruction.x,
                                        instruction.y,
                                        instruction.x + instruction.w,
                                        instruction.y + instruction.h
                                    )

                                    // 9. Dibujar en el Canvas usando RectF para ignorar la densidad física de pantalla del terminal
                                    val paint = Paint().apply {
                                        isFilterBitmap = true
                                        isAntiAlias = true
                                    }
                                    canvas.drawBitmap(scaled, null, destRect, paint)

                                    // 10. Reciclar recursos
                                    if (scaled != bitmap) {
                                        scaled.recycle()
                                    }
                                    bitmap.recycle()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
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

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
