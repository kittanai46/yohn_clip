package th.yohnclip.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import th.yohnclip.data.*

data class UiState(
    val loading: Boolean = false,
    val videos: List<VideoWithStatus> = emptyList(),
    val tab: VideoSource = VideoSource.GAME,
    val selected: Set<String> = emptySet(),       // fingerprints
    val exporting: Boolean = false,
    val progress: Pair<Int, Int>? = null,
    val message: String? = null,
    val exportFolder: Pair<Int, Long> = 0 to 0L,  // (จำนวนไฟล์, bytes) ใน Movies/GameExport
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

    init {
        // รวมผลสแกน + ตารางส่งแล้ว -> รายการพร้อมสถานะ
        viewModelScope.launch {
            combine(scanned, dao.observeAll()) { items, sent ->
                val map = sent.associateBy { it.fingerprint }
                items.map { VideoWithStatus(it, map.containsKey(it.fingerprint), map[it.fingerprint]?.sentAtMs) }
            }.collect { list -> _state.update { it.copy(videos = list) } }
        }
    }

    fun rescan() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        scanned.value = scanner.scanAll()
        _state.update { it.copy(loading = false, exportFolder = exporter.exportFolderSize()) }
    }

    fun setTab(src: VideoSource) = _state.update { it.copy(tab = src) }

    fun toggle(fp: String) = _state.update {
        it.copy(selected = if (fp in it.selected) it.selected - fp else it.selected + fp)
    }

    fun selectAllUnsentInTab() = _state.update { s ->
        s.copy(selected = s.selected + s.unsentInTab.map { it.item.fingerprint })
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun exportSelected() = viewModelScope.launch {
        val items = _state.value.selectedItems
        if (items.isEmpty()) return@launch
        _state.update { it.copy(exporting = true, progress = 0 to items.size) }
        val result = exporter.export(items) { d, t -> _state.update { it.copy(progress = d to t) } }
        val now = System.currentTimeMillis()
        dao.upsertAll(result.ok.map { (v, name) ->
            SentFile(v.fingerprint, v.id, v.displayName, v.sizeBytes, now, name)
        })
        val msg = buildString {
            append("คัดลอกไป Movies/GameExport แล้ว ${result.ok.size} ไฟล์")
            if (result.failed.isNotEmpty()) append(" (ล้มเหลว ${result.failed.size})")
        }
        _state.update {
            it.copy(exporting = false, progress = null, selected = emptySet(), message = msg,
                exportFolder = exporter.exportFolderSize())
        }
    }

    /** mark ด้วยมือ (เช่นเคยส่งไปก่อนมีแอป) */
    fun markSent(fps: List<String>, sent: Boolean) = viewModelScope.launch {
        if (sent) {
            val now = System.currentTimeMillis()
            val items = _state.value.videos.filter { it.item.fingerprint in fps }.map { it.item }
            dao.upsertAll(items.map { SentFile(it.fingerprint, it.id, it.displayName, it.sizeBytes, now, "") })
        } else dao.deleteByFingerprints(fps)
        _state.update { it.copy(selected = emptySet()) }
    }

    fun clearExportFolder() = viewModelScope.launch {
        val n = exporter.clearExportFolder()
        _state.update { it.copy(message = "ลบไฟล์ในโฟลเดอร์ Export แล้ว $n ไฟล์", exportFolder = 0 to 0L) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
