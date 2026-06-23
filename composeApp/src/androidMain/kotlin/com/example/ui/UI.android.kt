package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import coil3.compose.AsyncImage
import com.example.appContext
import com.example.data.SketchStroke
import androidx.compose.runtime.saveable.rememberSaveable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun PlatformImagePicker(onImageSelected: (InputStream, Long?) -> Unit, visitId: Long?) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(true) }
    var tempPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                if (stream != null) onImageSelected(stream, visitId)
            } catch (e: Exception) {
                Toast.makeText(context, "Error al abrir galería: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
        showDialog = false
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempPhotoPath != null) {
            val file = File(tempPhotoPath!!)
            if (file.exists()) {
                try {
                    val stream = file.inputStream()
                    onImageSelected(stream, visitId)
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al procesar foto: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        showDialog = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, now we can launch the camera
            // We need to re-trigger the camera logic. 
            // A simple way is to just let the user click again, 
            // or we can store a 'pending' state.
            Toast.makeText(context, "Permiso concedido. Pulsa Cámara de nuevo.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Añadir Imagen", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Selecciona el origen de la imagen:", fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        PickerOption(
                            icon = Icons.Default.PhotoCamera,
                            label = "Cámara",
                            onClick = {
                                val permission = android.Manifest.permission.CAMERA
                                if (context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        val file = File(context.cacheDir, "temp_photo_${UUID.randomUUID()}.jpg")
                                        tempPhotoPath = file.absolutePath
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        takePictureLauncher.launch(uri)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    permissionLauncher.launch(permission)
                                }
                            }
                        )
                        PickerOption(
                            icon = Icons.Default.PhotoLibrary,
                            label = "Galería",
                            onClick = {
                                pickImageLauncher.launch("image/*")
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun PickerOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
actual fun PlatformFolderSelector(onFolderSelected: (String) -> Unit) {
    val folderSelectorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                appContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
                onFolderSelected(uri.toString())
            } catch (e: Exception) {
                Toast.makeText(appContext, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    LaunchedEffect(Unit) {
        folderSelectorLauncher.launch(null)
    }
}

@Composable
actual fun SignatureDialog(onDismiss: () -> Unit, onConfirm: (ByteArray) -> Unit) {
    val strokes = remember { mutableStateListOf<SketchStroke>() }
    val currentStrokePoints = remember { mutableStateListOf<Offset>() }
    var canvasWidth by remember { mutableIntStateOf(0) }
    var canvasHeight by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(4.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Panel de Firma", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(240.dp).background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .onSizeChanged { canvasWidth = it.width; canvasHeight = it.height }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { currentStrokePoints.clear(); currentStrokePoints.add(it) },
                                onDrag = { change, _ -> change.consume(); currentStrokePoints.add(change.position) },
                                onDragEnd = { if (currentStrokePoints.isNotEmpty()) { strokes.add(SketchStroke(currentStrokePoints.toList())); currentStrokePoints.clear() } }
                            )
                        }
                ) {
                    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                        (strokes + listOf(SketchStroke(currentStrokePoints.toList()))).forEach { stroke ->
                            val path = Path().apply { stroke.points.forEachIndexed { idx, point -> if (idx == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) } }
                            drawPath(path = path, color = androidx.compose.ui.graphics.Color.Black, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { strokes.clear(); currentStrokePoints.clear() }, enabled = strokes.isNotEmpty()) { Text("Limpiar") }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val bitmap = createBitmap(if (canvasWidth > 0) canvasWidth else 600, if (canvasHeight > 0) canvasHeight else 240, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            val paint = Paint().apply { color = Color.BLACK; strokeWidth = 8f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true }
                            strokes.forEach { stroke -> val path = android.graphics.Path(); stroke.points.forEachIndexed { idx, pt -> if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }; canvas.drawPath(path, paint) }
                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            onConfirm(stream.toByteArray())
                        },
                        enabled = strokes.isNotEmpty()
                    ) { Text("Confirmar") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PdfPreviewScreen(pdfFile: File, onBack: () -> Unit) {
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var renderError by remember { mutableStateOf<String?>(null) }
    
    val savePdfLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { 
            appContext.contentResolver.openOutputStream(it)?.use { out -> pdfFile.inputStream().use { it.copyTo(out) } }
            Toast.makeText(appContext, "Guardado con éxito", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(pdfFile) {
        try {
            val renderPages = mutableListOf<Bitmap>()
            val pfd = android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = createBitmap(800, (800 * 1.414).toInt(), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                renderPages.add(bitmap)
                page.close()
            }
            renderer.close(); pfd.close()
            pages = renderPages
        } catch (e: Exception) { renderError = e.localizedMessage }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Vista Previa") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (renderError != null) Text(renderError!!, color = MaterialTheme.colorScheme.error)
            else LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(pages) { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f/1.414f)) }
            }
            Button(onClick = { savePdfLauncher.launch("reporte.pdf") }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Guardar PDF") }
        }
    }
}

@Composable
actual fun PlatformLazyColumnScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier
) {}

@Composable
actual fun PlatformLazyGridScrollbar(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier
) {}

@Composable
actual fun PlatformColumnScrollbar(
    state: androidx.compose.foundation.ScrollState,
    modifier: Modifier
) {}

