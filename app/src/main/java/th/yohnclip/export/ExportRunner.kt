package th.yohnclip.export

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import th.yohnclip.data.ExportSummary
import th.yohnclip.data.VideoItem

/**
 * สถานะการคัดลอกที่ใช้ร่วมกันระหว่าง ExportService กับหน้าจอ
 * การคัดลอกอยู่ใน service จึงไม่หยุดเมื่อออกจากแอป ส่วนหน้าจอแค่คอยดูสถานะ
 */
object ExportRunner {
    sealed interface State {
        data object Idle : State
        data class Running(val done: Int, val total: Int) : State
        /** summary = null แปลว่าไม่มีไฟล์ไหนคัดลอกสำเร็จ */
        data class Done(val summary: ExportSummary?, val failed: Int) : State
    }

    val state = MutableStateFlow<State>(State.Idle)

    /** รายการที่รอให้ service หยิบไปคัดลอก */
    @Volatile var pending: List<VideoItem> = emptyList()

    /** หน้าจอแอปเปิดอยู่ไหม ถ้าเปิดอยู่จะไม่แจ้งเตือนตอนเสร็จ เพราะหน้าจอแสดงผลเองแล้ว */
    @Volatile var uiVisible = false

    /** @return false ถ้ากำลังคัดลอกอยู่แล้ว หรือระบบไม่ยอมให้เริ่ม service */
    fun start(context: Context, items: List<VideoItem>): Boolean {
        if (items.isEmpty() || state.value is State.Running) return false
        pending = items
        state.value = State.Running(0, items.size)
        return try {
            ContextCompat.startForegroundService(context, Intent(context, ExportService::class.java))
            true
        } catch (e: Exception) {
            // เช่น Android 12+ ไม่ให้เริ่ม service ตอนแอปอยู่เบื้องหลัง
            pending = emptyList()
            state.value = State.Idle
            false
        }
    }

    /** หน้าจอรับผลแล้ว */
    fun consume() {
        if (state.value is State.Done) state.value = State.Idle
    }
}
