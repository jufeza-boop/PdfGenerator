package com.example.ui

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

interface PlatformUtils {
    fun showToast(message: String)
    fun sharePdf(file: File)
    fun savePdfToStorage(file: File, suggestedName: String)
}

expect fun getPlatformUtils(): PlatformUtils
