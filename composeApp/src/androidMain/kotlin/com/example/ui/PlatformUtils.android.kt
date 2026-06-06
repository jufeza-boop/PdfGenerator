package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.appContext
import java.io.File

class AndroidPlatformUtils(private val context: Context) : PlatformUtils {
    override fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun sharePdf(file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir Reporte PDF"))
    }

    override fun savePdfToStorage(file: File, suggestedName: String) {
        showToast("Use el botón de guardar en la vista previa")
    }
}

actual fun getPlatformUtils(): PlatformUtils = AndroidPlatformUtils(appContext)
