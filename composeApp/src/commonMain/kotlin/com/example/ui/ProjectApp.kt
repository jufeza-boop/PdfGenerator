package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.data.*
import com.example.ui.screens.*
import com.example.ui.theme.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import com.example.viewmodel.ProjectViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
expect fun PlatformImagePicker(onImageSelected: (InputStream, String?) -> Unit, visitId: String?)

@Composable
fun AppIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // Helmet Body
            val helmetPath = Path().apply {
                moveTo(w * 0.2f, h * 0.7f)
                cubicTo(w * 0.2f, h * 0.3f, w * 0.8f, h * 0.3f, w * 0.8f, h * 0.7f)
                close()
            }
            drawPath(helmetPath, color = tint)
            
            // Helmet Rim
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.1f, h * 0.7f),
                size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.1f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )
            
            // Checklist Small
            val checkColor = Color.White
            drawRect(
                color = checkColor,
                topLeft = Offset(w * 0.55f, h * 0.55f),
                size = androidx.compose.ui.geometry.Size(w * 0.35f, h * 0.35f)
            )
            
            // Checklist Lines
            for (i in 0..2) {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.6f, h * (0.65f + i * 0.08f)),
                    end = Offset(w * 0.85f, h * (0.65f + i * 0.08f)),
                    strokeWidth = 2f
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectApp(
    viewModel: ProjectViewModel,
    modifier: Modifier = Modifier
) {
    val platformUtils = getPlatformUtils()
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
    val selectedProject by viewModel.selectedProject.collectAsStateWithLifecycle()
    val draftBlocks by viewModel.draftBlocks.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val generatedPdfFile by viewModel.generatedPdfFile.collectAsStateWithLifecycle()
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsStateWithLifecycle()
    val isUploadingCloud by viewModel.isUploadingCloud.collectAsStateWithLifecycle()
    val showTemplateManagement by viewModel.showTemplateManagement.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var activeSignatureBlockForDrawing by remember { mutableStateOf<BlockData?>(null) }
    var activeSignatureVisitId by remember { mutableStateOf<String?>(null) }

    // Observe mock upload status
    LaunchedEffect(Unit) {
        viewModel.uploadSuccess.collect { success ->
            val message = if (success) {
                "¡Reporte PDF subido con éxito a la nube!"
            } else {
                "Error al subir el reporte a la nube."
            }
            platformUtils.showToast(message)
        }
    }

    val workspaceConfigured by viewModel.workspaceConfigured.collectAsStateWithLifecycle()
    val customTemplates by viewModel.customTemplates.collectAsStateWithLifecycle()
    var showFolderSelector by remember { mutableStateOf(false) }

    if (!workspaceConfigured && !showFolderSelector) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {}, // Cannot be dismissed
            title = { Text("Configurar Workspace", fontWeight = FontWeight.Bold) },
            text = { Text("Para comenzar a utilizar la aplicación, por favor selecciona o crea una carpeta en tu dispositivo. Aquí se guardarán todos los proyectos JSON localmente.") },
            confirmButton = {
                androidx.compose.material3.Button(onClick = { showFolderSelector = true }) {
                    Text("Seleccionar Carpeta")
                }
            }
        )
    }

    if (showFolderSelector) {
        PlatformFolderSelector(onFolderSelected = { uri ->
            if (uri != null) {
                viewModel.setWorkspaceUri(uri)
            }
            showFolderSelector = false
        })
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
                showTemplateManagement -> AppScreen.TemplateManagement
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
                        onDeleteProject = { project -> viewModel.deleteProject(project) },
                        onSyncClick = { showFolderSelector = true },
                        onRunSync = { /* No-op */ },
                        onManageTemplatesClick = { viewModel.setShowTemplateManagement(true) }
                    )
                }
                AppScreen.Editor -> {
                    selectedProject?.let { project ->
                        ProjectEditorScreen(
                            project = project,
                            blocks = draftBlocks,
                            customTemplates = customTemplates,
                            isDirty = isDirty,
                            isGeneratingPdf = isGeneratingPdf,
                            onBack = { viewModel.selectProject(null) },
                            onSave = { viewModel.saveDraft() },
                            onUndo = { viewModel.discardChanges() },
                            onAddTextBlock = { text, visitId -> viewModel.addTextBlock(text, visitId) },
                            onSaveTextBlockEdit = { block, text -> viewModel.updateBlockText(block, text) },
                            onDeleteBlock = { block -> viewModel.deleteBlock(block) },
                            onImageSelected = { stream, visitId -> viewModel.addImageBlock(stream, visitId) },
                            onAddSignatureClick = { visitId -> 
                                activeSignatureVisitId = visitId
                                showSignatureDialog = true 
                            },
                            onDrawSignatureClick = { block -> activeSignatureBlockForDrawing = block },
                            onUpdateProjectInfo = { name, label, showLabel, showDate, comp, compSub, headerTitle, showHeaderBox, showHeaderTitle ->
                                viewModel.updateProjectInfo(name, label, showLabel, showDate, comp, compSub, headerTitle, showHeaderBox, showHeaderTitle)
                            },
                            onExportPdf = { viewModel.exportPdf(exportMode = PdfExportMode.FULL_REPORT) },
                            onMoveBlockUp = { block -> viewModel.moveBlockUp(block) },
                            onMoveBlockDown = { block -> viewModel.moveBlockDown(block) },
                            onToggleBlockWidth = { block -> viewModel.toggleBlockWidth(block) },
                            onAddTitleBlock = { text, visitId -> viewModel.addTitleBlock(text, visitId) },
                            onAddFooterBlock = { text, visitId -> viewModel.addFooterBlock(text, visitId) },
                            onAddTableBlock = { visitId -> viewModel.addTableBlock(visitId) },
                            onAddChecklistBlock = { visitId -> viewModel.addChecklistBlock(visitId) },
                            onAddChecklistTableBlock = { visitId -> viewModel.addChecklistTableBlock(visitId) },
                            onAddVisit = { title, notes, templateType -> viewModel.createVisit(title, notes, templateType) },
                            onDeleteVisit = { visit -> viewModel.deleteVisit(visit) },
                            onUpdateVisit = { visit -> viewModel.updateVisit(visit) },
                            onExportSingleVisit = { visitId -> viewModel.exportPdf(exportMode = PdfExportMode.SINGLE_VISIT, singleVisitId = visitId) },
                            onResolvePath = { path -> viewModel.resolveAbsolutePath(path) },
                            onSaveProjectAsTemplate = { name -> viewModel.saveProjectAsTemplate(name) },
                            onSaveVisitAsTemplate = { name, visitId -> viewModel.saveVisitAsTemplate(name, visitId) }
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
                AppScreen.TemplateManagement -> {
                    TemplateManagementScreen(
                        customTemplates = customTemplates,
                        onBack = { viewModel.setShowTemplateManagement(false) },
                        onDeleteTemplate = { uuid -> viewModel.deleteCustomTemplate(uuid) },
                        onRenameTemplate = { uuid, newName -> viewModel.renameCustomTemplate(uuid, newName) },
                        onEditTemplate = { uuid -> 
                            viewModel.setShowTemplateManagement(false)
                            viewModel.selectProject("template_$uuid") 
                        },
                        onCreateVisitTemplate = { name -> viewModel.createNewVisitTemplate(name) }
                    )
                }
            }
        }

        val scope = rememberCoroutineScope()

        // Global float dialog overlays
        if (showCreateDialog) {
            CreateProjectDialog(
                customTemplates = customTemplates,
                onDismiss = { showCreateDialog = false },
                onConfirm = { name, templateType ->
                    scope.launch {
                        viewModel.createProject(name, templateType) { id ->
                            viewModel.selectProject(id)
                        }
                    }
                    showCreateDialog = false
                }
            )
        }

        if (showSignatureDialog) {
            SignatureDialog(
                onDismiss = { showSignatureDialog = false },
                onConfirm = { signatureBitmap ->
                    viewModel.addSignatureBlock(signatureBitmap, activeSignatureVisitId)
                    showSignatureDialog = false
                }
            )
        }

        if (activeSignatureBlockForDrawing != null) {
            SignatureDialog(
                onDismiss = { activeSignatureBlockForDrawing = null },
                onConfirm = { signatureBitmap ->
                    activeSignatureBlockForDrawing?.let { block ->
                        viewModel.updateSignatureDrawing(block.uuid, signatureBitmap)
                    }
                    activeSignatureBlockForDrawing = null
                }
            )
        }
    }
}

@Composable
fun CreateProjectDialog(
    customTemplates: List<CustomTemplateData>,
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
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
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
                    title = "Plantilla de Actas",
                    description = "Inicializa el proyecto con el encabezado general y los datos principales de la obra.",
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    isSelected = selectedTemplate == "ACTA_VISITA",
                    onClick = { selectedTemplate = "ACTA_VISITA" }
                )
                
                customTemplates.filter { it.target == "PROJECT" }.forEach { template ->
                    TemplateOptionCard(
                        title = template.name,
                        description = "Plantilla personalizada.",
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        isSelected = selectedTemplate == template.uuid,
                        onClick = { selectedTemplate = template.uuid }
                    )
                }
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

@Composable
expect fun SignatureDialog(
    onDismiss: () -> Unit,
    onConfirm: (ByteArray) -> Unit
)

@Composable
expect fun PlatformFolderSelector(onFolderSelected: (String?) -> Unit)
