package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class AndroidFolderSyncManager(
    private val context: Context,
    private val repository: ProjectRepository
) : FolderSyncManager {
    private val sharedPrefs = context.getSharedPreferences("folder_sync_prefs", Context.MODE_PRIVATE)

    override fun getConfig(): FolderSyncConfig {
        return FolderSyncConfig(
            rootFolderUri = sharedPrefs.getString("root_folder_uri", "") ?: "",
            isAutoSync = sharedPrefs.getBoolean("auto_sync", false)
        )
    }

    override fun saveConfig(rootFolderUri: String, isAutoSync: Boolean) {
        sharedPrefs.edit()
            .putString("root_folder_uri", rootFolderUri.trim())
            .putBoolean("auto_sync", isAutoSync)
            .apply()
    }

    override fun isConfigured(): Boolean {
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

    override fun deleteProjectFolder(createdAt: Long): Boolean {
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
            false
        }
    }

    private fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    override fun runSync(realSync: Boolean): Flow<SyncState> = flow {
        val config = getConfig()
        emit(SyncState.Syncing("Accediendo a Carpeta", 0.05f, "Conectando con la carpeta de trabajo seleccionada..."))
        delay(400)

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
            emit(SyncState.Success("Simulación de sincronización completada de forma segura."))
            return@flow
        }

        val rootUriStr = config.rootFolderUri
        if (rootUriStr.isBlank()) {
            emit(SyncState.Error("Falta seleccionar la carpeta compartida en la configuración."))
            return@flow
        }

        val rootUri = Uri.parse(rootUriStr)
        val rootDir = try { DocumentFile.fromTreeUri(context, rootUri) } catch (e: Exception) { null }

        if (rootDir == null || !rootDir.exists() || !rootDir.canWrite()) {
            emit(SyncState.Error("La carpeta seleccionada no es accesible o faltan permisos de escritura."))
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

            remoteSubDirs.forEachIndexed { index, subDir ->
                val jsonFile = subDir.findFile("project_data.json")
                if (jsonFile != null && jsonFile.exists()) {
                    val fileContents = readTextFromDocument(jsonFile)
                    if (!fileContents.isNullOrBlank()) {
                        val decodedJson = JSONObject(fileContents)
                        val remoteProjJson = decodedJson.getJSONObject("project")
                        val remoteBlocksArr = decodedJson.getJSONArray("blocks")
                        val remoteCreatedAt = remoteProjJson.getLong("createdAt")
                        val remoteUpdatedAt = remoteProjJson.optLong("updatedAt", remoteCreatedAt)
                        val remoteName = remoteProjJson.getString("name")
                        handledLocalCreatedAts.add(remoteCreatedAt)
                        val matchedLocal = localProjects.find { it.project.createdAt == remoteCreatedAt }

                        if (matchedLocal == null) {
                            val newProjEntity = ProjectEntity(
                                name = remoteName,
                                createdAt = remoteCreatedAt,
                                updatedAt = remoteUpdatedAt,
                                reportLabel = remoteProjJson.optString("reportLabel", "REPORTE DE PROYECTO"),
                                showHeaderLabel = remoteProjJson.optBoolean("showHeaderLabel", true),
                                showHeaderDate = remoteProjJson.optBoolean("showHeaderDate", true),
                                headerCompany = remoteProjJson.optString("headerCompany", "Nombre de la empresa"),
                                headerCompanySub = remoteProjJson.optString("headerCompanySub", ""),
                                headerTitle = remoteProjJson.optString("headerTitle", "INFORME DE VISITA A OBRA"),
                                showHeaderBox = remoteProjJson.optBoolean("showHeaderBox", true),
                                showHeaderTitle = remoteProjJson.optBoolean("showHeaderTitle", true)
                            )
                            val insertedId = repository.projectDao.insertProject(newProjEntity)
                            for (bIdx in 0 until remoteBlocksArr.length()) {
                                val bObj = remoteBlocksArr.getJSONObject(bIdx)
                                val bType = BlockType.valueOf(bObj.getString("type"))
                                val bContentStr = bObj.optString("content", "")
                                var finalLocalContent = bContentStr
                                if ((bType == BlockType.IMAGE || bType == BlockType.SIGNATURE) && !bContentStr.startsWith("/") && bContentStr.isNotBlank()) {
                                    val mediaDocFile = subDir.findFile(bContentStr)
                                    if (mediaDocFile != null && mediaDocFile.exists()) {
                                        val destDirName = if (bType == BlockType.IMAGE) "project_${insertedId}_images" else "project_${insertedId}_signatures"
                                        copyMediaFromDocumentToLocal(mediaDocFile, insertedId, destDirName, bContentStr)?.let { finalLocalContent = it }
                                    }
                                }
                                repository.projectDao.insertBlock(ContentBlockEntity(projectId = insertedId, type = bType, content = finalLocalContent, sequence = bObj.getInt("sequence"), isHalfWidth = bObj.optBoolean("isHalfWidth", false)))
                            }
                            downloadsCount++
                        } else if (remoteUpdatedAt > matchedLocal.project.updatedAt) {
                            repository.projectDao.updateProject(matchedLocal.project.copy(
                                name = remoteName,
                                updatedAt = remoteUpdatedAt,
                                reportLabel = remoteProjJson.optString("reportLabel", "REPORTE DE PROYECTO"),
                                showHeaderLabel = remoteProjJson.optBoolean("showHeaderLabel", true),
                                showHeaderDate = remoteProjJson.optBoolean("showHeaderDate", true),
                                headerCompany = remoteProjJson.optString("headerCompany", "Nombre de la empresa"),
                                headerCompanySub = remoteProjJson.optString("headerCompanySub", ""),
                                headerTitle = remoteProjJson.optString("headerTitle", "INFORME DE VISITA A OBRA"),
                                showHeaderBox = remoteProjJson.optBoolean("showHeaderBox", true),
                                showHeaderTitle = remoteProjJson.optBoolean("showHeaderTitle", true)
                            ))
                            repository.projectDao.deleteBlocksForProject(matchedLocal.project.id)
                            for (bIdx in 0 until remoteBlocksArr.length()) {
                                val bObj = remoteBlocksArr.getJSONObject(bIdx)
                                val bType = BlockType.valueOf(bObj.getString("type"))
                                val bContentStr = bObj.optString("content", "")
                                var finalLocalContent = bContentStr
                                if ((bType == BlockType.IMAGE || bType == BlockType.SIGNATURE) && !bContentStr.startsWith("/") && bContentStr.isNotBlank()) {
                                    val mediaDocFile = subDir.findFile(bContentStr)
                                    if (mediaDocFile != null && mediaDocFile.exists()) {
                                        val destDirName = if (bType == BlockType.IMAGE) "project_${matchedLocal.project.id}_images" else "project_${matchedLocal.project.id}_signatures"
                                        copyMediaFromDocumentToLocal(mediaDocFile, matchedLocal.project.id, destDirName, bContentStr)?.let { finalLocalContent = it }
                                    }
                                }
                                repository.projectDao.insertBlock(ContentBlockEntity(projectId = matchedLocal.project.id, type = bType, content = finalLocalContent, sequence = bObj.getInt("sequence"), isHalfWidth = bObj.optBoolean("isHalfWidth", false)))
                            }
                            updatesLocalCount++
                        }
                    }
                }
            }

            val projectsToExport = localProjects.filter { localProj ->
                !handledLocalCreatedAts.contains(localProj.project.createdAt) || run {
                    val suffix = "_${localProj.project.createdAt}"
                    val remoteDir = remoteSubDirs.find { it.name?.endsWith(suffix) == true }
                    if (remoteDir != null) {
                        val jsonFile = remoteDir.findFile("project_data.json")
                        if (jsonFile != null && jsonFile.exists()) {
                            val content = readTextFromDocument(jsonFile)
                            if (content != null) {
                                val remoteUpdatedAt = JSONObject(content).getJSONObject("project").optLong("updatedAt", 0)
                                remoteUpdatedAt < localProj.project.updatedAt
                            } else true
                        } else true
                    } else true
                }
            }

            projectsToExport.forEachIndexed { exIdx, projWithBlocks ->
                val folderName = "${projWithBlocks.project.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()}_${projWithBlocks.project.createdAt}"
                var projDir = rootDir.findFile(folderName) ?: rootDir.listFiles().find { it.isDirectory && it.name?.endsWith("_${projWithBlocks.project.createdAt}") == true } ?: rootDir.createDirectory(folderName)
                if (projDir == null) throw Exception("Failed to create folder $folderName")
                
                val processedBlocks = projWithBlocks.blocks.map { block ->
                    if ((block.type == BlockType.IMAGE || block.type == BlockType.SIGNATURE) && block.content.startsWith("/")) {
                        val localFile = File(block.content)
                        if (localFile.exists()) {
                            // Delete existing remote file to prevent (1) duplicates
                            projDir!!.findFile(localFile.name)?.delete()
                            val sfMediaFile = projDir!!.createFile("image/jpeg", localFile.name)
                            sfMediaFile?.let { copyLocalFileToDocument(localFile, it) }
                            block.copy(content = localFile.name)
                        } else block
                    } else block
                }

                val finalJson = JSONObject().apply {
                    put("project", JSONObject().apply {
                        put("name", projWithBlocks.project.name)
                        put("createdAt", projWithBlocks.project.createdAt)
                        put("updatedAt", projWithBlocks.project.updatedAt)
                        put("reportLabel", projWithBlocks.project.reportLabel)
                        put("showHeaderLabel", projWithBlocks.project.showHeaderLabel)
                        put("showHeaderDate", projWithBlocks.project.showHeaderDate)
                        put("headerCompany", projWithBlocks.project.headerCompany)
                        put("headerCompanySub", projWithBlocks.project.headerCompanySub)
                        put("headerTitle", projWithBlocks.project.headerTitle)
                        put("showHeaderBox", projWithBlocks.project.showHeaderBox)
                        put("showHeaderTitle", projWithBlocks.project.showHeaderTitle)
                    })
                    put("blocks", JSONArray().apply {
                        processedBlocks.forEach { b ->
                            put(JSONObject().apply {
                                put("type", b.type.name)
                                put("content", b.content)
                                put("sequence", b.sequence)
                                put("isHalfWidth", b.isHalfWidth)
                            })
                        }
                    })
                }

                // Delete existing remote project_data.json to prevent (1) duplicates
                projDir!!.findFile("project_data.json")?.delete()
                projDir!!.createFile("application/json", "project_data.json")?.let { 
                    writeTextToDocument(it, finalJson.toString()) 
                }
                updatesRemoteCount++
            }
            emit(SyncState.Success("Sync completed: $downloadsCount imported, $updatesLocalCount local updated, $updatesRemoteCount remote updated."))
        } catch (e: Exception) {
            emit(SyncState.Error("Sync error: ${e.localizedMessage}"))
        }
    }

    private fun readTextFromDocument(file: DocumentFile): String? = try { context.contentResolver.openInputStream(file.uri)?.use { it.bufferedReader().readText() } } catch (e: Exception) { null }
    private fun writeTextToDocument(file: DocumentFile, text: String) = try { context.contentResolver.openOutputStream(file.uri, "rwt")?.use { it.bufferedWriter().use { w -> w.write(text) } } } catch (e: Exception) { }
    private fun copyLocalFileToDocument(localFile: File, docFile: DocumentFile) = try { localFile.inputStream().use { input -> context.contentResolver.openOutputStream(docFile.uri, "rwt")?.use { output -> input.copyTo(output) } } } catch (e: Exception) { }
    private fun copyMediaFromDocumentToLocal(docFile: DocumentFile, projectId: Long, destinationDirName: String, fileName: String): String? = try {
        val destDir = File(context.filesDir, destinationDirName).apply { if (!exists()) mkdirs() }
        val destFile = File(destDir, fileName)
        context.contentResolver.openInputStream(docFile.uri)?.use { input -> destFile.outputStream().use { output -> input.copyTo(output) } }
        destFile.absolutePath
    } catch (e: Exception) { null }
}
