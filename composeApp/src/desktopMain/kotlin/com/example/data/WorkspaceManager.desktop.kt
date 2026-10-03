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

    /**
     * Escritura atómica: escribe a archivo .tmp, luego renombra al destino final.
     * Si el archivo destino ya existe, se guarda una copia .bak antes de sobreescribir.
     * Esto previene corrupción de datos por escrituras parciales (ej. cierre forzado).
     */
    private fun atomicWrite(file: File, writeAction: (File) -> Unit) {
        file.parentFile?.mkdirs()
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        val bakFile = File(file.parentFile, "${file.name}.bak")
        try {
            // 1. Escribir al archivo temporal
            writeAction(tmpFile)
            // 2. Si el original existe, moverlo a .bak
            if (file.exists()) {
                bakFile.delete() // Borrar backup anterior si existe
                file.renameTo(bakFile)
            }
            // 3. Renombrar .tmp al nombre final
            if (!tmpFile.renameTo(file)) {
                // Fallback: si renameTo falla (ej. cross-filesystem), copiar y borrar
                tmpFile.copyTo(file, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            // Si algo falla, intentar restaurar desde backup
            if (!file.exists() && bakFile.exists()) {
                bakFile.renameTo(file)
            }
            tmpFile.delete()
            throw e
        }
    }

    override suspend fun writeText(relativePath: String, content: String) = withContext(Dispatchers.IO) {
        val file = getFile(relativePath)
        atomicWrite(file) { tmpFile -> tmpFile.writeText(content) }
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
        atomicWrite(file) { tmpFile -> tmpFile.writeBytes(data) }
    }

    override suspend fun readBytes(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = getFile(relativePath)
        if (file.exists()) file.readBytes() else null
    }

    override suspend fun getAbsolutePath(relativePath: String): String {
        return getFile(relativePath).absolutePath
    }
}
