package th.yohnclip.data

import java.util.Locale

/**
 * แยกว่าวิดีโอมาจากไหน โดยดูจาก (1) โฟลเดอร์ (2) ชื่อไฟล์ (3) สัดส่วนภาพ
 * ปรับเพิ่มคำใน list ได้ตามเกม/แอปที่คุณใช้
 */
object SourceClassifier {

    // โฟลเดอร์ของกล้อง
    private val cameraDirs = listOf("dcim/camera", "dcim/100andro", "dcim/100media", "/camera/")

    // โฟลเดอร์ของแอปอัดหน้าจอ / เกม (ตัวพิมพ์เล็กทั้งหมด)
    private val gameDirs = listOf(
        "screenrecorder", "screen record", "screenrecord", "screen_record",
        "capture", "gamelauncher", "game launcher", "game recorder", "gamerecorder",
        "az screen", "xrecorder", "mobizen", "du recorder",
        "movies/recorder", "recorder",
        "pubg", "freefire", "free fire", "roblox", "genshin", "honkai", "valorant",
        "codm", "call of duty", "rov", "arena of valor", "minecraft", "mlbb",
        "mobile legends", "wild rift", "brawl", "clash", "fortnite", "apex",
        "tiktok", // อัดจอเกมแล้ว tiktok ก็มักเก็บแบบนี้ -> ปรับได้
    )

    // คำในชื่อไฟล์ที่บ่งว่าเป็นอัดจอ
    private val gameFilePrefixes = listOf(
        "screenrecord", "screen_record", "screen record", "screenrecorder",
        "record_", "rec_", "capture", "gamerecord", "game_", "clip_",
    )

    // ชื่อไฟล์จากกล้องมักขึ้นต้นแบบนี้
    private val cameraFilePrefixes = listOf("vid_", "vid-", "mov_", "img_", "pxl_", "dsc", "mvi_")

    fun classify(relativePath: String, displayName: String, width: Int, height: Int): VideoSource {
        val dir = relativePath.lowercase(Locale.ROOT)
        val name = displayName.lowercase(Locale.ROOT)

        // 1) โฟลเดอร์ชัดเจน
        if (cameraDirs.any { dir.contains(it) } && !gameDirs.any { dir.contains(it) }) {
            // ยกเว้นถ้าชื่อไฟล์เป็นอัดจอชัดๆ (บางแอปเก็บอัดจอลง DCIM)
            if (gameFilePrefixes.any { name.startsWith(it) }) return VideoSource.GAME
            return VideoSource.CAMERA
        }
        if (gameDirs.any { dir.contains(it) }) return VideoSource.GAME

        // 2) ชื่อไฟล์
        if (gameFilePrefixes.any { name.startsWith(it) || name.contains(it) }) return VideoSource.GAME
        if (cameraFilePrefixes.any { name.startsWith(it) }) return VideoSource.CAMERA

        // 3) heuristic สัดส่วนภาพ: อัดจอมักเป็นสัดส่วนจอมือถือพอดี (เช่น 1080x2400, 2400x1080)
        if (width > 0 && height > 0) {
            val long = maxOf(width, height).toFloat()
            val short = minOf(width, height).toFloat()
            val ratio = long / short
            val phoneRatios = listOf(2.0f, 2.111f, 2.167f, 2.222f, 2.333f, 2.4f) // 18:9 .. 21.6:9
            val looksLikePhoneScreen = phoneRatios.any { kotlin.math.abs(ratio - it) < 0.03f }
            val looksLikeCamera = kotlin.math.abs(ratio - 16f / 9f) < 0.03f || kotlin.math.abs(ratio - 4f / 3f) < 0.03f
            if (looksLikePhoneScreen && !looksLikeCamera) return VideoSource.GAME
        }
        return VideoSource.OTHER
    }

    /** เดาชื่อเกมจากโฟลเดอร์ย่อยสุดท้าย หรือ prefix ของชื่อไฟล์ */
    fun guessGameName(relativePath: String, displayName: String): String? {
        val segs = relativePath.trim('/').split('/').filter { it.isNotBlank() }
        val last = segs.lastOrNull() ?: return null
        val generic = setOf("movies", "dcim", "download", "camera", "screenrecorder", "screen recorder",
            "recorder", "videos", "pictures", "screenrecords", "screen_recordings")
        if (last.lowercase(Locale.ROOT) !in generic) return last
        // ลองจากชื่อไฟล์: ตัดส่วนที่เป็นตัวเลข/วันที่ออก
        val base = displayName.substringBeforeLast('.')
        val word = base.split('_', '-', ' ').firstOrNull { it.any { c -> c.isLetter() } && it.length >= 3 }
        return word?.takeIf { it.lowercase(Locale.ROOT) !in setOf("screenrecord", "screen", "record", "video", "vid") }
    }
}
