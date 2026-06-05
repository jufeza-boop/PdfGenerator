package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class FolderSyncConfig(
    val rootFolderUri: String,
    val isAutoSync: Boolean = false
)

class FolderSyncManager(
    private val context: Context,
    private val repository: ProjectRepository
) {
    private val sharedPrefs = context.getSharedPreferences("folder_sync_prefs", Context.MODE_PRIVATE)

    fun getConfig(): FolderSyncConfig {
        return FolderSyncConfig(
            rootFolderUri = sharedPrefs.getString("root_folder_uri", "") ?: "",
            isAutoSync = sharedPrefs.getBoolean("auto_sync", false)
        )
    }

    fun saveConfig(rootFolderUri: String, isAutoSync: Boolean) {
        sharedPrefs.edit()
            .putString("root_folder_uri", rootFolderUri.trim())
            .putBoolean("auto_sync", isAutoSync)
            .apply()
    }

    fun isConfigured(): Boolean {
        val uriStr = getConfig().rootFolderUri
        if (uriStr.isBlank()) return false
        return try {
            val uri = Uri.parse(uriStr)
            val doc = DocumentFile.fromTreeUri(context, uri)
            doc != null && doc.exists() && doc.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    fun deleteProjectFolder(createdAt: Long): Boolean {
        if (!isConfigured()) return false
        return try {
            val uri = Uri.parse(getConfig().rootFolderUri)
            val rootDir = DocumentFile.fromTreeUri(context, uri) ?: return false
            val suffix = "_$createdAt"
            val projDir = rootDir.listFiles().find { it.isDirectory && it.name?.endsWith(suffix) == true }
            if (projDir != null && projDir.exists()) {
                projDir.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("FolderSync", "deleteProjectFolder error", e)
            false
        }
    }

    private fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    fun runSync(realSync: Boolean): Flow<SyncState> = flow {
        val config = getConfig()

        emit(SyncState.Syncing("Accediendo a Carpeta", 0.05f, "Conectando con la carpeta de trabajo seleccionada..."))
        delay(400)

        // Read SQLite data
        emit(SyncState.Syncing("Cargando base de datos", 0.12f, "Buscando tus obras y reportes en el dispositivo..."))
        val localProjects: List<ProjectWithBlocks>
        try {
            localProjects = repository.allProjects.first()
            emit(SyncState.Syncing("Base de datos de Room cargada", 0.18f, "Dispositivo listo. Encontradas ${localProjects.size} obras locales."))
            delay(300)
        } catch (e: Exception) {
            emit(SyncState.Error("Fallo al leer datos locales: ${e.localizedMessage}"))
            return@flow
        }

        if (!realSync) {
            // Dry run
            emit(SyncState.Success("Simulación de sincronización completada de forma segura."))
            return@flow
        }

        val rootUriStr = config.rootFolderUri
        if (rootUriStr.isBlank()) {
            emit(SyncState.Error("Falta seleccionar la carpeta compartida en la configuración. Elige una carpeta de Drive / OneDrive o local."))
            return@flow
        }

        val rootUri = Uri.parse(rootUriStr)
        val rootDir = try {
            DocumentFile.fromTreeUri(context, rootUri)
        } catch (e: Exception) {
            null
        }

        if (rootDir == null || !rootDir.exists() || !rootDir.canWrite()) {
            emit(SyncState.Error("La carpeta seleccionada no es accesible o faltan permisos de escritura. Por favor, selecciona la carpeta nuevamente desde la configuración."))
            return@flow
        }

        try {
            emit(SyncState.Syncing("Leyendo Archivos", 0.25f, "Explorando subcarpetas en tu carpeta de trabajo..."))
            delay(400)

            val remoteSubDirs = rootDir.listFiles().filter { it.isDirectory }
            val handledLocalCreatedAts = mutableSetOf<Long>()
            var downloadsCount = 0
            var updatesLocalCount = 0
            var updatesRemoteCount = 0

            // STEP 1: Process every project-directory in the shared folder
            remoteSubDirs.forEachIndexed { index, subDir ->
                val progressBase = 0.25f + (index.toFloat() / remoteSubDirs.size.coerceAtLeast(1)) * 0.40f
                val folderName = subDir.name ?: ""
                
                // Find project_data.json inside this directory
                val jsonFile = subDir.findFile("project_data.json")
                if (jsonFile != null && jsonFile.exists()) {
                    emit(SyncState.Syncing("Leyendo: $folderName", progressBase, "Analizando contenido de '$folderName' en la carpeta compartida..."))
                    
                    val fileContents = readTextFromDocument(jsonFile)
                    if (!fileContents.isNullOrBlank()) {
                        val decodedJson = JSONObject(fileContents)
                        val remoteProjJson = decodedJson.getJSONObject("project")
                        val remoteBlocksArr = decodedJson.getJSONArray("blocks")

                        val remoteCreatedAt = remoteProjJson.getLong("createdAt")
                        val remoteUpdatedAt = remoteProjJson.optLong("updatedAt", remoteCreatedAt)
                        val remoteName = remoteProjJson.getString("name")

                        handledLocalCreatedAts.add(remoteCreatedAt)

                        // Compares with our SQLite DB
                        val matchedLocal = localProjects.find { it.project.createdAt == remoteCreatedAt }

                        if (matchedLocal == null) {
                            // CASE A: Project is remote-only. Download/import to local SQLite!
                            emit(SyncState.Syncing("Importando obra nueva", progressBase + 0.02f, "Instalando en tu móvil la obra: '$remoteName'..."))
                            
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
                                var finalLocalContent = ""
                                val bObj = remoteBlocksArr.getJSONObject(bIdx)
                                val bTypeStr = bObj.getString("type")
                                val bType = BlockType.valueOf(bTypeStr)
                                val bContentStr = bObj.optString("content", "")
                                val isHalfWidth = bObj.optBoolean("isHalfWidth", false)
                                
                                finalLocalContent = bContentStr
                                if ((bType == BlockType.IMAGE || bType == BlockType.SIGNATURE) && !bContentStr.startsWith("/") && bContentStr.isNotBlank()) {
                                    // It's a relative filename in the shared folder (e.g. "img_123.jpg")
                                    val mediaDocFile = subDir.findFile(bContentStr)
                                    if (mediaDocFile != null && mediaDocFile.exists()) {
                                        val destinationDirName = if (bType == BlockType.IMAGE) "project_${insertedId}_images" else "project_${insertedId}_signatures"
                                        val localFilePath = copyMediaFromDocumentToLocal(mediaDocFile, insertedId, destinationDirName, bContentStr)
                                        if (localFilePath != null) {
                                            finalLocalContent = localFilePath
                                        }
                                    }
                                }

                                val blockResult = ContentBlockEntity(
                                    projectId = insertedId,
                                    type = bType,
                                    content = finalLocalContent,
                                    sequence = bObj.getInt("sequence"),
                                    isHalfWidth = isHalfWidth
                                )
                                repository.projectDao.insertBlock(blockResult)
                            }
                            downloadsCount++
                        } else {
                            // CASE B: Project exists both locally and remote. Compare timestamps.
                            val localUpdatedAt = matchedLocal.project.updatedAt
                            if (remoteUpdatedAt > localUpdatedAt) {
                                // Overwrite local in SQLite
                                emit(SyncState.Syncing("Sincronizando copia local", progressBase + 0.02f, "La copia en la carpeta compartida para '$remoteName' es más reciente. Actualizando tu móvil..."))
                                
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

                                // Clear local blocks
                                repository.projectDao.deleteBlocksForProject(matchedLocal.project.id)

                                for (bIdx in 0 until remoteBlocksArr.length()) {
                                    var finalLocalContent = ""
                                    val bObj = remoteBlocksArr.getJSONObject(bIdx)
                                    val bTypeStr = bObj.getString("type")
                                    val bType = BlockType.valueOf(bTypeStr)
                                    val bContentStr = bObj.optString("content", "")
                                    val isHalfWidth = bObj.optBoolean("isHalfWidth", false)

                                    finalLocalContent = bContentStr
                                    if ((bType == BlockType.IMAGE || bType == BlockType.SIGNATURE) && !bContentStr.startsWith("/") && bContentStr.isNotBlank()) {
                                        // Relative media filename
                                        val mediaDocFile = subDir.findFile(bContentStr)
                                        if (mediaDocFile != null && mediaDocFile.exists()) {
                                            val destinationDirName = if (bType == BlockType.IMAGE) "project_${matchedLocal.project.id}_images" else "project_${matchedLocal.project.id}_signatures"
                                            val localFilePath = copyMediaFromDocumentToLocal(mediaDocFile, matchedLocal.project.id, destinationDirName, bContentStr)
                                            if (localFilePath != null) {
                                                finalLocalContent = localFilePath
                                            }
                                        }
                                    }

                                    val blockResult = ContentBlockEntity(
                                        projectId = matchedLocal.project.id,
                                        type = bType,
                                        content = finalLocalContent,
                                        sequence = bObj.getInt("sequence"),
                                        isHalfWidth = isHalfWidth
                                    )
                                    repository.projectDao.insertBlock(blockResult)
                                }
                                updatesLocalCount++
                            } else if (localUpdatedAt > remoteUpdatedAt) {
                                // Local project has newer changes. We'll write them to this directory in Step 2.
                                Log.d("FolderSync", "Local version of '$remoteName' is newer. Scheduled to write back to shared folder.")
                            }
                        }
                    }
                }
            }

            // STEP 2: Find local projects to export/upload (missing in folder, or local copy is newer)
            val projectsToExport = localProjects.filter {
                !handledLocalCreatedAts.contains(it.project.createdAt) || let { _ ->
                    val matchedSubdirNameEnd = "_${it.project.createdAt}"
                    val hasSubdir = remoteSubDirs.any { sd -> sd.name?.endsWith(matchedSubdirNameEnd) == true }
                    if (hasSubdir) {
                        // Find this folder, look inside project_data.json and compare dates
                        var localIsNewer = false
                        val sd = remoteSubDirs.find { s -> s.name?.endsWith(matchedSubdirNameEnd) == true }
                        val fileJson = sd?.findFile("project_data.json")
                        if (fileJson != null) {
                            val content = readTextFromDocument(fileJson)
                            if (!content.isNullOrBlank()) {
                                val obj = JSONObject(content)
                                val remoteUp = obj.getJSONObject("project").optLong("updatedAt", 0)
                                if (it.project.updatedAt > remoteUp) {
                                    localIsNewer = true
                                }
                            }
                        }
                        localIsNewer
                    } else {
                        true
                    }
                }
            }

            if (projectsToExport.isNotEmpty()) {
                emit(SyncState.Syncing(
                    "Guardando en Carpeta Compartida", 
                    0.65f, 
                    "Escribiendo/actualizando ${projectsToExport.size} reporte(s) en tu carpeta compartida..."
                ))
                delay(400)
            }

            projectsToExport.forEachIndexed { exIdx, projWithBlocks ->
                val proj = projWithBlocks.project
                val blocks = projWithBlocks.blocks
                val progressBase = 0.65f + (exIdx.toFloat() / projectsToExport.size.coerceAtLeast(1)) * 0.30f

                val sanitizedName = sanitizeFolderName(proj.name)
                val folderName = "${sanitizedName}_${proj.createdAt}"
                
                emit(SyncState.Syncing("Escribiendo: ${proj.name}", progressBase, "Creando carpeta y transfiriendo adjuntos para '${proj.name}'..."))

                // Obtain or create the project subdirectory
                var projDir = rootDir.findFile(folderName)
                if (projDir == null || !projDir.exists()) {
                    // Try to finding it by timestamp suffix first so we don't duplicate if renamed
                    val suffix = "_${proj.createdAt}"
                    projDir = rootDir.listFiles().find { it.isDirectory && it.name?.endsWith(suffix) == true }
                    if (projDir == null) {
                        projDir = rootDir.createDirectory(folderName)
                    }
                }

                if (projDir == null || !projDir.exists()) {
                    throw Exception("No se pudo crear el subdirectorio '$folderName' en la carpeta seleccionada.")
                }

                // Copy images & signatures to this folder, and write relative pointers in JSON
                val processedBlocks = blocks.map { block ->
                    if ((block.type == BlockType.IMAGE || block.type == BlockType.SIGNATURE) && block.content.startsWith("/")) {
                        val localFile = File(block.content)
                        if (localFile.exists() && localFile.length() > 0) {
                            val fileName = localFile.name // e.g. "img_xxx.jpg"
                            emit(SyncState.Syncing("Escribiendo Foto: ${proj.name}", progressBase + 0.01f, "Escribiendo archivo de imagen '${localFile.name}' de ${(localFile.length() / 1024)} KB..."))
                            
                            // Create or get the media file in SAF project directory
                            var sfMediaFile = projDir!!.findFile(fileName)
                            if (sfMediaFile == null || !sfMediaFile.exists()) {
                                sfMediaFile = projDir!!.createFile("image/jpeg", fileName)
                            }
                            if (sfMediaFile != null) {
                                copyLocalFileToDocument(localFile, sfMediaFile)
                            }
                            // Crucial: store the relative name in the exported JSON so other devices or web can read it relatively!
                            block.copy(content = fileName)
                        } else {
                            block
                        }
                    } else {
                        block
                    }
                }

                // Create project_data.json string
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

                // Write project_data.json
                var jsonDocFile = projDir!!.findFile("project_data.json")
                if (jsonDocFile == null || !jsonDocFile.exists()) {
                    jsonDocFile = projDir!!.createFile("application/json", "project_data.json")
                }
                if (jsonDocFile != null) {
                    writeTextToDocument(jsonDocFile, finalProjectJson.toString())
                }
                updatesRemoteCount++
            }

            emit(SyncState.Syncing("Guardando Cambios", 0.98f, "Guardando datos directamente en los archivos de tu carpeta..."))
            delay(400)

            val summaryLines = mutableListOf<String>()
            summaryLines.add("Carpeta de trabajo actualizada con éxito:")
            if (downloadsCount > 0) summaryLines.add("• $downloadsCount obra(s) nueva(s) cargada(s) de la carpeta.")
            if (updatesLocalCount > 0) summaryLines.add("• $updatesLocalCount copia(s) local(es) actualizada(s) desde la carpeta.")
            if (updatesRemoteCount > 0) summaryLines.add("• $updatesRemoteCount proyecto(s) guardado(s) directamente en tu carpeta.")
            if (downloadsCount == 0 && updatesLocalCount == 0 && updatesRemoteCount == 0) {
                summaryLines.add("• Todo al día. Los proyectos de la app coinciden perfectamente con tu carpeta.")
            }

            emit(SyncState.Success(
                summary = summaryLines.joinToString("\n")
            ))

        } catch (e: Exception) {
            Log.e("FolderSync", "Sync execution error", e)
            emit(SyncState.Error("Fallo en sincronización local de carpeta: ${e.localizedMessage}"))
        }
    }

    // SAF File Read helpers
    private fun readTextFromDocument(file: DocumentFile): String? {
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e("FolderSync", "readTextFromDocument error", e)
            null
        }
    }

    private fun writeTextToDocument(file: DocumentFile, text: String) {
        try {
            context.contentResolver.openOutputStream(file.uri, "rwt")?.use { outputStream ->
                outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            }
        } catch (e: Exception) {
            Log.e("FolderSync", "writeTextToDocument error", e)
        }
    }

    private fun copyLocalFileToDocument(localFile: File, docFile: DocumentFile) {
        try {
            localFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(docFile.uri, "rwt")?.use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e("FolderSync", "copyLocalFileToDocument error", e)
        }
    }

    private fun copyMediaFromDocumentToLocal(docFile: DocumentFile, projectId: Long, destinationDirName: String, fileName: String): String? {
        return try {
            val destinationDir = File(context.filesDir, destinationDirName)
            if (!destinationDir.exists()) destinationDir.mkdirs()

            val destinationFile = File(destinationDir, fileName)
            context.contentResolver.openInputStream(docFile.uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            destinationFile.absolutePath
        } catch (e: Exception) {
            Log.e("FolderSync", "copyMediaFromDocumentToLocal error", e)
            null
        }
    }
}
