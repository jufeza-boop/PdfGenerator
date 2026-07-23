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
            try {
                val inputStream = if (path.startsWith("content://")) {
                    context.contentResolver.openInputStream(android.net.Uri.parse(path))
                } else {
                    java.io.FileInputStream(path)
                }
                
                if (inputStream != null) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    inputStream.close()
                    var w = options.outWidth.toFloat()
                    var h = options.outHeight.toFloat()
                    
                    try {
                        val exifInputStream = if (path.startsWith("content://")) {
                            context.contentResolver.openInputStream(android.net.Uri.parse(path))
                        } else {
                            java.io.FileInputStream(path)
                        }
                        if (exifInputStream != null) {
                            val exif = android.media.ExifInterface(exifInputStream)
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
                            exifInputStream.close()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    w to h
                } else {
                    0f to 0f
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
                        try {
                            val isContentUri = instruction.path.startsWith("content://")
                            
                            // 1. Detectar orientación primero para ajustar las dimensiones requeridas
                            val exifInputStream = if (isContentUri) {
                                context.contentResolver.openInputStream(android.net.Uri.parse(instruction.path))
                            } else {
                                java.io.FileInputStream(instruction.path)
                            }
                            
                            var orientation = android.media.ExifInterface.ORIENTATION_NORMAL
                            if (exifInputStream != null) {
                                val exif = android.media.ExifInterface(exifInputStream)
                                orientation = exif.getAttributeInt(
                                    android.media.ExifInterface.TAG_ORIENTATION,
                                    android.media.ExifInterface.ORIENTATION_NORMAL
                                )
                                exifInputStream.close()
                            }
                            
                            val isSwapped = orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90 ||
                                    orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270 ||
                                    orientation == android.media.ExifInterface.ORIENTATION_TRANSPOSE ||
                                    orientation == android.media.ExifInterface.ORIENTATION_TRANSVERSE

                            // 2. Calcular dimensiones
                            val targetW = (instruction.w * 1.5f).toInt().coerceAtLeast(1)
                            val targetH = (instruction.h * 1.5f).toInt().coerceAtLeast(1)
                            val reqWidth = if (isSwapped) targetH else targetW
                            val reqHeight = if (isSwapped) targetW else targetH

                            // 3. Obtener dimensiones de la imagen
                            val options = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            
                            val boundsInputStream = if (isContentUri) {
                                context.contentResolver.openInputStream(android.net.Uri.parse(instruction.path))
                            } else {
                                java.io.FileInputStream(instruction.path)
                            }
                            
                            if (boundsInputStream != null) {
                                BitmapFactory.decodeStream(boundsInputStream, null, options)
                                boundsInputStream.close()

                                // 4. Configurar el factor de submuestreo
                                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                                options.inJustDecodeBounds = false
                                options.inScaled = false

                                // 5. Decodificar el bitmap
                                val decodeInputStream = if (isContentUri) {
                                    context.contentResolver.openInputStream(android.net.Uri.parse(instruction.path))
                                } else {
                                    java.io.FileInputStream(instruction.path)
                                }
                                
                                if (decodeInputStream != null) {
                                    var bitmap = BitmapFactory.decodeStream(decodeInputStream, null, options)
                                    decodeInputStream.close()
                                    
                                    if (bitmap != null) {
                                        // 6. Aplicar la rotación
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
                                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                            )
                                            if (rotatedBitmap != bitmap) {
                                                bitmap.recycle()
                                                bitmap = rotatedBitmap
                                            }
                                        }

                                        // 7. Escalar
                                        val scaled = bitmap.scale(targetW, targetH, true)

                                        // 8. Dibujar
                                        val destRect = android.graphics.RectF(
                                            instruction.x,
                                            instruction.y,
                                            instruction.x + instruction.w,
                                            instruction.y + instruction.h
                                        )
                                        val paint = Paint().apply {
                                            isFilterBitmap = true
                                            isAntiAlias = true
                                        }
                                        canvas.drawBitmap(scaled, null, destRect, paint)

                                        if (scaled != bitmap) scaled.recycle()
                                        bitmap.recycle()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
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
