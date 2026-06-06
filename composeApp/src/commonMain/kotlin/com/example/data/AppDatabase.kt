package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

enum class BlockType {
    TEXT, IMAGE, SIGNATURE, TITLE, FOOTER, TABLE, CHECKLIST
}

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val reportLabel: String = "REPORTE DE PROYECTO",
    val showHeaderLabel: Boolean = true,
    val showHeaderDate: Boolean = true,
    val headerCompany: String = "JAVIER MARTÍNEZ PARRA",
    val headerCompanySub: String = "ARQUITECTO TÉCNICO-INGENIERO DE EDIFICACIÓN\nESPECIALISTA EN C.S.S. EN OBRAS DE CONSTRUCCIÓN",
    val headerTitle: String = "INFORME DE VISITA A OBRA",
    val showHeaderBox: Boolean = true
)

@Entity(
    tableName = "visits",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val title: String,
    val date: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "content_blocks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class ContentBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val type: BlockType,
    val content: String, // String value for Text OR local filepath for Image / Signature
    val sequence: Int,
    val isHalfWidth: Boolean = false,
    val visitId: Long? = null
)

data class ProjectWithBlocks(
    @Embedded val project: ProjectEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val blocks: List<ContentBlockEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val visits: List<VisitEntity>
)

@Dao
interface ProjectDao {
    @Transaction
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjectsFlow(): Flow<List<ProjectWithBlocks>>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id")
    fun getProjectByIdFlow(id: Long): Flow<ProjectWithBlocks?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: ContentBlockEntity): Long

    @Update
    suspend fun updateBlock(block: ContentBlockEntity)

    @Delete
    suspend fun deleteBlock(block: ContentBlockEntity)

    @Query("DELETE FROM content_blocks WHERE projectId = :projectId")
    suspend fun deleteBlocksForProject(projectId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisit(visit: VisitEntity): Long

    @Update
    suspend fun updateVisit(visit: VisitEntity)

    @Delete
    suspend fun deleteVisit(visit: VisitEntity)

    @Query("DELETE FROM visits WHERE projectId = :projectId")
    suspend fun deleteVisitsForProject(projectId: Long)
}

@Database(entities = [ProjectEntity::class, ContentBlockEntity::class, VisitEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}

expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
