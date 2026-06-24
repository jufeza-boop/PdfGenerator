package com.example.data

interface PdfGenerator {
    suspend fun generatePdf(
        project: ProjectData,
        exportMode: PdfExportMode = PdfExportMode.FULL_REPORT,
        singleVisitId: String? = null
    ): String
}
