package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SyncConfig
import com.example.data.SyncState
import com.example.viewmodel.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSyncDialog(
    config: SyncConfig?,
    state: SyncState,
    onSaveConfig: (String, String, String, Boolean) -> Unit,
    onRunSync: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onResetState: () -> Unit
) {
    val context = LocalContext.current
    
    var accessToken by remember(config) { mutableStateOf(config?.accessToken ?: "") }
    var spreadsheetId by remember(config) { mutableStateOf(config?.spreadsheetId ?: "") }
    var folderId by remember(config) { mutableStateOf(config?.folderId ?: "") }
    var isAutoSync by remember(config) { mutableStateOf(config?.isAutoSync ?: false) }

    var showConfigHelp by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (state !is SyncState.Syncing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
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
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sincronización en la Nube",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (state !is SyncState.Syncing) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (state) {
                        is SyncState.Idle -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Conecta tus reportes de obra con Google Workspace. Los datos estructurales se guardarán en Google Sheets para compartirse con clientes Web o Windows, y las fotos o firmas se subirán a Google Drive.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp
                                )

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { showConfigHelp = !showConfigHelp }
                                        ) {
                                            Icon(
                                                imageVector = if (showConfigHelp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "¿Cómo configurar la sincronización?",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }

                                        if (showConfigHelp) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "1. Cree un Google Sheet en su cuenta y copie su ID desde la barra de direcciones (es la cadena larga entre /d/ y /edit).\n\n" +
                                                       "2. Cree una carpeta en Google Drive para guardar las imágenes y copie su ID de la URL.\n\n" +
                                                       "3. Si desea realizar sincronización real, ingrese su Token de Acceso de Google OAuth. Alternativamente, presione el botón \"Simular Sincronización\" para visualizar cómo opera el flujo completo.",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "PARÁMETROS DE CONEXIÓN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )

                                OutlinedTextField(
                                    value = spreadsheetId,
                                    onValueChange = { spreadsheetId = it },
                                    label = { Text("ID de Google Sheet (Spreadsheet ID)") },
                                    placeholder = { Text("ej. 1X8B_Sheets_Base_Demo_Id...") },
                                    modifier = Modifier.fillMaxWidth().testTag("spreadsheet_id_input"),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = folderId,
                                    onValueChange = { folderId = it },
                                    label = { Text("ID de Carpeta Google Drive (Folder ID)") },
                                    placeholder = { Text("ej. 1_Drive_Multimedia_Folder_Demo_Id...") },
                                    modifier = Modifier.fillMaxWidth().testTag("folder_id_input"),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                                )

                                OutlinedTextField(
                                    value = accessToken,
                                    onValueChange = { accessToken = it },
                                    label = { Text("Google OAuth Token de Acceso (Opcional)") },
                                    placeholder = { Text("Pegue su access_token para pruebas reales...") },
                                    modifier = Modifier.fillMaxWidth().testTag("auth_token_input"),
                                    leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Action Buttons for Idle Mode
                                Button(
                                    onClick = {
                                        onSaveConfig(accessToken, spreadsheetId, folderId, isAutoSync)
                                        onRunSync(true) // Real mode
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("start_real_sync_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.SyncAlt, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sincronizar Datos Reales (Con OAuth)")
                                }

                                OutlinedButton(
                                    onClick = {
                                        onSaveConfig(accessToken, spreadsheetId, folderId, isAutoSync)
                                        onRunSync(false) // Demo/Simulation mode
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("start_demo_sync_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Dvr, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Presenciar Simulación de Integración")
                                }
                            }
                        }
                        is SyncState.Syncing -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = state.step,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Sincronizando... ${(state.progress * 100).toInt()}%",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Log frame
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(Color.Black, RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                                        .padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = state.log,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color.Green,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                        is SyncState.Success -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(72.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "¡Excelente!",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = state.summary,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "ENLACES DE ACCESO DE CONEXIÓN",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.sheetsUrl))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF22C55E),
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(Icons.Default.GridOn, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Abrir Google Sheet")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.driveUrl))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Ver Carpeta Google Drive")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        onResetState()
                                        onDismiss()
                                    },
                                    modifier = Modifier.fillMaxWidth(0.6f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Aceptar y Salir")
                                }
                            }
                        }
                        is SyncState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(72.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Ocurrió un Detalle",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = state.message,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = onResetState,
                                    modifier = Modifier.fillMaxWidth(0.6f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reintentar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
