package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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
                val blocks = listOf(
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "ACTA DE VISITA - DIRECCIÓN FACULTATIVA", sequence = seq++),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TABLE, content = "Dato General | Información del Proyecto\nNombre de la obra | [Nombre de la Obra]\nDirección de la obra | [Dirección / Localización]\nPromotor | [Nombre del Promotor]\nContratista principal | [Nombre de la Constructora / Contratista]\nDirección de la obra (DO) | -\nDirección de la Ejecución (DEO) | -\nCoordinación de Seguridad y Salud (CSS) | -", sequence = seq++)
                )
                blocks.forEach { projectDao.insertBlock(it) }
            }
            "CONTROL_CALIDAD" -> {
                var seq = 0
                val blocks = listOf(
                    ContentBlockEntity(projectId = projectId, type = BlockType.TITLE, content = "CONTROL DE CALIDAD Y RECEPCIÓN DE HORMIGÓN", sequence = seq++),
                    ContentBlockEntity(projectId = projectId, type = BlockType.TABLE, content = "Ensayo y Control | Especificación del Proyecto\nTipo de Hormigón | HA-25 / B / 20 / IIa (Fck = 25 N/mm²)\nTipo de Cemento | CEM II/A-L 42.5R (Uso general)\nConsistencia / Cono | Consistencia Blanda (Asentamiento Cono: 6-9 cm)\nTamaño Máximo Árido | 20 mm de piedra de machaqueo\nAditivos incorporados | Plastificantes homologados", sequence = seq++)
                )
                blocks.forEach { projectDao.insertBlock(it) }
            }
        }
        projectId
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
