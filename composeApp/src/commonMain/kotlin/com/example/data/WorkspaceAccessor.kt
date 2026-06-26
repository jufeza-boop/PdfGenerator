package com.example.data

interface WorkspaceAccessor {
    suspend fun exists(relativePath: String): Boolean
    suspend fun readText(relativePath: String): String?
    suspend fun writeText(relativePath: String, content: String)
    suspend fun delete(relativePath: String)
    suspend fun listDirectories(relativePath: String): List<String>
    suspend fun writeBytes(relativePath: String, data: ByteArray)
    suspend fun readBytes(relativePath: String): ByteArray?
    suspend fun getAbsolutePath(relativePath: String): String
}
