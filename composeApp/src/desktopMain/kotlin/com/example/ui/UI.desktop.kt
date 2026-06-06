package com.example.ui

import androidx.compose.runtime.*
import java.io.File
import java.io.InputStream
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import javax.swing.JFileChooser

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop doesn't have a system back button usually
}

@Composable
actual fun PlatformImagePicker(onImageSelected: (InputStream, Long?) -> Unit, visitId: Long?) {
    LaunchedEffect(Unit) {
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            onImageSelected(chooser.selectedFile.inputStream(), visitId)
        }
    }
}

@Composable
actual fun PlatformFolderSelector(onFolderSelected: (String) -> Unit) {
    LaunchedEffect(Unit) {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            onFolderSelected(chooser.selectedFile.absolutePath)
        }
    }
}

@Composable
actual fun SignatureDialog(onDismiss: () -> Unit, onConfirm: (ByteArray) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Firma en Escritorio") },
        text = { Text("La firma táctil no está implementada en escritorio aún.") },
        confirmButton = { Button(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
actual fun PdfPreviewScreen(pdfFile: File, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Vista previa no disponible en escritorio.")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { getPlatformUtils().sharePdf(pdfFile) }) { Text("Abrir con visor del sistema") }
        Button(onClick = onBack) { Text("Volver") }
    }
}
