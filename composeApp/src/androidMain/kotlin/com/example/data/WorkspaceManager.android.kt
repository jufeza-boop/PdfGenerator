package com.example.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class WorkspaceManager(private val context: Context) {
    
    // Using SharedPreferences for simplicity to store the selected URI
    private val prefs = context.getSharedPreferences("workspace_prefs", Context.MODE_PRIVATE)
    
    actual fun saveWorkspaceUri(uri: String) {
        prefs.edit().putString("workspace_uri", uri).apply()
    }
    
    actual fun getAccessor(): WorkspaceAccessor? {
        val uriStr = prefs.getString("workspace_uri", null) ?: return null
        val uri = try {
            Uri.parse(uriStr)
        } catch (e: Exception) {
            return null
        }
        val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return null
        if (!rootDoc.exists() || !rootDoc.canRead() || !rootDoc.canWrite()) return null
        
        return AndroidWorkspaceAccessor(context, rootDoc)
    }
}

class AndroidWorkspaceAccessor(
    private val context: Context,
    private val rootDoc: DocumentFile
) : WorkspaceAccessor {

    private suspend fun getDoc(relativePath: String, createParents: Boolean = false): DocumentFile? = withContext(Dispatchers.IO) {
        if (relativePath.isEmpty()) return@withContext rootDoc
        
        val parts = relativePath.split("/")
        var current = rootDoc
        
        for (i in parts.indices) {
            val part = parts[i]
            if (part.isEmpty()) continue
            
            var next = current.findFile(part)
            val isDir = i < parts.size - 1 || !part.contains(".")
            
            if (next == null && !isDir && !createParents) {
                // SAF providers sometimes append duplicate extensions (e.g., .jpg.jpg).
                // If exact match fails, try to find a file starting with the base name.
                val baseName = part.substringBeforeLast(".")
                next = current.listFiles().firstOrNull { it.name?.startsWith(baseName) == true }
            }

            if (next == null) {
                if (createParents) {
                    val mimeType = if (part.endsWith(".json")) "application/json"
                                   else if (part.endsWith(".jpg")) "image/jpeg"
                                   else if (part.endsWith(".png")) "image/png"
                                   else "application/octet-stream"
                    next = if (isDir) {
                        current.createDirectory(part)
                    } else {
                        current.createFile(mimeType, part)
                    }
                }
                if (next == null) return@withContext null
            }
            current = next
        }
        current
    }

    override suspend fun exists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        getDoc(relativePath, createParents = false)?.exists() == true
    }

    override suspend fun readText(relativePath: String): String? = withContext(Dispatchers.IO) {
        val doc = getDoc(relativePath, createParents = false) ?: return@withContext null
        try {
            context.contentResolver.openInputStream(doc.uri)?.use { 
                it.bufferedReader().readText() 
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun writeText(relativePath: String, content: String) = withContext(Dispatchers.IO) {
        val doc = getDoc(relativePath, createParents = true) ?: return@withContext
        try {
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use {
                it.write(content.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun delete(relativePath: String) = withContext(Dispatchers.IO) {
        getDoc(relativePath, createParents = false)?.delete()
        Unit
    }

    override suspend fun listDirectories(relativePath: String): List<String> = withContext(Dispatchers.IO) {
        val doc = getDoc(relativePath, createParents = false) ?: return@withContext emptyList()
        if (!doc.isDirectory) return@withContext emptyList()
        
        doc.listFiles().filter { it.isDirectory }.mapNotNull { it.name }
    }

    override suspend fun writeBytes(relativePath: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val doc = getDoc(relativePath, createParents = true) ?: return@withContext
        try {
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use {
                it.write(data)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun getAbsolutePath(relativePath: String): String {
        val doc = getDoc(relativePath, createParents = false)
        return doc?.uri?.toString() ?: ""
    }
}
