package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class FolderSyncOrchestrator(
    private val repository: ProjectRepository,
    private val folderAccessor: FolderAccessor
) : FolderSyncManager {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val syncAdapter = moshi.adapter(ProjectSyncData::class.java)

    override fun getConfig(): FolderSyncConfig {
        return folderAccessor.getConfig()
    }

    override fun saveConfig(rootFolderUri: String, isAutoSync: Boolean) {
        folderAccessor.saveConfig(rootFolderUri, isAutoSync)
    }

    override fun isConfigured(): Boolean {
        return folderAccessor.isConfigured()
    }

    override fun deleteProjectFolder(createdAt: Long): Boolean {
        return folderAccessor.deleteProjectFolder(createdAt)
    }

    private fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    override fun runSync(realSync: Boolean): Flow<SyncState> = flow {
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

        if (!folderAccessor.isConfigured()) {
            emit(SyncState.Error("La carpeta seleccionada no es accesible o faltan permisos de escritura."))
            return@flow
        }

        try {
            emit(SyncState.Syncing("Leyendo Archivos", 0.25f, "Explorando subcarpetas en tu carpeta de trabajo..."))
            delay(400)

            val remoteSubDirs = folderAccessor.listSubFolders()
            val handledLocalCreatedAts = mutableSetOf<Long>()
            var downloadsCount = 0
            var updatesLocalCount = 0
            var updatesRemoteCount = 0

            withContext(Dispatchers.IO) {
                remoteSubDirs.forEach { subDir ->
                    val fileContents = subDir.readProjectDataJson()
                    if (!fileContents.isNullOrBlank()) {
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
                                // Download new project from remote
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
                                        val destDirName = if (bType == BlockType.IMAGE) "project_${insertedId}_images" else "project_${insertedId}_signatures"
                                        val destDir = File(repository.filesDir, destDirName).apply { if (!exists()) mkdirs() }
                                        val destFile = File(destDir, b.content)
                                        val copied = subDir.copyFileToLocal(b.content, destFile)
                                        if (copied) {
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
                                // Update existing local project
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
                                        val destDirName = if (bType == BlockType.IMAGE) "project_${matchedLocal.project.id}_images" else "project_${matchedLocal.project.id}_signatures"
                                        val destDir = File(repository.filesDir, destDirName).apply { if (!exists()) mkdirs() }
                                        val destFile = File(destDir, b.content)
                                        val copied = subDir.copyFileToLocal(b.content, destFile)
                                        if (copied) {
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

                // Identify local projects that are new or newer than remote
                val projectsToExport = localProjects.filter { localProj ->
                    !handledLocalCreatedAts.contains(localProj.project.createdAt) || run {
                        val suffix = "_${localProj.project.createdAt}"
                        val remoteDir = remoteSubDirs.find { it.name.endsWith(suffix) }
                        if (remoteDir != null) {
                            val json = remoteDir.readProjectDataJson()
                            if (json != null) {
                                val remoteUpdatedAt = try { syncAdapter.fromJson(json)?.project?.updatedAt ?: 0 } catch (e: Exception) { 0 }
                                remoteUpdatedAt < localProj.project.updatedAt
                            } else true
                        } else true
                    }
                }

                projectsToExport.forEach { projWithBlocks ->
                    val folderName = "${sanitizeFolderName(projWithBlocks.project.name)}_${projWithBlocks.project.createdAt}"
                    val projDir = folderAccessor.getOrCreateSubFolder(folderName, projWithBlocks.project.createdAt)
                        ?: throw Exception("Failed to get/create remote folder: $folderName")

                    val processedBlocks = projWithBlocks.blocks.map { block ->
                        if ((block.type == BlockType.IMAGE || block.type == BlockType.SIGNATURE) && block.content.startsWith("/")) {
                            val localFile = File(block.content)
                            if (localFile.exists()) {
                                projDir.deleteFile(localFile.name)
                                val copied = projDir.copyFileFromLocal(localFile.name, localFile)
                                if (copied) {
                                    block.copy(content = localFile.name)
                                } else block
                            } else block
                        } else block
                    }

                    val syncProj = ProjectSyncEntity(
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
                    )
                    val syncBlocks = processedBlocks.map { BlockSyncEntity(type = it.type.name, content = it.content, sequence = it.sequence, isHalfWidth = it.isHalfWidth) }
                    val syncData = ProjectSyncData(project = syncProj, blocks = syncBlocks)
                    val jsonString = syncAdapter.toJson(syncData)
                    projDir.writeProjectDataJson(jsonString)
                    updatesRemoteCount++
                }
            }

            emit(SyncState.Success("Sincronización completada. Descargados: $downloadsCount, Actualizados localmente: $updatesLocalCount, Subidos/Actualizados remotos: $updatesRemoteCount."))
        } catch (e: Exception) {
            emit(SyncState.Error("Fallo durante la sincronización: ${e.localizedMessage}"))
        }
    }
}
