package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.prefs.Preferences

class DesktopFolderSyncManager(
    private val repository: ProjectRepository
) : FolderSyncManager {
    private val prefs = Preferences.userRoot().node("pdfgenerator_sync")
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val syncAdapter = moshi.adapter(ProjectSyncData::class.java)

    override fun getConfig(): FolderSyncConfig {
        return FolderSyncConfig(
            rootFolderUri = prefs.get("root_folder_uri", ""),
            isAutoSync = prefs.getBoolean("auto_sync", false)
        )
    }

    override fun saveConfig(rootFolderUri: String, isAutoSync: Boolean) {
        prefs.put("root_folder_uri", rootFolderUri)
        prefs.putBoolean("auto_sync", isAutoSync)
        prefs.flush()
    }

    override fun isConfigured(): Boolean {
        val path = getConfig().rootFolderUri
        if (path.isBlank()) return false
        val file = File(path)
        return file.exists() && file.isDirectory && file.canWrite()
    }

    override fun deleteProjectFolder(createdAt: Long): Boolean {
        val rootPath = getConfig().rootFolderUri
        if (rootPath.isBlank()) return false
        val rootDir = File(rootPath)
        val projDir = rootDir.listFiles()?.find { it.isDirectory && it.name.endsWith("_$createdAt") }
        return projDir?.deleteRecursively() ?: false
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
            emit(SyncState.Syncing("Base de datos Room cargada", 0.18f, "Dispositivo listo. Encontradas ${localProjects.size} obras locales."))
            delay(300)
        } catch (e: Exception) {
            emit(SyncState.Error("Fallo al leer datos locales: ${e.localizedMessage}"))
            return@flow
        }

        if (!realSync) {
            emit(SyncState.Success("Simulación de sincronización completada de forma segura."))
            return@flow
        }

        val rootPath = config.rootFolderUri
        if (rootPath.isBlank()) {
            emit(SyncState.Error("Falta seleccionar la carpeta compartida en la configuración."))
            return@flow
        }

        val rootDir = File(rootPath)
        if (!rootDir.exists() || !rootDir.isDirectory || !rootDir.canWrite()) {
            emit(SyncState.Error("La carpeta seleccionada no es accesible o faltan permisos de escritura."))
            return@flow
        }

        try {
            emit(SyncState.Syncing("Leyendo Archivos", 0.25f, "Explorando subcarpetas en tu carpeta de trabajo..."))
            delay(400)
            
            val remoteSubDirs = rootDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            val handledLocalCreatedAts = mutableSetOf<Long>()
            var downloadsCount = 0
            var updatesLocalCount = 0
            var updatesRemoteCount = 0

            withContext(Dispatchers.IO) {
                remoteSubDirs.forEach { subDir ->
                    val jsonFile = File(subDir, "project_data.json")
                    if (jsonFile.exists()) {
                        val fileContents = jsonFile.readText()
                        val syncData = try { syncAdapter.fromJson(fileContents) } catch (e: Exception) { null }
                        
                        if (syncData != null) {
                            val remoteProj = syncData.project
                            val remoteBlocks = syncData.blocks
                            val remoteCreatedAt = remoteProj.createdAt
                            val remoteUpdatedAt = remoteProj.updatedAt
                            val remoteName = remoteProj.name
                            
                            handledLocalCreatedAts.add(remoteCreatedAt)
                            val matchedLocal = localProjects.find { it.project.createdAt == remoteCreatedAt }

                            if (matchedLocal == null) {
                                // Download new project
                                val newProjEntity = ProjectEntity(
                                    name = remoteName,
                                    createdAt = remoteCreatedAt,
                                    updatedAt = remoteUpdatedAt,
                                    reportLabel = remoteProj.reportLabel,
                                    showHeaderLabel = remoteProj.showHeaderLabel,
                                    showHeaderDate = remoteProj.showHeaderDate,
                                    headerCompany = remoteProj.headerCompany,
                                    headerCompanySub = remoteProj.headerCompanySub,
                                    headerTitle = remoteProj.headerTitle,
                                    showHeaderBox = remoteProj.showHeaderBox,
                                    showHeaderTitle = remoteProj.showHeaderTitle
                                )
                                val insertedId = repository.projectDao.insertProject(newProjEntity)
                                remoteBlocks.forEach { b ->
                                    val bType = BlockType.valueOf(b.type)
                                    var finalContent = b.content
                                    if ((bType == BlockType.IMAGE || bType == BlockType.SIGNATURE) && b.content.isNotBlank()) {
                                        val mediaFile = File(subDir, b.content)
                                        if (mediaFile.exists()) {
                                            val destDirName = if (bType == BlockType.IMAGE) "project_${insertedId}_images" else "project_${insertedId}_signatures"
                                            val destDir = File(repository.filesDir, destDirName).apply { if (!exists()) mkdirs() }
                                            val destFile = File(destDir, b.content)
                                            mediaFile.copyTo(destFile, overwrite = true)
                                            finalContent = destFile.absolutePath
                                        }
                                    }
                                    repository.projectDao.insertBlock(ContentBlockEntity(
                                        projectId = insertedId,
                                        type = bType,
                                        content = finalContent,
                                        sequence = b.sequence,
                                        isHalfWidth = b.isHalfWidth
                                    ))
                                }
                                downloadsCount++
                            } else if (remoteUpdatedAt > matchedLocal.project.updatedAt) {
                                // Update local project
                                repository.projectDao.updateProject(matchedLocal.project.copy(
                                    name = remoteName,
                                    updatedAt = remoteUpdatedAt,
                                    reportLabel = remoteProj.reportLabel,
                                    showHeaderLabel = remoteProj.showHeaderLabel,
                                    showHeaderDate = remoteProj.showHeaderDate,
                                    headerCompany = remoteProj.headerCompany,
                                    headerCompanySub = remoteProj.headerCompanySub,
                                    headerTitle = remoteProj.headerTitle,
                                    showHeaderBox = remoteProj.showHeaderBox,
                                    showHeaderTitle = remoteProj.showHeaderTitle
                                ))
                                repository.projectDao.deleteBlocksForProject(matchedLocal.project.id)
                                remoteBlocks.forEach { b ->
                                    val bType = BlockType.valueOf(b.type)
                                    var finalContent = b.content
                                    if ((bType == BlockType.IMAGE || bType == BlockType.SIGNATURE) && b.content.isNotBlank()) {
                                        val mediaFile = File(subDir, b.content)
                                        if (mediaFile.exists()) {
                                            val destDirName = if (bType == BlockType.IMAGE) "project_${matchedLocal.project.id}_images" else "project_${matchedLocal.project.id}_signatures"
                                            val destDir = File(repository.filesDir, destDirName).apply { if (!exists()) mkdirs() }
                                            val destFile = File(destDir, b.content)
                                            mediaFile.copyTo(destFile, overwrite = true)
                                            finalContent = destFile.absolutePath
                                        }
                                    }
                                    repository.projectDao.insertBlock(ContentBlockEntity(
                                        projectId = matchedLocal.project.id,
                                        type = bType,
                                        content = finalContent,
                                        sequence = b.sequence,
                                        isHalfWidth = b.isHalfWidth
                                    ))
                                }
                                updatesLocalCount++
                            }
                        }
                    }
                }

                // Projects to upload
                val projectsToExport = localProjects.filter { localProj ->
                    !handledLocalCreatedAts.contains(localProj.project.createdAt) || run {
                        val suffix = "_${localProj.project.createdAt}"
                        val remoteDir = remoteSubDirs.find { it.name.endsWith(suffix) }
                        if (remoteDir != null) {
                            val jsonFile = File(remoteDir, "project_data.json")
                            if (jsonFile.exists()) {
                                val syncData = try { syncAdapter.fromJson(jsonFile.readText()) } catch (e: Exception) { null }
                                syncData != null && syncData.project.updatedAt < localProj.project.updatedAt
                            } else true
                        } else true
                    }
                }

                projectsToExport.forEach { projWithBlocks ->
                    val folderName = "${sanitizeFolderName(projWithBlocks.project.name)}_${projWithBlocks.project.createdAt}"
                    val projDir = File(rootDir, folderName).apply { if (!exists()) mkdirs() }
                    
                    val blockSyncs = projWithBlocks.blocks.map { block ->
                        if ((block.type == BlockType.IMAGE || block.type == BlockType.SIGNATURE) && block.content.startsWith("/")) {
                            val localFile = File(block.content)
                            if (localFile.exists()) {
                                val destFile = File(projDir, localFile.name)
                                localFile.copyTo(destFile, overwrite = true)
                                BlockSyncEntity(block.type.name, localFile.name, block.sequence, block.isHalfWidth)
                            } else {
                                BlockSyncEntity(block.type.name, block.content, block.sequence, block.isHalfWidth)
                            }
                        } else {
                            BlockSyncEntity(block.type.name, block.content, block.sequence, block.isHalfWidth)
                        }
                    }

                    val syncData = ProjectSyncData(
                        project = ProjectSyncEntity(
                            name = projWithBlocks.project.name,
                            createdAt = projWithBlocks.project.createdAt,
                            updatedAt = projWithBlocks.project.updatedAt,
                            reportLabel = projWithBlocks.project.reportLabel,
                            showHeaderLabel = projWithBlocks.project.showHeaderLabel,
                            showHeaderDate = projWithBlocks.project.showHeaderDate,
                            headerCompany = projWithBlocks.project.headerCompany,
                            headerCompanySub = projWithBlocks.project.headerCompanySub,
                            headerTitle = projWithBlocks.project.headerTitle,
                            showHeaderBox = projWithBlocks.project.showHeaderBox,
                            showHeaderTitle = projWithBlocks.project.showHeaderTitle
                        ),
                        blocks = blockSyncs
                    )
                    
                    File(projDir, "project_data.json").writeText(syncAdapter.toJson(syncData))
                    updatesRemoteCount++
                }
            }

            emit(SyncState.Success("Sincronización completada: $downloadsCount importados, $updatesLocalCount locales actualizados, $updatesRemoteCount remotos actualizados."))
        } catch (e: Exception) {
            emit(SyncState.Error("Error de sincronización: ${e.localizedMessage}"))
        }
    }
}
