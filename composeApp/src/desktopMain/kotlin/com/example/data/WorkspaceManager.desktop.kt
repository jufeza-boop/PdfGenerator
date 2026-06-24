package com.example.data

import java.io.File
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class WorkspaceManager {
    
    private val prefs = Preferences.userRoot().node(this::class.java.name)
    
    actual fun saveWorkspaceUri(uri: String) {
        prefs.put("workspace_path", uri)
    }
    
    actual fun getAccessor(): WorkspaceAccessor? {
        val path = prefs.get("workspace_path", null) ?: return null
        val rootDir = File(path)
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        if (!rootDir.exists() || !rootDir.canRead() || !rootDir.canWrite()) return null
        
        return DesktopWorkspaceAccessor(rootDir)
    }
}

class DesktopWorkspaceAccessor(private val rootDir: File) : WorkspaceAccessor {

    private fun getFile(relativePath: String): File {
        return File(rootDir, relativePath)
    }

    override suspend fun exists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        getFile(relativePath).exists()
    }

    override suspend fun readText(relativePath: String): String? = withContext(Dispatchers.IO) {
        val file = getFile(relativePath)
        if (file.exists()) file.readText() else null
    }

    override suspend fun writeText(relativePath: String, content: String) = withContext(Dispatchers.IO) {
        val file = getFile(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    override suspend fun delete(relativePath: String) = withContext(Dispatchers.IO) {
        val file = getFile(relativePath)
        if (file.exists()) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
        Unit
    }

    override suspend fun listDirectories(relativePath: String): List<String> = withContext(Dispatchers.IO) {
        val dir = getFile(relativePath)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        dir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
    }

    override suspend fun writeBytes(relativePath: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val file = getFile(relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
    }

    override suspend fun getAbsolutePath(relativePath: String): String {
        return getFile(relativePath).absolutePath
    }
}
