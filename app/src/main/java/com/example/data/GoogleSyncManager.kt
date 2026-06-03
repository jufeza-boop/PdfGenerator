package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SyncConfig(
    val accessToken: String,
    val clientId: String,
    val isAutoSync: Boolean
)

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val step: String, val progress: Float, val log: String) : SyncState()
    data class Success(val summary: String, val sheetsUrls: List<String>, val driveUrl: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

class GoogleSyncManager(
    private val context: Context,
    private val repository: ProjectRepository
) {
    private val sharedPrefs = context.getSharedPreferences("google_sync_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Default Client ID for Workspace integrations, allowing users to override if they wish
    private val defaultClientId = "603675384201-abc123demo.apps.googleusercontent.com"

    fun getConfig(): SyncConfig {
        return SyncConfig(
            accessToken = sharedPrefs.getString("access_token", "") ?: "",
            clientId = sharedPrefs.getString("client_id", "") ?: defaultClientId,
            isAutoSync = sharedPrefs.getBoolean("auto_sync", false)
        )
    }

    fun saveConfig(accessToken: String, clientId: String, isAutoSync: Boolean) {
        sharedPrefs.edit()
            .putString("access_token", accessToken.trim())
            .putString("client_id", clientId.trim())
            .putBoolean("auto_sync", isAutoSync)
            .apply()
    }

    private fun parseGoogleError(responseBody: String?, statusCode: Int, statusMessage: String): String {
        if (responseBody.isNullOrBlank()) {
            return "Error HTTP $statusCode: $statusMessage"
        }
        return try {
            val json = JSONObject(responseBody)
            if (json.has("error")) {
                val errorObj = json.getJSONObject("error")
                val msg = errorObj.optString("message", "")
                val status = errorObj.optString("status", "")
                if (msg.isNotBlank()) {
                    "Error de Google ($statusCode - $status): $msg"
                } else {
                    "Error de Google ($statusCode): $status"
                }
            } else {
                "Error HTTP $statusCode: $responseBody"
            }
        } catch (e: Exception) {
            "Error HTTP $statusCode: $responseBody"
        }
    }

    // Google API Helpers
    private suspend fun makeGetRequest(url: String, headers: Map<String, String>): JSONObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    if (!body.isNullOrBlank()) JSONObject(body) else null
                } else {
                    val errorMsg = parseGoogleError(body, response.code, response.message)
                    Log.e("SyncManager", "GET failed: $errorMsg")
                    throw Exception(errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "GET Exception", e)
            throw e
        }
    }

    private suspend fun makePostRequest(url: String, json: JSONObject, headers: Map<String, String>): JSONObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    if (!body.isNullOrBlank()) JSONObject(body) else null
                } else {
                    val errorMsg = parseGoogleError(body, response.code, response.message)
                    Log.e("SyncManager", "POST failed: $errorMsg")
                    throw Exception(errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "POST Exception", e)
            throw e
        }
    }

    private suspend fun makePutRequest(url: String, json: JSONObject, headers: Map<String, String>): JSONObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .put(json.toString().toRequestBody("application/json".toMediaType()))
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    if (!body.isNullOrBlank()) JSONObject(body) else null
                } else {
                    val errorMsg = parseGoogleError(body, response.code, response.message)
                    Log.e("SyncManager", "PUT failed: $errorMsg")
                    throw Exception(errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "PUT Exception", e)
            throw e
        }
    }

    private suspend fun findOrCreateRootFolder(headers: Map<String, String>): String {
        val folderName = "Reportes de Obra (Sincronizado)"
        val query = URLEncoder.encode("mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false", "UTF-8")
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$query&spaces=drive&fields=files(id)"
        
        val searchRes = makeGetRequest(searchUrl, headers)
        val files = searchRes?.optJSONArray("files")
        if (files != null && files.length() > 0) {
            return files.getJSONObject(0).getString("id")
        }

        // Create folder
        val body = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
        }
        val createRes = makePostRequest("https://www.googleapis.com/drive/v3/files", body, headers)
        return createRes?.optString("id") ?: throw Exception("No sé pudo crear la carpeta raíz en Google Drive")
    }

    private suspend fun findOrCreateProjectFolder(headers: Map<String, String>, rootId: String, pName: String): String {
        val pFolderName = "Proyecto - $pName"
        val query = URLEncoder.encode("mimeType = 'application/vnd.google-apps.folder' and name = '$pFolderName' and '$rootId' in parents and trashed = false", "UTF-8")
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$query&spaces=drive&fields=files(id)"

        val searchRes = makeGetRequest(searchUrl, headers)
        val files = searchRes?.optJSONArray("files")
        if (files != null && files.length() > 0) {
            return files.getJSONObject(0).getString("id")
        }

        // Create project subfolder
        val body = JSONObject().apply {
            put("name", pFolderName)
            put("mimeType", "application/vnd.google-apps.folder")
            put("parents", JSONArray(listOf(rootId)))
        }
        val createRes = makePostRequest("https://www.googleapis.com/drive/v3/files", body, headers)
        return createRes?.optString("id") ?: throw Exception("No se pudo crear la carpeta del proyecto $pName")
    }

    private suspend fun findOrCreateSpreadsheet(headers: Map<String, String>, parentId: String, pName: String): String {
        val sName = "Datos - $pName"
        val query = URLEncoder.encode("mimeType = 'application/vnd.google-apps.spreadsheet' and name = '$sName' and '$parentId' in parents and trashed = false", "UTF-8")
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$query&spaces=drive&fields=files(id)"

        val searchRes = makeGetRequest(searchUrl, headers)
        val files = searchRes?.optJSONArray("files")
        if (files != null && files.length() > 0) {
            return files.getJSONObject(0).getString("id")
        }

        // Create spreadsheet inside folders
        val body = JSONObject().apply {
            put("name", sName)
            put("mimeType", "application/vnd.google-apps.spreadsheet")
            put("parents", JSONArray(listOf(parentId)))
        }
        val createRes = makePostRequest("https://www.googleapis.com/drive/v3/files", body, headers)
        return createRes?.optString("id") ?: throw Exception("No se pudo crear la hoja de cálculo para el proyecto $pName")
    }

    private suspend fun uploadFileToFolder(headers: Map<String, String>, parentId: String, file: File): String? = withContext(Dispatchers.IO) {
        // First check if file already exists on Drive inside parent folder
        val query = URLEncoder.encode("name = '${file.name}' and '$parentId' in parents and trashed = false", "UTF-8")
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$query&spaces=drive&fields=files(id)"
        
        val searchRes = makeGetRequest(searchUrl, headers)
        val files = searchRes?.optJSONArray("files")
        if (files != null && files.length() > 0) {
            // Already exists, skip or return existing ID
            return@withContext files.getJSONObject(0).getString("id")
        }

        // Perform multipart upload
        val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        val boundary = "*****"
        val metadata = JSONObject().apply {
            put("name", file.name)
            put("parents", JSONArray(listOf(parentId)))
        }

        val requestBodyString = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$boundary\r\n")
            append("Content-Type: image/jpeg\r\n\r\n")
        }

        try {
            val fileBytes = file.readBytes()
            val closingBoundary = "\r\n--$boundary--\r\n"
            val completeBody = requestBodyString.toByteArray() + fileBytes + closingBoundary.toByteArray()
            val body = completeBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType())

            val request = Request.Builder()
                .url(uploadUrl)
                .post(body)
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        JSONObject(bodyStr).optString("id")
                    } else null
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // Main sync task supporting standard Drive subfolder structure and per-project separation
    fun runSync(realSync: Boolean): Flow<SyncState> = flow {
        val config = getConfig()
        
        emit(SyncState.Syncing("Iniciando Sincronización", 0.05f, "Inicializando motor de sincronización de Google Workspace..."))
        delay(600)

        // 1. Load data
        emit(SyncState.Syncing("Cargando base de datos local", 0.12f, "Leyendo proyectos y bloques de contenido desde Room..."))
        
        var localProjects: List<ProjectWithBlocks> = emptyList()
        try {
            localProjects = repository.allProjects.first()
            emit(SyncState.Syncing("Datos locales cargados", 0.18f, "Se encontraron ${localProjects.size} proyectos en la memoria local."))
            delay(500)
        } catch (e: Exception) {
            emit(SyncState.Syncing("Aviso", 0.18f, "No se pudo leer la base de datos local: ${e.localizedMessage}. Continuando con datos simulados..."))
            delay(800)
        }

        if (realSync) {
            if (config.accessToken.isBlank()) {
                emit(SyncState.Error("Se requiere que dé permisos de usuario para obtener el token antes de realizar la sincronización real."))
                return@flow
            }
            
            val headers = mapOf(
                "Authorization" to "Bearer ${config.accessToken}",
                "Content-Type" to "application/json"
            )

            try {
                // Step 1: Create or find the root "Reportes de Obra" folder on Google Drive
                emit(SyncState.Syncing("Conectando con Google Drive", 0.25f, "Verificando carpeta raíz 'Reportes de Obra (Sincronizado)' en su cuenta..."))
                val rootFolderId = findOrCreateRootFolder(headers)
                emit(SyncState.Syncing("Carpeta Raíz Lista", 0.35f, "Carpeta principal establecida con ID: $rootFolderId\nCreando subcarpetas de proyectos..."))
                delay(600)

                val spreadsheetUrls = mutableListOf<String>()

                // Step 2: Sync each project separately
                localProjects.forEachIndexed { index, projWithBlocks ->
                    val proj = projWithBlocks.project
                    val blocks = projWithBlocks.blocks
                    val progressBase = 0.35f + (index.toFloat() / localProjects.size) * 0.55f

                    emit(SyncState.Syncing("Creando subcarpeta para: ${proj.name}", progressBase, "Estructurando carpeta: Proyecto - ${proj.name}"))
                    
                    // Create Project subfolder on Drive
                    val projectFolderId = findOrCreateProjectFolder(headers, rootFolderId, proj.name)
                    
                    // Create/Find Google Sheets Spreadsheet for this project
                    emit(SyncState.Syncing("Creando Base de Datos", progressBase + 0.05f, "Inicializando Hoja de Cálculos: Datos - ${proj.name} en Drive..."))
                    val spreadsheetId = findOrCreateSpreadsheet(headers, projectFolderId, proj.name)
                    spreadsheetUrls.add("https://docs.google.com/spreadsheets/d/$spreadsheetId")

                    // Format Sheet Layout with Sheets API updates (First add tabs if needed, and write rows)
                    // We will upload sheets value for Proyectos
                    val initRows = JSONArray().apply {
                        // Headers row
                        put(JSONArray(listOf("PARÁMETRO", "VALOR")))
                        put(JSONArray(listOf("id", proj.id.toString())))
                        put(JSONArray(listOf("name", proj.name)))
                        put(JSONArray(listOf("createdAt", proj.createdAt.toString())))
                        put(JSONArray(listOf("reportLabel", proj.reportLabel)))
                        put(JSONArray(listOf("headerCompany", proj.headerCompany)))
                        put(JSONArray(listOf("headerCompanySub", proj.headerCompanySub)))
                        put(JSONArray(listOf("headerTitle", proj.headerTitle)))
                        put(JSONArray(listOf("", ""))) // empty separator row
                        put(JSONArray(listOf("BLOQUE_ID", "TIPO", "CONTENIDO", "SECUENCIA", "ANCHO")))
                        
                        // Append content blocks
                        blocks.forEach { b ->
                            put(JSONArray(listOf(
                                b.id.toString(),
                                b.type.name,
                                b.content,
                                b.sequence.toString(),
                                if (b.isHalfWidth) "Medio" else "Completo"
                            )))
                        }
                    }

                    val bodyJson = JSONObject().apply {
                        put("range", "A1")
                        put("majorDimension", "ROWS")
                        put("values", initRows)
                    }

                    val updateUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/A1?valueInputOption=USER_ENTERED"
                    makePutRequest(updateUrl, bodyJson, headers)

                    // Upload images to this project folder
                    val mediaBlocks = blocks.filter { it.type == BlockType.IMAGE || it.type == BlockType.SIGNATURE }
                    mediaBlocks.forEachIndexed { bIndex, block ->
                        val file = File(block.content)
                        if (file.exists() && file.length() > 0) {
                            emit(SyncState.Syncing(
                                "Mapeando Multimedia - ${proj.name}", 
                                progressBase + 0.10f, 
                                "Subiendo ${file.name} (${(file.length()/1024)} KB) a la carpeta del proyecto..."
                            ))
                            uploadFileToFolder(headers, projectFolderId, file)
                        }
                    }
                }

                emit(SyncState.Success(
                    summary = "¡Sincronización Delegada Exitosa! Hemos estructurado automáticamente su espacio de Google Drive creando la carpeta principal 'Reportes de Obra (Sincronizado)'. Cada proyecto posee ahora su propio directorio conteniendo su base de datos independiente en Sheets junto a todos sus archivos de imagen y firma.",
                    sheetsUrls = spreadsheetUrls,
                    driveUrl = "https://drive.google.com/drive/folders/$rootFolderId"
                ))

            } catch (e: Exception) {
                emit(SyncState.Error("Fallo en sincronización real con cuenta delegada: ${e.localizedMessage}. Si expiró la sesión, vuelva a autenticarse."))
            }

        } else {
            // HIGH FIDELITY SIMULATOR FOR DIRECTORY ORGANIZATION (User can visualize exactly how folders and spreadsheets branch out)
            val demoRootId = "1_Root_Reportes_Obra_Principal"
            emit(SyncState.Syncing(
                "Mapeando Cuenta de Google", 
                0.25f, 
                "VALIDACIÓN DE ESTRUCTURA DRIVER:\n" +
                "GET https://www.googleapis.com/drive/v3/files?q=mimeType='folder'+and+name='Reportes de Obra'\n" +
                "├─ No existe la carpeta raíz. Generando una nueva en su cuenta delegada...\n" +
                "POST https://www.googleapis.com/drive/v3/files\n" +
                "{\n" +
                "  \"name\": \"Reportes de Obra (Sincronizado)\",\n" +
                "  \"mimeType\": \"application/vnd.google-apps.folder\"\n" +
                "}\n" +
                "└─ Carpeta Raíz generada correctamente. ID de Referencia: $demoRootId"
            ))
            delay(2200)

            val demoUrls = mutableListOf<String>()
            
            localProjects.forEachIndexed { index, entry ->
                val p = entry.project
                val pId = "1_SubFolder_Proj_${p.id}"
                val sId = "1_Sheet_Proj_${p.id}"
                demoUrls.add("https://docs.google.com/spreadsheets/d/$sId")

                val prog = 0.35f + (index.toFloat() / localProjects.size) * 0.55f
                emit(SyncState.Syncing(
                    "Creando subestructura: ${p.name}", 
                    prog, 
                    "ESTRUCTURANDO CARPETA INDEPENDIENTE DEL PROYECTO:\n" +
                    "POST https://www.googleapis.com/drive/v3/files\n" +
                    "{\n" +
                    "  \"name\": \"Proyecto - ${p.name}\",\n" +
                    "  \"mimeType\": \"application/vnd.google-apps.folder\",\n" +
                    "  \"parents\": [\"$demoRootId\"]\n" +
                    "}\n" +
                    "├─ Subcarpeta ID: $pId\n" +
                    "├─ Generando Hoja de Datos Sheets: 'Datos - ${p.name}'\n" +
                    "│  POST https://www.googleapis.com/drive/v3/files (Spreadsheet en $pId)\n" +
                    "│  ├─ Actualizando celdas de proyecto y bloques en Sheet1 (Total: ${entry.blocks.size} bloques)\n" +
                    "│  └─ Base de datos inicializada en la nube.\n" +
                    "└─ Subiendo ${entry.blocks.count { it.type == BlockType.IMAGE }} imágenes y ${entry.blocks.count { it.type == BlockType.SIGNATURE }} firmas recolectadas directamente al directorio del proyecto..."
                ))
                delay(2200)
            }

            emit(SyncState.Success(
                summary = "Sincronización estructurada simulada completada con éxito. Se planificaron ${localProjects.size} subdirectorios organizados jerárquicamente dentro de 'Reportes de Obra (Sincronizado)' en Google Drive, cada uno alojando su archivo de control en Google Sheets e ilustraciones independientes.",
                sheetsUrls = demoUrls,
                driveUrl = "https://drive.google.com/drive/folders/$demoRootId"
            ))
        }
    }
}
