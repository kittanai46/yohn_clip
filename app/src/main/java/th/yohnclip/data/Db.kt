package th.yohnclip.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sent_files")
data class SentFile(
    @PrimaryKey val fingerprint: String,   // id:size:mtime
    val mediaId: Long,
    val displayName: String,
    val sizeBytes: Long,
    val sentAtMs: Long,
    val exportedName: String,
)

@Dao
interface SentFileDao {
    @Query("SELECT * FROM sent_files")
    fun observeAll(): Flow<List<SentFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SentFile>)

    @Query("DELETE FROM sent_files WHERE fingerprint IN (:fps)")
    suspend fun deleteByFingerprints(fps: List<String>)

    @Query("DELETE FROM sent_files")
    suspend fun clear()
}

@Database(entities = [SentFile::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sentFileDao(): SentFileDao

    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "yohnclip.db")
                .fallbackToDestructiveMigration()
                .build().also { inst = it }
        }
    }
}
