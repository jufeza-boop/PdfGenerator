package com.example

import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
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
    
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Seguimiento Obras",
            icon = androidx.compose.ui.res.painterResource("icon.png")
        ) {
            MyApplicationTheme {
                ProjectApp(viewModel)
            }
        }
    }
}
