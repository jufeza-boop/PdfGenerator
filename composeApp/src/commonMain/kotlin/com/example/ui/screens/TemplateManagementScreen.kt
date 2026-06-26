package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomTemplateData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateManagementScreen(
    customTemplates: List<CustomTemplateData>,
    onBack: () -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onRenameTemplate: (String, String) -> Unit,
    onEditTemplate: (String) -> Unit,
    onCreateVisitTemplate: (String) -> Unit
) {
    var activeTab by remember { mutableStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Mis Plantillas Personales", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Proyectos", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Visitas", fontWeight = FontWeight.Bold) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (activeTab == 1) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva Plantilla de Visita")
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val filteredTemplates = remember(customTemplates, activeTab) {
                customTemplates.filter { 
                    if (activeTab == 0) it.target == "PROJECT" else it.target == "VISIT" 
                }
            }

            if (filteredTemplates.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tienes plantillas en esta categoría", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTemplates, key = { it.uuid }) { template ->
                        TemplateListItem(
                            template = template,
                            onDelete = { onDeleteTemplate(it) },
                            onRename = { uuid, newName -> onRenameTemplate(uuid, newName) },
                            onEdit = { onEditTemplate(it) }
                        )
                    }
                }
            }
        }

        if (showCreateDialog) {
            var newTemplateName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Nueva Plantilla de Visita") },
                text = {
                    OutlinedTextField(
                        value = newTemplateName,
                        onValueChange = { newTemplateName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombre de la plantilla") }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newTemplateName.isNotBlank()) {
                                onCreateVisitTemplate(newTemplateName.trim())
                                showCreateDialog = false
                            }
                        }
                    ) {
                        Text("Crear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) { Text("Cancelar") }
                }
            )
        }
    }
}

@Composable
fun TemplateListItem(
    template: CustomTemplateData,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onEdit: (String) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(template.name) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                val targetText = if (template.target == "VISIT") "Plantilla de Visita" else "Plantilla de Proyecto"
                Text(
                    text = "$targetText • ${template.blocks.size} bloques",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { onEdit(template.uuid) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar Contenido", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Renombrar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar Plantilla") },
            text = { Text("¿Estás seguro de que deseas eliminar la plantilla '${template.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(template.uuid)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renombrar Plantilla") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRename(template.uuid, renameText.trim())
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
