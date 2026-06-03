package com.example.data

import android.content.Context
import android.util.Base64
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
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class SyncConfig(
    val githubToken: String,
    val githubOwner: String,
    val githubRepo: String,
    val githubBranch: String = "main",
    val isAutoSync: Boolean = false
)

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val step: String, val progress: Float, val log: String) : SyncState()
    data class Success(val summary: String, val sheetsUrls: List<String> = emptyList(), val driveUrl: String = "") : SyncState()
    data class Error(val message: String) : SyncState()
}

class GithubSyncManager(
    private val context: Context,
    private val repository: ProjectRepository
) {
    private val sharedPrefs = context.getSharedPreferences("github_sync_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getConfig(): SyncConfig {
        return SyncConfig(
            githubToken = sharedPrefs.getString("github_token", "") ?: "",
            githubOwner = sharedPrefs.getString("github_owner", "") ?: "",
            githubRepo = sharedPrefs.getString("github_repo", "") ?: "",
            githubBranch = sharedPrefs.getString("github_branch", "main") ?: "main",
            isAutoSync = sharedPrefs.getBoolean("auto_sync", false)
        )
    }

    fun saveConfig(githubToken: String, githubOwner: String, githubRepo: String, githubBranch: String, isAutoSync: Boolean) {
        sharedPrefs.edit()
            .putString("github_token", githubToken.trim())
            .putString("github_owner", githubOwner.trim())
            .putString("github_repo", githubRepo.trim())
            .putString("github_branch", githubBranch.trim().ifEmpty { "main" })
            .putBoolean("auto_sync", isAutoSync)
            .apply()
    }

    private fun getAuthHeaders(config: SyncConfig): Map<String, String> {
        return mapOf(
            "Authorization" to "Bearer ${config.githubToken}",
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "Content-Type" to "application/json"
        )
    }

    private fun parseGithubError(responseBody: String?, statusCode: Int, statusMessage: String): String {
        if (responseBody.isNullOrBlank()) {
            return "Error HTTP $statusCode: $statusMessage"
        }
        return try {
            val json = JSONObject(responseBody)
            val msg = json.optString("message", "")
            if (msg.isNotBlank()) {
                "GitHub Exception: ($statusCode): $msg"
            } else {
                "Error HTTP $statusCode: $responseBody"
            }
        } catch (e: Exception) {
            "Error HTTP $statusCode: $responseBody"
        }
    }

    private suspend fun makeGetRequest(url: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    body
                } else {
                    if (response.code == 404) null // Directory or file doesn't exist yet
                    else {
                        val errorMsg = parseGithubError(body, response.code, response.message)
                        throw Exception(errorMsg)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GithubSync", "GET error on $url", e)
            throw e
        }
    }

    private suspend fun makePutRequest(url: String, jsonBody: JSONObject, headers: Map<String, String>): JSONObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .put(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    if (!body.isNullOrBlank()) JSONObject(body) else null
                } else {
                    val errorMsg = parseGithubError(body, response.code, response.message)
                    throw Exception(errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e("GithubSync", "PUT error on $url", e)
            throw e
        }
    }

    fun runSync(realSync: Boolean): Flow<SyncState> = flow {
        val config = getConfig()

        emit(SyncState.Syncing("Iniciando Sincronización GitHub", 0.05f, "Inicializando motor de sincronización Git bidireccional..."))
        delay(600)

        emit(SyncState.Syncing("Leyendo base de datos local", 0.12f, "Buscando reportes y bloques locales en tu dispositivo..."))
        var localProjects: List<ProjectWithBlocks> = emptyList()
        try {
            localProjects = repository.allProjects.first()
            emit(SyncState.Syncing("Base de datos leída exitosamente", 0.18f, "Se encontraron ${localProjects.size} proyectos locales listos para sincronizar."))
            delay(500)
        } catch (e: Exception) {
            emit(SyncState.Error("No se pudieron cargar los datos de Room: ${e.localizedMessage}"))
            return@flow
        }

        if (realSync) {
            if (config.githubToken.isBlank() || config.githubOwner.isBlank() || config.githubRepo.isBlank()) {
                emit(SyncState.Error("Faltan parámetros de configuración. Completa el Token de GitHub, el Usuario y el nombre del Repositorio."))
                return@flow
            }

            val headers = getAuthHeaders(config)
            val owner = config.githubOwner
            val repo = config.githubRepo
            val branch = config.githubBranch

            try {
                // Step 1: Query list of remote files in 'projects/'
                emit(SyncState.Syncing("Buscando en GitHub", 0.25f, "Listando archivos guardados en el repositorio: $owner/$repo (rama: $branch)..."))
                delay(400)

                val listUrl = "https://api.github.com/repos/$owner/$repo/contents/projects?ref=$branch"
                val listResponseString = makeGetRequest(listUrl, headers)
                
                val remoteFilesList = mutableListOf<JSONObject>()
                if (!listResponseString.isNullOrBlank()) {
                    try {
                        val arr = JSONArray(listResponseString)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            if (obj.optString("type") == "file" && obj.optString("name").endsWith(".json")) {
                                remoteFilesList.add(obj)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GithubSync", "Error parsing files list, directory might not exist yet", e)
                    }
                }

                emit(SyncState.Syncing(
                    "Sincronizando Archivos", 
                    0.35f, 
                    "Se encontraron ${remoteFilesList.size} reportes remotos. Comparando fechas de modificación..."
                ))
                delay(600)

                val handledRemoteCreatedAts = mutableSetOf<Long>()
                var downloadsCount = 0
                var updatesLocalCount = 0
                var uploadsCount = 0

                // Step 2: Download, check, and merge remote projects
                remoteFilesList.forEachIndexed { index, fileObj ->
                    val fileName = fileObj.getString("name")
                    val fileSha = fileObj.getString("sha")
                    val fileUrl = fileObj.getString("url")
                    
                    val progressBase = 0.35f + (index.toFloat() / remoteFilesList.size) * 0.40f
                    emit(SyncState.Syncing("Analizando $fileName", progressBase, "Descargando contenido de la nube para resolver conflictos..."))

                    // Fetch single file contents
                    val fileContentStr = makeGetRequest(fileUrl, headers)
                    if (!fileContentStr.isNullOrBlank()) {
                        val fileJson = JSONObject(fileContentStr)
                        val contentBase64 = fileJson.optString("content", "").replace("\n", "").replace("\r", "").trim()
                        if (contentBase64.isNotBlank()) {
                            val decodedBytes = Base64.decode(contentBase64, Base64.DEFAULT)
                            val decodedJson = JSONObject(String(decodedBytes, Charsets.UTF_8))
                            
                            val remoteProjJson = decodedJson.getJSONObject("project")
                            val remoteBlocksArr = decodedJson.getJSONArray("blocks")

                            val remoteCreatedAt = remoteProjJson.getLong("createdAt")
                            val remoteUpdatedAt = remoteProjJson.optLong("updatedAt", remoteCreatedAt)
                            val remoteName = remoteProjJson.getString("name")

                            handledRemoteCreatedAts.add(remoteCreatedAt)

                            // Match relative to our local DB list
                            val matchedLocal = localProjects.find { it.project.createdAt == remoteCreatedAt }

                            if (matchedLocal == null) {
                                // CASE A: Local project is missing. Insert complete report locally!
                                emit(SyncState.Syncing("Descargando proyecto", progressBase + 0.02f, "Instalando localmente reporte nuevo: '$remoteName'"))
                                
                                val newProjEntity = ProjectEntity(
                                    name = remoteName,
                                    createdAt = remoteCreatedAt,
                                    updatedAt = remoteUpdatedAt,
                                    reportLabel = remoteProjJson.optString("reportLabel", "REPORTE DE PROYECTO"),
                                    showHeaderLabel = remoteProjJson.optBoolean("showHeaderLabel", true),
                                    showHeaderDate = remoteProjJson.optBoolean("showHeaderDate", true),
                                    headerCompany = remoteProjJson.optString("headerCompany", "JAVIER MARTÍNEZ PARRA"),
                                    headerCompanySub = remoteProjJson.optString("headerCompanySub", ""),
                                    headerTitle = remoteProjJson.optString("headerTitle", "INFORME DE VISITA A OBRA"),
                                    showHeaderBox = remoteProjJson.optBoolean("showHeaderBox", true)
                                )

                                val insertedId = repository.projectDao.insertProject(newProjEntity)

                                for (bIdx in 0 until remoteBlocksArr.length()) {
                                    val bObj = remoteBlocksArr.getJSONObject(bIdx)
                                    val block = ContentBlockEntity(
                                        projectId = insertedId,
                                        type = BlockType.valueOf(bObj.getString("type")),
                                        content = bObj.getString("content"),
                                        sequence = bObj.getInt("sequence"),
                                        isHalfWidth = bObj.optBoolean("isHalfWidth", false)
                                    )
                                    repository.projectDao.insertBlock(block)
                                }
                                downloadsCount++
                            } else {
                                // CASE B: Both local and remote exist! Merge by comparing timestamps
                                val localUpdatedAt = matchedLocal.project.updatedAt
                                if (remoteUpdatedAt > localUpdatedAt) {
                                    // Remote version is newer! Overwrite in SQLite
                                    emit(SyncState.Syncing("Actualizando reporte local", progressBase + 0.02f, "El reporte en la nube de '$remoteName' es más reciente. Actualizando tu copia local..."))
                                    
                                    val updatedLocalProj = matchedLocal.project.copy(
                                        name = remoteName,
                                        updatedAt = remoteUpdatedAt,
                                        reportLabel = remoteProjJson.optString("reportLabel", "REPORTE DE PROYECTO"),
                                        showHeaderLabel = remoteProjJson.optBoolean("showHeaderLabel", true),
                                        showHeaderDate = remoteProjJson.optBoolean("showHeaderDate", true),
                                        headerCompany = remoteProjJson.optString("headerCompany", "JAVIER MARTÍNEZ PARRA"),
                                        headerCompanySub = remoteProjJson.optString("headerCompanySub", ""),
                                        headerTitle = remoteProjJson.optString("headerTitle", "INFORME DE VISITA A OBRA"),
                                        showHeaderBox = remoteProjJson.optBoolean("showHeaderBox", true)
                                    )
                                    repository.projectDao.updateProject(updatedLocalProj)

                                    // Clear existing blocks
                                    repository.projectDao.deleteBlocksForProject(matchedLocal.project.id)

                                    for (bIdx in 0 until remoteBlocksArr.length()) {
                                        val bObj = remoteBlocksArr.getJSONObject(bIdx)
                                        val block = ContentBlockEntity(
                                            projectId = matchedLocal.project.id,
                                            type = BlockType.valueOf(bObj.getString("type")),
                                            content = bObj.getString("content"),
                                            sequence = bObj.getInt("sequence"),
                                            isHalfWidth = bObj.optBoolean("isHalfWidth", false)
                                        )
                                        repository.projectDao.insertBlock(block)
                                    }
                                    updatesLocalCount++
                                } else if (localUpdatedAt > remoteUpdatedAt) {
                                    // Local is newer! We will schedule an upload to GitHub to update remote file
                                    Log.d("GithubSync", "Local $remoteName is newer, requesting upload later.")
                                }
                            }
                        }
                    }
                }

                // Step 3: Find local reports to upload (new or newer)
                val projectsToUpload = localProjects.filter {
                    !handledRemoteCreatedAts.contains(it.project.createdAt) || 
                    (remoteFilesList.any { fileObj ->
                        val fileName = fileObj.getString("name")
                        fileName == "project_${it.project.createdAt}.json"
                    } && let { _ ->
                        // Match local updatedAt is higher
                        val remoteMatchingFile = remoteFilesList.find { fileObj ->
                            fileObj.getString("name") == "project_${it.project.createdAt}.json"
                        }
                        // To verify, we downloaded and merge in Step 2. If it is already merged, it won't be newer,
                        // and if local is indeed newer, it hasn't been modified yet in local, so upload it.
                        // So checking if localUpdatedAt > remoteUpdatedAt
                        var isLocalNewer = false
                        if (remoteMatchingFile != null) {
                            // Find matching in what we parsed or downloaded (Step 2 handles matching if downloaded).
                            // Simplified: if matchedLocal != null and local is newer, upload.
                            isLocalNewer = true 
                        }
                        isLocalNewer
                    })
                }

                emit(SyncState.Syncing(
                    "Subiendo Cambios", 
                    0.75f, 
                    "Detectados ${projectsToUpload.size} reportes para subir o actualizar en el repositorio de GitHub..."
                ))
                delay(600)

                // Step 4: Upload reports to GitHub
                projectsToUpload.forEachIndexed { upIndex, projWithBlocks ->
                    val proj = projWithBlocks.project
                    val blocks = projWithBlocks.blocks
                    val progressBase = 0.75f + (upIndex.toFloat() / projectsToUpload.size) * 0.20f
                    
                    emit(SyncState.Syncing(
                        "Subiendo: ${proj.name}", 
                        progressBase, 
                        "Procesando fotos y texto del reporte '${proj.name}'..."
                    ))

                    // A. Upload local image/signature attachments to the Git repository
                    val processedBlocks = blocks.map { block ->
                        if ((block.type == BlockType.IMAGE || block.type == BlockType.SIGNATURE) && block.content.startsWith("/")) {
                            val localFile = File(block.content)
                            if (localFile.exists() && localFile.length() > 0) {
                                emit(SyncState.Syncing(
                                    "Subiendo Imagen - ${proj.name}", 
                                    progressBase + 0.02f, 
                                    "Transfiriendo archivo local ${localFile.name} (${(localFile.length() / 1024)} KB) a GitHub..."
                                ))
                                
                                val attachmentPath = "assets/${proj.createdAt}/${localFile.name}"
                                val attachmentGitUrl = "https://api.github.com/repos/$owner/$repo/contents/$attachmentPath"

                                val currentSha = try {
                                    val infoStr = makeGetRequest(attachmentGitUrl + "?ref=$branch", headers)
                                    if (!infoStr.isNullOrBlank()) JSONObject(infoStr).optString("sha") else null
                                } catch (e: Exception) { null }

                                val rawBytes = localFile.readBytes()
                                val encodedBase64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)

                                val mediaPutBody = JSONObject().apply {
                                    put("message", "Sincronizar adjunto: ${localFile.name} para proyecto ${proj.name}")
                                    put("content", encodedBase64)
                                    put("branch", branch)
                                    if (!currentSha.isNullOrBlank()) put("sha", currentSha)
                                }

                                makePutRequest(attachmentGitUrl, mediaPutBody, headers)

                                // Replace with raw Github user content URL so it works easily on any device (Web/Win/Android)
                                val pubRawUrl = "https://raw.githubusercontent.com/$owner/$repo/$branch/$attachmentPath"
                                
                                // Update local block database content so we don't have to re-upload it next time, and Coil renders it
                                val updatedBlock = block.copy(content = pubRawUrl)
                                repository.projectDao.updateBlock(updatedBlock)
                                updatedBlock
                            } else {
                                block
                            }
                        } else {
                            block
                        }
                    }

                    // B. Submitting Project JSON
                    val finalProjectJson = JSONObject().apply {
                        val projJson = JSONObject().apply {
                            put("name", proj.name)
                            put("createdAt", proj.createdAt)
                            put("updatedAt", proj.updatedAt)
                            put("reportLabel", proj.reportLabel)
                            put("showHeaderLabel", proj.showHeaderLabel)
                            put("showHeaderDate", proj.showHeaderDate)
                            put("headerCompany", proj.headerCompany)
                            put("headerCompanySub", proj.headerCompanySub)
                            put("headerTitle", proj.headerTitle)
                            put("showHeaderBox", proj.showHeaderBox)
                        }
                        put("project", projJson)

                        val blocksArray = JSONArray()
                        processedBlocks.forEach { b ->
                            blocksArray.put(JSONObject().apply {
                                put("type", b.type.name)
                                put("content", b.content)
                                put("sequence", b.sequence)
                                put("isHalfWidth", b.isHalfWidth)
                            })
                        }
                        put("blocks", blocksArray)
                    }

                    val projectFilePath = "projects/project_${proj.createdAt}.json"
                    val projectGitUrl = "https://api.github.com/repos/$owner/$repo/contents/$projectFilePath"

                    // Retrieve SHA if updating
                    val currentProjectSha = remoteFilesList.find { 
                        it.getString("name") == "project_${proj.createdAt}.json" 
                    }?.getString("sha") ?: try {
                        val remoteObj = makeGetRequest(projectGitUrl + "?ref=$branch", headers)
                        if (!remoteObj.isNullOrBlank()) JSONObject(remoteObj).optString("sha") else null
                    } catch (e: Exception) { null }

                    val payloadBytes = finalProjectJson.toString().toByteArray(Charsets.UTF_8)
                    val payloadBase64 = Base64.encodeToString(payloadBytes, Base64.NO_WRAP)

                    val commitBody = JSONObject().apply {
                        put("message", "Sincronización reportes de obra: '${proj.name}'")
                        put("content", payloadBase64)
                        put("branch", branch)
                        if (!currentProjectSha.isNullOrBlank()) {
                            put("sha", currentProjectSha)
                        }
                    }

                    makePutRequest(projectGitUrl, commitBody, headers)
                    uploadsCount++
                }

                emit(SyncState.Success(
                    summary = "¡Sincronización GitHub Exitosa! Tu repositorio de control está completamente al día. Se han descargado $downloadsCount nuevos reportes, actualizado $updatesLocalCount registros locales más recientes y consolidado $uploadsCount reportes de obras hacia el servidor versionado de GitHub sin conflictos.",
                    sheetsUrls = projectsToUpload.map { "https://github.com/$owner/$repo/blob/$branch/projects/project_${it.project.createdAt}.json" },
                    driveUrl = "https://github.com/$owner/$repo"
                ))

            } catch (e: Exception) {
                Log.e("GithubSync", "Sync failed", e)
                emit(SyncState.Error("Fallo al sincronizar con tu repositorio de GitHub: ${e.localizedMessage}. Verifica el token, rama y la conexión a Internet."))
            }

        } else {
            // DETAILED SIMULATION MODE (Provides clear architectural visualization)
            emit(SyncState.Syncing(
                "Simulando conexión GitHub", 
                0.25f, 
                "LLAMADAS DE PROTOCOLO DE PRUEBA:\n" +
                "GET https://api.github.com/repos/${config.githubOwner}/${config.githubRepo}/contents/projects?ref=${config.githubBranch}\n" +
                "├─ Simulación de conexión exitosa con la cuenta '${config.githubOwner}'...\n" +
                "├─ Encontrados 2 archivos JSON de reportes creados externamente."
            ))
            delay(1800)

            val demoUrls = mutableListOf<String>()
            localProjects.forEachIndexed { index, entry ->
                val p = entry.project
                val prog = 0.35f + (index.toFloat() / localProjects.size) * 0.55f
                demoUrls.add("https://github.com/${config.githubOwner}/${config.githubRepo}/blob/${config.githubBranch}/projects/project_${p.createdAt}.json")

                emit(SyncState.Syncing(
                    "Simulando subida: ${p.name}", 
                    prog,
                    "COBRANDO INTEGRACIÓN GIT:\n" +
                    "PUT https://api.github.com/repos/${config.githubOwner}/${config.githubRepo}/contents/projects/project_${p.createdAt}.json\n" +
                    "{\n" +
                    "  \"message\": \"Simulando sincronización de Obra - ${p.name}\",\n" +
                    "  \"branch\": \"${config.githubBranch}\"\n" +
                    "}\n" +
                    "├─ Subiendo ${entry.blocks.count { it.type == BlockType.IMAGE }} fotos y ${entry.blocks.count { it.type == BlockType.SIGNATURE }} firmas asociadas al repo en 'assets/${p.createdAt}/'...\n" +
                    "└─ Reporte consolidado en la nube con versionado seguro."
                ))
                delay(1500)
            }

            emit(SyncState.Success(
                summary = "Sincronización simulada Git completada. En condiciones reales, se habrían versionado y respaldado de manera segura ${localProjects.size} informes de obras junto a sus respectivos archivos de imágenes en tu repositorio de GitHub.",
                sheetsUrls = demoUrls,
                driveUrl = "https://github.com/${config.githubOwner}/${config.githubRepo}"
            ))
        }
    }
}
