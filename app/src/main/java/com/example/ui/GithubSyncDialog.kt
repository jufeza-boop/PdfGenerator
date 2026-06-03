package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import com.example.data.SyncConfig
import com.example.data.SyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GithubSyncDialog(
    config: SyncConfig?,
    state: SyncState,
    onSaveConfig: (String, String, String, String, Boolean) -> Unit,
    onRunSync: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onResetState: () -> Unit
) {
    val context = LocalContext.current
    
    var token by remember(config) { mutableStateOf(config?.githubToken ?: "") }
    var owner by remember(config) { mutableStateOf(config?.githubOwner ?: "") }
    var repo by remember(config) { mutableStateOf(config?.githubRepo ?: "") }
    var branch by remember(config) { mutableStateOf(config?.githubBranch ?: "main") }
    var isAutoSync by remember(config) { mutableStateOf(config?.isAutoSync ?: false) }

    var isAddingConfig by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (state !is SyncState.Syncing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f)
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isAddingConfig) "Configurar Repositorio Git" else "Sincronizador Seguro GitHub",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (state !is SyncState.Syncing) {
                        IconButton(onClick = {
                            if (isAddingConfig) {
                                isAddingConfig = false
                            } else {
                                onDismiss()
                            }
                        }) {
                            Icon(
                                imageVector = if (isAddingConfig) Icons.Default.ArrowBack else Icons.Default.Close,
                                contentDescription = if (isAddingConfig) "Volver" else "Cerrar"
                            )
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
                    if (isAddingConfig) {
                        // Configuration & Guide Screeen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Configura tu repositorio de GitHub para sincronizar bases de datos locales y multimedia de manera descentralizada con versionado automático:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )

                            // Form Fields
                            OutlinedTextField(
                                value = token,
                                onValueChange = { token = it },
                                label = { Text("Token de Acceso Personal (PAT) de GitHub") },
                                placeholder = { Text("ghp_xxxx...") },
                                leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth().testTag("github_token_input"),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = owner,
                                    onValueChange = { owner = it },
                                    label = { Text("Usuario/Organización") },
                                    placeholder = { Text("ej. juanelpipa") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    modifier = Modifier.weight(1f).testTag("github_owner_input"),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = repo,
                                    onValueChange = { repo = it },
                                    label = { Text("Repositorio") },
                                    placeholder = { Text("ej. obras-db") },
                                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                    modifier = Modifier.weight(1f).testTag("github_repo_input"),
                                    singleLine = true
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = branch,
                                    onValueChange = { branch = it },
                                    label = { Text("Rama (Branch)") },
                                    placeholder = { Text("main") },
                                    leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                                    modifier = Modifier.weight(1f).testTag("github_branch_input"),
                                    singleLine = true
                                )

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isAutoSync,
                                            onCheckedChange = { isAutoSync = it }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Auto Sincronización",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Interactive instructions to generate a Classic Token
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("¿Cómo crear tu Token Classic paso a paso?", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("1. Ve a GitHub.com ➔ Settings ➔ Developer Settings ➔ Personal Access Tokens ➔ Tokens (classic).", fontSize = 12.sp, lineHeight = 18.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("2. Genera un nuevo token con alcance (Scope) 'repo' (Full control of private and public repositories).", fontSize = 12.sp, lineHeight = 18.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("3. Crea el repositorio en tu perfil si aún no lo has hecho (ej. 'obras-db').", fontSize = 12.sp, lineHeight = 18.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new"))
                                            context.startActivity(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Ir directo a generar Token GitHub", fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (token.isBlank() || owner.isBlank() || repo.isBlank()) {
                                        Toast.makeText(context, "Completa todos los campos obligatorios.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onSaveConfig(token, owner, repo, branch, isAutoSync)
                                        Toast.makeText(context, "¡Configuración de GitHub Guardada!", Toast.LENGTH_SHORT).show()
                                        isAddingConfig = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Guardar y Aplicar Configuración")
                            }
                        }
                    } else {
                        // Standard Synchronizer Main Control Panel
                        when (state) {
                            is SyncState.Idle -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MergeType,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(40.dp)
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column {
                                                Text(
                                                    "Sincronización Inteligente Bidireccional",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    "Compara las timestamps local y remota (updatedAt) resolviendo conflictos al instante. Es ideal para modificar datos desde Windows, Web o la App sin duplicados.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }

                                    // Render Credentials Check State
                                    val hasCredentials = token.isNotBlank() && owner.isNotBlank() && repo.isNotBlank()
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (hasCredentials) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (hasCredentials) Icons.Default.CheckCircle else Icons.Default.Error,
                                                contentDescription = null,
                                                tint = if (hasCredentials) Color(0xFF16A34A) else Color(0xFFDC2626),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (hasCredentials) "Credenciales Configuradas" else "Sincronizador No Configurado",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = if (hasCredentials) Color(0xFF14532D) else Color(0xFF7F1D1D)
                                                )
                                                Text(
                                                    text = if (hasCredentials) "Repo: $owner/$repo (rama: $branch)" else "Presiona el botón para agregar el token Classic y ruta del repo.",
                                                    fontSize = 11.sp,
                                                    color = if (hasCredentials) Color(0xFF15803D) else Color(0xFFB91C1C)
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = { isAddingConfig = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (hasCredentials) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (hasCredentials) "Modificar Ajustes de GitHub" else "Configurar Acceso de GitHub")
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Action Area - Real sync or simulation
                                    Text(
                                        "Acciones de Consolidación:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Demo/Simulation Button
                                        Button(
                                            onClick = { onRunSync(false) },
                                            modifier = Modifier.weight(1f).height(54.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.FactCheck, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Modo Prueba", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("Ver simulador", fontSize = 10.sp)
                                            }
                                        }

                                        // Real Action Button
                                        Button(
                                            onClick = { onRunSync(true) },
                                            modifier = Modifier.weight(1.3f).height(54.dp),
                                            enabled = hasCredentials,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF2EA44F),
                                                disabledContainerColor = MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Sincronizar Ahora", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                                Text("Conexión real", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                                            }
                                        }
                                    }
                                }
                            }
                            is SyncState.Syncing -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(54.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 4.dp
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Text(
                                        text = state.step,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    LinearProgressIndicator(
                                        progress = { state.progress },
                                        modifier = Modifier.fillMaxWidth(0.85f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.outlineVariant
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().height(160.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(
                                                text = state.log,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(64.dp)
                                    )

                                    Text(
                                        text = "Consolidado Completado",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Text(
                                        text = state.summary,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            "Accesar Recursos Directos:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        // Button for Repository
                                        if (state.driveUrl.isNotBlank()) {
                                            Button(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.driveUrl))
                                                    context.startActivity(intent)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24292E)),
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.Terminal, contentDescription = null, tint = Color.White)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Ver Repositorio de GitHub", color = Color.White)
                                            }
                                        }

                                        // Render a button for each synced file
                                        state.sheetsUrls.forEachIndexed { idx, url ->
                                            OutlinedButton(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.InsertDriveFile, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Archivo Proyecto ${idx + 1} (.json)", fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            onResetState()
                                            onDismiss()
                                        },
                                        modifier = Modifier.fillMaxWidth(0.6f).height(46.dp),
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
                                        modifier = Modifier.size(64.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "Error en Sincronización",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = state.message,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Button(
                                        onClick = onResetState,
                                        modifier = Modifier.fillMaxWidth(0.6f).height(46.dp),
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
}
