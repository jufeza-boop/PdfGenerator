package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

object MoshiProvider {
    val instance: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }
}

class JsonProjectStore(
    private val workspaceManager: WorkspaceManager,
    private val moshi: Moshi = MoshiProvider.instance
) {
    private val _allProjects = MutableStateFlow<List<ProjectData>>(emptyList())
    val allProjects: StateFlow<List<ProjectData>> = _allProjects.asStateFlow()

    private val projectAdapter = moshi.adapter(ProjectData::class.java)
    private val manifestAdapter = moshi.adapter(ManifestData::class.java)

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val projects = mutableListOf<ProjectData>()
        val loadedUuids = mutableSetOf<String>()
        
        val accessor = workspaceManager.getAccessor() ?: return@withContext
        if (accessor.exists("manifest.json")) {
            try {
                val manifestText = accessor.readText("manifest.json")
                if (manifestText != null) {
                    val manifest = manifestAdapter.fromJson(manifestText)
                    manifest?.projects?.forEach { entry ->
                        val dataFile = "${entry.uuid}/project_data.json"
                        if (accessor.exists(dataFile)) {
                            accessor.readText(dataFile)?.let { text ->
                                projectAdapter.fromJson(text)?.let { 
                                    projects.add(it)
                                    loadedUuids.add(it.uuid)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Siempre escaneamos carpetas para encontrar proyectos copiados manualmente que no estén en el manifest
        scanFoldersForProjects(projects, loadedUuids)
        
        _allProjects.value = projects.sortedByDescending { it.createdAt }
        saveManifest()
    }

    private suspend fun scanFoldersForProjects(projects: MutableList<ProjectData>, skipUuids: Set<String> = emptySet()) {
        val accessor = workspaceManager.getAccessor() ?: return
        val dirs = accessor.listDirectories("")
        for (dir in dirs) {
            if (dir in skipUuids) continue
            val dataFile = "$dir/project_data.json"
            if (accessor.exists(dataFile)) {
                try {
                    accessor.readText(dataFile)?.let { text ->
                        try {
                            projectAdapter.fromJson(text)?.let { projects.add(it) }
                        } catch (e: Exception) {
                            // Intento de migración desde formato legacy
                            val legacyAdapter = moshi.adapter(ProjectSyncData::class.java)
                            try {
                                legacyAdapter.fromJson(text)?.let { legacy ->
                                    val projectData = ProjectData(
                                        uuid = dir,
                                        name = legacy.project.name,
                                        createdAt = legacy.project.createdAt,
                                        updatedAt = legacy.project.updatedAt,
                                        reportLabel = legacy.project.reportLabel,
                                        showHeaderLabel = legacy.project.showHeaderLabel,
                                        showHeaderDate = legacy.project.showHeaderDate,
                                        headerCompany = legacy.project.headerCompany,
                                        headerCompanySub = legacy.project.headerCompanySub,
                                        headerTitle = legacy.project.headerTitle,
                                        showHeaderBox = legacy.project.showHeaderBox,
                                        showHeaderTitle = legacy.project.showHeaderTitle,
                                        visits = emptyList(), // Legacy did not support visits natively
                                        blocks = legacy.blocks.map { b ->
                                            BlockData(
                                                uuid = java.util.UUID.randomUUID().toString(),
                                                type = b.type,
                                                content = b.content,
                                                sequence = b.sequence,
                                                isHalfWidth = b.isHalfWidth
                                            )
                                        }
                                    )
                                    projects.add(projectData)
                                    // Guardar el proyecto migrado
                                    accessor.writeText(dataFile, projectAdapter.toJson(projectData))
                                }
                            } catch (e2: Exception) {
                                e2.printStackTrace()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun saveProject(project: ProjectData) = withContext(Dispatchers.IO) {
        val dataFile = "${project.uuid}/project_data.json"
        
        try {
            val accessor = workspaceManager.getAccessor() ?: return@withContext
            val json = projectAdapter.toJson(project)
            accessor.writeText(dataFile, json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val current = _allProjects.value.toMutableList()
        val index = current.indexOfFirst { it.uuid == project.uuid }
        if (index >= 0) {
            current[index] = project
        } else {
            current.add(project)
        }
        _allProjects.value = current.sortedByDescending { it.createdAt }
        
        saveManifest()
    }

    suspend fun deleteProject(uuid: String) = withContext(Dispatchers.IO) {
        val accessor = workspaceManager.getAccessor() ?: return@withContext
        accessor.delete(uuid)
        _allProjects.value = _allProjects.value.filter { it.uuid != uuid }
        saveManifest()
    }

    fun getProject(uuid: String): ProjectData? {
        return _allProjects.value.find { it.uuid == uuid }
    }

    private suspend fun saveManifest() {
        try {
            val accessor = workspaceManager.getAccessor() ?: return
            val entries = _allProjects.value.map { 
                ManifestEntry(uuid = it.uuid, name = it.name, createdAt = it.createdAt, updatedAt = it.updatedAt)
            }
            val manifest = ManifestData(version = 1, projects = entries)
            val json = manifestAdapter.toJson(manifest)
            accessor.writeText("manifest.json", json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
