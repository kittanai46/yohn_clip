package th.yohnclip.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * คัดลอกไฟล์ไปที่ Movies/GameExport/<วันที่>/ โดยผ่าน MediaStore
 * เพื่อให้ PC เห็นทันทีเมื่อเสียบ USB (MTP จะเห็นเฉพาะไฟล์ที่ถูก index แล้ว)
 */
class Exporter(private val context: Context) {

    companion object {
        const val EXPORT_RELATIVE_PATH = "Movies/GameExport/"
    }

    data class Result(val ok: List<Pair<VideoItem, String>>, val failed: List<Pair<VideoItem, String>>)

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val nameFmt = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    fun exportedName(item: VideoItem): String {
        val ext = item.displayName.substringAfterLast('.', "mp4")
        val game = (item.gameName ?: item.source.label).replace(Regex("[^A-Za-z0-9ก-๙_-]"), "")
            .ifBlank { "Clip" }
        val stamp = nameFmt.format(Date(item.dateAddedSec * 1000))
        return "${game}_$stamp.$ext"
    }

    suspend fun export(
        items: List<VideoItem>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val ok = ArrayList<Pair<VideoItem, String>>()
        val failed = ArrayList<Pair<VideoItem, String>>()
        val resolver = context.contentResolver
        val batchDay = dayFmt.format(Date())
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        items.forEachIndexed { idx, item ->
            val newName = exportedName(item)
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, newName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "$EXPORT_RELATIVE_PATH$batchDay/")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val dst = resolver.insert(collection, values)
            if (dst == null) {
                failed += item to "insert ล้มเหลว"
            } else {
                try {
                    resolver.openInputStream(item.uri).use { input ->
                        resolver.openOutputStream(dst).use { output ->
                            requireNotNull(input); requireNotNull(output)
                            input.copyTo(output, 1 shl 20)
                        }
                    }
                    values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(dst, values, null, null)
                    ok += item to newName
                } catch (e: Exception) {
                    resolver.delete(dst, null, null)
                    failed += item to (e.message ?: e.javaClass.simpleName)
                }
            }
            onProgress(idx + 1, items.size)
        }
        Result(ok, failed)
    }

    /** ลบทุกไฟล์ในโฟลเดอร์ Export (หลังจากคัดลอกไป PC เสร็จแล้ว) */
    suspend fun clearExportFolder(): Int = withContext(Dispatchers.IO) {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.delete(
            collection,
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("$EXPORT_RELATIVE_PATH%")
        )
    }

    suspend fun exportFolderSize(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var n = 0; var bytes = 0L
        context.contentResolver.query(
            collection, arrayOf(MediaStore.Video.Media.SIZE),
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?", arrayOf("$EXPORT_RELATIVE_PATH%"), null
        )?.use { c -> while (c.moveToNext()) { n++; bytes += c.getLong(0) } }
        n to bytes
    }
}
