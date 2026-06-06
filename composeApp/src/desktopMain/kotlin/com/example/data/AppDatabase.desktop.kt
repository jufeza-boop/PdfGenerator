package com.example.data

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(System.getProperty("user.home"), ".pdfgenerator/project_manager_db")
    if (!dbFile.parentFile.exists()) dbFile.parentFile.mkdirs()
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath
    )
}
