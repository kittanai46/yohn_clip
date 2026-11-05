package th.yohnclip.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** สแกนวิดีโอทั้งเครื่องผ่าน MediaStore ไม่ต้องไล่โฟลเดอร์เอง */
class VideoScanner(private val context: Context) {

    suspend fun scanAll(excludeRelativePathPrefix: String = Exporter.EXPORT_RELATIVE_PATH): List<VideoItem> =
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.RELATIVE_PATH,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
            )
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val out = ArrayList<VideoItem>()
            context.contentResolver.query(
                collection, projection, null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val iPath = c.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val iMod = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val iW = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                while (c.moveToNext()) {
                    val relPath = c.getString(iPath) ?: ""
                    // ข้ามไฟล์ในโฟลเดอร์ Export ของเราเอง
                    if (relPath.startsWith(excludeRelativePathPrefix, ignoreCase = true)) continue
                    val id = c.getLong(iId)
                    val name = c.getString(iName) ?: "video_$id.mp4"
                    val w = c.getInt(iW)
                    val h = c.getInt(iH)
                    out += VideoItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = name,
                        relativePath = relPath,
                        sizeBytes = c.getLong(iSize),
                        dateAddedSec = c.getLong(iAdded),
                        dateModifiedSec = c.getLong(iMod),
                        durationMs = c.getLong(iDur),
                        width = w,
                        height = h,
                        source = SourceClassifier.classify(relPath, name, w, h),
                        gameName = SourceClassifier.guessGameName(relPath, name),
                    )
                }
            }
            out
        }
}
