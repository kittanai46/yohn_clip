package th.yohnclip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import th.yohnclip.data.VideoSource
import th.yohnclip.data.VideoWithStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayFmt = SimpleDateFormat("EEE d MMM yyyy", Locale("th", "TH"))
private val timeFmt = SimpleDateFormat("HH:mm", Locale.US)

fun humanBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
    b >= 1L shl 20 -> "%.1f MB".format(b / (1024.0 * 1024))
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

fun humanDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(s.message) {
        s.message?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Yohn Clip") },
                actions = {
                    IconButton(onClick = { vm.rescan() }, enabled = !s.loading) {
                        Icon(Icons.Default.Refresh, "สแกนใหม่")
                    }
                }
            )
        },
        bottomBar = { BottomActionBar(s, vm, onClearExport = { confirmClear = true }) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // แท็บแหล่งที่มา
            TabRow(selectedTabIndex = VideoSource.entries.indexOf(s.tab)) {
                VideoSource.entries.forEach { src ->
                    val n = s.countFor(src)
                    Tab(
                        selected = s.tab == src,
                        onClick = { vm.setTab(src) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(src.label)
                                if (n > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    Badge { Text("$n") }
                                }
                            }
                        }
                    )
                }
            }

            if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            // แถวเครื่องมือ
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ยังไม่ส่ง ${s.unsentInTab.size} / ทั้งหมด ${s.filtered.size}", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { vm.selectAllUnsentInTab() }, enabled = s.unsentInTab.isNotEmpty()) {
                    Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(4.dp)); Text("เลือกที่ยังไม่ส่งทั้งหมด")
                }
            }

            if (!s.loading && s.filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ไม่พบวิดีโอในกลุ่มนี้", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                VideoList(s, vm)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("ลบไฟล์ในโฟลเดอร์ Export?") },
            text = { Text("จะลบ ${s.exportFolder.first} ไฟล์ (${humanBytes(s.exportFolder.second)}) ใน Movies/GameExport\nต้นฉบับในมือถือยังอยู่เหมือนเดิม และสถานะ \"ส่งแล้ว\" ยังถูกจำไว้") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearExportFolder() }) { Text("ลบ") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("ยกเลิก") } }
        )
    }
}

@Composable
private fun VideoList(s: UiState, vm: MainViewModel) {
    // จัดกลุ่มตามวันที่อัด
    val groups = remember(s.filtered) {
        s.filtered.groupBy { dayFmt.format(Date(it.item.dateAddedSec * 1000)) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        groups.forEach { (day, list) ->
            item(key = "h_$day") {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(day, fontWeight = FontWeight.SemiBold)
                    Text("${list.count { !it.sent }} ยังไม่ส่ง · ${humanBytes(list.sumOf { it.item.sizeBytes })}",
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            items(list, key = { it.item.fingerprint }) { v -> VideoRow(v, v.item.fingerprint in s.selected) { vm.toggle(it) } }
        }
    }
}

@Composable
private fun VideoRow(v: VideoWithStatus, checked: Boolean, onToggle: (String) -> Unit) {
    val item = v.item
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(item.fingerprint) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { _ -> onToggle(item.fingerprint) })
        AsyncImage(
            model = item.uri, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 72.dp, height = 48.dp).clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.displayName, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${timeFmt.format(Date(item.dateAddedSec * 1000))} · ${humanDuration(item.durationMs)} · ${humanBytes(item.sizeBytes)}" +
                        (item.gameName?.let { g -> " · $g" } ?: ""),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(item.relativePath, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline, maxLines = 1)
        }
        if (v.sent) {
            Icon(Icons.Default.CheckCircle, "ส่งแล้ว", tint = MaterialTheme.colorScheme.primary)
        } else {
            Icon(Icons.Default.RadioButtonUnchecked, "ยังไม่ส่ง", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun BottomActionBar(s: UiState, vm: MainViewModel, onClearExport: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            if (s.exporting && s.progress != null) {
                val (d, t) = s.progress
                Text("กำลังคัดลอก $d / $t", style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(progress = { if (t == 0) 0f else d.toFloat() / t }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("เลือก ${s.selected.size} ไฟล์ · ${humanBytes(s.selectedBytes)}", style = MaterialTheme.typography.bodyMedium)
                    Text("ใน Export: ${s.exportFolder.first} ไฟล์ · ${humanBytes(s.exportFolder.second)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = { vm.markSent(s.selected.toList(), true) }, enabled = s.selected.isNotEmpty()) {
                        Icon(Icons.Default.Check, "mark ส่งแล้ว")
                    }
                    IconButton(onClick = { vm.markSent(s.selected.toList(), false) }, enabled = s.selected.isNotEmpty()) {
                        Icon(Icons.Default.Undo, "mark ยังไม่ส่ง")
                    }
                    IconButton(onClick = onClearExport, enabled = s.exportFolder.first > 0) {
                        Icon(Icons.Default.DeleteSweep, "เคลียร์ Export")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.clearSelection() }, enabled = s.selected.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("ล้างที่เลือก")
                }
                Button(onClick = { vm.exportSelected() }, enabled = s.selected.isNotEmpty() && !s.exporting, modifier = Modifier.weight(2f)) {
                    Icon(Icons.Default.Upload, null); Spacer(Modifier.width(6.dp)); Text("จัดเตรียมส่ง USB")
                }
            }
        }
    }
}
