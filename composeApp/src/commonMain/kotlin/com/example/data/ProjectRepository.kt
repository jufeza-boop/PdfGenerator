package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.InputStream
import java.util.UUID

enum class PdfExportMode {
    FULL_REPORT,
    COMMON_ONLY,
    SINGLE_VISIT
}

class ProjectRepository(
    private val store: JsonProjectStore,
    val workspaceManager: WorkspaceManager,
    private val pdfGenerator: PdfGenerator
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val tableAdapter = moshi.adapter(TableBlockContent::class.java)
    private val checklistAdapter = moshi.adapter(ChecklistBlockContent::class.java)
    private val checklistTableAdapter = moshi.adapter(ChecklistTableBlockContent::class.java)

    val allProjects: Flow<List<ProjectData>> = store.allProjects
    val customTemplates: Flow<List<CustomTemplateData>> = store.customTemplates

    fun getProjectById(uuid: String): Flow<ProjectData?> {
        return store.allProjects.map { projects -> projects.find { it.uuid == uuid } }
    }

    suspend fun insertVisit(projectId: String, visit: VisitData) = withContext(Dispatchers.IO) {
        store.getProject(projectId)?.let { proj ->
            val updated = proj.copy(visits = proj.visits + visit, updatedAt = System.currentTimeMillis())
            store.saveProject(updated)
        }
    }

    suspend fun updateVisit(projectId: String, visit: VisitData) = withContext(Dispatchers.IO) {
        store.getProject(projectId)?.let { proj ->
            val updatedVisits = proj.visits.map { if (it.uuid == visit.uuid) visit else it }
            store.saveProject(proj.copy(visits = updatedVisits, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteVisit(projectId: String, visitUuid: String) = withContext(Dispatchers.IO) {
        store.getProject(projectId)?.let { proj ->
            val updatedVisits = proj.visits.filter { it.uuid != visitUuid }
            val updatedBlocks = proj.blocks.filter { it.visitUuid != visitUuid }
            store.saveProject(proj.copy(visits = updatedVisits, blocks = updatedBlocks, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun createProject(name: String, templateType: String = "NONE"): String = withContext(Dispatchers.IO) {
        val projectId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        val customTemplate = store.customTemplates.value.find { it.uuid == templateType }
        
        var project = when (templateType) {
            "ACTA_VISITA" -> ProjectData(
                uuid = projectId, name = name, createdAt = now, updatedAt = now,
                headerCompany = "Nombre de la empresa",
                headerCompanySub = "ARQUITECTO TÉCNICO-INGENIERO DE EDIFICACIÓN\nESPECIALISTA EN C.S.S. EN OBRAS DE CONSTRUCCIÓN",
                headerTitle = "INFORME DE VISITA A OBRA"
            )
            "CONTROL_CALIDAD" -> ProjectData(
                uuid = projectId, name = name, createdAt = now, updatedAt = now,
                headerCompany = "LABORATORIO DE CONTROL S.L.",
                headerCompanySub = "CONTROL DE CALIDAD DE EDIFICACIÓN\nREGISTRO DE ENSAYOS DE HORMIGÓN ESTRUCTURAL",
                headerTitle = "CONTROL DE RECEPCIÓN DE HORMIGÓN"
            )
            else -> {
                if (customTemplate != null) {
                    ProjectData(
                        uuid = projectId, name = name, createdAt = now, updatedAt = now,
                        headerCompany = customTemplate.headerCompany,
                        headerCompanySub = customTemplate.headerCompanySub,
                        headerTitle = customTemplate.headerTitle
                    )
                } else {
                    ProjectData(uuid = projectId, name = name, createdAt = now, updatedAt = now)
                }
            }
        }
        
        val blocks = mutableListOf<BlockData>()
        when (templateType) {
            "ACTA_VISITA" -> {
                var seq = 0
                val tableContent = TableBlockContent(
                    title = "DATOS GENERALES DEL PROYECTO",
                    headers = listOf("Concepto", "Información"),
                    rows = listOf(listOf("Nombre de la obra", "[Nombre]"), listOf("Promotor", "[Empresa]"), listOf("Localización", "[Dirección]"))
                )
                blocks.add(BlockData(UUID.randomUUID().toString(), BlockType.TITLE.name, "ACTA DE VISITA - DIRECCIÓN FACULTATIVA", seq++))
                blocks.add(BlockData(UUID.randomUUID().toString(), BlockType.TABLE.name, tableAdapter.toJson(tableContent), seq++))
            }
            "CONTROL_CALIDAD" -> {
                var seq = 0
                val tableContent = TableBlockContent(
                    title = "CONTROL DE CALIDAD Y RECEPCIÓN DE HORMIGÓN",
                    headers = listOf("Ensayo y Control", "Especificación del Proyecto"),
                    rows = listOf(
                        listOf("Tipo de Hormigón", "HA-25 / B / 20 / IIa (Fck = 25 N/mm²)"),
                        listOf("Tipo de Cemento", "CEM II/A-L 42.5R (Uso general)"),
                        listOf("Consistencia / Cono", "Consistencia Blanda (Asentamiento Cono: 6-9 cm)"),
                        listOf("Tamaño Máximo Árido", "20 mm de piedra de machaqueo"),
                        listOf("Aditivos incorporados", "Plastificantes homologados")
                    )
                )
                blocks.add(BlockData(UUID.randomUUID().toString(), BlockType.TITLE.name, "CONTROL DE CALIDAD Y RECEPCIÓN DE HORMIGÓN", seq++))
                blocks.add(BlockData(UUID.randomUUID().toString(), BlockType.TABLE.name, tableAdapter.toJson(tableContent), seq++))
            }
            else -> {
                if (customTemplate != null) {
                    var seq = 0
                    blocks.addAll(customTemplate.blocks.map { b ->
                        b.copy(
                            uuid = UUID.randomUUID().toString(),
                            sequence = seq++,
                            visitUuid = null
                        )
                    })
                    copyMediaFromTemplate(templateType, projectId, customTemplate.blocks)
                }
            }
        }
        
        project = project.copy(blocks = blocks)
        store.saveProject(project)
        projectId
    }

    suspend fun createVisit(
        projectId: String,
        title: String,
        notes: String,
        templateType: String = "NONE",
        date: Long = System.currentTimeMillis()
    ): String = withContext(Dispatchers.IO) {
        val proj = store.getProject(projectId) ?: return@withContext ""
        val visitId = UUID.randomUUID().toString()
        val visit = VisitData(uuid = visitId, title = title, notes = notes, date = date)
        
        val currentMaxSeq = proj.blocks.maxOfOrNull { it.sequence } ?: -1
        var nextSeq = currentMaxSeq + 1
        
        val customTemplate = store.customTemplates.value.find { it.uuid == templateType }
        val blocksToInsert = mutableListOf<BlockData>()
        if (templateType == "DIRECCION_OBRA") {
            val tableContent = TableBlockContent(
                title = "ASISTENTES A LA VISITA",
                headers = listOf("Entidad / Parte", "Representantes"),
                rows = listOf(listOf("Promotor", ""), listOf("Constructor", ""), listOf("Dirección de Obra", ""))
            )
            blocksToInsert.addAll(listOf(
                BlockData(UUID.randomUUID().toString(), BlockType.TABLE.name, tableAdapter.toJson(tableContent), nextSeq++, visitUuid = visitId),
                BlockData(UUID.randomUUID().toString(), BlockType.TITLE.name, "DATOS DE LA VISITA Y ESTADO DE LOS TRABAJOS", nextSeq++, visitUuid = visitId),
                BlockData(UUID.randomUUID().toString(), BlockType.TEXT.name, "Estado de ejecución...", nextSeq++, visitUuid = visitId),
                BlockData(UUID.randomUUID().toString(), BlockType.SIGNATURE.name, "|D.O.|Dirección de Obra", nextSeq++, isHalfWidth = true, visitUuid = visitId),
                BlockData(UUID.randomUUID().toString(), BlockType.SIGNATURE.name, "|C|Constructor / Jefe de Obra", nextSeq++, isHalfWidth = true, visitUuid = visitId)
            ))
        } else if (templateType == "COORDINACION_CSS") {
            val checklistTable = ChecklistTableBlockContent(
                title = "CHECKLIST DE SEGURIDAD Y SALUD",
                headers = listOf("SI", "NO", "NP"),
                rows = listOf(ChecklistTableRow("Protecciones colectivas...", -1), ChecklistTableRow("Uso de EPIs...", -1), ChecklistTableRow("Maquinaria con marcado CE...", -1))
            )
            blocksToInsert.addAll(listOf(
                BlockData(UUID.randomUUID().toString(), BlockType.CHECKLIST_TABLE.name, checklistTableAdapter.toJson(checklistTable), nextSeq++, visitUuid = visitId),
                BlockData(UUID.randomUUID().toString(), BlockType.TEXT.name, "Órdenes de seguridad...", nextSeq++, visitUuid = visitId),
                BlockData(UUID.randomUUID().toString(), BlockType.SIGNATURE.name, "|C.S.S.|Coord. Seguridad y Salud", nextSeq++, isHalfWidth = true, visitUuid = visitId)
            ))
        } else if (customTemplate != null) {
            blocksToInsert.addAll(customTemplate.blocks.map { b ->
                b.copy(
                    uuid = UUID.randomUUID().toString(),
                    sequence = nextSeq++,
                    visitUuid = visitId
                )
            })
            copyMediaFromTemplate(templateType, projectId, customTemplate.blocks)
        }
        
        val updated = proj.copy(
            visits = proj.visits + visit,
            blocks = proj.blocks + blocksToInsert,
            updatedAt = System.currentTimeMillis()
        )
        store.saveProject(updated)
        visitId
    }

    fun getDefaultTableBlockJson(): String {
        val content = TableBlockContent(title = "Nueva Tabla", headers = listOf("Columna 1", "Columna 2"), rows = listOf(listOf("", "")))
        return tableAdapter.toJson(content)
    }

    fun getDefaultChecklistBlockJson(): String {
        val content = ChecklistBlockContent(title = "Nuevo Checklist", items = listOf(ChecklistItem("", false)))
        return checklistAdapter.toJson(content)
    }

    fun getDefaultChecklistTableBlockJson(): String {
        val content = ChecklistTableBlockContent(title = "Nueva Tabla de Chequeo", headers = listOf("SI", "NO", "NP"), rows = listOf(ChecklistTableRow("", -1)))
        return checklistTableAdapter.toJson(content)
    }

    suspend fun updateProject(project: ProjectData) = withContext(Dispatchers.IO) {
        store.saveProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(project: ProjectData) = withContext(Dispatchers.IO) {
        store.deleteProject(project.uuid)
    }

    suspend fun insertBlock(projectId: String, block: BlockData) = withContext(Dispatchers.IO) {
        store.getProject(projectId)?.let { proj ->
            store.saveProject(proj.copy(blocks = proj.blocks + block, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun updateBlock(projectId: String, block: BlockData) = withContext(Dispatchers.IO) {
        store.getProject(projectId)?.let { proj ->
            val updatedBlocks = proj.blocks.map { if (it.uuid == block.uuid) block else it }
            store.saveProject(proj.copy(blocks = updatedBlocks, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteBlock(projectId: String, block: BlockData) = withContext(Dispatchers.IO) {
        store.getProject(projectId)?.let { proj ->
            if (block.type == BlockType.IMAGE.name || block.type == BlockType.SIGNATURE.name) {
                val relPath = block.content.split("|")[0]
                workspaceManager.getAccessor()?.delete("$projectId/$relPath")
            }
            val updatedBlocks = proj.blocks.filter { it.uuid != block.uuid }
            store.saveProject(proj.copy(blocks = updatedBlocks, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun addTextBlock(projectId: String, visitId: String?, text: String, sequence: Int) {
        insertBlock(projectId, BlockData(UUID.randomUUID().toString(), BlockType.TEXT.name, text, sequence, visitUuid = visitId))
    }

    suspend fun addTableBlock(projectId: String, visitId: String?, sequence: Int) {
        insertBlock(projectId, BlockData(UUID.randomUUID().toString(), BlockType.TABLE.name, getDefaultTableBlockJson(), sequence, visitUuid = visitId))
    }

    suspend fun addChecklistBlock(projectId: String, visitId: String?, sequence: Int) {
        insertBlock(projectId, BlockData(UUID.randomUUID().toString(), BlockType.CHECKLIST.name, getDefaultChecklistBlockJson(), sequence, visitUuid = visitId))
    }

    suspend fun addChecklistTableBlock(projectId: String, visitId: String?, sequence: Int) {
        insertBlock(projectId, BlockData(UUID.randomUUID().toString(), BlockType.CHECKLIST_TABLE.name, getDefaultChecklistTableBlockJson(), sequence, visitUuid = visitId))
    }

    suspend fun saveImageBlock(projectId: String, visitId: String?, inputStream: InputStream, sequence: Int) = withContext(Dispatchers.IO) {
        val relPath = "images/img_${UUID.randomUUID()}.jpg"
        workspaceManager.getAccessor()?.writeBytes("$projectId/$relPath", inputStream.readBytes())
        insertBlock(projectId, BlockData(UUID.randomUUID().toString(), BlockType.IMAGE.name, relPath, sequence, visitUuid = visitId))
    }

    suspend fun copyImageToLocalFile(projectId: String, inputStream: InputStream): String = withContext(Dispatchers.IO) {
        val relPath = "images/img_${UUID.randomUUID()}.jpg"
        workspaceManager.getAccessor()?.writeBytes("$projectId/$relPath", inputStream.readBytes())
        relPath
    }

    suspend fun saveSignatureBlock(projectId: String, visitId: String?, signatureBytes: ByteArray, sequence: Int) = withContext(Dispatchers.IO) {
        val relPath = "signatures/sig_${UUID.randomUUID()}.png"
        workspaceManager.getAccessor()?.writeBytes("$projectId/$relPath", signatureBytes)
        insertBlock(projectId, BlockData(UUID.randomUUID().toString(), BlockType.SIGNATURE.name, relPath, sequence, visitUuid = visitId))
    }

    suspend fun saveSignatureToLocalFile(projectId: String, signatureBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val relPath = "signatures/sig_${UUID.randomUUID()}.png"
        workspaceManager.getAccessor()?.writeBytes("$projectId/$relPath", signatureBytes)
        relPath
    }

    suspend fun copyMediaForTemplate(sourceProjectId: String, templateUuid: String, blocks: List<BlockData>) = withContext(Dispatchers.IO) {
        val accessor = workspaceManager.getAccessor() ?: return@withContext
        blocks.forEach { block ->
            if (block.type == BlockType.IMAGE.name || block.type == BlockType.SIGNATURE.name) {
                val relPath = block.content.split("|")[0]
                val bytes = accessor.readBytes("$sourceProjectId/$relPath")
                if (bytes != null) {
                    accessor.writeBytes("template_$templateUuid/$relPath", bytes)
                }
            }
        }
    }

    suspend fun copyMediaFromTemplate(templateUuid: String, destProjectId: String, blocks: List<BlockData>) = withContext(Dispatchers.IO) {
        val accessor = workspaceManager.getAccessor() ?: return@withContext
        blocks.forEach { block ->
            if (block.type == BlockType.IMAGE.name || block.type == BlockType.SIGNATURE.name) {
                val relPath = block.content.split("|")[0]
                val bytes = accessor.readBytes("template_$templateUuid/$relPath")
                if (bytes != null) {
                    accessor.writeBytes("$destProjectId/$relPath", bytes)
                }
            }
        }
    }

    suspend fun generatePdf(
        project: ProjectData,
        exportMode: PdfExportMode = PdfExportMode.FULL_REPORT,
        singleVisitId: String? = null
    ): File {
        val accessor = workspaceManager.getAccessor()
        val resolvedBlocks = project.blocks.map { block ->
            when (block.type) {
                BlockType.IMAGE.name -> {
                    val absPath = accessor?.getAbsolutePath("${project.uuid}/${block.content}") ?: block.content
                    block.copy(content = absPath)
                }
                BlockType.SIGNATURE.name -> {
                    val parts = block.content.split("|")
                    val absPath = accessor?.getAbsolutePath("${project.uuid}/${parts[0]}") ?: parts[0]
                    val resolvedContent = if (parts.size > 1) {
                        "$absPath|${parts.drop(1).joinToString("|")}"
                    } else {
                        absPath
                    }
                    block.copy(content = resolvedContent)
                }
                else -> block
            }
        }
        val projectForExport = project.copy(blocks = resolvedBlocks)
        val path = pdfGenerator.generatePdf(projectForExport, exportMode, singleVisitId)
        return File(path)
    }

    suspend fun uploadPdfToCloudMock(pdfFile: File): Boolean = withContext(Dispatchers.IO) {
        kotlinx.coroutines.delay(2000)
        true
    }
}
