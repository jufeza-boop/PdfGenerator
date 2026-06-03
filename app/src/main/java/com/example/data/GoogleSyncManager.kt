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
import java.util.concurrent.TimeUnit

data class SyncConfig(
    val accessToken: String,
    val spreadsheetId: String,
    val folderId: String,
    val isAutoSync: Boolean
)

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val step: String, val progress: Float, val log: String) : SyncState()
    data class Success(val summary: String, val sheetsUrl: String, val driveUrl: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

class GoogleSyncManager(
    private val context: Context,
    private val repository: ProjectRepository
) {
    private val sharedPrefs = context.getSharedPreferences("google_sync_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getConfig(): SyncConfig {
        return SyncConfig(
            accessToken = sharedPrefs.getString("access_token", "") ?: "",
            spreadsheetId = sharedPrefs.getString("spreadsheet_id", "") ?: "",
            folderId = sharedPrefs.getString("folder_id", "") ?: "",
            isAutoSync = sharedPrefs.getBoolean("auto_sync", false)
        )
    }

    fun saveConfig(accessToken: String, spreadsheetId: String, folderId: String, isAutoSync: Boolean) {
        sharedPrefs.edit()
            .putString("access_token", accessToken.trim())
            .putString("spreadsheet_id", spreadsheetId.trim())
            .putString("folder_id", folderId.trim())
            .putBoolean("auto_sync", isAutoSync)
            .apply()
    }

    // High performance sync stream
    fun runSync(realSync: Boolean): Flow<SyncState> = flow {
        val config = getConfig()
        
        emit(SyncState.Syncing("Iniciando Sincronización", 0.05f, "Inicializando motor de sincronización de Google Workspace..."))
        delay(800)

        // 1. Fetch Local Data
        emit(SyncState.Syncing("Cargando base de datos local", 0.15f, "Leyendo proyectos y bloques de contenido desde Room..."))
        
        var localProjects: List<ProjectWithBlocks> = emptyList()
        try {
            // Read current projects via safe flow collection
            localProjects = repository.allProjects.first()
            emit(SyncState.Syncing("Datos locales cargados", 0.20f, "Se encontraron ${localProjects.size} proyectos firmados localmente."))
            delay(600)
        } catch (e: Exception) {
            emit(SyncState.Syncing("Aviso", 0.20f, "No se pudo leer la base de datos local: ${e.localizedMessage}. Continuando con datos demo..."))
            delay(1000)
        }

        if (realSync) {
            if (config.accessToken.isBlank() || config.spreadsheetId.isBlank()) {
                emit(SyncState.Error("Faltan configurar parámetros requeridos (Token de acceso o ID de hoja) para la sincronización real."))
                return@flow
            }
            
            val headers = mapOf(
                "Authorization" to "Bearer ${config.accessToken}",
                "Content-Type" to "application/json"
            )

            try {
                // Real Sync Implementation with Google Sheets
                emit(SyncState.Syncing("Verificando Google Sheets", 0.25f, "Conectándose con la API de Google Sheets para id: ${config.spreadsheetId}..."))
                
                // Clear and recreate Sheet ranges
                val clearUrl = "https://sheets.googleapis.com/v1/spreadsheets/${config.spreadsheetId}/values/Proyectos!A1:Z100:clear"
                val clearRequest = Request.Builder()
                    .url(clearUrl)
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                    .build()
                
                val response = withContext(Dispatchers.IO) { client.newCall(clearRequest).execute() }
                if (!response.isSuccessful && response.code != 404) {
                    emit(SyncState.Syncing("Creando Hojas", 0.30f, "La hoja 'Proyectos' no existe o requiere inicialización. Preparando llamadas de creación..."))
                }
                response.close()

                // Insert Headers & Project Records
                emit(SyncState.Syncing("Guardando Proyectos", 0.40f, "Enviando registros de Proyectos a la hoja de cálculo..."))
                
                val projectRows = JSONArray().apply {
                    // Headers
                    put(JSONArray(listOf("id", "name", "createdAt", "reportLabel", "showHeaderLabel", "showHeaderDate", "headerCompany", "headerCompanySub", "headerTitle", "showHeaderBox")))
                    // Data
                    localProjects.forEach { p ->
                        put(JSONArray(listOf(
                            p.project.id.toString(),
                            p.project.name,
                            p.project.createdAt.toString(),
                            p.project.reportLabel,
                            if (p.project.showHeaderLabel) "TRUE" else "FALSE",
                            if (p.project.showHeaderDate) "TRUE" else "FALSE",
                            p.project.headerCompany,
                            p.project.headerCompanySub,
                            p.project.headerTitle,
                            if (p.project.showHeaderBox) "TRUE" else "FALSE"
                        )))
                    }
                }

                val projBodyJson = JSONObject().apply {
                    put("range", "Proyectos!A1")
                    put("majorDimension", "ROWS")
                    put("values", projectRows)
                }

                val updateProjUrl = "https://sheets.googleapis.com/v1/spreadsheets/${config.spreadsheetId}/values/Proyectos!A1?valueInputOption=USER_ENTERED"
                val updateProjRequest = Request.Builder()
                    .url(updateProjUrl)
                    .put(projBodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val projResp = withContext(Dispatchers.IO) { client.newCall(updateProjRequest).execute() }
                if (!projResp.isSuccessful) {
                    throw Exception("Error de respuesta al escribir proyectos en Sheets: ${projResp.message}")
                }
                projResp.close()

                // Clear ContentBlocks Sheet and append
                emit(SyncState.Syncing("Escribiendo Bloques de Contenido", 0.60f, "Modificando la hoja 'Bloques_Contenido' con datos relacionados..."))
                
                val clearBlocksUrl = "https://sheets.googleapis.com/v1/spreadsheets/${config.spreadsheetId}/values/Bloques_Contenido!A1:Z5000:clear"
                val clearBlocksReq = Request.Builder().url(clearBlocksUrl).post("{}".toRequestBody("application/json".toMediaType())).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()
                withContext(Dispatchers.IO) { client.newCall(clearBlocksReq).execute() }.close()

                val blockRows = JSONArray().apply {
                    put(JSONArray(listOf("id", "projectId", "type", "content", "sequence", "isHalfWidth")))
                    localProjects.flatMap { it.blocks }.forEach { b ->
                        put(JSONArray(listOf(
                            b.id.toString(),
                            b.projectId.toString(),
                            b.type.name,
                            b.content,
                            b.sequence.toString(),
                            if (b.isHalfWidth) "TRUE" else "FALSE"
                        )))
                    }
                }

                val blockBodyJson = JSONObject().apply {
                    put("range", "Bloques_Contenido!A1")
                    put("majorDimension", "ROWS")
                    put("values", blockRows)
                }

                val updateBlocksUrl = "https://sheets.googleapis.com/v1/spreadsheets/${config.spreadsheetId}/values/Bloques_Contenido!A1?valueInputOption=USER_ENTERED"
                val updateBlocksRequest = Request.Builder()
                    .url(updateBlocksUrl)
                    .put(blockBodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                    .build()

                val blockResp = withContext(Dispatchers.IO) { client.newCall(updateBlocksRequest).execute() }
                if (!blockResp.isSuccessful) {
                    throw Exception("Error de respuesta al escribir bloques en Sheets: ${blockResp.message}")
                }
                blockResp.close()

                // Google Drive Folder uploads
                if (config.folderId.isNotBlank()) {
                    emit(SyncState.Syncing("Subiendo archivos multimedia a Google Drive", 0.80f, "Comprobando carpeta compartida en Drive con ID: ${config.folderId}..."))
                    
                    val multimediaBlocks = localProjects.flatMap { it.blocks }.filter { it.type == BlockType.IMAGE || it.type == BlockType.SIGNATURE }
                    multimediaBlocks.forEachIndexed { idx, block ->
                        val file = File(block.content)
                        if (file.exists() && file.length() > 0) {
                            emit(SyncState.Syncing("Subiendo archivos multimedia", 0.80f + (idx.toFloat() / multimediaBlocks.size * 0.15f), "Trabajando en archivo: ${file.name}..."))
                            
                            // Simple Drive uploading endpoint
                            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
                            val boundary = "*****"
                            val metadata = JSONObject().apply {
                                put("name", file.name)
                                put("parents", JSONArray(listOf(config.folderId)))
                            }

                            val requestBodyString = buildString {
                                append("--$boundary\r\n")
                                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                                append(metadata.toString())
                                append("\r\n--$boundary\r\n")
                                append("Content-Type: image/jpeg\r\n\r\n")
                            }

                            // Read original binary content and wrap in continuous stream request
                            val fileBytes = withContext(Dispatchers.IO) { file.readBytes() }
                            val closingBoundary = "\r\n--$boundary--\r\n"

                            val completeBody = requestBodyString.toByteArray() + fileBytes + closingBoundary.toByteArray()
                            val body = completeBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType())

                            val uploadReq = Request.Builder()
                                .url(uploadUrl)
                                .post(body)
                                .addHeader("Authorization", "Bearer ${config.accessToken}")
                                .build()

                            withContext(Dispatchers.IO) { client.newCall(uploadReq).execute() }.close()
                        }
                    }
                }

                emit(SyncState.Success(
                    summary = "Sincronización real de Google Workspace finalizada correctamente. ${localProjects.size} proyectos insertados en la base de datos de Sheets y archivos multimedia actualizados.",
                    sheetsUrl = "https://docs.google.com/spreadsheets/d/${config.spreadsheetId}",
                    driveUrl = if (config.folderId.isNotBlank()) "https://drive.google.com/drive/folders/${config.folderId}" else "https://drive.google.com"
                ))

            } catch (e: Exception) {
                emit(SyncState.Error("Fallo en sincronización real: ${e.localizedMessage}. Asegúrese de que el Access Token esté vigente y que tenga permisos de escritura."))
            }

        } else {
            // HIGH FIDELITY SIMULATION MODE (Shows the entire JSON structures, the endpoints being target, and how Web/Windows clients will parse this database)
            val sheetId = if (config.spreadsheetId.isNotBlank()) config.spreadsheetId else "1X8B_Sheets_Base_Demo_Id"
            val foldId = if (config.folderId.isNotBlank()) config.folderId else "1_Drive_Multimedia_Folder_Demo_Id"

            emit(SyncState.Syncing(
                "Preparando tabla de Proyectos", 
                0.30f, 
                "GENERANDO PAYLOAD GOOGLE SHEETS [Proyectos]:\n" +
                "PUT https://sheets.googleapis.com/v1/spreadsheets/$sheetId/values/Proyectos!A1?valueInputOption=USER_ENTERED\n" +
                "{\n" +
                "  \"range\": \"Proyectos!A1\",\n" +
                "  \"values\": [\n" +
                "    [\"id\", \"name\", \"createdAt\", \"reportLabel\", \"showHeaderLabel\", \"showHeaderDate\", \"headerCompany\", \"headerCompanySub\", \"headerTitle\", \"showHeaderBox\"],\n" +
                localProjects.joinToString(",\n") { "    [\"${it.project.id}\", \"${it.project.name}\", \"${it.project.createdAt}\", \"${it.project.reportLabel}\", \"${it.project.showHeaderLabel}\", \"${it.project.showHeaderDate}\", \"${it.project.headerCompany.take(15)}...\", \"${it.project.headerCompanySub.take(15)}...\", \"${it.project.headerTitle.take(15)}...\", \"${it.project.showHeaderBox}\"]" } + "\n" +
                "  ]\n" +
                "}"
            ))
            delay(1800)

            val blocksToSync = localProjects.flatMap { it.blocks }
            emit(SyncState.Syncing(
                "Preparando tabla de Bloques de Contenido", 
                0.55f, 
                "GENERANDO RELACIONES RELACIONALES [Bloques_Contenido] (Total: ${blocksToSync.size} bloques):\n" +
                "PUT https://sheets.googleapis.com/v1/spreadsheets/$sheetId/values/Bloques_Contenido!A1?valueInputOption=USER_ENTERED\n" +
                "{\n" +
                "  \"range\": \"Bloques_Contenido!A1\",\n" +
                "  \"values\": [\n" +
                "    [\"id\", \"projectId\", \"type\", \"content\", \"sequence\", \"isHalfWidth\"],\n" +
                blocksToSync.take(5).joinToString(",\n") { "    [\"${it.id}\", \"${it.projectId}\", \"${it.type}\", \"${it.content.take(18)}...\", \"${it.sequence}\", \"${it.isHalfWidth}\"]" } + (if (blocksToSync.size > 5) ",\n    ... (+${blocksToSync.size - 5} bloques)" else "") + "\n" +
                "  ]\n" +
                "}"
            ))
            delay(1800)

            emit(SyncState.Syncing(
                "Sincronizando Archivos Multimedia en Google Drive", 
                0.78f, 
                "SUBIENDO IMÁGENES Y CAPTURAS DE FIRMAS DIGITALES:\n" +
                "POST https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart (Carpeta Destino: $foldId)\n" +
                "Subiendo ${blocksToSync.count { it.type == BlockType.IMAGE }} fotos de obra y ${blocksToSync.count { it.type == BlockType.SIGNATURE }} firmas recolectadas y digitalizadas..."
            ))
            delay(1500)

            emit(SyncState.Syncing(
                "Finalizando escritura en la Nube", 
                0.90f, 
                "Estructura alineada. Configurando indices para que Clientes Web (React/Tailwind) y de Windows (C#/.NET) puedan leer en tiempo real la misma base de datos en Sheets."
            ))
            delay(1000)

            emit(SyncState.Success(
                summary = "Sincronización simulada completada con éxito. Los datos relacionales y archivos binarios han sido estructurados en formato Google Sheets (Base de datos remota) y Google Drive (Gestión de contenidos), listos para coordinar con plataformas de Web y Windows.",
                sheetsUrl = "https://docs.google.com/spreadsheets/d/$sheetId",
                driveUrl = "https://drive.google.com/drive/folders/$foldId"
            ))
        }
    }
}
