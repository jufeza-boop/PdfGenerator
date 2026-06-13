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

@JsonClass(generateAdapter = true)
data class ProjectSyncData(
    val project: ProjectSyncEntity,
    val blocks: List<BlockSyncEntity>
)

@JsonClass(generateAdapter = true)
data class ProjectSyncEntity(
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val reportLabel: String = "REPORTE DE PROYECTO",
    val showHeaderLabel: Boolean = true,
    val showHeaderDate: Boolean = true,
    val headerCompany: String = "Nombre de la empresa",
    val headerCompanySub: String = "",
    val headerTitle: String = "INFORME DE VISITA A OBRA",
    val showHeaderBox: Boolean = true,
    val showHeaderTitle: Boolean = true
)

@JsonClass(generateAdapter = true)
data class BlockSyncEntity(
    val type: String,
    val content: String,
    val sequence: Int,
    val isHalfWidth: Boolean = false
)
