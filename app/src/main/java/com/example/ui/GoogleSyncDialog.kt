package com.example.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SyncConfig
import com.example.data.SyncState
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSyncDialog(
    config: SyncConfig?,
    state: SyncState,
    onSaveConfig: (String, String, Boolean) -> Unit,
    onRunSync: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onResetState: () -> Unit
) {
    val context = LocalContext.current
    
    var accessToken by remember(config) { mutableStateOf(config?.accessToken ?: "") }
    var clientId by remember(config) { mutableStateOf(config?.clientId ?: "") }
    var isAutoSync by remember(config) { mutableStateOf(config?.isAutoSync ?: false) }

    var showConfigHelp by remember { mutableStateOf(false) }
    var isWebViewVisible by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (state !is SyncState.Syncing && !isWebViewVisible) onDismiss() },
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
                            imageVector = if (isWebViewVisible) Icons.Default.VpnKey else Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isWebViewVisible) "Autorizar Cuenta Google" else "Sincronización Delegada",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (state !is SyncState.Syncing && !isWebViewVisible) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    } else if (isWebViewVisible) {
                        IconButton(onClick = { isWebViewVisible = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
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
                    if (isWebViewVisible) {
                        // Integrated browser window for OAuth dynamic login
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                                    
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            if (url != null && url.startsWith("http://localhost")) {
                                                // Check for token in hash fragment/redirect parameter
                                                val fragment = Uri.parse(url).fragment
                                                if (!fragment.isNullOrBlank()) {
                                                    val params = fragment.split("&")
                                                    var tokenFound = ""
                                                    for (param in params) {
                                                        if (param.startsWith("access_token=")) {
                                                            tokenFound = param.substringAfter("access_token=")
                                                            break
                                                        }
                                                    }
                                                    if (tokenFound.isNotBlank()) {
                                                        accessToken = tokenFound
                                                        onSaveConfig(tokenFound, clientId, isAutoSync)
                                                        Toast.makeText(context, "¡Sesión iniciada con éxito!", Toast.LENGTH_LONG).show()
                                                        isWebViewVisible = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            update = { webView ->
                                val encodedScope = URLEncoder.encode("https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/spreadsheets", "UTF-8")
                                val oauthUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                                        "client_id=$clientId" +
                                        "&redirect_uri=http://localhost" +
                                        "&response_type=token" +
                                        "&scope=$encodedScope"
                                webView.loadUrl(oauthUrl)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        // Regular Sync States
                        when (state) {
                            is SyncState.Idle -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Conecta la aplicación directamente con tu cuenta de Google. El sistema creará de forma autónoma una carpeta principal en Google Drive. Cada proyecto tendrá su propia subcarpeta aislada con su respectivo informe estructurado en Google Sheets y todas sus imágenes asociadas.",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 20.sp
                                    )

                                    // Display authentication status banner
                                    if (accessToken.isNotBlank()) {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color(0xFFDCFCE7) // Solid subtle green background
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF16A34A),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Sesión de Google Autorizada",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = Color(0xFF14532D)
                                                    )
                                                    Text(
                                                        text = "Listo para iniciar la estructuración automática.",
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF15803D)
                                                    )
                                                }
                                                TextButton(
                                                    onClick = {
                                                        accessToken = ""
                                                        onSaveConfig("", clientId, isAutoSync)
                                                    }
                                                ) {
                                                    Text("Cerrar", color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    } else {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Default.AccountCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = "Requiere Autorización de Usuario",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Inicia sesión con Google para conceder permisos temporales de Drive y Sheets sin exponer tus claves.",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                    textAlign = TextAlign.Center,
                                                    lineHeight = 18.sp
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Button(
                                                    onClick = {
                                                        onSaveConfig(accessToken, clientId, isAutoSync)
                                                        isWebViewVisible = true
                                                    },
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Default.Login, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Iniciar Sesión con Google")
                                                }
                                            }
                                        }
                                    }

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
                                                    text = "Configuración Avanzada de OAuth Client ID",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }

                                            if (showConfigHelp) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = "Por defecto, la aplicación utiliza un Client ID seguro de desarrollo para simular y agilizar pruebas. Si desea conectar su propio proyecto de Google Cloud:\n\n" +
                                                           "1. Vaya a Google Cloud Console > APIs y Servicios > Credenciales.\n" +
                                                           "2. Cree una ID de cliente de OAuth 2.0 de tipo 'Aplicación web' o 'Instalada', y agregue 'http://localhost' como URI de redireccionamiento autorizado.\n" +
                                                           "3. Ingrese el ID de cliente generado en el campo de texto a continuación.",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    lineHeight = 18.sp
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                OutlinedTextField(
                                                    value = clientId,
                                                    onValueChange = { clientId = it },
                                                    label = { Text("Google Cloud OAuth Client ID") },
                                                    modifier = Modifier.fillMaxWidth().testTag("client_id_input"),
                                                    leadingIcon = { Icon(Icons.Default.SettingsApplications, contentDescription = null) }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Action Buttons for Idle Mode
                                    Button(
                                        onClick = {
                                            onSaveConfig(accessToken, clientId, isAutoSync)
                                            onRunSync(true) // Run Real Google Workspace Sync
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("start_real_sync_btn"),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = accessToken.isNotBlank()
                                    ) {
                                        Icon(Icons.Default.SyncAlt, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Crear Carpeta y Sincronizar (Real)")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onSaveConfig(accessToken, clientId, isAutoSync)
                                            onRunSync(false) // Run High-Fidelity Simulation
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("start_demo_sync_btn"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Dvr, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Simular Organización en Subcarpetas")
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
                                        text = "Creando Directorios y Hojas... ${(state.progress * 100).toInt()}%",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // Log terminal screen
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
                                        text = "¡Espacio Organizado!",
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

                                    // Directory Tree Visual Markup
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "ESTRUCTURA DE DIRECTORIOS LOGRADA",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                letterSpacing = 1.sp
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFEAB308), modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Reportes de Obra (Sincronizado) [Carpeta Raíz]", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                                state.sheetsUrls.forEachIndexed { i, _ ->
                                                    Column(modifier = Modifier.padding(start = 24.dp)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFEAB308), modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Proyecto ${i + 1} [Subcarpeta]", fontSize = 12.sp)
                                                        }
                                                        Row(modifier = Modifier.padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.GridOn, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Datos de Control (Google Sheet)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                        Row(modifier = Modifier.padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Fotos e Ilustraciones en Drive", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

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
                                                text = "ENLACES DE ACCESO EN DRIVE Y SHEETS",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            // Render a button for each Sheet created!
                                            state.sheetsUrls.forEachIndexed { idx, url ->
                                                Button(
                                                    onClick = {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                        context.startActivity(intent)
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF16A34A),
                                                        contentColor = Color.White
                                                    ),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Default.GridOn, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Hojas de Reporte Proyecto ${idx + 1}")
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.driveUrl))
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Abrir Carpeta Principal en Drive")
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
}
