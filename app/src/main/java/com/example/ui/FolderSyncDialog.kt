package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.FolderSyncConfig
import com.example.data.SyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSyncDialog(
    config: FolderSyncConfig?,
    state: SyncState,
    onSaveConfig: (String, Boolean) -> Unit,
    onRunSync: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onResetState: () -> Unit
) {
    val context = LocalContext.current
    val currentUriStr = config?.rootFolderUri ?: ""
    val isAutoSync = config?.isAutoSync ?: false

    // State of the dialog (configuring versus showing main syncing engine UI)
    var isConfiguringPath by remember { mutableStateOf(currentUriStr.isEmpty()) }

    // Launcher for Android's modern Storage Access Framework folder selection
    val folderSelectorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                onSaveConfig(uri.toString(), isAutoSync)
                isConfiguringPath = false
                Toast.makeText(context, "✓ ¡Carpeta vinculada con éxito!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar permisos: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Dialog(
        onDismissRequest = { if (state !is SyncState.Syncing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FolderShared,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isConfiguringPath) "Configurar Carpeta Compartida" else "Sincronización Local & Nube",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (state !is SyncState.Syncing) {
                        IconButton(onClick = {
                            if (isConfiguringPath && currentUriStr.isNotEmpty()) {
                                isConfiguringPath = false
                            } else {
                                onDismiss()
                            }
                        }) {
                            Icon(
                                imageVector = if (isConfiguringPath && currentUriStr.isNotEmpty()) Icons.Default.ArrowBack else Icons.Default.Close,
                                contentDescription = "Cerrar"
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isConfiguringPath) {
                        // Configuration Flow Screen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Para eliminar integraciones con plataformas de terceros (como GitHub) y ganar simplicidad, " +
                                       "ahora puedes sincronizar los reportes e imágenes utilizando una carpeta local o compartida directamente en tu dispositivo.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )

                            // Informational card explaining Cloud providers integration
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Sincroniza con Google Drive / OneDrive",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Si eliges una carpeta dentro de tu Drive, OneDrive, Dropbox, etc. " +
                                                   "(mediante el selector que se abrirá), el propio sistema operativo " +
                                                   "sincronizará automáticamente los datos con tus otros dispositivos y el ordenador.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }

                            // Directory Selection Box
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Directorio Raíz Actual:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (currentUriStr.isNotEmpty()) {
                                    val friendlyPath = try {
                                        Uri.decode(Uri.parse(currentUriStr).lastPathSegment ?: "Carpeta Seleccionada")
                                    } catch (e: Exception) {
                                        "Carpeta Seleccionada"
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B), // Folder Orange
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = friendlyPath,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "URI: $currentUriStr",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Ninguna carpeta seleccionada",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "Debes seleccionar una carpeta raíz para continuar.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Button(
                                    onClick = { folderSelectorLauncher.launch(null) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("select_shared_folder_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DriveFileMove,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (currentUriStr.isNotEmpty()) "Cambiar Carpeta Compartida" else "Seleccionar Carpeta en tu Móvil",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Auto sync helper checkbox
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isAutoSync,
                                    onCheckedChange = { onSaveConfig(currentUriStr, it) },
                                    enabled = currentUriStr.isNotEmpty()
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Guardado automático continuo",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentUriStr.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                    Text(
                                        text = "Exporta automáticamente los reportes modificados.",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    } else {
                        // Main Synchronizer Status Screen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val friendlyPath = try {
                                Uri.decode(Uri.parse(currentUriStr).lastPathSegment ?: "Carpeta Seleccionada")
                            } catch (e: Exception) {
                                "Carpeta Seleccionada"
                            }

                            // Current Folder badge info card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderSpecial,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981), // Green Color
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = friendlyPath,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Vinculada correctamente para exportar cada obra en su propia carpeta.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // SYNC ENGINE STATUS / ACTIONS CONTROLLER
                            AnimatedVisibility(
                                visible = state is SyncState.Idle,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "La aplicación creará de forma inteligente una carpeta individual por proyecto/obra " +
                                               "dentro del directorio raiz sincronizado, guardando los datos estructurados en 'project_data.json' " +
                                               "y las imágenes o reportes fotográficos en la misma ruta.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 17.sp
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = { onRunSync(true) },
                                            modifier = Modifier.weight(1f).testTag("sync_execute_button"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Icon(Icons.Default.Sync, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Sincronizar Ahora", fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = { isConfiguringPath = true },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Settings, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Ajustes Carpeta", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Dynamic Live Progress Sync Section
                            AnimatedVisibility(
                                visible = state !is SyncState.Idle,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    when (state) {
                                        is SyncState.Syncing -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = state.step,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            LinearProgressIndicator(
                                                progress = { state.progress },
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                            )

                                            Text(
                                                text = state.log,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 15.sp,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        is SyncState.Success -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = "¡Sincronizado Correctamente!",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF047857)
                                                )
                                            }

                                            Text(
                                                text = state.summary,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 16.sp,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Button(
                                                onClick = { onResetState() },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            ) {
                                                Text("Entendido / Volver", fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        is SyncState.Error -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Error,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = "Fallo de Sincronización",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }

                                            Text(
                                                text = state.message,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.error,
                                                lineHeight = 16.sp,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Button(
                                                    onClick = { onRunSync(true) },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Reintentar", fontWeight = FontWeight.Bold)
                                                }
                                                OutlinedButton(
                                                    onClick = { onResetState() },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Cancelar", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        else -> {}
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}
