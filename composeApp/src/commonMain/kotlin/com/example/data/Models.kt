package com.example.data

import androidx.compose.ui.geometry.Offset
import com.squareup.moshi.JsonClass

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val step: String, val progress: Float, val log: String) : SyncState()
    data class Success(val summary: String, val sheetsUrls: List<String> = emptyList(), val driveUrl: String = "") : SyncState()
    data class Error(val message: String) : SyncState()
}

data class SketchStroke(val points: List<Offset>)

// Structured content for complex blocks
@JsonClass(generateAdapter = true)
data class TableBlockContent(
    val title: String = "",
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChecklistBlockContent(
    val title: String = "",
    val items: List<ChecklistItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChecklistItem(val text: String, val checked: Boolean)

@JsonClass(generateAdapter = true)
data class ChecklistTableBlockContent(
    val title: String = "",
    val headers: List<String> = emptyList(), // e.g. ["SI", "NO", "NP"]
    val rows: List<ChecklistTableRow> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChecklistTableRow(val text: String, val selectedIndex: Int)
