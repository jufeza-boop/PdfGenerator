package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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
    private val _projectSummaries = MutableStateFlow<List<ManifestEntry>>(emptyList())
    val projectSummaries: StateFlow<List<ManifestEntry>> = _projectSummaries.asStateFlow()

    private val _customTemplates = MutableStateFlow<List<CustomTemplateData>>(emptyList())
    val customTemplates: StateFlow<List<CustomTemplateData>> = _customTemplates.asStateFlow()

    private val projectAdapter = moshi.adapter(ProjectData::class.java)
    private val manifestAdapter = moshi.adapter(ManifestData::class.java)
    private val templatesType = Types.newParameterizedType(List::class.java, CustomTemplateData::class.java)
    private val templatesAdapter = moshi.adapter<List<CustomTemplateData>>(templatesType)

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val entries = mutableListOf<ManifestEntry>()
        
        val accessor = workspaceManager.getAccessor() ?: return@withContext
        if (accessor.exists("manifest.json")) {
            try {
                val manifestText = accessor.readText("manifest.json")
                if (manifestText != null) {
                    val manifest = manifestAdapter.fromJson(manifestText)
                    if (manifest != null) {
                        entries.addAll(manifest.projects)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        _projectSummaries.value = entries.sortedByDescending { it.updatedAt }
        
        if (accessor.exists("templates.json")) {
            try {
                accessor.readText("templates.json")?.let { text ->
                    templatesAdapter.fromJson(text)?.let {
                        _customTemplates.value = it
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        saveManifest()
    }

    suspend fun syncFolders() = withContext(Dispatchers.IO) {
        val entries = _projectSummaries.value.toMutableList()
        val loadedUuids = entries.map { it.uuid }.toSet()
        val accessor = workspaceManager.getAccessor() ?: return@withContext
        val dirs = accessor.listDirectories("")
        var changed = false
        for (dir in dirs) {
            if (dir in loadedUuids) continue
            val dataFile = "$dir/project_data.json"
            if (accessor.exists(dataFile)) {
                try {
                    accessor.readText(dataFile)?.let { text ->
                        try {
                            projectAdapter.fromJson(text)?.let { 
                                entries.add(ManifestEntry(it.uuid, it.name, it.createdAt, it.updatedAt))
                                changed = true
                            }
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
                                        visits = emptyList(),
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
                                    entries.add(ManifestEntry(projectData.uuid, projectData.name, projectData.createdAt, projectData.updatedAt))
                                    changed = true
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
        if (changed) {
            _projectSummaries.value = entries.sortedByDescending { it.updatedAt }
            saveManifest()
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
        
        val current = _projectSummaries.value.toMutableList()
        val index = current.indexOfFirst { it.uuid == project.uuid }
        val newEntry = ManifestEntry(project.uuid, project.name, project.createdAt, project.updatedAt)
        if (index >= 0) {
            current[index] = newEntry
        } else {
            current.add(newEntry)
        }
        _projectSummaries.value = current.sortedByDescending { it.updatedAt }
        
        saveManifest()
    }

    suspend fun deleteProject(uuid: String) = withContext(Dispatchers.IO) {
        val accessor = workspaceManager.getAccessor() ?: return@withContext
        accessor.delete(uuid)
        _projectSummaries.value = _projectSummaries.value.filter { it.uuid != uuid }
        saveManifest()
    }

    suspend fun getProject(uuid: String): ProjectData? = withContext(Dispatchers.IO) {
        val accessor = workspaceManager.getAccessor() ?: return@withContext null
        val dataFile = "$uuid/project_data.json"
        if (accessor.exists(dataFile)) {
            try {
                accessor.readText(dataFile)?.let { text ->
                    return@withContext projectAdapter.fromJson(text)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext null
    }

    private suspend fun saveManifest() {
        try {
            val accessor = workspaceManager.getAccessor() ?: return
            val manifest = ManifestData(version = 1, projects = _projectSummaries.value)
            val json = manifestAdapter.toJson(manifest)
            accessor.writeText("manifest.json", json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveCustomTemplate(template: CustomTemplateData) = withContext(Dispatchers.IO) {
        val current = _customTemplates.value.toMutableList()
        val index = current.indexOfFirst { it.uuid == template.uuid }
        if (index >= 0) current[index] = template else current.add(template)
        _customTemplates.value = current
        saveTemplates()
    }

    suspend fun deleteCustomTemplate(uuid: String) = withContext(Dispatchers.IO) {
        _customTemplates.value = _customTemplates.value.filter { it.uuid != uuid }
        saveTemplates()
    }

    private suspend fun saveTemplates() {
        try {
            val accessor = workspaceManager.getAccessor() ?: return
            val json = templatesAdapter.toJson(_customTemplates.value)
            accessor.writeText("templates.json", json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
