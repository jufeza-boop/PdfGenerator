package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.theme.*
import com.example.viewmodel.ProjectViewModel
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectApp(
    viewModel: ProjectViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
    val selectedProject by viewModel.selectedProject.collectAsStateWithLifecycle()
    val draftBlocks by viewModel.draftBlocks.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val generatedPdfFile by viewModel.generatedPdfFile.collectAsStateWithLifecycle()
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsStateWithLifecycle()
    val isUploadingCloud by viewModel.isUploadingCloud.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var activeSignatureBlockForDrawing by remember { mutableStateOf<ContentBlockEntity?>(null) }

    // Observe mock upload status
    LaunchedEffect(Unit) {
        viewModel.uploadSuccess.collect { success ->
            val message = if (success) {
                "¡Reporte PDF subido con éxito a la nube!"
            } else {
                "Error al subir el reporte a la nube."
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Dynamic Navigation states based on active bindings
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Crossfade(
            targetState = when {
                generatedPdfFile != null -> AppScreen.PdfPreview
                selectedProjectId != null -> AppScreen.Editor
                else -> AppScreen.Dashboard
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                AppScreen.Dashboard -> {
                    DashboardScreen(
                        projects = allProjects,
                        onProjectSelected = { id -> viewModel.selectProject(id) },
                        onCreateProjectClick = { showCreateDialog = true },
                        onDeleteProject = { project -> viewModel.deleteProject(project) }
                    )
                }
                AppScreen.Editor -> {
                    selectedProject?.let { project ->
                        ProjectEditorScreen(
                            project = project,
                            blocks = draftBlocks,
                            isDirty = isDirty,
                            isGeneratingPdf = isGeneratingPdf,
                            onBack = { viewModel.selectProject(null) },
                            onSave = { viewModel.saveDraft() },
                            onUndo = { viewModel.discardChanges() },
                            onAddTextBlock = { text -> viewModel.addTextBlock(text) },
                            onSaveTextBlockEdit = { block, text -> viewModel.updateBlockText(block, text) },
                            onDeleteBlock = { block -> viewModel.deleteBlock(block) },
                            onImageSelected = { stream -> viewModel.addImageBlock(stream) },
                            onAddSignatureClick = { showSignatureDialog = true },
                            onDrawSignatureClick = { block -> activeSignatureBlockForDrawing = block },
                            onExportPdf = { viewModel.exportPdf() },
                            onMoveBlockUp = { block -> viewModel.moveBlockUp(block) },
                            onMoveBlockDown = { block -> viewModel.moveBlockDown(block) },
                            onToggleBlockWidth = { block -> viewModel.toggleBlockWidth(block) },
                            onAddTitleBlock = { text -> viewModel.addTitleBlock(text) },
                            onAddFooterBlock = { text -> viewModel.addFooterBlock(text) },
                            onAddTableBlock = { text -> viewModel.addTableBlock(text) },
                            onAddChecklistBlock = { text -> viewModel.addChecklistBlock(text) }
                        )
                    } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                AppScreen.PdfPreview -> {
                    generatedPdfFile?.let { pdfFile ->
                        PdfPreviewScreen(
                            pdfFile = pdfFile,
                            onBack = { viewModel.clearPdfPreviewState() }
                        )
                    }
                }
            }
        }

        // Global float dialog overlays
        if (showCreateDialog) {
            CreateProjectDialog(
                onDismiss = { showCreateDialog = false },
                onConfirm = { name, templateType ->
                    viewModel.createProject(name, templateType) { id ->
                        viewModel.selectProject(id)
                    }
                    showCreateDialog = false
                }
            )
        }

        if (showSignatureDialog) {
            SignatureDialog(
                onDismiss = { showSignatureDialog = false },
                onConfirm = { signatureBitmap ->
                    viewModel.addSignatureBlock(signatureBitmap)
                    showSignatureDialog = false
                }
            )
        }

        if (activeSignatureBlockForDrawing != null) {
            SignatureDialog(
                onDismiss = { activeSignatureBlockForDrawing = null },
                onConfirm = { signatureBitmap ->
                    activeSignatureBlockForDrawing?.let { block ->
                        viewModel.updateSignatureDrawing(block.id, signatureBitmap)
                    }
                    activeSignatureBlockForDrawing = null
                }
            )
        }
    }
}

enum class AppScreen {
    Dashboard, Editor, PdfPreview
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    projects: List<ProjectWithBlocks>,
    onProjectSelected: (Long) -> Unit,
    onCreateProjectClick: () -> Unit,
    onDeleteProject: (ProjectEntity) -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Mis Proyectos",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateProjectClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_project_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo Proyecto")
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (projects.isEmpty()) {
                // Empty state card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Sin proyectos todavía",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Presiona el botón + para empezar un nuevo reporte de proyecto.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(projects, key = { it.project.id }) { item ->
                        ProjectGridCard(
                            item = item,
                            onClick = { onProjectSelected(item.project.id) },
                            onDelete = { onDeleteProject(item.project) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectGridCard(
    item: ProjectWithBlocks,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .testTag("project_card_${item.project.id}"),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                val formattedDate = remember(item.project.createdAt) {
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    sdf.format(Date(item.project.createdAt))
                }
                
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("delete_project_${item.project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Borrar proyecto",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = item.project.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Short summary info icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val blockCounts = remember(item.blocks) {
                    val textCount = item.blocks.count { it.type == BlockType.TEXT }
                    val imageCount = item.blocks.count { it.type == BlockType.IMAGE }
                    val sigCount = item.blocks.count { it.type == BlockType.SIGNATURE }
                    Triple(textCount, imageCount, sigCount)
                }

                BadgeCountIcon(imageVector = Icons.Default.Description, count = blockCounts.first)
                BadgeCountIcon(imageVector = Icons.Default.Photo, count = blockCounts.second)
                BadgeCountIcon(imageVector = Icons.Default.Gesture, count = blockCounts.third)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar Proyecto") },
            text = { Text("¿Estás seguro de que deseas eliminar '${item.project.name}'? Todos los archivos y firmas locales asociados se borrarán permanentemente.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun BadgeCountIcon(imageVector: ImageVector, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = count.toString(),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorScreen(
    project: ProjectWithBlocks,
    blocks: List<ContentBlockEntity>,
    isDirty: Boolean,
    isGeneratingPdf: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onAddTextBlock: (String) -> Unit,
    onSaveTextBlockEdit: (ContentBlockEntity, String) -> Unit,
    onDeleteBlock: (ContentBlockEntity) -> Unit,
    onImageSelected: (InputStream) -> Unit,
    onAddSignatureClick: () -> Unit,
    onDrawSignatureClick: (ContentBlockEntity) -> Unit,
    onExportPdf: () -> Unit,
    onMoveBlockUp: (ContentBlockEntity) -> Unit,
    onMoveBlockDown: (ContentBlockEntity) -> Unit,
    onToggleBlockWidth: (ContentBlockEntity) -> Unit,
    onAddTitleBlock: (String) -> Unit,
    onAddFooterBlock: (String) -> Unit,
    onAddTableBlock: (String) -> Unit,
    onAddChecklistBlock: (String) -> Unit
) {
    val context = LocalContext.current
    var textInputToInsert by remember { mutableStateOf("") }
    var focusedBlockIdToEdit by remember { mutableStateOf<Long?>(null) }
    var runningDraftEditVal by remember { mutableStateOf("") }
    var showExitConfirmation by remember { mutableStateOf(false) }

    // Intercept physical or gesture system back triggers
    BackHandler(enabled = true) {
        if (isDirty) {
            showExitConfirmation = true
        } else {
            onBack()
        }
    }

    // Confirmation dialog overlay
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = {
                Text(
                    text = "Cambios sin guardar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Tienes cambios sin guardar en el editor de bloques de este proyecto. ¿Qué deseas hacer?",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmation = false
                        onSave()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Guardar y salir")
                }
            },
            dismissButton = {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            showExitConfirmation = false
                            onUndo()
                            onBack()
                        }
                    ) {
                        Text(
                            text = "Descartar",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { showExitConfirmation = false }
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }

    // Prepare contracts for Pick Image
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                if (stream != null) {
                    onImageSelected(stream)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error cargando imagen de galería", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Capture Image Contract
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            try {
                val stream = context.contentResolver.openInputStream(tempCameraUri!!)
                if (stream != null) {
                    onImageSelected(stream)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error cargando foto de cámara", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val tempFile = File(context.cacheDir, "temp_camera_${System.currentTimeMillis()}.jpg")
                if (tempFile.exists()) tempFile.delete()
                tempFile.createNewFile()
                
                val authority = "${context.packageName}.fileprovider"
                tempCameraUri = FileProvider.getUriForFile(context, authority, tempFile)
                
                tempCameraUri?.let { uri ->
                    takePictureLauncher.launch(uri)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "No se pudo iniciar la cámara", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permiso de cámara es necesario para tomar fotos", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = project.project.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Editor de Bloques",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isDirty) {
                                showExitConfirmation = true
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        // Undo (Deshacer) action
                        IconButton(
                            onClick = onUndo,
                            enabled = isDirty
                        ) {
                            Icon(
                                imageVector = Icons.Default.Undo,
                                contentDescription = "Deshacer cambios",
                                tint = if (isDirty) MaterialTheme.colorScheme.primary else BrandGreySupport.copy(alpha = 0.5f)
                            )
                        }

                        // Save (Guardar) action
                        IconButton(
                            onClick = onSave,
                            enabled = isDirty
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Guardar cambios",
                                tint = if (isDirty) MaterialTheme.colorScheme.primary else BrandGreySupport.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        if (isGeneratingPdf) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 12.dp))
                        } else {
                            IconButton(onClick = onExportPdf) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "Generar PDF",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            // Horizontal tool box bar dynamically spaced
            Surface(
                color = BrandNavBg,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .border(
                        1.dp,
                        BrandOutline.copy(alpha = 0.5f),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Fast insert typing zone
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = textInputToInsert,
                            onValueChange = { textInputToInsert = it },
                            placeholder = { Text("Escribe una nota rápida...") },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(max = 120.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = BrandOutline
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (textInputToInsert.isNotBlank()) {
                                    onAddTextBlock(textInputToInsert.trim())
                                    textInputToInsert = ""
                                }
                            },
                            enabled = textInputToInsert.isNotBlank(),
                            modifier = Modifier
                                .background(
                                    if (textInputToInsert.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Añadir nota",
                                tint = if (textInputToInsert.isNotBlank()) Color.White else BrandGreySupport
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                ToolbarButton(
                                    icon = Icons.Default.CameraAlt,
                                    label = "Cámara",
                                    onClick = {
                                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.CAMERA
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                        if (hasPermission) {
                                            try {
                                                val tempFile = File(context.cacheDir, "temp_camera_${System.currentTimeMillis()}.jpg")
                                                if (tempFile.exists()) tempFile.delete()
                                                tempFile.createNewFile()
                                                
                                                val authority = "${context.packageName}.fileprovider"
                                                tempCameraUri = FileProvider.getUriForFile(context, authority, tempFile)
                                                
                                                tempCameraUri?.let { uri ->
                                                    takePictureLauncher.launch(uri)
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                Toast.makeText(context, "No se pudo iniciar la cámara", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                        }
                                    }
                                )
                            }
                            item {
                                ToolbarButton(
                                    icon = Icons.Default.PhotoLibrary,
                                    label = "Galería",
                                    onClick = { pickImageLauncher.launch("image/*") }
                                )
                            }
                            item {
                                ToolbarButton(
                                    icon = Icons.Default.Gesture,
                                    label = "Firma",
                                    onClick = onAddSignatureClick
                                )
                            }
                            item {
                                ToolbarButton(
                                    icon = Icons.Default.Subject,
                                    label = "Título",
                                    onClick = { onAddTitleBlock("Nuevo Título de Sección") }
                                )
                            }
                            item {
                                ToolbarButton(
                                    icon = Icons.Default.Info,
                                    label = "Footer",
                                    onClick = { onAddFooterBlock("Pie de página y observaciones finales.") }
                                )
                            }
                            item {
                                ToolbarButton(
                                    icon = Icons.Default.List,
                                    label = "Tabla",
                                    onClick = { onAddTableBlock("Columna 1|Columna 2\nFila 1 Col 1|Fila 1 Col 2") }
                                )
                            }
                            item {
                                ToolbarButton(
                                    icon = Icons.Default.CheckBox,
                                    label = "Checklist",
                                    onClick = { onAddChecklistBlock("false|Elemento checklist 1\nfalse|Elemento checklist 2") }
                                )
                            }
                        }

                        // Export Pill button exactly matching HTML visual contrast
                        Button(
                            onClick = onExportPdf,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp), // Pill
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            modifier = Modifier.widthIn(min = 90.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Exportar PDF",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PDF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BrandBg)
        ) {
            val sortedBlocks = remember(blocks) {
                blocks.sortedBy { it.sequence }
            }

            val groupedRows = remember(sortedBlocks) {
                val result = mutableListOf<List<ContentBlockEntity>>()
                var i = 0
                while (i < sortedBlocks.size) {
                    val block = sortedBlocks[i]
                    if (block.isHalfWidth) {
                        val nextBlock = sortedBlocks.getOrNull(i + 1)
                        if (nextBlock != null && nextBlock.isHalfWidth) {
                            result.add(listOf(block, nextBlock))
                            i += 2
                        } else {
                            result.add(listOf(block))
                            i += 1
                        }
                    } else {
                        result.add(listOf(block))
                        i += 1
                    }
                }
                result
            }

            @Composable
            fun RenderSingleBlock(block: ContentBlockEntity) {
                BlockItemView(
                    block = block,
                    isEditing = focusedBlockIdToEdit == block.id,
                    editValue = runningDraftEditVal,
                    onEditValueChange = { runningDraftEditVal = it },
                    onStartEdit = {
                        focusedBlockIdToEdit = block.id
                        if (block.type == BlockType.SIGNATURE) {
                            val parts = block.content.split("|")
                            val label = parts.getOrNull(1) ?: "Firma de Validación"
                            val subtitle = parts.getOrNull(2) ?: "Firma Autorizada"
                            runningDraftEditVal = "$label|$subtitle"
                        } else {
                            runningDraftEditVal = block.content
                        }
                    },
                    onCancelEdit = { focusedBlockIdToEdit = null },
                    onSaveEdit = {
                        if (block.type == BlockType.SIGNATURE) {
                            val parts = block.content.split("|")
                            val filePath = parts[0]
                            val editParts = runningDraftEditVal.split("|")
                            val labelText = editParts.getOrNull(0)?.trim() ?: "Firma de Validación"
                            val subtitleText = editParts.getOrNull(1)?.trim() ?: "Firma Autorizada"
                            val newContent = "$filePath|$labelText|$subtitleText"
                            onSaveTextBlockEdit(block, newContent)
                        } else {
                            onSaveTextBlockEdit(block, runningDraftEditVal.trim())
                        }
                        focusedBlockIdToEdit = null
                    },
                    onDelete = { onDeleteBlock(block) },
                    onMoveUp = { onMoveBlockUp(block) },
                    onMoveDown = { onMoveBlockDown(block) },
                    onToggleWidth = { onToggleBlockWidth(block) },
                    onSaveDirectEdit = { newContent -> onSaveTextBlockEdit(block, newContent) },
                    onDrawSignature = { onDrawSignatureClick(block) }
                )
            }

            if (sortedBlocks.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Assignment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Proyecto vacío",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Inserta notas escritas, fotos del terreno o firmas autorizadas usando las herramientas de abajo.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(groupedRows, key = { row -> row.map { it.id }.joinToString("-") }) { rowBlocks ->
                        if (rowBlocks.size == 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(modifier = Modifier.weight(1.5f)) {
                                    RenderSingleBlock(rowBlocks[0])
                                }
                                Box(modifier = Modifier.weight(1.5f)) {
                                    RenderSingleBlock(rowBlocks[1])
                                }
                            }
                        } else {
                            val block = rowBlocks[0]
                            if (block.isHalfWidth) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth(0.5f).padding(end = 8.dp)) {
                                        RenderSingleBlock(block)
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    RenderSingleBlock(block)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolbarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = BrandGreySupport
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(width = 64.dp, height = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BlockActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun BlockItemView(
    block: ContentBlockEntity,
    isEditing: Boolean,
    editValue: String,
    onEditValueChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleWidth: () -> Unit,
    onSaveDirectEdit: ((String) -> Unit)? = null,
    onDrawSignature: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .testTag("block_item_${block.id}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Block Type Badge & Action Controls (Adaptive for Half Width space constraints)
            val (icon, typeLabel, badgeColor) = when (block.type) {
                BlockType.TEXT -> Triple(Icons.Default.Notes, "OBSERVACIONES", MaterialTheme.colorScheme.primary)
                BlockType.IMAGE -> Triple(Icons.Default.Photo, "REGISTRO FOTOGRÁFICO", MaterialTheme.colorScheme.secondary)
                BlockType.SIGNATURE -> Triple(Icons.Default.Draw, "FIRMA DE VALIDACIÓN", MaterialTheme.colorScheme.tertiary)
                BlockType.TITLE -> Triple(Icons.Default.Subject, "TÍTULO DE SECCIÓN", MaterialTheme.colorScheme.primary)
                BlockType.FOOTER -> Triple(Icons.Default.Info, "NOTAS AL PIE", MaterialTheme.colorScheme.outline)
                BlockType.TABLE -> Triple(Icons.Default.List, "TABLA DE DATOS", MaterialTheme.colorScheme.primary)
                BlockType.CHECKLIST -> Triple(Icons.Default.CheckBox, "CHECKLIST / TAREAS", MaterialTheme.colorScheme.secondary)
            }

            if (block.isHalfWidth) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = typeLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BlockActionIconButton(
                            icon = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Subir bloque",
                            onClick = onMoveUp,
                            tint = MaterialTheme.colorScheme.primary,
                            iconSize = 20.dp
                        )
                        BlockActionIconButton(
                            icon = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Bajar bloque",
                            onClick = onMoveDown,
                            tint = MaterialTheme.colorScheme.primary,
                            iconSize = 20.dp
                        )
                        BlockActionIconButton(
                            icon = if (block.isHalfWidth) Icons.Default.SwapHoriz else Icons.Default.Fullscreen,
                            contentDescription = "Alternar ancho",
                            onClick = onToggleWidth,
                            tint = MaterialTheme.colorScheme.primary,
                            iconSize = 18.dp
                        )
                        BlockActionIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Borrar bloque",
                            onClick = onDelete,
                            tint = MaterialTheme.colorScheme.error,
                            iconSize = 18.dp
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = typeLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            letterSpacing = 1.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BlockActionIconButton(
                            icon = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Subir bloque",
                            onClick = onMoveUp,
                            tint = MaterialTheme.colorScheme.primary,
                            iconSize = 20.dp
                        )
                        BlockActionIconButton(
                            icon = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Bajar bloque",
                            onClick = onMoveDown,
                            tint = MaterialTheme.colorScheme.primary,
                            iconSize = 20.dp
                        )
                        BlockActionIconButton(
                            icon = if (block.isHalfWidth) Icons.Default.SwapHoriz else Icons.Default.Fullscreen,
                            contentDescription = "Alternar ancho",
                            onClick = onToggleWidth,
                            tint = MaterialTheme.colorScheme.primary,
                            iconSize = 18.dp
                        )
                        BlockActionIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Borrar bloque",
                            onClick = onDelete,
                            tint = MaterialTheme.colorScheme.error,
                            iconSize = 18.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body rendering based on block type
            when (block.type) {
                BlockType.TEXT -> {
                    if (isEditing) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = editValue,
                                onValueChange = onEditValueChange,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = onCancelEdit) {
                                    Text("Cancelar")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = onSaveEdit) {
                                    Text("Guardar")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = block.content,
                            fontSize = 14.sp,
                            color = BrandGreySupport,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStartEdit() }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
                BlockType.TITLE -> {
                    if (isEditing) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = editValue,
                                onValueChange = onEditValueChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Texto del Título") },
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = onCancelEdit) { Text("Cancelar") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = onSaveEdit) { Text("Guardar") }
                            }
                        }
                    } else {
                        Text(
                            text = block.content,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStartEdit() }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
                BlockType.FOOTER -> {
                    if (isEditing) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = editValue,
                                onValueChange = onEditValueChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Texto del Pie de Página") },
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = onCancelEdit) { Text("Cancelar") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = onSaveEdit) { Text("Guardar") }
                            }
                        }
                    } else {
                        Text(
                            text = block.content,
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStartEdit() }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
                BlockType.TABLE -> {
                    if (isEditing) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Edita las celdas de la tabla (Soporta múltiples columnas y filas)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = editValue,
                                onValueChange = onEditValueChange,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Cabecera A|Cabecera B\nCelda 1|Celda 2") },
                                label = { Text("Contenido de la Tabla (Separado por '|')") },
                                maxLines = 8,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "💡 Formato: Escribe cada fila en una nueva línea y separa las columnas con el carácter '|'. Ejemplo:\nItem|Estado\nInspección|Completado",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = onCancelEdit) { Text("Cancelar") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = onSaveEdit) { Text("Guardar") }
                            }
                        }
                    } else {
                        val rows = block.content.split("\n").filter { it.isNotBlank() }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onStartEdit() }
                        ) {
                            rows.forEachIndexed { rowIndex, rowText ->
                                val cells = rowText.split("|")
                                val isHeader = rowIndex == 0
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isHeader) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.White)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    cells.forEach { cellText ->
                                        Text(
                                            text = cellText.trim(),
                                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f),
                                            color = if (isHeader) MaterialTheme.colorScheme.onPrimaryContainer else BrandGreySupport
                                        )
                                    }
                                }
                                if (rowIndex < rows.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
                BlockType.CHECKLIST -> {
                    if (isEditing) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Edita el Checklist (Soporta múltiples tareas y estados)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = editValue,
                                onValueChange = onEditValueChange,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("false|Nueva tarea\ntrue|Tarea completada") },
                                label = { Text("Elementos del Checklist") },
                                maxLines = 8,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "💡 Formato: Escribe 'true' para completado y 'false' para pendiente, seguido de '|' y la descripción del elemento.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = onCancelEdit) { Text("Cancelar") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = onSaveEdit) { Text("Guardar") }
                            }
                        }
                    } else {
                        val items = block.content.split("\n").filter { it.isNotBlank() }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items.forEachIndexed { idx, itemLine ->
                                val checked = itemLine.startsWith("true")
                                val label = if (itemLine.contains("|")) itemLine.substringAfter("|") else itemLine
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val updatedItems = items.toMutableList()
                                            updatedItems[idx] = "${!checked}|$label"
                                            val newContent = updatedItems.joinToString("\n")
                                            onSaveDirectEdit?.invoke(newContent)
                                        }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                        contentDescription = if (checked) "Completado" else "Pendiente",
                                        tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = label,
                                        fontSize = 14.sp,
                                        color = if (checked) MaterialTheme.colorScheme.outline else BrandGreySupport,
                                        style = if (checked) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = onStartEdit,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar texto checklist",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                BlockType.IMAGE -> {
                    val file = File(block.content)
                    if (file.exists()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.77f) // aspect-video
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Foto local del proyecto",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            
                            // Top-left design-matching badge
                            Box(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = file.name.uppercase(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            // Bottom overlay grid caption
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomStart)
                                    .background(Color.Black.copy(alpha = 0.2f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Captura de Inspección del Sitio",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        EmptyFilePlaceholder(message = "Fotografía no encontrada localmente")
                    }
                }
                BlockType.SIGNATURE -> {
                    val parts = block.content.split("|")
                    val filePath = parts[0]
                    val signatureLabel = parts.getOrNull(1)?.ifBlank { null } ?: "Firma de Validación"
                    val signatureSubtitle = parts.getOrNull(2)?.ifBlank { null } ?: "Firma Autorizada"
                    val file = File(filePath)
                    val hasSignature = file.exists()

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Interactive signature rendering block
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(
                                    color = if (hasSignature) BrandSignatureBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (hasSignature) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onDrawSignature?.invoke() }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasSignature) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = "Firma digital",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Gesture,
                                        contentDescription = "Firmar",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Haz clic aquí para firmar",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (isEditing) {
                            val editParts = editValue.split("|")
                            var labelText by remember(editValue) { mutableStateOf(editParts.getOrNull(0) ?: "Firma de Validación") }
                            var subtitleText by remember(editValue) { mutableStateOf(editParts.getOrNull(1) ?: "Firma Autorizada") }
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = labelText,
                                    onValueChange = {
                                        labelText = it
                                        onEditValueChange("$labelText|$subtitleText")
                                    },
                                    placeholder = { Text("Firma de Validación") },
                                    label = { Text("Etiqueta principal", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = subtitleText,
                                    onValueChange = {
                                        subtitleText = it
                                        onEditValueChange("$labelText|$subtitleText")
                                    },
                                    placeholder = { Text("Firma Autorizada") },
                                    label = { Text("Subtítulo aclaratorio", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = onCancelEdit) {
                                        Text("Cancelar")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(onClick = onSaveEdit) {
                                        Text("Guardar")
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).clickable { onStartEdit() }) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = signatureLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar etiquetas de firma",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = signatureSubtitle,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                
                                TextButton(
                                    onClick = { onDrawSignature?.invoke() },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = if (hasSignature) Icons.Default.Draw else Icons.Default.Gesture,
                                        contentDescription = "Firmar",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (hasSignature) "Editar dibujo" else "Firmar ahora",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyFilePlaceholder(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf("NONE") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Reporte de Obra") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Ingrese el nombre identificador para este proyecto de reporte:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Ej. Reporte Obra Tarancon") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_project_name_input"),
                    shape = RoundedCornerShape(10.dp)
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "Seleccione una plantilla inicial:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Option 1: Empty
                TemplateOptionCard(
                    title = "Proyecto Vacío",
                    description = "Comenzar el reporte sin bloques predefinidos para diseñarlo desde cero.",
                    icon = Icons.Default.Add,
                    isSelected = selectedTemplate == "NONE",
                    onClick = { selectedTemplate = "NONE" }
                )
                
                // Option 2: ACTA_VISITA
                TemplateOptionCard(
                    title = "Acta de Visita (Dirección de Obra)",
                    description = "Datos generales, tabla de asistentes, observaciones predefinidas de hormigonado y 4 firmas de validación (D.O., DEO, Promotor, Contratista).",
                    icon = Icons.Default.Assignment,
                    isSelected = selectedTemplate == "ACTA_VISITA",
                    onClick = { selectedTemplate = "ACTA_VISITA" }
                )
                
                // Option 3: CONTROL_CALIDAD
                TemplateOptionCard(
                    title = "Recepción y Control de Hormigón",
                    description = "Especificación de materiales/amasas, registro de probetas, checklist de vertido y firmas de recepción de obra.",
                    icon = Icons.Default.DoneAll,
                    isSelected = selectedTemplate == "CONTROL_CALIDAD",
                    onClick = { selectedTemplate = "CONTROL_CALIDAD" }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title.trim(), selectedTemplate)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun TemplateOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val borderWidth = if (isSelected) 1.5.dp else 1.dp
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 13.sp
                )
            }
            
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

// Points tracing helper structure for canvas sketching
data class SketchStroke(val points: List<Offset>)

@Composable
fun SignatureDialog(
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val strokes = remember { mutableStateListOf<SketchStroke>() }
    val currentStrokePoints = remember { mutableStateListOf<Offset>() }
    var canvasWidth by remember { mutableStateOf(0) }
    var canvasHeight by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Panel de Firma",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Estampe su firma con el dedo sobre el lienzo blanco:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Drawing Canvas Block
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .onSizeChanged { size ->
                            canvasWidth = size.width
                            canvasHeight = size.height
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStrokePoints.clear()
                                    currentStrokePoints.add(offset)
                                },
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
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Render previous strokes
                        strokes.forEach { stroke ->
                            val path = Path().apply {
                                stroke.points.forEachIndexed { idx, point ->
                                    if (idx == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = Color.Black,
                                style = Stroke(
                                    width = 6f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // Render active stroke
                        if (currentStrokePoints.isNotEmpty()) {
                            val path = Path().apply {
                                currentStrokePoints.forEachIndexed { idx, point ->
                                    if (idx == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = Color.Black,
                                style = Stroke(
                                    width = 6f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    if (strokes.isEmpty() && currentStrokePoints.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Lienzo de firma vacío",
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom control buttons for sketchpad
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            strokes.clear()
                            currentStrokePoints.clear()
                        },
                        enabled = strokes.isNotEmpty() || currentStrokePoints.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Limpiar")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            // Synthesize a beautiful Bitmap from current lines
                            val w = if (canvasWidth > 0) canvasWidth else 600
                            val h = if (canvasHeight > 0) canvasHeight else 240
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            canvas.drawColor(android.graphics.Color.WHITE) // absolute opaque white as required

                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.BLACK
                                strokeWidth = 8f
                                style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                strokeJoin = android.graphics.Paint.Join.ROUND
                                isAntiAlias = true
                            }

                            // Scaling factors are 1:1 since the canvas size matches the layout box in pixels
                            strokes.forEach { stroke ->
                                val path = android.graphics.Path()
                                stroke.points.forEachIndexed { idx, point ->
                                    if (idx == 0) {
                                        path.moveTo(point.x, point.y)
                                    } else {
                                        path.lineTo(point.x, point.y)
                                    }
                                }
                                canvas.drawPath(path, paint)
                            }

                            onConfirm(bitmap)
                        },
                        enabled = strokes.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirmar")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    pdfFile: File,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var renderError by remember { mutableStateOf<String?>(null) }

    // SAF Create Document launcher to let the user choose standard directories (Downloads, Documents, Drive, etc.)
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    pdfFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(context, "Reporte PDF guardado con éxito", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "No se pudo guardar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Render pages dynamically utilizing background thread dispatcher as recommended
    LaunchedEffect(pdfFile) {
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val renderPages = mutableListOf<Bitmap>()
                val pfd = android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    // Width = 800px, responsive aspect height matching A4 layout
                    val width = 800
                    val height = (width * 1.414).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    renderPages.add(bitmap)
                    page.close()
                }
                renderer.close()
                pfd.close()
                Result.success(renderPages)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
        result.onSuccess {
            pages = it
        }.onFailure {
            renderError = "Error al abrir o renderizar PDF: \n${it.localizedMessage}"
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
                    IconButton(
                        onClick = {
                            try {
                                val authority = "${context.packageName}.fileprovider"
                                val uri = FileProvider.getUriForFile(context, authority, pdfFile)
                                
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir Reporte PDF"))
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "No se pudo compartir el archivo", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir Reporte")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            if (renderError != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = renderError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }
            } else if (pages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Renderizando reporte...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(pages) { pageBitmap ->
                        ElevatedCard(
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f / 1.414f)
                        ) {
                            Image(
                                bitmap = pageBitmap.asImageBitmap(),
                                contentDescription = "Página reporte PDF",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                              )
                        }
                    }
                }
            }

            // Save action panel
            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { 
                            savePdfLauncher.launch("reporte_proyecto_${pdfFile.nameWithoutExtension}.pdf")
                        },
                        enabled = pages.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Guardar PDF en Dispositivo")
                    }
                }
            }
        }
    }
}
