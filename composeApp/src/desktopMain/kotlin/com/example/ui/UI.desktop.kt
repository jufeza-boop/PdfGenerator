package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.SketchStroke
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import java.io.InputStream
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.*
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Canvas as SkiaCanvas
import org.jetbrains.skia.Path as SkiaPath
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

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
    val strokes = remember { mutableStateListOf<SketchStroke>() }
    val currentStrokePoints = remember { mutableStateListOf<androidx.compose.ui.geometry.Offset>() }
    var canvasWidth by remember { mutableStateOf(0) }
    var canvasHeight by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(0.8f).padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Panel de Firma (Escritorio)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .onSizeChanged { canvasWidth = it.width; canvasHeight = it.height }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { currentStrokePoints.clear(); currentStrokePoints.add(it) },
                                onDrag = { change, _ -> 
                                    change.consume()
                                    currentStrokePoints.add(change.position)
                                },
                                onDragEnd = {
                                    if (currentStrokePoints.isNotEmpty()) {
                                        strokes.add(SketchStroke(currentStrokePoints.toList()))
                                        currentStrokePoints.clear()
                                    }
                                }
                            )
                        }
                ) {
                    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                        (strokes + listOf(SketchStroke(currentStrokePoints.toList()))).forEach { stroke ->
                            if (stroke.points.isNotEmpty()) {
                                val path = ComposePath().apply {
                                    stroke.points.forEachIndexed { idx, point ->
                                        if (idx == 0) moveTo(point.x, point.y)
                                        else lineTo(point.x, point.y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = androidx.compose.ui.graphics.Color.Black,
                                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                    
                    if (strokes.isEmpty() && currentStrokePoints.isEmpty()) {
                        Text(
                            "Use el ratón para firmar aquí",
                            modifier = Modifier.align(Alignment.Center),
                            color = androidx.compose.ui.graphics.Color.LightGray
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { strokes.clear(); currentStrokePoints.clear() }, enabled = strokes.isNotEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Limpiar")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val w = if (canvasWidth > 0) canvasWidth else 600
                            val h = if (canvasHeight > 0) canvasHeight else 300
                            
                            val skiaBitmap = Bitmap()
                            skiaBitmap.allocN32Pixels(w, h)
                            val skiaCanvas = SkiaCanvas(skiaBitmap)
                            skiaCanvas.clear(0xFFFFFFFF.toInt())
                            
                            val skiaPaint = Paint().apply {
                                color = 0xFF000000.toInt()
                                strokeWidth = 8f
                                mode = PaintMode.STROKE
                                strokeCap = PaintStrokeCap.ROUND
                                strokeJoin = PaintStrokeJoin.ROUND
                                isAntiAlias = true
                            }
                            
                            strokes.forEach { stroke ->
                                if (stroke.points.isNotEmpty()) {
                                    val skiaPath = PathBuilder().apply {
                                        stroke.points.forEachIndexed { idx, pt ->
                                            if (idx == 0) moveTo(pt.x, pt.y)
                                            else lineTo(pt.x, pt.y)
                                        }
                                    }.snapshot()
                                    skiaCanvas.drawPath(skiaPath, skiaPaint)
                                }
                            }
                            
                            val img = SkiaImage.makeFromBitmap(skiaBitmap)
                            val data = img.encodeToData(EncodedImageFormat.PNG, 100)
                            if (data != null) {
                                onConfirm(data.bytes)
                            }
                        },
                        enabled = strokes.isNotEmpty()
                    ) { Text("Confirmar Firma") }
                }
            }
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
