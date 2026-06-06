package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.ProjectApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ProjectViewModel
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun testAppLaunchAndRender() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = ProjectViewModel(application)
    
    composeTestRule.setContent {
      MyApplicationTheme {
        ProjectApp(viewModel = viewModel)
      }
    }
    
    assertNotNull(viewModel.allProjects.value)
  }
}
