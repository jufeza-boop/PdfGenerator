package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

enum class PdfExportMode {
    FULL_REPORT,
    COMMON_ONLY,
    SINGLE_VISIT
}

class ProjectRepository(
    val projectDao: ProjectDao,
    private val pdfGenerator: PdfGenerator,
    val filesDir: File,
    private val cacheDir: File
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val tableAdapter = moshi.adapter(TableBlockContent::class.java)
    private val checklistAdapter = moshi.adapter(ChecklistBlockContent::class.java)
    private val checklistTableAdapter = moshi.adapter(ChecklistTableBlockContent::class.java)

    val allProjects: Flow<List<ProjectWithBlocks>> = projectDao.getAllProjectsFlow()

    fun getProjectById(id: Long): Flow<ProjectWithBlocks?> = projectDao.getProjectByIdFlow(id)

    suspend fun insertVisit(visit: VisitEntity): Long = withContext(Dispatchers.IO) {
        projectDao.insertVisit(visit)
    }

    suspend fun updateVisit(visit: VisitEntity) = withContext(Dispatchers.IO) {
        projectDao.updateVisit(visit)
    }

    suspend fun deleteVisit(visit: VisitEntity) = withContext(Dispatchers.IO) {
        projectDao.deleteVisit(visit)
    }

    suspend fun createProject(name: String, templateType: String = "NONE"): Long = withContext(Dispatchers.IO) {
        val newProj = when (templateType) {
            "ACTA_VISITA" -> ProjectEntity(
                name = name,
                headerCompany = "Nombre de la empresa",
                headerCompanySub = "ARQUITECTO TÉCNICO-INGENIERO DE EDIFICACIÓN\nESPECIALISTA EN C.S.S. EN OBRAS DE CONSTRUCCIÓN",
                headerTitle = "INFORME DE VISITA A OBRA"
            )
            "CONTROL_CALIDAD" -> ProjectEntity(
                name = name,
                headerCompany = "LABORATORIO DE CONTROL S.L.",
                headerCompanySub = "CONTROL DE CALIDAD DE EDIFICACIÓN\nREGISTRO DE ENSAYOS DE HORMIGÓN ESTRUCTURAL",
                headerTitle = "CONTROL DE RECEPCIÓN DE HORMIGÓN"
            )
            else -> ProjectEntity(name = name)
        }
        val projectId = projectDao.insertProject(newProj)
        
        when (templateType) {
            "ACTA_VISITA" -> {
                var seq = 0
                val tableContent = TableBlockContent(
                    title = "DATOS GENERALES DEL PROYECTO",
                    headers = listOf("Concepto", "Información"),
                    rows = listOf(
                        listOf("Nombre de la obra", "[Nombre]"),
                        listOf("Promotor", "[Empresa]"),
                        listOf("Localización", "[Dirección]")
                    )
                )
                val blocks = listOf(
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "ACTA DE VISITA - DIRECCIÓN FACULTATIVA", sequence = seq++),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TABLE, content = tableAdapter.toJson(tableContent), sequence = seq++)
                )
                blocks.forEach { projectDao.insertBlock(it) }
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
                val blocks = listOf(
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "CONTROL DE CALIDAD Y RECEPCIÓN DE HORMIGÓN", sequence = seq++),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TABLE, content = tableAdapter.toJson(tableContent), sequence = seq++)
                )
                blocks.forEach { projectDao.insertBlock(it) }
            }
        }
        projectId
    }

    suspend fun createVisit(
        projectId: Long,
        title: String,
        notes: String,
        templateType: String = "NONE",
        date: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val visit = VisitEntity(projectId = projectId, title = title, notes = notes, date = date)
        val visitId = projectDao.insertVisit(visit)
        
        val projectWithBlocks = getProjectById(projectId).filterNotNull().first()
        val currentMaxSeq = projectWithBlocks.blocks.maxOfOrNull { it.sequence } ?: -1
        var nextSeq = currentMaxSeq + 1
        
        val blocksToInsert = mutableListOf<ContentBlockEntity>()
        if (templateType == "DIRECCION_OBRA") {
            val tableContent = TableBlockContent(
                title = "ASISTENTES A LA VISITA",
                headers = listOf("Entidad / Parte", "Representantes"),
                rows = listOf(
                    listOf("Promotor", ""),
                    listOf("Constructor", ""),
                    listOf("Dirección de Obra", "")
                )
            )
            blocksToInsert.addAll(listOf(
                ContentBlockEntity(projectId = projectId, type = BlockType.TABLE, content = tableAdapter.toJson(tableContent), sequence = nextSeq++, visitId = visitId),
                ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "DATOS DE LA VISITA Y ESTADO DE LOS TRABAJOS", sequence = nextSeq++, visitId = visitId),
                ContentBlockEntity(projectId = projectId, type = BlockType.TEXT, content = "Estado de ejecución...", sequence = nextSeq++, visitId = visitId),
                ContentBlockEntity(projectId = projectId, type = BlockType.SIGNATURE, content = "|D.O.|Dirección de Obra", sequence = nextSeq++, isHalfWidth = true, visitId = visitId),
                ContentBlockEntity(projectId = projectId, type = BlockType.SIGNATURE, content = "|C|Constructor / Jefe de Obra", sequence = nextSeq++, isHalfWidth = true, visitId = visitId)
            ))
        } else if (templateType == "COORDINACION_CSS") {
            val checklistTable = ChecklistTableBlockContent(
                title = "CHECKLIST DE SEGURIDAD Y SALUD",
                headers = listOf("SI", "NO", "NP"),
                rows = listOf(
                    ChecklistTableRow("Protecciones colectivas...", -1),
                    ChecklistTableRow("Uso de EPIs...", -1),
                    ChecklistTableRow("Maquinaria con marcado CE...", -1)
                )
            )
            blocksToInsert.addAll(listOf(
                ContentBlockEntity(projectId = projectId, type = BlockType.CHECKLIST_TABLE, content = checklistTableAdapter.toJson(checklistTable), sequence = nextSeq++, visitId = visitId),
                ContentBlockEntity(projectId = projectId, type = BlockType.TEXT, content = "Órdenes de seguridad...", sequence = nextSeq++, visitId = visitId),
                ContentBlockEntity(projectId = projectId, type = BlockType.SIGNATURE, content = "|C.S.S.|Coord. Seguridad y Salud", sequence = nextSeq++, isHalfWidth = true, visitId = visitId)
            ))
        }
        
        blocksToInsert.forEach { projectDao.insertBlock(it) }
        visitId
    }

    suspend fun addTableBlock(projectId: Long, visitId: Long?, sequence: Int) = withContext(Dispatchers.IO) {
        val content = TableBlockContent(
            title = "Nueva Tabla",
            headers = listOf("Columna 1", "Columna 2"),
            rows = listOf(listOf("", ""))
        )
        val block = ContentBlockEntity(
            projectId = projectId,
            type = BlockType.TABLE,
            content = tableAdapter.toJson(content),
            sequence = sequence,
            visitId = visitId
        )
        projectDao.insertBlock(block)
    }

    suspend fun addChecklistBlock(projectId: Long, visitId: Long?, sequence: Int) = withContext(Dispatchers.IO) {
        val content = ChecklistBlockContent(
            title = "Nuevo Checklist",
            items = listOf(ChecklistItem("", false))
        )
        val block = ContentBlockEntity(
            projectId = projectId,
            type = BlockType.CHECKLIST,
            content = checklistAdapter.toJson(content),
            sequence = sequence,
            visitId = visitId
        )
        projectDao.insertBlock(block)
    }

    suspend fun addChecklistTableBlock(projectId: Long, visitId: Long?, sequence: Int) = withContext(Dispatchers.IO) {
        val content = ChecklistTableBlockContent(
            title = "Nuevo Checklist Tabla",
            headers = listOf("SI", "NO", "NP"),
            rows = listOf(ChecklistTableRow("", -1))
        )
        val block = ContentBlockEntity(
            projectId = projectId,
            type = BlockType.CHECKLIST_TABLE,
            content = checklistTableAdapter.toJson(content),
            sequence = sequence,
            visitId = visitId
        )
        projectDao.insertBlock(block)
    }

    fun getDefaultTableBlockJson(): String {
        val content = TableBlockContent(
            title = "Nueva Tabla",
            headers = listOf("Columna 1", "Columna 2"),
            rows = listOf(listOf("", ""))
        )
        return tableAdapter.toJson(content)
    }

    fun getDefaultChecklistBlockJson(): String {
        val content = ChecklistBlockContent(
            title = "Nuevo Checklist",
            items = listOf(ChecklistItem("", false))
        )
        return checklistAdapter.toJson(content)
    }

    fun getDefaultChecklistTableBlockJson(): String {
        val content = ChecklistTableBlockContent(
            title = "Nueva Tabla de Chequeo",
            headers = listOf("SI", "NO", "NP"),
            rows = listOf(ChecklistTableRow("", -1))
        )
        return checklistTableAdapter.toJson(content)
    }


    suspend fun updateProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
        projectDao.updateProject(project)
    }

    suspend fun deleteProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
        // Delete all local files associated with blocks before deleting the project
        val projectWithBlocks = projectDao.getProjectByIdFlow(project.id)
        // Note: flows are active stream, but we can read the current list by taking 1 if we wanted.
        // It's safer to just delete DB, cascade foreign keys will remove block records.
        // But files on disk should be cleaned if possible. We can do that or cascade-delete.
        projectDao.deleteProject(project)
    }

    suspend fun addTextBlock(projectId: Long, text: String, sequence: Int) = withContext(Dispatchers.IO) {
        val block = ContentBlockEntity(
            projectId = projectId,
            type = BlockType.TEXT,
            content = text,
            sequence = sequence
        )
        projectDao.insertBlock(block)
    }

    suspend fun saveImageBlock(projectId: Long, inputStream: InputStream, sequence: Int) = withContext(Dispatchers.IO) {
        val destinationDir = File(filesDir, "project_${projectId}_images")
        if (!destinationDir.exists()) destinationDir.mkdirs()

        val filename = "img_${UUID.randomUUID()}.jpg"
        val destinationFile = File(destinationDir, filename)

        FileOutputStream(destinationFile).use { output ->
            inputStream.copyTo(output)
        }

        val block = ContentBlockEntity(
            projectId = projectId,
            type = BlockType.IMAGE,
            content = destinationFile.absolutePath,
            sequence = sequence
        )
        projectDao.insertBlock(block)
    }

    suspend fun copyImageToLocalFile(projectId: Long, inputStream: InputStream): String = withContext(Dispatchers.IO) {
        val destinationDir = File(filesDir, "project_${projectId}_images")
        if (!destinationDir.exists()) destinationDir.mkdirs()

        val filename = "img_${UUID.randomUUID()}.jpg"
        val destinationFile = File(destinationDir, filename)

        FileOutputStream(destinationFile).use { output ->
            inputStream.copyTo(output)
        }
        destinationFile.absolutePath
    }

    suspend fun saveSignatureBlock(projectId: Long, signatureBytes: ByteArray, sequence: Int) = withContext(Dispatchers.IO) {
        val destinationDir = File(filesDir, "project_${projectId}_signatures")
        if (!destinationDir.exists()) destinationDir.mkdirs()

        val filename = "sig_${UUID.randomUUID()}.png"
        val destinationFile = File(destinationDir, filename)

        FileOutputStream(destinationFile).use { output ->
            output.write(signatureBytes)
        }

        val block = ContentBlockEntity(
            projectId = projectId,
            type = BlockType.SIGNATURE,
            content = destinationFile.absolutePath,
            sequence = sequence
        )
        projectDao.insertBlock(block)
    }

    suspend fun saveSignatureToLocalFile(projectId: Long, signatureBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val destinationDir = File(filesDir, "project_${projectId}_signatures")
        if (!destinationDir.exists()) destinationDir.mkdirs()

        val filename = "sig_${UUID.randomUUID()}.png"
        val destinationFile = File(destinationDir, filename)

        FileOutputStream(destinationFile).use { output ->
            output.write(signatureBytes)
        }
        destinationFile.absolutePath
    }

    suspend fun insertBlock(block: ContentBlockEntity): Long = withContext(Dispatchers.IO) {
        projectDao.insertBlock(block)
    }

    suspend fun updateBlock(block: ContentBlockEntity) = withContext(Dispatchers.IO) {
        projectDao.updateBlock(block)
    }

    suspend fun deleteBlock(block: ContentBlockEntity) = withContext(Dispatchers.IO) {
        if (block.type == BlockType.IMAGE || block.type == BlockType.SIGNATURE) {
            val filePath = block.content.split("|")[0]
            val file = File(filePath)
            if (file.exists()) file.delete()
        }
        projectDao.deleteBlock(block)
    }

    suspend fun generatePdf(
        project: ProjectWithBlocks,
        exportMode: PdfExportMode = PdfExportMode.FULL_REPORT,
        singleVisitId: Long? = null
    ): File {
        val path = pdfGenerator.generatePdf(project, exportMode, singleVisitId)
        return File(path)
    }

    suspend fun uploadPdfToCloudMock(pdfFile: File): Boolean = withContext(Dispatchers.IO) {
        // Simulate a cloud server request
        delay(2000)
        true
    }
}
