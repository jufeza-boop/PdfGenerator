package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectViewModel(
    private val repository: ProjectRepository,
    private val workspaceManager: WorkspaceManager,
    private val store: JsonProjectStore
) : ViewModel() {

    // List of all projects for the dashboard
    val allProjects: StateFlow<List<ProjectData>> = repository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _workspaceConfigured = MutableStateFlow(workspaceManager.getAccessor() != null)
    val workspaceConfigured: StateFlow<Boolean> = _workspaceConfigured.asStateFlow()

    fun setWorkspaceUri(uri: String) {
        workspaceManager.saveWorkspaceUri(uri)
        _workspaceConfigured.value = true
        viewModelScope.launch {
            store.initialize()
        }
    }

    // Selected project state
    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId: StateFlow<String?> = _selectedProjectId.asStateFlow()

    // Retrieve active details reactively using selected ID mapping
    val selectedProject: StateFlow<ProjectData?> = _selectedProjectId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.getProjectById(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Draft blocks state of currently editing project
    private val _draftBlocks = MutableStateFlow<List<BlockData>>(emptyList())
    val draftBlocks: StateFlow<List<BlockData>> = _draftBlocks.asStateFlow()

    private val _originalBlocks = MutableStateFlow<List<BlockData>>(emptyList())
    val originalBlocks: StateFlow<List<BlockData>> = _originalBlocks.asStateFlow()

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
        viewModelScope.launch {
            if (workspaceManager.getAccessor() != null) {
                store.initialize()
            }
        }
    }

    fun selectProject(id: String?) {
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

    fun createProject(name: String, templateType: String = "NONE", onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        val projectId = repository.createProject(name, templateType)
        onCreated(projectId)
    }

    fun deleteProject(project: ProjectData) {
        viewModelScope.launch {
            repository.deleteProject(project)
            if (_selectedProjectId.value == project.uuid) {
                _selectedProjectId.value = null
            }
        }
    }

    fun addTextBlock(text: String, visitId: String? = null) {
        val projectId = _selectedProjectId.value ?: return
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        val newBlock = BlockData(
            uuid = "draft_${UUID.randomUUID()}",
            type = BlockType.TEXT.name,
            content = text,
            sequence = nextSequence,
            visitUuid = visitId
        )
        currentDraft.add(newBlock)
        _draftBlocks.value = currentDraft
    }

    fun addImageBlock(inputStream: InputStream, visitId: String? = null) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            try {
                val filePath = repository.copyImageToLocalFile(projectId, inputStream)
                val currentDraft = _draftBlocks.value.toMutableList()
                val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
                
                val newBlock = BlockData(
                    uuid = "draft_${UUID.randomUUID()}",
                    type = BlockType.IMAGE.name,
                    content = filePath,
                    sequence = nextSequence,
                    visitUuid = visitId
                )
                currentDraft.add(newBlock)
                _draftBlocks.value = currentDraft
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addSignatureBlock(signatureBytes: ByteArray, visitId: String? = null) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            try {
                val filePath = repository.saveSignatureToLocalFile(projectId, signatureBytes)
                val currentDraft = _draftBlocks.value.toMutableList()
                val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
                
                val newBlock = BlockData(
                    uuid = "draft_${UUID.randomUUID()}",
                    type = BlockType.SIGNATURE.name,
                    content = filePath,
                    sequence = nextSequence,
                    visitUuid = visitId
                )
                currentDraft.add(newBlock)
                _draftBlocks.value = currentDraft
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addTitleBlock(text: String, visitId: String? = null) {
        val projectId = _selectedProjectId.value ?: return
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        val newBlock = BlockData(
            uuid = "draft_${UUID.randomUUID()}",
            type = BlockType.TITLE.name,
            content = text,
            sequence = nextSequence,
            visitUuid = visitId
        )
        currentDraft.add(newBlock)
        _draftBlocks.value = currentDraft
    }

    fun addFooterBlock(text: String, visitId: String? = null) {
        val projectId = _selectedProjectId.value ?: return
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        val newBlock = BlockData(
            uuid = "draft_${UUID.randomUUID()}",
            type = BlockType.FOOTER.name,
            content = text,
            sequence = nextSequence,
            visitUuid = visitId
        )
        currentDraft.add(newBlock)
        _draftBlocks.value = currentDraft
    }

    fun addTableBlock(visitId: String? = null) {
        val projectId = _selectedProjectId.value ?: return
        val json = repository.getDefaultTableBlockJson()
        
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        currentDraft.add(BlockData(uuid = "draft_${UUID.randomUUID()}", type = BlockType.TABLE.name, content = json, sequence = nextSequence, visitUuid = visitId))
        _draftBlocks.value = currentDraft
    }

    fun addChecklistBlock(visitId: String? = null) {
        val projectId = _selectedProjectId.value ?: return
        val json = repository.getDefaultChecklistBlockJson()
        
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        currentDraft.add(BlockData(uuid = "draft_${UUID.randomUUID()}", type = BlockType.CHECKLIST.name, content = json, sequence = nextSequence, visitUuid = visitId))
        _draftBlocks.value = currentDraft
    }

    fun addChecklistTableBlock(visitId: String? = null) {
        val projectId = _selectedProjectId.value ?: return
        val json = repository.getDefaultChecklistTableBlockJson()
        
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        currentDraft.add(BlockData(uuid = "draft_${UUID.randomUUID()}", type = BlockType.CHECKLIST_TABLE.name, content = json, sequence = nextSequence, visitUuid = visitId))
        _draftBlocks.value = currentDraft
    }

    fun moveBlockUp(block: BlockData) {
        val currentDraft = _draftBlocks.value.toMutableList()
        val index = currentDraft.indexOfFirst { it.uuid == block.uuid }
        if (index > 0) {
            val elementCurrent = currentDraft[index]
            val elementPrev = currentDraft[index - 1]
            
            // Swap in list and update their sequences
            currentDraft[index] = elementPrev.copy(sequence = elementCurrent.sequence)
            currentDraft[index - 1] = elementCurrent.copy(sequence = elementPrev.sequence)
            
            _draftBlocks.value = currentDraft
        }
    }

    fun moveBlockDown(block: BlockData) {
        val currentDraft = _draftBlocks.value.toMutableList()
        val index = currentDraft.indexOfFirst { it.uuid == block.uuid }
        if (index >= 0 && index < currentDraft.size - 1) {
            val elementCurrent = currentDraft[index]
            val elementNext = currentDraft[index + 1]
            
            // Swap in list and update their sequences
            currentDraft[index] = elementNext.copy(sequence = elementCurrent.sequence)
            currentDraft[index + 1] = elementCurrent.copy(sequence = elementNext.sequence)
            
            _draftBlocks.value = currentDraft
        }
    }

    fun toggleBlockWidth(block: BlockData) {
        val currentDraft = _draftBlocks.value.map {
            if (it.uuid == block.uuid) {
                it.copy(isHalfWidth = !it.isHalfWidth)
            } else {
                it
            }
        }
        _draftBlocks.value = currentDraft
    }

    fun updateBlockText(block: BlockData, newText: String) {
        val currentDraft = _draftBlocks.value.map {
            if (it.uuid == block.uuid) {
                it.copy(content = newText)
            } else {
                it
            }
        }
        _draftBlocks.value = currentDraft
    }

    fun updateProjectInfo(
        name: String,
        reportLabel: String,
        showHeaderLabel: Boolean,
        showHeaderDate: Boolean,
        headerCompany: String,
        headerCompanySub: String,
        headerTitle: String,
        showHeaderBox: Boolean,
        showHeaderTitle: Boolean
    ) {
        val project = selectedProject.value ?: return
        viewModelScope.launch {
            val updated = project.copy(
                name = name,
                reportLabel = reportLabel,
                showHeaderLabel = showHeaderLabel,
                showHeaderDate = showHeaderDate,
                headerCompany = headerCompany,
                headerCompanySub = headerCompanySub,
                headerTitle = headerTitle,
                showHeaderBox = showHeaderBox,
                showHeaderTitle = showHeaderTitle,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateProject(updated)
        }
    }

    fun updateSignatureDrawing(blockUuid: String, signatureBytes: ByteArray) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            try {
                val filePath = repository.saveSignatureToLocalFile(projectId, signatureBytes)
                val currentDraft = _draftBlocks.value.map {
                    if (it.uuid == blockUuid) {
                        val parts = it.content.split("|")
                        val labelText = parts.getOrNull(1)?.ifBlank { "" } ?: ""
                        val subtitleText = parts.getOrNull(2)?.ifBlank { "" } ?: ""
                        val newContent = "$filePath|$labelText|$subtitleText"
                        it.copy(content = newContent)
                    } else {
                        it
                    }
                }
                _draftBlocks.value = currentDraft
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteBlock(block: BlockData) {
        val currentDraft = _draftBlocks.value.filter { it.uuid != block.uuid }
        _draftBlocks.value = currentDraft
    }

    fun saveDraft(onSaved: () -> Unit = {}) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            val draft = _draftBlocks.value
            val original = _originalBlocks.value

            // 1. Delete blocks removed from the draft
            val draftIds = draft.map { it.uuid }.toSet()
            val deletedBlocks = original.filter { it.uuid !in draftIds }
            for (del in deletedBlocks) {
                repository.deleteBlock(projectId, del)
            }

            // 2. Insert or update the current draft blocks with updated sequence
            draft.forEachIndexed { idx, block ->
                val updatedBlock = block.copy(sequence = idx)
                if (updatedBlock.uuid.startsWith("draft_")) {
                    val newBlock = updatedBlock.copy(uuid = UUID.randomUUID().toString())
                    repository.insertBlock(projectId, newBlock)
                } else {
                    repository.updateBlock(projectId, updatedBlock)
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

    fun createVisit(title: String, notes: String, templateType: String = "NONE", date: Long = System.currentTimeMillis()) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            repository.createVisit(projectId, title, notes, templateType, date)
            val freshProject = repository.getProjectById(projectId).filterNotNull().first()
            val sorted = freshProject.blocks.sortedBy { it.sequence }
            _originalBlocks.value = sorted
            _draftBlocks.value = sorted
        }
    }

    fun deleteVisit(visit: VisitData) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            repository.deleteVisit(projectId, visit.uuid)
        }
    }

    fun updateVisit(visit: VisitData) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            repository.updateVisit(projectId, visit)
        }
    }

    fun exportPdf(exportMode: PdfExportMode = PdfExportMode.FULL_REPORT, singleVisitId: String? = null) {
        val project = selectedProject.value ?: return
        val currentDraft = _draftBlocks.value
        val draftProject = project.copy(blocks = currentDraft)
        viewModelScope.launch {
            _isGeneratingPdf.value = true
            try {
                val pdfFile = repository.generatePdf(draftProject, exportMode, singleVisitId)
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
