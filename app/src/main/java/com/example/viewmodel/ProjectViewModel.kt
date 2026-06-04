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
    val syncManager: GithubSyncManager

    private val _syncConfig = MutableStateFlow<SyncConfig?>(null)
    val syncConfig: StateFlow<SyncConfig?> = _syncConfig.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

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
        syncManager = GithubSyncManager(application, repository)
        _syncConfig.value = syncManager.getConfig()
        
        allProjects = repository.allProjects
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun updateSyncConfig(githubToken: String, githubOwner: String, githubRepo: String, githubBranch: String, isAutoSync: Boolean) {
        syncManager.saveConfig(githubToken, githubOwner, githubRepo, githubBranch, isAutoSync)
        _syncConfig.value = syncManager.getConfig()
    }

    fun runGithubSync(realSync: Boolean) {
        viewModelScope.launch {
            syncManager.runSync(realSync).collect { state ->
                _syncState.value = state
            }
        }
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
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

    fun createProject(name: String, templateType: String = "NONE", onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.createProject(name, templateType)
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

    fun addTextBlock(text: String, visitId: Long? = null) {
        val projectId = _selectedProjectId.value ?: return
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextId = (currentDraft.minOfOrNull { it.id } ?: 0L).let { if (it < 0) it - 1 else -1L }
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        val newBlock = ContentBlockEntity(
            id = nextId,
            projectId = projectId,
            type = BlockType.TEXT,
            content = text,
            sequence = nextSequence,
            visitId = visitId
        )
        currentDraft.add(newBlock)
        _draftBlocks.value = currentDraft
    }

    fun addImageBlock(inputStream: InputStream, visitId: Long? = null) {
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
                    sequence = nextSequence,
                    visitId = visitId
                )
                currentDraft.add(newBlock)
                _draftBlocks.value = currentDraft
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addSignatureBlock(signatureBitmap: Bitmap, visitId: Long? = null) {
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
                    sequence = nextSequence,
                    visitId = visitId
                )
                currentDraft.add(newBlock)
                _draftBlocks.value = currentDraft
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addTitleBlock(text: String, visitId: Long? = null) {
        val projectId = _selectedProjectId.value ?: return
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextId = (currentDraft.minOfOrNull { it.id } ?: 0L).let { if (it < 0) it - 1 else -1L }
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        val newBlock = ContentBlockEntity(
            id = nextId,
            projectId = projectId,
            type = BlockType.TITLE,
            content = text,
            sequence = nextSequence,
            visitId = visitId
        )
        currentDraft.add(newBlock)
        _draftBlocks.value = currentDraft
    }

    fun addFooterBlock(text: String, visitId: Long? = null) {
        val projectId = _selectedProjectId.value ?: return
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextId = (currentDraft.minOfOrNull { it.id } ?: 0L).let { if (it < 0) it - 1 else -1L }
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        val newBlock = ContentBlockEntity(
            id = nextId,
            projectId = projectId,
            type = BlockType.FOOTER,
            content = text,
            sequence = nextSequence,
            visitId = visitId
        )
        currentDraft.add(newBlock)
        _draftBlocks.value = currentDraft
    }

    fun addTableBlock(initialRowsAndCols: String = "Columna 1|Columna 2\nFila 1 Col 1|Fila 1 Col 2", visitId: Long? = null) {
        val projectId = _selectedProjectId.value ?: return
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextId = (currentDraft.minOfOrNull { it.id } ?: 0L).let { if (it < 0) it - 1 else -1L }
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        val newBlock = ContentBlockEntity(
            id = nextId,
            projectId = projectId,
            type = BlockType.TABLE,
            content = initialRowsAndCols,
            sequence = nextSequence,
            visitId = visitId
        )
        currentDraft.add(newBlock)
        _draftBlocks.value = currentDraft
    }

    fun addChecklistBlock(initialItems: String = "false|Elemento checklist 1\nfalse|Elemento checklist 2", visitId: Long? = null) {
        val projectId = _selectedProjectId.value ?: return
        val currentDraft = _draftBlocks.value.toMutableList()
        val nextId = (currentDraft.minOfOrNull { it.id } ?: 0L).let { if (it < 0) it - 1 else -1L }
        val nextSequence = (currentDraft.maxOfOrNull { it.sequence } ?: -1) + 1
        
        val newBlock = ContentBlockEntity(
            id = nextId,
            projectId = projectId,
            type = BlockType.CHECKLIST,
            content = initialItems,
            sequence = nextSequence,
            visitId = visitId
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

    fun updateProjectInfo(
        name: String,
        reportLabel: String,
        showHeaderLabel: Boolean,
        showHeaderDate: Boolean,
        headerCompany: String,
        headerCompanySub: String,
        headerTitle: String,
        showHeaderBox: Boolean
    ) {
        val project = selectedProject.value?.project ?: return
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
                updatedAt = System.currentTimeMillis()
            )
            repository.updateProject(updated)
        }
    }

    fun updateSignatureDrawing(blockId: Long, signatureBitmap: android.graphics.Bitmap) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            try {
                val filePath = repository.saveSignatureToLocalFile(projectId, signatureBitmap)
                val currentDraft = _draftBlocks.value.map {
                    if (it.id == blockId) {
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
                        isHalfWidth = updatedBlock.isHalfWidth,
                        visitId = updatedBlock.visitId
                    )
                    repository.insertBlock(newBlock)
                } else {
                    repository.updateBlock(updatedBlock)
                }
            }

            // 3. Update project updatedAt when blocks are changed
            val currentProj = selectedProject.value?.project
            if (currentProj != null) {
                repository.updateProject(currentProj.copy(updatedAt = System.currentTimeMillis()))
            }

            // 4. Reload saved blocks from DB
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
            val visit = VisitEntity(projectId = projectId, title = title, notes = notes, date = date)
            val visitId = repository.insertVisit(visit)
            
            val currentMaxSeq = _draftBlocks.value.maxOfOrNull { it.sequence } ?: -1
            var nextSeq = currentMaxSeq + 1
            
            val blocksToInsert = mutableListOf<ContentBlockEntity>()
            if (templateType == "ACTA_VISITA") {
                blocksToInsert.addAll(listOf(
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "ASISTENTES A LA VISITA", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TABLE, content = "Entidad / Parte | Representantes Asistentes\nDe la parte promotora | [Asistentes de la propiedad]\nDe la parte constructora | [Asistentes del contratista / Jefe de Obra]\nDe la Dirección Facultativa | -\nOtros | -", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "DATOS DE LA VISITA", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TEXT, content = "DÍA DE LA VISITA: [DD/MM/AAAA]", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "ESTADO DE LA OBRA, TEMAS TRATADOS Y OBSERVACIONES", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.CHECKLIST, content = "false|Hormigonado de elements estructurales por tongadas\nfalse|Levantamiento de cerramientos y muros perimetrales\nfalse|Colocación de red de saneamiento separativa en PVC\nfalse|Verificación y medición de la toma de tierra", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TEXT, content = "- Se ha comenzado con los trabajos de hormigonado según lo previsto, realizándose por tongadas conforme a las indicaciones de la Dirección Facultativa.\n- Se preparan las armaduras para la cimentación de la estructura, comprobándose su correcta colocación según plano de cimentación.\n- Se realiza la instalación de la red de saneamiento separativa empleando tuberías de PVC corrugado para pluviales y fecales.\n- Se encuentra pendiente la ejecución y verificación de la toma de tierra general del edificio.", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "REPORTAJE FOTOGRÁFICO DE LA VISITA", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TEXT, content = "[Añada imágenes usando el botón de captura de foto para registrar el avance de los trabajos]", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "ENTERADO Y CONFORME", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.SIGNATURE, content = "|D.O.|Dirección de Obra", sequence = nextSeq++, isHalfWidth = true, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.SIGNATURE, content = "|D.E.O. / CSS|Dir. Ejecución / Seg. y Salud", sequence = nextSeq++, isHalfWidth = true, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.SIGNATURE, content = "|P|Promotor", sequence = nextSeq++, isHalfWidth = true, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.SIGNATURE, content = "|C|Contratista", sequence = nextSeq++, isHalfWidth = true, visitId = visitId)
                ))
            } else if (templateType == "CONTROL_CALIDAD") {
                blocksToInsert.addAll(listOf(
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "REGISTRO DE PROBETAS Y RESISTENCIA", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TABLE, content = "Identificador | Fecha Confección | Plazo Ensayo (Días) | Resistencia Obtenida\nProbeta P-1 (Cimiento) | [Fecha] | 7 días | Pendiente de ensayo\nProbeta P-2 (Cimiento) | [Fecha] | 28 días | Pendiente de ensayo\nProbeta P-3 (Muro Sótano) | [Fecha] | 28 días | Pendiente de ensayo", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "CHECKLIST DE VERIFICACIONES PREVIAS", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.CHECKLIST, content = "false|Verificación y cotejo del documento de suministro (Albarán)\nfalse|Medición del tiempo máximo transcurrido desde adición de agua en planta\nfalse|Prueba de docilidad mediante Cono de Abrams en obra\nfalse|Toma de muestras por probetas cilíndricas\nfalse|Vibrado correcto por inmersión de la masa y curado posterior", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "INSTRUCCIONES DE LA DIRECCIÓN DE EJECUCIÓN (D.E.O.)", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TEXT, content = "- Se autoriza la descarga del hormigón tras realizar los controles de consistencia.\n- Ensayar las probetas a los 7 y 28 días según especificaciones de la norma conforme al Código Estructural.\n- Queda prohibida la adición de agua en obra para aumentar la docilidad sin consentimiento explícito.\n- Extremar las precauciones de curado humedeciendo la superficie expuesta durante al menos los 3 primeros días.", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "DOCUMENTO DE VALIDACIÓN", sequence = nextSeq++, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.SIGNATURE, content = "|D.E.O.|Dirección de Ejecución de Obra", sequence = nextSeq++, isHalfWidth = true, visitId = visitId),
                    ContentBlockEntity(projectId = projectId, type = BlockType.SIGNATURE, content = "|Jefe de Obra|Representante de Suministro", sequence = nextSeq++, isHalfWidth = true, visitId = visitId)
                ))
            }
            
            blocksToInsert.forEach { repository.insertBlock(it) }
            
            val freshProject = repository.getProjectById(projectId).filterNotNull().first()
            val sorted = freshProject.blocks.sortedBy { it.sequence }
            _originalBlocks.value = sorted
            _draftBlocks.value = sorted
        }
    }

    fun deleteVisit(visit: VisitEntity) {
        viewModelScope.launch {
            repository.deleteVisit(visit)
        }
    }

    fun updateVisit(visit: VisitEntity) {
        viewModelScope.launch {
            repository.updateVisit(visit)
        }
    }

    fun exportPdf(exportMode: PdfExportMode = PdfExportMode.FULL_REPORT, singleVisitId: Long? = null) {
        val project = selectedProject.value ?: return
        val currentDraft = _draftBlocks.value
        val draftProject = ProjectWithBlocks(project = project.project, blocks = currentDraft, visits = project.visits)
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
