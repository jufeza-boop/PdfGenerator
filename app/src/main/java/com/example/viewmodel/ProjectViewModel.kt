package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository

    // List of all projects for the dashboard
    val allProjects: StateFlow<List<ProjectWithBlocks>>

    // Selected project state
    private val _selectedProjectId = MutableStateFlow<Long?>(null)
    val selectedProjectId: StateFlow<Long?> = _selectedProjectId.asStateFlow()

    // Retrieve active details reactively using selected ID mapping
    val selectedProject: StateFlow<ProjectWithBlocks?> = _selectedProjectId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.getProjectById(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Export PDF generation process states
    private val _generatedPdfFile = MutableStateFlow<File?>(null)
    val generatedPdfFile: StateFlow<File?> = _generatedPdfFile.asStateFlow()

    private val _isGeneratingPdf = MutableStateFlow(false)
    val isGeneratingPdf: StateFlow<Boolean> = _isGeneratingPdf.asStateFlow()

    private val _isUploadingCloud = MutableStateFlow(false)
    val isUploadingCloud: StateFlow<Boolean> = _isUploadingCloud.asStateFlow()

    private val _uploadSuccess = MutableSharedFlow<Boolean>()
    val uploadSuccess: SharedFlow<Boolean> = _uploadSuccess.asSharedFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ProjectRepository(application, database.projectDao())
        
        allProjects = repository.allProjects
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun selectProject(id: Long?) {
        _selectedProjectId.value = id
        _generatedPdfFile.value = null // reset preview when changing project
    }

    fun createProject(name: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.createProject(name)
            onCreated(id)
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.deleteProject(project)
            if (_selectedProjectId.value == project.id) {
                _selectedProjectId.value = null
            }
        }
    }

    fun addTextBlock(text: String) {
        val projectId = _selectedProjectId.value ?: return
        val currentBlocks = selectedProject.value?.blocks ?: emptyList()
        val nextSequence = (currentBlocks.maxOfOrNull { it.sequence } ?: -1) + 1
        
        viewModelScope.launch {
            repository.addTextBlock(projectId, text, nextSequence)
        }
    }

    fun addImageBlock(inputStream: InputStream) {
        val projectId = _selectedProjectId.value ?: return
        val currentBlocks = selectedProject.value?.blocks ?: emptyList()
        val nextSequence = (currentBlocks.maxOfOrNull { it.sequence } ?: -1) + 1

        viewModelScope.launch {
            repository.saveImageBlock(projectId, inputStream, nextSequence)
        }
    }

    fun addSignatureBlock(signatureBitmap: Bitmap) {
        val projectId = _selectedProjectId.value ?: return
        val currentBlocks = selectedProject.value?.blocks ?: emptyList()
        val nextSequence = (currentBlocks.maxOfOrNull { it.sequence } ?: -1) + 1

        viewModelScope.launch {
            repository.saveSignatureBlock(projectId, signatureBitmap, nextSequence)
        }
    }

    fun updateBlockText(block: ContentBlockEntity, newText: String) {
        viewModelScope.launch {
            repository.updateBlockContent(block.id, block.projectId, block.type, newText, block.sequence)
        }
    }

    fun deleteBlock(block: ContentBlockEntity) {
        viewModelScope.launch {
            repository.deleteBlock(block)
        }
    }

    fun exportPdf() {
        val project = selectedProject.value ?: return
        viewModelScope.launch {
            _isGeneratingPdf.value = true
            try {
                val pdfFile = repository.generatePdf(project)
                _generatedPdfFile.value = pdfFile
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isGeneratingPdf.value = false
            }
        }
    }

    fun uploadPdfToCloud() {
        val file = _generatedPdfFile.value ?: return
        viewModelScope.launch {
            _isUploadingCloud.value = true
            try {
                val success = repository.uploadPdfToCloudMock(file)
                _uploadSuccess.emit(success)
            } catch (e: Exception) {
                e.printStackTrace()
                _uploadSuccess.emit(false)
            } finally {
                _isUploadingCloud.value = false
            }
        }
    }

    fun clearPdfPreviewState() {
        _generatedPdfFile.value = null
    }
}
