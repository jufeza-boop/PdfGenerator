package com.example.ui

import androidx.compose.runtime.Composable
import java.io.File

@Composable
expect fun PdfPreviewScreen(pdfFile: File, onBack: () -> Unit)
