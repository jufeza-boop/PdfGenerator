package com.example.data

expect class WorkspaceManager {
    fun saveWorkspaceUri(uri: String)
    fun getAccessor(): WorkspaceAccessor?
}
