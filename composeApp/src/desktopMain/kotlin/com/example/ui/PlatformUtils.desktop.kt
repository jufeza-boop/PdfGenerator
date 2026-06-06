package com.example.ui

import java.io.File
import java.awt.Desktop
import javax.swing.JOptionPane
import javax.swing.JFileChooser

class DesktopPlatformUtils : PlatformUtils {
    override fun showToast(message: String) {
        JOptionPane.showMessageDialog(null, message)
    }

    override fun sharePdf(file: File) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun savePdfToStorage(file: File, suggestedName: String) {
        val chooser = JFileChooser().apply {
            selectedFile = File(suggestedName)
        }
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            try {
                file.copyTo(chooser.selectedFile, overwrite = true)
                showToast("Archivo guardado con éxito")
            } catch (e: Exception) {
                showToast("Error al guardar: ${e.localizedMessage}")
            }
        }
    }
}

actual fun getPlatformUtils(): PlatformUtils = DesktopPlatformUtils()
