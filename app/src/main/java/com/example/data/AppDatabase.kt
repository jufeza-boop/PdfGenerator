package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class BlockType {
    TEXT, IMAGE, SIGNATURE, TITLE, FOOTER, TABLE, CHECKLIST
}

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val reportLabel: String = "REPORTE DE PROYECTO",
    val showHeaderLabel: Boolean = true,
    val showHeaderDate: Boolean = true
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
    val isHalfWidth: Boolean = false
)

data class ProjectWithBlocks(
    @Embedded val project: ProjectEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val blocks: List<ContentBlockEntity>
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
}

@Database(entities = [ProjectEntity::class, ContentBlockEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "project_manager_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
