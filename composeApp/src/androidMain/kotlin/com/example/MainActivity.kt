package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ProjectApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ProjectViewModel
import com.example.data.*
import com.example.ui.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    appContext = applicationContext
    enableEdgeToEdge()
    
    val workspaceManager = WorkspaceManager(applicationContext)
    val store = JsonProjectStore(workspaceManager)
    val repository = ProjectRepository(
        store = store,
        workspaceManager = workspaceManager,
        pdfGenerator = AndroidPdfGenerator(applicationContext)
    )
    
    val viewModelFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProjectViewModel(repository, workspaceManager, store) as T
        }
    }

    setContent {
      MyApplicationTheme {
        val viewModel: ProjectViewModel = viewModel(factory = viewModelFactory)
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          ProjectApp(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}
