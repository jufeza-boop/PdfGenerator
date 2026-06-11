package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.dp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import java.io.InputStream
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image as SkiaImage

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop doesn't have a system back button
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
actual fun SignatureDialog(onDismiss: () -> Unit, onConfirm: (ByteArray) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Firma en Escritorio") },
        text = { Text("La firma táctil no está implementada en escritorio aún.") },
        confirmButton = { Button(onClick = onDismiss) { Text("Cerrar") } }
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PdfPreviewScreen(pdfFile: File, onBack: () -> Unit) {
    var pages by remember { mutableStateOf<List<androidx.compose.ui.graphics.ImageBitmap>>(emptyList()) }
    var renderError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(pdfFile) {
        withContext(Dispatchers.IO) {
            try {
                val document = Loader.loadPDF(pdfFile)
                val renderer = PDFRenderer(document)
                val loadedPages = mutableListOf<androidx.compose.ui.graphics.ImageBitmap>()
                
                for (i in 0 until document.numberOfPages) {
                    val bufferedImage = renderer.renderImageWithDPI(i, 150f)
                    val bytes = java.io.ByteArrayOutputStream().use { out ->
                        javax.imageio.ImageIO.write(bufferedImage, "png", out)
                        out.toByteArray()
                    }
                    val skiaImg = SkiaImage.makeFromEncoded(bytes)
                    val skiaBitmap = Bitmap.makeFromImage(skiaImg)
                    loadedPages.add(skiaBitmap.asComposeImageBitmap())
                }
                document.close()
                pages = loadedPages
                isLoading = false
            } catch (e: Exception) {
                renderError = "Error al renderizar PDF: ${e.localizedMessage}"
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Previsualización de Reporte") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    IconButton(onClick = { getPlatformUtils().sharePdf(pdfFile) }) {
                        Icon(Icons.Default.Share, contentDescription = "Abrir externo")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (renderError != null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(text = renderError!!, color = MaterialTheme.colorScheme.error)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(pages) { bitmap ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f / 1.414f),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                        ) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Página del PDF",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = { getPlatformUtils().savePdfToStorage(pdfFile, "reporte_proyecto.pdf") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Guardar PDF en Equipo")
                }
            }
        }
    }
}
