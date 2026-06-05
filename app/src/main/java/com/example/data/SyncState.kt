package com.example.data

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val step: String, val progress: Float, val log: String) : SyncState()
    data class Success(val summary: String, val sheetsUrls: List<String> = emptyList(), val driveUrl: String = "") : SyncState()
    data class Error(val message: String) : SyncState()
}
