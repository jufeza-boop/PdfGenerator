package com.example

import androidx.compose.ui.window.singleWindowApplication
import com.example.ui.ProjectApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ProjectViewModel
import com.example.data.*
import com.example.ui.*
import java.io.File

fun main() {
    val database = getRoomDatabase(getDatabaseBuilder())
    val repository = ProjectRepository(
        projectDao = database.projectDao(),
        pdfGenerator = DesktopPdfGenerator(),
        filesDir = File(System.getProperty("user.home"), ".pdfgenerator/files").apply { if(!exists()) mkdirs() },
        cacheDir = File(System.getProperty("java.io.tmpdir"), "pdfgenerator_cache").apply { if(!exists()) mkdirs() }
    )
    val syncManager = DesktopFolderSyncManager(repository)
    val viewModel = ProjectViewModel(repository, syncManager)
    
    singleWindowApplication(title = "Project PDF Manager") {
        MyApplicationTheme {
            ProjectApp(viewModel)
        }
    }
}
