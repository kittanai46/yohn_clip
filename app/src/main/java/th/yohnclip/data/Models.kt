package th.yohnclip.data

import android.net.Uri

enum class VideoSource(val label: String) {
    GAME("อัดจอเกม"),
    CAMERA("กล้อง"),
    OTHER("อื่นๆ")
}

/** วิดีโอ 1 ไฟล์ที่สแกนได้จาก MediaStore */
data class VideoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val relativePath: String,   // เช่น "Movies/ScreenRecorder/"
    val sizeBytes: Long,
    val dateAddedSec: Long,      // epoch seconds
    val dateModifiedSec: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val source: VideoSource,
    val gameName: String?,      // เดาจากชื่อโฟลเดอร์/ชื่อไฟล์
) {
    /** key สำหรับจำว่าเคยส่งแล้ว: id + size + mtime ทนต่อชื่อซ้ำและย้ายโฟลเดอร์ */
    val fingerprint: String get() = "$id:$sizeBytes:$dateModifiedSec"
}

data class VideoWithStatus(
    val item: VideoItem,
    val sent: Boolean,
    val sentAtMs: Long?,
)
