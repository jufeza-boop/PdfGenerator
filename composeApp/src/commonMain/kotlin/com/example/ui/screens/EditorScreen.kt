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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorScreen(
    project: ProjectData,
    blocks: List<BlockData>,
    isDirty: Boolean,
    isGeneratingPdf: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onAddTextBlock: (String, String?) -> Unit,
    onSaveTextBlockEdit: (BlockData, String) -> Unit,
    onDeleteBlock: (BlockData) -> Unit,
    onImageSelected: (InputStream, String?) -> Unit,
    onAddSignatureClick: (String?) -> Unit,
    onDrawSignatureClick: (BlockData) -> Unit,
    onUpdateProjectInfo: (String, String, Boolean, Boolean, String, String, String, Boolean, Boolean) -> Unit,
    onExportPdf: () -> Unit,
    onMoveBlockUp: (BlockData) -> Unit,
    onMoveBlockDown: (BlockData) -> Unit,
    onToggleBlockWidth: (BlockData) -> Unit,
    onAddTitleBlock: (String, String?) -> Unit,
    onAddFooterBlock: (String, String?) -> Unit,
    onAddTableBlock: (String?) -> Unit,
    onAddChecklistBlock: (String?) -> Unit,
    onAddChecklistTableBlock: (String?) -> Unit,
    onAddVisit: (String, String, String) -> Unit,
    onDeleteVisit: (VisitData) -> Unit,
    onUpdateVisit: (VisitData) -> Unit,
    onExportSingleVisit: (String) -> Unit
) {
    var textInputToInsert by remember { mutableStateOf("") }
    var focusedBlockIdToEdit by remember { mutableStateOf<String?>(null) }
    var runningDraftEditVal by remember { mutableStateOf("") }
    var showExitConfirmation by remember { mutableStateOf(false) }

    // Intercept physical or gesture system back triggers
    PlatformBackHandler(enabled = true) {
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

    var targetVisitIdForImage by remember { mutableStateOf<String?>(null) }
    var showImagePicker by remember { mutableStateOf(false) }

    if (showImagePicker) {
        PlatformImagePicker(
            onImageSelected = { stream, visitId ->
                onImageSelected(stream, visitId)
                showImagePicker = false
            },
            visitId = targetVisitIdForImage
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = project.name,
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
                                imageVector = Icons.AutoMirrored.Filled.Undo,
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
                            var showPdfDropdown by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showPdfDropdown = true }) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "Generar PDF",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                DropdownMenu(
                                    expanded = showPdfDropdown,
                                    onDismissRequest = { showPdfDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Reporte Completo (Parte Común + Visitas)", fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showPdfDropdown = false
                                            onExportPdf()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sólo Parte Común / Configuración", fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showPdfDropdown = false
                                            onExportSingleVisit("") // export modeCOMMON_ONLY
                                        }
                                    )
                                }
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
                                    onAddTextBlock(textInputToInsert.trim(), null)
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
                                imageVector = Icons.AutoMirrored.Filled.Send,
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
                                    icon = Icons.Default.AddAPhoto,
                                    label = "Imágenes",
                                    onClick = { 
                                        targetVisitIdForImage = null
                                        showImagePicker = true
                                    }
                                )
                            }
                            item {
                                ToolbarButton(
                                    icon = Icons.Default.Gesture,
                                    label = "Firma",
                                    onClick = { onAddSignatureClick(null) }
                                )
                            }
                            item {
                                ToolbarButton(
                                    icon = Icons.AutoMirrored.Filled.Subject,
                                    label = "Título",
                                    onClick = { onAddTitleBlock("Nuevo Título de Sección", null) }
                                )
                            }
                            item {
                                ToolbarButton(
                                    icon = Icons.Default.Info,
                                    label = "Footer",
                                    onClick = { onAddFooterBlock("Pie de página y observaciones finales.", null) }
                                )
                            }
                            item {
                                ToolbarButton(
                                    icon = Icons.AutoMirrored.Filled.List,
                                    label = "Tabla",
                                    onClick = { onAddTableBlock(null) }
                                )
                            }
                            item {
                                ToolbarButton(
                                    icon = Icons.Default.CheckBox,
                                    label = "Checklist",
                                    onClick = { onAddChecklistBlock(null) }
                                )
                            }
                            item {
                                ToolbarButton(
                                    icon = Icons.Default.GridOn,
                                    label = "Ch. Tabla",
                                    onClick = { onAddChecklistTableBlock(null) }
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
        var activeTab by remember { mutableStateOf(0) } // 0: Common Part, 1: Visits
        
        // State variables for managing visits
        var showCreateVisitDialog by remember { mutableStateOf(false) }
        var visitToEdit by remember { mutableStateOf<com.example.data.VisitData?>(null) }
        var visitToDeleteConfirm by remember { mutableStateOf<com.example.data.VisitData?>(null) }
        
        var visitTitleInput by remember { mutableStateOf("") }
        var visitNotesInput by remember { mutableStateOf("") }
        var selectedVisitTemplate by remember { mutableStateOf("NONE") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BrandBg)
        ) {
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Parte Común / Config", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Visitas de Obra (${project.visits.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            val filteredBlocksByTab = remember(blocks, activeTab) {
                if (activeTab == 0) {
                    blocks.filter { it.visitUuid == null || it.visitUuid == "" }
                } else {
                    emptyList()
                }
            }

            val sortedBlocks = remember(filteredBlocksByTab) {
                filteredBlocksByTab.sortedBy { it.sequence }
            }

            val groupedRows = remember(sortedBlocks) {
                val result = mutableListOf<List<BlockData>>()
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
            fun RenderSingleBlock(block: BlockData) {
                BlockItemView(
                    block = block,
                    isEditing = focusedBlockIdToEdit == block.uuid,
                    editValue = runningDraftEditVal,
                    onEditValueChange = { runningDraftEditVal = it },
                    onStartEdit = {
                        focusedBlockIdToEdit = block.uuid
                        if (block.type == BlockType.SIGNATURE.name) {
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
                        if (block.type == BlockType.SIGNATURE.name) {
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
                    onSaveDirectEdit = { newContent -> 
                        onSaveTextBlockEdit(block, newContent)
                        focusedBlockIdToEdit = null
                    },
                    onDrawSignature = { onDrawSignatureClick(block) }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeTab == 0) {
                    val commonListState = androidx.compose.foundation.lazy.rememberLazyListState()
                    PlatformLazyColumnWithScrollbar(
                        state = commonListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                item(key = "header_settings") {
                    var expanded by remember { mutableStateOf(false) }
                    
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Configuración de Cabecera",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Edita el logotipo/empresa de cabecera y el diseño general",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            if (expanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Show/Hide entire box header toggle
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = project.showHeaderBox,
                                        onCheckedChange = { isChecked ->
                                            onUpdateProjectInfo(
                                                project.name,
                                                project.reportLabel,
                                                project.showHeaderLabel,
                                                project.showHeaderDate,
                                                project.headerCompany,
                                                project.headerCompanySub,
                                                project.headerTitle,
                                                isChecked,
                                                project.showHeaderTitle
                                            )
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Habilitar Cabecera Multicapa (Tabla)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Muestra una cabecera profesional con Logo/Empresa, Título y Paginación en todas las hojas",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                if (project.showHeaderBox) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Live Beautiful Render Preview of the Box Header Component!
                                    Text(
                                        text = "VISTA PREVIA DE CABECERA EN PDF:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )

                                    // Component box layout matching exactly the requested PDF look!
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(4.dp))
                                            .padding(0.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.White
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(IntrinsicSize.Min)
                                        ) {
                                            // Left Column (Company) - 40% width
                                            Column(
                                                modifier = Modifier
                                                    .weight(0.40f)
                                                    .padding(6.dp)
                                            ) {
                                                Text(
                                                    text = project.headerCompany.ifBlank { "Nombre de la empresa" },
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF9A6640), // copper brown
                                                    maxLines = 1
                                                )
                                                Spacer(modifier = Modifier.height(1.dp))
                                                Text(
                                                    text = project.headerCompanySub.ifBlank { "ARQUITECTO TÉCNICO..." },
                                                    fontSize = 5.5.sp,
                                                    lineHeight = 7.sp,
                                                    color = Color(0xFF6B7280),
                                                    maxLines = 3
                                                )
                                            }

                                            // Divider 1
                                            VerticalDivider(color = Color(0xFFE5E7EB), modifier = Modifier.fillMaxHeight())

                                            // Center Column (Title with grey background) - 42% width
                                            Box(
                                                modifier = Modifier
                                                    .weight(0.42f)
                                                    .fillMaxHeight()
                                                    .background(Color(0xFFF3F4F6))
                                                    .padding(6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = project.headerTitle.ifBlank { "INFORME DE VISITA A OBRA" }.uppercase(Locale.getDefault()),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF111827),
                                                    textAlign = TextAlign.Center
                                                )
                                            }

                                            // Divider 2
                                            VerticalDivider(color = Color(0xFFE5E7EB), modifier = Modifier.fillMaxHeight())

                                            // Right Column (Pagination) - 18% width
                                            Box(
                                                modifier = Modifier
                                                    .weight(0.18f)
                                                    .fillMaxHeight(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Pág. 1 de 2",
                                                    fontSize = 8.5.sp,
                                                    color = Color(0xFF4B5563),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Editable Field: Left Side (Company Name)
                                    var headerCompValue by remember(project.headerCompany) { mutableStateOf(project.headerCompany) }
                                    OutlinedTextField(
                                        value = headerCompValue,
                                        onValueChange = {
                                            headerCompValue = it
                                            onUpdateProjectInfo(
                                                project.name,
                                                project.reportLabel,
                                                project.showHeaderLabel,
                                                project.showHeaderDate,
                                                it,
                                                project.headerCompanySub,
                                                project.headerTitle,
                                                project.showHeaderBox,
                                                project.showHeaderTitle
                                            )
                                        },
                                        placeholder = { Text("Nombre o Nombre de Empresa") },
                                        label = { Text("Firma / Empresa (Cabecera Izq.)", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Editable Field: Left Side Subtitle details
                                    var headerCompSubValue by remember(project.headerCompanySub) { mutableStateOf(project.headerCompanySub) }
                                    OutlinedTextField(
                                        value = headerCompSubValue,
                                        onValueChange = {
                                            headerCompSubValue = it
                                            onUpdateProjectInfo(
                                                project.name,
                                                project.reportLabel,
                                                project.showHeaderLabel,
                                                project.showHeaderDate,
                                                project.headerCompany,
                                                it,
                                                project.headerTitle,
                                                project.showHeaderBox,
                                                project.showHeaderTitle
                                            )
                                        },
                                        placeholder = { Text("Ej. Especialidades, título, dirección...") },
                                        label = { Text("Detalles Corporativos (Subtítulo Izq.)", fontSize = 11.sp) },
                                        maxLines = 3,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Editable Field: Center column label
                                    var headerTitleValue by remember(project.headerTitle) { mutableStateOf(project.headerTitle) }
                                    OutlinedTextField(
                                        value = headerTitleValue,
                                        onValueChange = {
                                            headerTitleValue = it
                                            onUpdateProjectInfo(
                                                project.name,
                                                project.reportLabel,
                                                project.showHeaderLabel,
                                                project.showHeaderDate,
                                                project.headerCompany,
                                                project.headerCompanySub,
                                                it,
                                                project.showHeaderBox,
                                                project.showHeaderTitle
                                            )
                                        },
                                        placeholder = { Text("Ej. INFORME DE VISITA A OBRA") },
                                        label = { Text("Título en Centro de Cabecera", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                // Project Name Editable Field con Check de Visibilidad
                                var projName by remember(project.name) { mutableStateOf(project.name) }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = project.showHeaderTitle,
                                        onCheckedChange = { isChecked ->
                                            onUpdateProjectInfo(
                                                project.name,
                                                project.reportLabel,
                                                project.showHeaderLabel,
                                                project.showHeaderDate,
                                                project.headerCompany,
                                                project.headerCompanySub,
                                                project.headerTitle,
                                                project.showHeaderBox,
                                                isChecked
                                            )
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Mostrar título del proyecto",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Controla si el nombre del proyecto aparece en el cuerpo del reporte",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = projName,
                                    onValueChange = {
                                        projName = it
                                        onUpdateProjectInfo(
                                            it, 
                                            project.reportLabel, 
                                            project.showHeaderLabel, 
                                            project.showHeaderDate,
                                            project.headerCompany,
                                            project.headerCompanySub,
                                            project.headerTitle,
                                            project.showHeaderBox,
                                            project.showHeaderTitle
                                        )
                                    },
                                    placeholder = { Text("Nombre del Proyecto") },
                                    label = { Text("Título Principal del Proyecto (Cuerpo)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Report Category Label (e.g. "REPORTE DE PROYECTO" / "ACTA DE VISITA")
                                var repLabel by remember(project.reportLabel) { mutableStateOf(project.reportLabel) }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = project.showHeaderLabel,
                                        onCheckedChange = { isChecked ->
                                            onUpdateProjectInfo(
                                                project.name, 
                                                project.reportLabel, 
                                                isChecked, 
                                                project.showHeaderDate,
                                                project.headerCompany,
                                                project.headerCompanySub,
                                                project.headerTitle,
                                                project.showHeaderBox,
                                                project.showHeaderTitle
                                            )
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Mostrar literal superior",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Categoría o tipo de reporte del cuerpo (Modificable / Ocultable)",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                
                                if (project.showHeaderLabel) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = repLabel,
                                        onValueChange = {
                                            repLabel = it
                                            onUpdateProjectInfo(
                                                project.name, 
                                                it, 
                                                project.showHeaderLabel, 
                                                project.showHeaderDate,
                                                project.headerCompany,
                                                project.headerCompanySub,
                                                project.headerTitle,
                                                project.showHeaderBox,
                                                project.showHeaderTitle
                                            )
                                        },
                                        placeholder = { Text("Ej. REPORTE DE PROYECTO") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Report Date show/hide
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = project.showHeaderDate,
                                        onCheckedChange = { isChecked ->
                                            onUpdateProjectInfo(
                                                project.name, 
                                                project.reportLabel, 
                                                project.showHeaderLabel, 
                                                isChecked,
                                                project.headerCompany,
                                                project.headerCompanySub,
                                                project.headerTitle,
                                                project.showHeaderBox,
                                                project.showHeaderTitle
                                            )
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column {
                                        Text(
                                            text = "Mostrar fecha en el PDF",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Registrará la fecha de creación del reporte",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (sortedBlocks.isEmpty()) {
                    item(key = "empty_placeholder") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Assignment,
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
                    }
                } else {
                    items(groupedRows, key = { row -> row.joinToString("-") { it.uuid } }) { rowBlocks ->
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

                if (activeTab == 1) {
                    val visitsScrollState = rememberScrollState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(visitsScrollState)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                        Button(
                            onClick = {
                                visitTitleInput = ""
                                visitNotesInput = ""
                                selectedVisitTemplate = "NONE"
                                showCreateVisitDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Añadir Nueva Visita")
                        }

                        if (project.visits.isEmpty()) {
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Event,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Aún no hay visitas registradas",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Cada visita guardará sus propios bloques, fotos, firmas e informes.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            project.visits.forEach { visit ->
                                val visitBlocks = remember(blocks) {
                                    blocks.filter { it.visitUuid == visit.uuid }.sortedBy { it.sequence }
                                }
                                val formattedDate = remember(visit.date) { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(visit.date)) }
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        // Visit top header
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = visit.title,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = formattedDate,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        visitToEdit = visit
                                                        visitTitleInput = visit.title
                                                        visitNotesInput = visit.notes
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(20.dp))
                                                }
                                                IconButton(
                                                    onClick = { visitToDeleteConfirm = visit }
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                                }
                                                Button(
                                                    onClick = { onExportSingleVisit(visit.uuid) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = Color.White
                                                    ),
                                                    shape = RoundedCornerShape(24.dp), // Pill
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PictureAsPdf,
                                                        contentDescription = "Imprimir Visita PDF",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Visita PDF",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        if (visit.notes.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = visit.notes,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(8.dp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Display blocks in this visit
                                        if (visitBlocks.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "Sin contenido. Añade bloques usando las opciones de abajo.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }
                                        } else {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                visitBlocks.forEach { block ->
                                                    RenderSingleBlock(block)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Quick insert controls for THIS visit
                                        Text(
                                            "AÑADIR BLOQUE A ESTA VISITA:",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AssistChip(
                                                onClick = { onAddTextBlock("Nueva nota de visita", visit.uuid) },
                                                label = { Text("Nota", fontSize = 11.sp) },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            )
                                            AssistChip(
                                                onClick = {
                                                    targetVisitIdForImage = visit.uuid
                                                    showImagePicker = true
                                                },
                                                label = { Text("Fotos", fontSize = 11.sp) },
                                                leadingIcon = { Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            )
                                            AssistChip(
                                                onClick = { onAddSignatureClick(visit.uuid) },
                                                label = { Text("Firma", fontSize = 11.sp) },
                                                leadingIcon = { Icon(Icons.Default.Gesture, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            )
                                            AssistChip(
                                                onClick = { onAddTitleBlock("Subtítulo de Visita", visit.uuid) },
                                                label = { Text("Título", fontSize = 11.sp) },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Subject, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            )
                                            AssistChip(
                                                onClick = { onAddTableBlock(visit.uuid) },
                                                label = { Text("Tabla", fontSize = 11.sp) },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            )
                                            AssistChip(
                                                onClick = { onAddChecklistBlock(visit.uuid) },
                                                label = { Text("Checklist", fontSize = 11.sp) },
                                                leadingIcon = { Icon(Icons.Default.CheckBox, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            )
                                            AssistChip(
                                                onClick = { onAddChecklistTableBlock(visit.uuid) },
                                                label = { Text("Ch. Tabla", fontSize = 11.sp) },
                                                leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    PlatformColumnScrollbar(
                        state = visitsScrollState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 2.dp)
                    )
                }
            }
            } // Closes Box

            // Dialogs for managing visits
            if (showCreateVisitDialog) {
                AlertDialog(
                    onDismissRequest = { showCreateVisitDialog = false },
                    title = { Text("Crear Nueva Visita", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = visitTitleInput,
                                onValueChange = { visitTitleInput = it },
                                label = { Text("Título de Visita") },
                                placeholder = { Text("Ej. Visita de Cimentación") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = visitNotesInput,
                                onValueChange = { visitNotesInput = it },
                                label = { Text("Notas / Observaciones") },
                                placeholder = { Text("Describe brevemente el estado de la obra...") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 4
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Text(
                                text = "Instanciar plantilla para esta visita:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            // Option 1: Empty
                            TemplateOptionCard(
                                title = "Visita Vacía",
                                description = "Agregar visita sin secciones predefinidas.",
                                icon = Icons.Default.Add,
                                isSelected = selectedVisitTemplate == "NONE",
                                onClick = { selectedVisitTemplate = "NONE" }
                            )
                            
                            // Option 2: Direcciones de Obra
                            TemplateOptionCard(
                                title = "Dirección de Obra",
                                description = "Asistentes de obra, estado actual de los trabajos, reportaje fotográfico y firmas.",
                                icon = Icons.AutoMirrored.Filled.Assignment,
                                isSelected = selectedVisitTemplate == "DIRECCION_OBRA",
                                onClick = { selectedVisitTemplate = "DIRECCION_OBRA" }
                            )
                            
                            // Option 3: Coordinación de Seguridad y Salud
                            TemplateOptionCard(
                                title = "Coordinación de Seguridad y Salud",
                                description = "Tabla de control de acceso, checklist completo de seguridad colectiva/EPIs y medidas correctivas.",
                                icon = Icons.Default.Security,
                                isSelected = selectedVisitTemplate == "COORDINACION_CSS",
                                onClick = { selectedVisitTemplate = "COORDINACION_CSS" }
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (visitTitleInput.isNotBlank()) {
                                    onAddVisit(visitTitleInput.trim(), visitNotesInput.trim(), selectedVisitTemplate)
                                    showCreateVisitDialog = false
                                }
                            },
                            enabled = visitTitleInput.isNotBlank()
                        ) {
                            Text("Crear")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateVisitDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            if (visitToEdit != null) {
                AlertDialog(
                    onDismissRequest = { visitToEdit = null },
                    title = { Text("Editar Visita", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = visitTitleInput,
                                onValueChange = { visitTitleInput = it },
                                label = { Text("Título de Visita") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = visitNotesInput,
                                onValueChange = { visitNotesInput = it },
                                label = { Text("Notas / Observaciones") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 4
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                visitToEdit?.let { current ->
                                    if (visitTitleInput.isNotBlank()) {
                                        onUpdateVisit(current.copy(title = visitTitleInput.trim(), notes = visitNotesInput.trim()))
                                        visitToEdit = null
                                    }
                                }
                            },
                            enabled = visitTitleInput.isNotBlank()
                        ) {
                            Text("Guardar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { visitToEdit = null }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            if (visitToDeleteConfirm != null) {
                AlertDialog(
                    onDismissRequest = { visitToDeleteConfirm = null },
                    title = { Text("¿Eliminar esta visita?", fontWeight = FontWeight.Bold) },
                    text = { Text("Se eliminará esta visita permanentemente junto con todo su contenido asociado.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                visitToDeleteConfirm?.let { current ->
                                    onDeleteVisit(current)
                                }
                                visitToDeleteConfirm = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Eliminar", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { visitToDeleteConfirm = null }) {
                            Text("Cancelar")
                        }
                    }
                )
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
    block: BlockData,
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
            .testTag("block_item_${block.uuid}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Block Type Badge & Action Controls (Adaptive for Half Width space constraints)
            val (icon, typeLabel, badgeColor) = when (block.type) {
                BlockType.TEXT.name -> Triple(Icons.AutoMirrored.Filled.Notes, "OBSERVACIONES", MaterialTheme.colorScheme.primary)
                BlockType.IMAGE.name -> Triple(Icons.Default.Photo, "REGISTRO FOTOGRÁFICO", MaterialTheme.colorScheme.secondary)
                BlockType.SIGNATURE.name -> Triple(Icons.Default.Draw, "FIRMA DE VALIDACIÓN", MaterialTheme.colorScheme.tertiary)
                BlockType.TITLE.name -> Triple(Icons.AutoMirrored.Filled.Subject, "TÍTULO DE SECCIÓN", MaterialTheme.colorScheme.primary)
                BlockType.FOOTER.name -> Triple(Icons.Default.Info, "NOTAS AL PIE", MaterialTheme.colorScheme.outline)
                BlockType.TABLE.name -> Triple(Icons.AutoMirrored.Filled.List, "TABLA DE DATOS", MaterialTheme.colorScheme.primary)
                BlockType.CHECKLIST.name -> Triple(Icons.Default.CheckBox, "CHECKLIST / TAREAS", MaterialTheme.colorScheme.secondary)
                BlockType.CHECKLIST_TABLE.name -> Triple(Icons.Default.GridOn, "TABLA DE CHEQUEO", MaterialTheme.colorScheme.primary)
                else -> Triple(Icons.Default.Info, "DESCONOCIDO", MaterialTheme.colorScheme.outline)
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
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            
            when (block.type) {
                BlockType.TEXT.name -> {
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
                BlockType.TITLE.name -> {
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
                BlockType.FOOTER.name -> {
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
                BlockType.TABLE.name -> {
                    val adapter = moshi.adapter(TableBlockContent::class.java)
                    val content = remember(block.content) { try { adapter.fromJson(block.content) ?: TableBlockContent() } catch(e: Exception) { TableBlockContent() } }
                    
                    if (isEditing) {
                        TableEditorForm(
                            content = content,
                            onSave = { onSaveDirectEdit?.invoke(adapter.toJson(it)) },
                            onCancel = onCancelEdit
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onStartEdit() }
                        ) {
                            if (content.title.isNotBlank()) {
                                Text(
                                    text = content.title,
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            content.rows.forEachIndexed { rowIndex, cells ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (rowIndex == 0 && content.headers.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.White)
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    cells.forEach { cellText ->
                                        Text(
                                            text = cellText,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f),
                                            color = BrandGreySupport
                                        )
                                    }
                                }
                                if (rowIndex < content.rows.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
                BlockType.CHECKLIST.name -> {
                    val adapter = moshi.adapter(ChecklistBlockContent::class.java)
                    val content = remember(block.content) { try { adapter.fromJson(block.content) ?: ChecklistBlockContent() } catch(e: Exception) { ChecklistBlockContent() } }

                    if (isEditing) {
                        ChecklistEditorForm(
                            content = content,
                            onSave = { onSaveDirectEdit?.invoke(adapter.toJson(it)) },
                            onCancel = onCancelEdit
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().clickable { onStartEdit() }) {
                            if (content.title.isNotBlank()) {
                                Text(content.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            content.items.forEachIndexed { idx, item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = item.checked,
                                        onCheckedChange = { checked ->
                                            val newItems = content.items.toMutableList()
                                            newItems[idx] = item.copy(checked = checked)
                                            onSaveDirectEdit?.invoke(adapter.toJson(content.copy(items = newItems)))
                                        }
                                    )
                                    Text(item.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                BlockType.CHECKLIST_TABLE.name -> {
                    val adapter = moshi.adapter(ChecklistTableBlockContent::class.java)
                    val content = remember(block.content) { try { adapter.fromJson(block.content) ?: ChecklistTableBlockContent() } catch(e: Exception) { ChecklistTableBlockContent() } }

                    if (isEditing) {
                        ChecklistTableEditorForm(
                            content = content,
                            onSave = { onSaveDirectEdit?.invoke(adapter.toJson(it)) },
                            onCancel = onCancelEdit
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onStartEdit() }
                        ) {
                            if (content.title.isNotBlank()) {
                                Text(content.title, modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            // Header
                            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(0.2f)).padding(8.dp)) {
                                Text("Comprobación", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                content.headers.forEach { h ->
                                    Text(h, modifier = Modifier.width(35.dp), textAlign = TextAlign.Center, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            // Rows
                            content.rows.forEachIndexed { rowIdx, row ->
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.text, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                    content.headers.forEachIndexed { colIdx, _ ->
                                        RadioButton(
                                            selected = row.selectedIndex == colIdx,
                                            onClick = {
                                                val newRows = content.rows.toMutableList()
                                                newRows[rowIdx] = row.copy(selectedIndex = if (row.selectedIndex == colIdx) -1 else colIdx)
                                                onSaveDirectEdit?.invoke(adapter.toJson(content.copy(rows = newRows)))
                                            },
                                            modifier = Modifier.size(35.dp)
                                        )
                                    }
                                }
                                if (rowIdx < content.rows.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
                BlockType.IMAGE.name -> {
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
                BlockType.SIGNATURE.name -> {
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
fun PlatformLazyColumnWithScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Box(modifier = modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content
        )
        PlatformLazyColumnScrollbar(
            state = state,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 2.dp)
        )
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
