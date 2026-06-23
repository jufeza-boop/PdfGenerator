package com.example.data

import java.io.File

interface FolderAccessor {
    fun getConfig(): FolderSyncConfig
    fun saveConfig(rootFolderUri: String, isAutoSync: Boolean)
    fun isConfigured(): Boolean
    fun deleteProjectFolder(createdAt: Long): Boolean
    
    fun listSubFolders(): List<RemoteFolder>
    fun getOrCreateSubFolder(name: String, createdAt: Long): RemoteFolder?
}

interface RemoteFolder {
    val name: String
    fun readProjectDataJson(): String?
    fun writeProjectDataJson(content: String): Boolean
    fun copyFileToLocal(fileName: String, destinationFile: File): Boolean
    fun copyFileFromLocal(fileName: String, sourceFile: File): Boolean
    fun deleteFile(fileName: String): Boolean
}
