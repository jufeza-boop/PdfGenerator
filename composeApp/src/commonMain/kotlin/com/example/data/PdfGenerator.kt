package com.example.data

interface PdfGenerator {
    suspend fun generatePdf(
        project: ProjectWithBlocks,
        exportMode: PdfExportMode = PdfExportMode.FULL_REPORT,
        singleVisitId: Long? = null
    ): String
}
