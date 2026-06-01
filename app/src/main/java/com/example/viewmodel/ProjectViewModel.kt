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
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
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

    // Draft blocks state of currently editing project
    private val _draftBlocks = MutableStateFlow<List<ContentBlockEntity>>(emptyList())
    val draftBlocks: StateFlow<List<ContentBlockEntity>> = _draftBlocks.asStateFlow()

    private val _originalBlocks = MutableStateFlow<List<ContentBlockEntity>>(emptyList())
    val originalBlocks: StateFlow<List<ContentBlockEntity>> = _originalBlocks.asStateFlow()

    // Determine if user made any edits
    val isDirty: StateFlow<Boolean> = combine(_draftBlocks, _originalBlocks) { draft, original ->
        draft != original
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
        if (id != null) {
            viewModelScope.launch {
                val projectWithBlocks = repository.getProjectById(id).filterNotNull().first()
                val blocks = projectWithBlocks.blocks.sortedBy { it.sequence }
                _originalBlocks.value = blocks
                _draftBlocks.value = blocks
            }
        } else {
            _originalBlocks.value = emptyList()
            _draftBlocks.value = emptyList()
        }
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
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextId = (currentDraft.minOfOrNull { it.id } ?: 0L).let { if (it < 0) it - 1 else -1L }
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        val newBlock = ContentBlockEntity(
            id = nextId,
            projectId = projectId,
            type = BlockType.TEXT,
            content = text,
            sequence = nextSequence
        )
        currentDraft.add(newBlock)
        _draftBlocks.value = currentDraft
    }

    fun addImageBlock(inputStream: InputStream) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            try {
                val filePath = repository.copyImageToLocalFile(projectId, inputStream)
                val currentDraft = _draftBlocks.value.toMutableList()
                val nextId = (currentDraft.minOfOrNull { it.id } ?: 0L).let { if (it < 0) it - 1 else -1L }
                val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
                
                val newBlock = ContentBlockEntity(
                    id = nextId,
                    projectId = projectId,
                    type = BlockType.IMAGE,
                    content = filePath,
                    sequence = nextSequence
                )
                currentDraft.add(newBlock)
                _draftBlocks.value = currentDraft
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addSignatureBlock(signatureBitmap: Bitmap) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            try {
                val filePath = repository.saveSignatureToLocalFile(projectId, signatureBitmap)
                val currentDraft = _draftBlocks.value.toMutableList()
                val nextId = (currentDraft.minOfOrNull { it.id } ?: 0L).let { if (it < 0) it - 1 else -1L }
                val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
                
                val newBlock = ContentBlockEntity(
                    id = nextId,
                    projectId = projectId,
                    type = BlockType.SIGNATURE,
                    content = filePath,
                    sequence = nextSequence
                )
                currentDraft.add(newBlock)
                _draftBlocks.value = currentDraft
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addTitleBlock(text: String) {
        val projectId = _selectedProjectId.value ?: return
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextId = (currentDraft.minOfOrNull { it.id } ?: 0L).let { if (it < 0) it - 1 else -1L }
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        val newBlock = ContentBlockEntity(
            id = nextId,
            projectId = projectId,
            type = BlockType.TITLE,
            content = text,
            sequence = nextSequence
        )
        currentDraft.add(newBlock)
        _draftBlocks.value = currentDraft
    }

    fun addFooterBlock(text: String) {
        val projectId = _selectedProjectId.value ?: return
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextId = (currentDraft.minOfOrNull { it.id } ?: 0L).let { if (it < 0) it - 1 else -1L }
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        val newBlock = ContentBlockEntity(
            id = nextId,
            projectId = projectId,
            type = BlockType.FOOTER,
            content = text,
            sequence = nextSequence
        )
        currentDraft.add(newBlock)
        _draftBlocks.value = currentDraft
    }

    fun addTableBlock(initialRowsAndCols: String = "Columna 1|Columna 2\nFila 1 Col 1|Fila 1 Col 2") {
        val projectId = _selectedProjectId.value ?: return
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextId = (currentDraft.minOfOrNull { it.id } ?: 0L).let { if (it < 0) it - 1 else -1L }
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        val newBlock = ContentBlockEntity(
            id = nextId,
            projectId = projectId,
            type = BlockType.TABLE,
            content = initialRowsAndCols,
            sequence = nextSequence
        )
        currentDraft.add(newBlock)
        _draftBlocks.value = currentDraft
    }

    fun addChecklistBlock(initialItems: String = "false|Elemento checklist 1\nfalse|Elemento checklist 2") {
        val projectId = _selectedProjectId.value ?: return
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextId = (currentDraft.minOfOrNull { it.id } ?: 0L).let { if (it < 0) it - 1 else -1L }
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        val newBlock = ContentBlockEntity(
            id = nextId,
            projectId = projectId,
            type = BlockType.CHECKLIST,
            content = initialItems,
            sequence = nextSequence
        )
        currentDraft.add(newBlock)
        _draftBlocks.value = currentDraft
    }

    fun moveBlockUp(block: ContentBlockEntity) {
        val currentDraft = _draftBlocks.value.toMutableList()
        val index = currentDraft.indexOfFirst { it.id == block.id }
        if (index > 0) {
            val elementCurrent = currentDraft[index]
            val elementPrev = currentDraft[index - 1]
            
            // Swap in list and update their sequences
            currentDraft[index] = elementPrev.copy(sequence = elementCurrent.sequence)
            currentDraft[index - 1] = elementCurrent.copy(sequence = elementPrev.sequence)
            
            _draftBlocks.value = currentDraft
        }
    }

    fun moveBlockDown(block: ContentBlockEntity) {
        val currentDraft = _draftBlocks.value.toMutableList()
        val index = currentDraft.indexOfFirst { it.id == block.id }
        if (index >= 0 && index < currentDraft.size - 1) {
            val elementCurrent = currentDraft[index]
            val elementNext = currentDraft[index + 1]
            
            // Swap in list and update their sequences
            currentDraft[index] = elementNext.copy(sequence = elementCurrent.sequence)
            currentDraft[index + 1] = elementCurrent.copy(sequence = elementNext.sequence)
            
            _draftBlocks.value = currentDraft
        }
    }

    fun toggleBlockWidth(block: ContentBlockEntity) {
        val currentDraft = _draftBlocks.value.map {
            if (it.id == block.id) {
                it.copy(isHalfWidth = !it.isHalfWidth)
            } else {
                it
            }
        }
        _draftBlocks.value = currentDraft
    }

    fun updateBlockText(block: ContentBlockEntity, newText: String) {
        val currentDraft = _draftBlocks.value.map {
            if (it.id == block.id) {
                it.copy(content = newText)
            } else {
                it
            }
        }
        _draftBlocks.value = currentDraft
    }

    fun deleteBlock(block: ContentBlockEntity) {
        val currentDraft = _draftBlocks.value.filter { it.id != block.id }
        _draftBlocks.value = currentDraft
    }

    fun saveDraft(onSaved: () -> Unit = {}) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            val draft = _draftBlocks.value
            val original = _originalBlocks.value

            // 1. Delete blocks removed from the draft
            val draftIds = draft.map { it.id }.toSet()
            val deletedBlocks = original.filter { it.id !in draftIds }
            for (del in deletedBlocks) {
                repository.deleteBlock(del)
            }

            // 2. Insert or update the current draft blocks with updated sequence
            draft.forEachIndexed { idx, block ->
                val updatedBlock = block.copy(sequence = idx)
                if (updatedBlock.id <= 0) {
                    val newBlock = ContentBlockEntity(
                        id = 0,
                        projectId = updatedBlock.projectId,
                        type = updatedBlock.type,
                        content = updatedBlock.content,
                        sequence = updatedBlock.sequence,
                        isHalfWidth = updatedBlock.isHalfWidth
                    )
                    repository.insertBlock(newBlock)
                } else {
                    repository.updateBlock(updatedBlock)
                }
            }

            // 3. Reload saved blocks from DB
            val freshProject = repository.getProjectById(projectId).filterNotNull().first()
            val sorted = freshProject.blocks.sortedBy { it.sequence }
            _originalBlocks.value = sorted
            _draftBlocks.value = sorted
            onSaved()
        }
    }

    fun discardChanges() {
        _draftBlocks.value = _originalBlocks.value
    }

    fun exportPdf() {
        val project = selectedProject.value ?: return
        val currentDraft = _draftBlocks.value
        val draftProject = ProjectWithBlocks(project = project.project, blocks = currentDraft)
        viewModelScope.launch {
            _isGeneratingPdf.value = true
            try {
                val pdfFile = repository.generatePdf(draftProject)
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
