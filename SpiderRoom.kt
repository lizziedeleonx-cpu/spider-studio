package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val documentJson: String,
    val thumbnailPath: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "palettes")
data class PaletteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorsCsv: String,
    val updatedAt: Long
)

@Entity(tableName = "custom_fonts")
data class CustomFontEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val filePath: String,
    val addedAt: Long
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int
}

@Dao
interface PaletteDao {
    @Query("SELECT * FROM palettes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PaletteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(palette: PaletteEntity)

    @Query("DELETE FROM palettes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM palettes")
    suspend fun count(): Int
}

@Dao
interface CustomFontDao {
    @Query("SELECT * FROM custom_fonts ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<CustomFontEntity>>

    @Query("SELECT * FROM custom_fonts")
    suspend fun getAllSync(): List<CustomFontEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(font: CustomFontEntity)

    @Query("DELETE FROM custom_fonts WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Entity(tableName = "chrome_styles")
data class ChromeStyleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val configJson: String,
    val updatedAt: Long
)

@Dao
interface ChromeStyleDao {
    @Query("SELECT * FROM chrome_styles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ChromeStyleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(style: ChromeStyleEntity)

    @Query("DELETE FROM chrome_styles WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(
    entities = [ProjectEntity::class, PaletteEntity::class, CustomFontEntity::class, ChromeStyleEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SpiderDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun paletteDao(): PaletteDao
    abstract fun customFontDao(): CustomFontDao
    abstract fun chromeStyleDao(): ChromeStyleDao

    companion object {
        @Volatile
        private var INSTANCE: SpiderDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chrome_styles` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `configJson` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): SpiderDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpiderDatabase::class.java,
                    "spider_studio.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}
