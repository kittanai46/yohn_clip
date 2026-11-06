package th.yohnclip.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import th.yohnclip.data.*
import th.yohnclip.export.ExportRunner

data class UiState(
    val loading: Boolean = false,
    val videos: List<VideoWithStatus> = emptyList(),
    val tab: VideoSource = VideoSource.GAME,
    val selected: Set<String> = emptySet(),       // fingerprints
    val exporting: Boolean = false,
    val progress: Pair<Int, Int>? = null,
    val message: String? = null,
    val exportFolder: Pair<Int, Long> = 0 to 0L,  // (จำนวนไฟล์, bytes) ใน Movies/GameExport
    val lastExport: ExportSummary? = null,
) {
    val filtered: List<VideoWithStatus> get() = videos.filter { it.item.source == tab }
    val unsentInTab: List<VideoWithStatus> get() = filtered.filter { !it.sent }
    val selectedItems: List<VideoItem> get() = videos.filter { it.item.fingerprint in selected }.map { it.item }
    val selectedBytes: Long get() = selectedItems.sumOf { it.sizeBytes }
    fun countFor(src: VideoSource) = videos.count { it.item.source == src && !it.sent }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val scanner = VideoScanner(app)
    private val exporter = Exporter(app)
    private val dao = AppDatabase.get(app).sentFileDao()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val scanned = MutableStateFlow<List<VideoItem>>(emptyList())
    private var scanJob: Job? = null
    private var scannedOnce = false

    init {
        // รวมผลสแกน + ตารางส่งแล้ว -> รายการพร้อมสถานะ
        viewModelScope.launch {
            combine(scanned, dao.observeAll()) { items, sent ->
                val map = sent.associateBy { it.fingerprint }
                items.map { VideoWithStatus(it, map.containsKey(it.fingerprint), map[it.fingerprint]?.sentAtMs) }
            }.collect { list -> _state.update { it.copy(videos = list) } }
        }
        // การคัดลอกทำใน ExportService หน้าจอแค่ตามสถานะ
        viewModelScope.launch { ExportRunner.state.collect { onExportState(it) } }
    }

    private suspend fun onExportState(st: ExportRunner.State) {
        when (st) {
            ExportRunner.State.Idle -> _state.update { it.copy(exporting = false, progress = null) }
            is ExportRunner.State.Running -> _state.update { it.copy(exporting = true, progress = st.done to st.total) }
            is ExportRunner.State.Done -> {
                val folder = exporter.exportFolderSize()
                _state.update {
                    it.copy(
                        exporting = false, progress = null, selected = emptySet(), lastExport = st.summary,
                        message = if (st.summary == null) "คัดลอกไม่สำเร็จ ${st.failed} ไฟล์" else null,
                        exportFolder = folder,
                    )
                }
                ExportRunner.consume()
            }
        }
    }

    /** กดสแกนใหม่เอง: แสดงสถานะกำลังสแกน */
    fun rescan() = scan(quiet = false)

    /** กลับเข้าแอป: ถ้าเคยสแกนแล้วให้สแกนเงียบๆ ไม่ให้หน้าจอกระพริบ */
    fun refresh() = scan(quiet = scannedOnce)

    private fun scan(quiet: Boolean) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            if (!quiet) _state.update { it.copy(loading = true) }
            // ไฟล์ที่คัดลอกค้างจากครั้งก่อน (แอปถูกปิดกลางทาง) PC มองไม่เห็นและกินพื้นที่
            if (ExportRunner.state.value !is ExportRunner.State.Running) exporter.cleanupPending()
            scanned.value = scanner.scanAll()
            scannedOnce = true
            _state.update { it.copy(loading = false, exportFolder = exporter.exportFolderSize()) }
        }
    }

    fun setTab(src: VideoSource) = _state.update { it.copy(tab = src) }

    fun toggle(fp: String) = _state.update {
        it.copy(selected = if (fp in it.selected) it.selected - fp else it.selected + fp)
    }

    fun selectAllUnsentInTab() = _state.update { s ->
        s.copy(selected = s.selected + s.unsentInTab.map { it.item.fingerprint })
    }

    /** เลือกเพิ่มทีละกลุ่ม (เช่น ทั้งวัน) */
    fun select(fps: Collection<String>) = _state.update { it.copy(selected = it.selected + fps) }

    fun deselect(fps: Collection<String>) = _state.update { it.copy(selected = it.selected - fps.toSet()) }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /** @return true ถ้าเริ่มคัดลอกแล้ว */
    fun exportSelected(): Boolean {
        val s = _state.value
        val items = s.selectedItems
        if (items.isEmpty() || s.exporting) return false
        // เช็กก่อนเริ่ม ไม่ให้ไปเต็มกลางทาง และเผื่อที่ไว้ให้ระบบ
        val need = items.sumOf { it.sizeBytes } + Exporter.SPACE_MARGIN
        val free = exporter.freeBytes()
        if (need > free) {
            _state.update {
                it.copy(message = "พื้นที่ไม่พอ ต้องใช้ ${humanBytes(need)} (เผื่อให้ระบบ ${Exporter.SPACE_MARGIN / (1024 * 1024)} MB) แต่ว่างอยู่ ${humanBytes(free)}")
            }
            return false
        }
        if (!ExportRunner.start(getApplication(), items)) {
            _state.update { it.copy(message = "เริ่มคัดลอกไม่ได้ ลองกดอีกครั้ง") }
            return false
        }
        return true
    }

    fun dismissExportSummary() = _state.update { it.copy(lastExport = null) }

    /** mark ด้วยมือ (เช่นเคยส่งไปก่อนมีแอป) แล้วล้างที่เลือก */
    fun markSent(fps: List<String>, sent: Boolean) = viewModelScope.launch {
        writeSent(fps, sent)
        _state.update { it.copy(selected = emptySet()) }
    }

    /** mark คลิปเดียวจากแผ่นรายละเอียด โดยไม่กระทบคลิปอื่นที่เลือกไว้ */
    fun setSent(fp: String, sent: Boolean) = viewModelScope.launch { writeSent(listOf(fp), sent) }

    private suspend fun writeSent(fps: List<String>, sent: Boolean) {
        if (sent) {
            val now = System.currentTimeMillis()
            val items = _state.value.videos.filter { it.item.fingerprint in fps }.map { it.item }
            dao.upsertAll(items.map { SentFile(it.fingerprint, it.id, it.displayName, it.sizeBytes, now, "") })
        } else dao.deleteByFingerprints(fps)
    }

    fun clearExportFolder() = viewModelScope.launch {
        val n = exporter.clearExportFolder()
        _state.update { it.copy(message = "ลบไฟล์ในโฟลเดอร์ Export แล้ว $n ไฟล์", exportFolder = 0 to 0L) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
