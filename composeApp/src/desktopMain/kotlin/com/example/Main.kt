package com.example

import androidx.compose.ui.window.singleWindowApplication
import com.example.ui.ProjectApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ProjectViewModel
import com.example.data.*
import com.example.ui.*
import java.io.File

fun main() {
    val workspaceManager = WorkspaceManager()
    val store = JsonProjectStore(workspaceManager)
    val pdfGenerator = DesktopPdfGenerator()
    val repository = ProjectRepository(
        store = store,
        workspaceManager = workspaceManager,
        pdfGenerator = pdfGenerator
    )
    val viewModel = ProjectViewModel(repository, workspaceManager, store)
    
    singleWindowApplication(title = "Project PDF Manager") {
        MyApplicationTheme {
            ProjectApp(viewModel)
        }
    }
}
