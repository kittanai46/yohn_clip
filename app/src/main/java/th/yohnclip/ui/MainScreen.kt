package th.yohnclip.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import th.yohnclip.PREFS
import th.yohnclip.data.ExportSummary
import th.yohnclip.data.VideoItem
import th.yohnclip.data.VideoSource
import th.yohnclip.data.VideoWithStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fullFmt = SimpleDateFormat("d MMM yyyy HH:mm", Locale("th", "TH"))
private val dayFmt = SimpleDateFormat("EEE d MMM yyyy", Locale("th", "TH"))
private val timeFmt = SimpleDateFormat("HH:mm", Locale.US)

// มุมโค้งตามดีไซน์: การ์ด 16, ปุ่มและช่อง 12, ภาพย่อ 8, แผ่นล่าง 24
private val CardShape = RoundedCornerShape(16.dp)
private val ControlShape = RoundedCornerShape(12.dp)
private val ThumbShape = RoundedCornerShape(8.dp)
private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
/** แถบหัวสีแดง โค้งเฉพาะมุมขวาล่าง */
private val BarShape = RoundedCornerShape(bottomEnd = 24.dp)
/** สีแดงหลักของแบรนด์ ใช้กับตัวหนังสือบนปุ่มขาวในการ์ดสรุป (ทั้งสองโหมด) */
private val Brand = Color(0xFFA3122F)

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

/** หัวข้อวัน: วันนี้ / เมื่อวาน / วันที่เต็ม */
private fun dayLabel(epochSec: Long): String {
    val day = dayFmt.format(Date(epochSec * 1000))
    val now = System.currentTimeMillis()
    return when (day) {
        dayFmt.format(Date(now)) -> "วันนี้"
        dayFmt.format(Date(now - 24L * 3600 * 1000)) -> "เมื่อวาน"
        else -> day
    }
}

/** ชื่อที่แสดงของคลิป: ชื่อเกมถ้าเดาได้ ไม่งั้นใช้ชื่อไฟล์ */
private fun clipTitle(item: VideoItem) = item.gameName ?: item.displayName.substringBeforeLast('.')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel, partialAccess: Boolean = false, onRequestFullAccess: () -> Unit = {}) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val send = {
        // เริ่มคัดลอกก่อน แล้วค่อยขอสิทธิ์แจ้งเตือนครั้งเดียว (ไม่ให้ก็คัดลอกได้ แค่ไม่เห็นความคืบหน้าในแถบแจ้งเตือน)
        if (vm.exportSelected() && needsNotificationAsk(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val snack = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    var detailFp by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(s.message) {
        s.message?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }

    val barVisible = s.selected.isNotEmpty() || s.exporting
    Scaffold(
        containerColor = C.bg,
        snackbarHost = {
            SnackbarHost(snack) { data ->
                Snackbar(
                    data, shape = ControlShape, containerColor = C.ink, contentColor = C.card,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        },
        topBar = { AppBar(s, vm, onAskClear = { confirmClear = true }) },
        bottomBar = { SelectionBar(s, vm, barVisible, onSend = send) }
    ) { pad ->
        // จัดกลุ่มตามวันที่อัด
        val groups = remember(s.filtered) { s.filtered.groupBy { dayLabel(it.item.dateAddedSec) } }
        val scanningFirst = s.loading && s.filtered.isEmpty()
        // แอปวาดเต็มจอ ถ้าไม่มีแถบล่าง รายการต้องเผื่อที่ให้แถบนำทางเอง
        val navBottom = if (barVisible) 0.dp
        else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp + navBottom),
        ) {
            item(key = "hero") { Summary(s, onSelectAll = { vm.selectAllUnsentInTab() }) }
            item(key = "tabs") { SourceTabs(s, onPick = { vm.setTab(it) }) }

            if (partialAccess) {
                item(key = "partial") {
                    InfoCard(
                        Icons.Outlined.PhotoLibrary, "เห็นเฉพาะคลิปที่เลือกไว้",
                        "คลิปใหม่จะไม่ขึ้นจนกว่าจะให้สิทธิ์ทั้งหมด", "ให้สิทธิ์", onRequestFullAccess
                    )
                }
            }

            // ไฟล์ที่ยังค้างในโฟลเดอร์ Export (กินพื้นที่อยู่)
            if (s.exportFolder.first > 0) {
                item(key = "export") {
                    InfoCard(
                        Icons.Outlined.Usb, "พร้อมลากไป PC",
                        "${s.exportFolder.first} ไฟล์ · ${humanBytes(s.exportFolder.second)} ใน Movies/GameExport",
                        "ลบ", { confirmClear = true }, enabled = !s.exporting
                    )
                }
            }

            if (scanningFirst) {
                item(key = "skeleton_head") { SectionHead { Text("กำลังสแกน", style = textStyle(14f, 20f, 600), color = C.muted) } }
                items(4, key = { "skeleton_$it" }) { SkeletonRow(first = it == 0, last = it == 3) }
            }

            if (!s.loading && s.filtered.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }

            groups.forEach { (day, list) ->
                item(key = "h_$day") { DayHeader(day, list, s, vm) }
                itemsIndexed(list, key = { _, v -> v.item.fingerprint }) { i, v ->
                    VideoRow(
                        v, first = i == 0, last = i == list.lastIndex,
                        checked = v.item.fingerprint in s.selected, enabled = !s.exporting,
                        onToggle = { vm.toggle(v.item.fingerprint) }, onDetail = { detailFp = v.item.fingerprint }
                    )
                }
            }

            if (s.filtered.isNotEmpty()) {
                item(key = "foot") {
                    Text(
                        "แตะแถวเพื่อเลือก · แตะภาพเพื่อดูรายละเอียด",
                        Modifier.fillMaxWidth().padding(top = 24.dp),
                        style = textStyle(13f, 20f), color = C.muted, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val detailSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    s.videos.firstOrNull { it.item.fingerprint == detailFp }?.let { v ->
        AppSheet(state = detailSheet, onDismiss = { detailFp = null }) {
            DetailSheet(
                v, checked = v.item.fingerprint in s.selected, enabled = !s.exporting,
                onToggleSent = { vm.setSent(v.item.fingerprint, !v.sent) },
                onToggleSelect = {
                    vm.toggle(v.item.fingerprint)
                    scope.launch { detailSheet.hide() }.invokeOnCompletion { detailFp = null }
                }
            )
        }
    }

    val readySheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    s.lastExport?.let { summary ->
        AppSheet(state = readySheet, onDismiss = { vm.dismissExportSummary() }) {
            ReadySheet(
                summary,
                onClear = {
                    scope.launch { readySheet.hide() }.invokeOnCompletion {
                        vm.dismissExportSummary()
                        confirmClear = true
                    }
                },
                onDone = {
                    scope.launch { readySheet.hide() }.invokeOnCompletion { vm.dismissExportSummary() }
                }
            )
        }
    }

    if (confirmClear) {
        ClearDialog(
            s,
            onCancel = { confirmClear = false },
            onConfirm = { confirmClear = false; vm.clearExportFolder() }
        )
    }
}

/** แถบหัวสีแดงต่อจากแถบสถานะ ชื่อแอปกับปุ่มสแกนและเมนู */
@Composable
private fun AppBar(s: UiState, vm: MainViewModel, onAskClear: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(C.hero, BarShape).statusBarsPadding()
            .heightIn(min = 64.dp).padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("โยนคลิป", Modifier.weight(1f), style = textStyle(20f, 28f, 700), color = Color.White)
        val iconColors = IconButtonDefaults.iconButtonColors(
            contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.45f)
        )
        IconButton(onClick = { vm.rescan() }, enabled = !s.loading && !s.exporting, colors = iconColors) {
            Icon(Icons.Rounded.Refresh, "สแกนใหม่", Modifier.size(24.dp))
        }
        Box {
            IconButton(onClick = { menuOpen = true }, colors = iconColors) {
                Icon(Icons.Rounded.MoreVert, "เมนูเพิ่มเติม", Modifier.size(24.dp))
            }
            DropdownMenu(
                expanded = menuOpen, onDismissRequest = { menuOpen = false },
                shape = ControlShape, containerColor = C.card,
                tonalElevation = 0.dp, shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, C.line)
            ) {
                val hasPicked = s.selected.isNotEmpty() && !s.exporting
                MenuRow("ทำเครื่องหมายว่าส่งแล้ว", enabled = hasPicked) {
                    menuOpen = false; vm.markSent(s.selected.toList(), true)
                }
                MenuRow("ทำเครื่องหมายว่ายังไม่ส่ง", enabled = hasPicked) {
                    menuOpen = false; vm.markSent(s.selected.toList(), false)
                }
                MenuRow(
                    "ลบไฟล์ในโฟลเดอร์ Export",
                    enabled = s.exportFolder.first > 0 && !s.exporting, color = C.pri
                ) { menuOpen = false; onAskClear() }
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, enabled: Boolean, color: Color = C.ink, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = textStyle(15f, 22f)) },
        enabled = enabled, onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = color, disabledTextColor = C.muted.copy(alpha = 0.6f))
    )
}

/** การ์ดสรุปสีแดงของแท็บปัจจุบัน: รอส่งกี่คลิป ขนาดเท่าไร ส่งไปแล้วเท่าไร */
@Composable
private fun Summary(s: UiState, onSelectAll: () -> Unit) {
    val total = s.filtered.size
    val unsent = s.unsentInTab
    val scanningFirst = s.loading && total == 0
    val allDone = unsent.isEmpty() && total > 0
    val soft = Color.White.copy(alpha = 0.88f)
    Column(Modifier.fillMaxWidth().background(C.hero, CardShape).padding(20.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(if (allDone) "ไม่มีคลิปค้าง" else "รอส่งไป PC", style = textStyle(14f, 21f), color = soft)
                Text(
                    when {
                        scanningFirst -> "กำลังสแกน…"
                        allDone -> "ส่งครบแล้ว"
                        else -> "${unsent.size} คลิป"
                    },
                    style = textStyle(32f, 42f, 700), color = Color.White, maxLines = 1
                )
            }
            if (unsent.isNotEmpty()) {
                // ปุ่มขาวบนการ์ดแดง แบบปุ่มเข้าสู่ระบบในดีไซน์
                Button(
                    onClick = onSelectAll, enabled = !s.exporting && !s.loading, shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White, contentColor = Brand,
                        disabledContainerColor = Color.White.copy(alpha = 0.5f), disabledContentColor = Brand.copy(alpha = 0.6f)
                    ),
                    elevation = null, contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(bottom = 4.dp).heightIn(min = 44.dp)
                ) { Text("เลือกทั้งหมด", style = textStyle(14f, 20f, 600)) }
            }
        }
        Text(
            when {
                scanningFirst -> "กำลังหาวิดีโอในเครื่อง"
                unsent.isEmpty() -> "ส่งแล้ว ${total - unsent.size} จาก $total คลิป"
                else -> "${humanBytes(unsent.sumOf { it.item.sizeBytes })} · ส่งแล้ว ${total - unsent.size} จาก $total คลิป"
            },
            style = textStyle(14f, 21f), color = soft
        )
        Spacer(Modifier.height(14.dp))
        val bar = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
        if (s.loading) {
            LinearProgressIndicator(bar, color = Color.White, trackColor = Color.White.copy(alpha = 0.25f), gapSize = 0.dp)
        } else {
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else (total - unsent.size).toFloat() / total },
                modifier = bar, color = Color.White, trackColor = Color.White.copy(alpha = 0.25f),
                gapSize = 0.dp, drawStopIndicator = {}
            )
        }
    }
}

/** แท็บแบบแถบแบ่งช่อง ช่องที่เลือกเป็นพื้นชมพูอ่อน ตัวเลข = คลิปที่ยังไม่ส่ง */
@Composable
private fun SourceTabs(s: UiState, onPick: (VideoSource) -> Unit) {
    Row(
        Modifier.padding(top = 16.dp).fillMaxWidth()
            .background(C.card, ControlShape).border(1.dp, C.line, ControlShape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        VideoSource.entries.forEach { src ->
            val on = s.tab == src
            val n = s.countFor(src)
            Row(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (on) C.soft else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onPick(src) })
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    src.label, maxLines = 1,
                    style = textStyle(14f, 20f, if (on) 600 else 400),
                    color = if (on) C.sel else C.muted
                )
                if (n > 0) {
                    Spacer(Modifier.width(5.dp))
                    Text("$n", style = textStyle(14f, 20f, 700), color = if (on) C.sel else C.pri, maxLines = 1)
                }
            }
        }
    }
}

/** ช่องไอคอนพื้นชมพูอ่อน แบบเมนูในดีไซน์ */
@Composable
private fun IconTile(icon: ImageVector, size: Int = 40) {
    Box(
        Modifier.size(size.dp).background(C.icf, RoundedCornerShape((size / 4).dp)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, Modifier.size((size * 0.55f).dp), tint = C.pri) }
}

/** การ์ดแจ้งสถานะหนึ่งแถว: ไอคอน หัวข้อ คำอธิบาย และปุ่มลิงก์ทางขวา */
@Composable
private fun InfoCard(
    icon: ImageVector, title: String, sub: String, action: String, onAction: () -> Unit, enabled: Boolean = true,
) {
    Row(
        Modifier.padding(top = 12.dp).fillMaxWidth()
            .background(C.card, CardShape).border(1.dp, C.line, CardShape)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = textStyle(15f, 22f, 600), color = C.ink)
            Text(sub, style = textStyle(13f, 20f), color = C.muted)
        }
        TextButton(
            onClick = onAction, enabled = enabled,
            colors = ButtonDefaults.textButtonColors(contentColor = C.pri),
            modifier = Modifier.heightIn(min = 44.dp)
        ) { Text(action, style = textStyle(14f, 21f, 600)) }
    }
}

/** ขอสิทธิ์แจ้งเตือนแค่ครั้งแรกที่ส่ง (Android 13+) */
private fun needsNotificationAsk(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    ) return false
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean("asked_notifications", false)) return false
    prefs.edit { putBoolean("asked_notifications", true) }
    return true
}

/**
 * แถวในการ์ดกลุ่ม: แถวแรกโค้งบน แถวสุดท้ายโค้งล่าง มีเส้นแบ่งระหว่างแถว
 * แต่ละแถวเป็น item แยกใน LazyColumn จึงต้องวาดขอบการ์ดเอง
 */
private fun Modifier.groupCell(first: Boolean, last: Boolean, fill: Color, line: Color): Modifier =
    clip(
        RoundedCornerShape(
            topStart = if (first) 16.dp else 0.dp, topEnd = if (first) 16.dp else 0.dp,
            bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp,
        )
    ).drawBehind {
        val r = 16.dp.toPx()
        val w = 1.dp.toPx()
        // ยืดกรอบเลยขอบบน/ล่างของแถวกลาง แล้วตัดให้เหลือแค่ในแถว จึงเห็นแค่ขอบข้าง
        val top = if (first) 0f else -2 * r
        val bottom = if (last) size.height else size.height + 2 * r
        clipRect {
            drawRoundRect(fill, Offset(0f, top), Size(size.width, bottom - top), CornerRadius(r))
            drawRoundRect(
                line, Offset(w / 2, top + w / 2), Size(size.width - w, bottom - top - w),
                CornerRadius(r - w / 2), style = Stroke(w)
            )
        }
        if (!first) drawRect(line, Offset.Zero, Size(size.width, w))
    }

@Composable
private fun SectionHead(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp).heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
}

@Composable
private fun SkeletonRow(first: Boolean, last: Boolean) {
    Row(
        Modifier.fillMaxWidth().groupCell(first, last, C.card, C.line).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(96.dp).height(54.dp).background(C.nSoft, ThumbShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).height(12.dp).background(C.nSoft, CircleShape))
            Box(Modifier.fillMaxWidth(0.4f).height(10.dp).background(C.nSoft, CircleShape))
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.padding(top = 12.dp).fillMaxWidth()
            .background(C.card, CardShape).border(1.dp, C.line, CardShape)
            .padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconTile(Icons.Outlined.Videocam, size = 56)
        Spacer(Modifier.height(14.dp))
        Text("ไม่พบวิดีโอในกลุ่มนี้", style = textStyle(16f, 24f, 600), color = C.ink)
        Spacer(Modifier.height(2.dp))
        Text("ลองดูแท็บอื่น หรือกดสแกนใหม่", style = textStyle(14f, 21f), color = C.muted)
    }
}

/** หัวข้อของแต่ละวัน: วันที่ · ยังไม่ส่งกี่คลิป · ขนาด และปุ่มเลือกทั้งวัน */
@Composable
private fun DayHeader(day: String, list: List<VideoWithStatus>, s: UiState, vm: MainViewModel) {
    val unsent = list.filter { !it.sent }
    SectionHead {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight(600))) { append(day) }
                append(
                    " · " + (if (unsent.isEmpty()) "ส่งครบแล้ว" else "${unsent.size} ยังไม่ส่ง") +
                            " · ${humanBytes(list.sumOf { it.item.sizeBytes })}"
                )
            },
            Modifier.weight(1f), style = textStyle(14f, 20f), color = C.muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        // เลือกคลิปที่ยังไม่ส่งของวันนี้ทั้งหมด (ถ้าส่งครบแล้ว = เลือกทุกคลิป เผื่อส่งซ้ำ) กดอีกครั้งเพื่อยกเลิก
        val targets = unsent.ifEmpty { list }.map { it.item.fingerprint }
        val allPicked = targets.all { it in s.selected }
        TextButton(
            onClick = { if (allPicked) vm.deselect(targets) else vm.select(targets) },
            enabled = !s.exporting,
            colors = ButtonDefaults.textButtonColors(contentColor = if (unsent.isEmpty()) C.muted else C.pri),
            contentPadding = PaddingValues(start = 10.dp, end = 0.dp),
            modifier = Modifier.heightIn(min = 44.dp)
        ) {
            Text(if (allPicked) "ยกเลิกทั้งวัน" else "เลือกทั้งวัน", style = textStyle(14f, 20f, 600))
        }
    }
}

/** ป้ายสถานะเล็กมุมโค้ง 6 */
@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Text(
        text, Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 1.dp),
        style = textStyle(12.5f, 20f, 600), color = fg, maxLines = 1
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoRow(
    v: VideoWithStatus, first: Boolean, last: Boolean, checked: Boolean, enabled: Boolean,
    onToggle: () -> Unit, onDetail: () -> Unit,
) {
    val item = v.item
    val title = clipTitle(item)
    Row(
        Modifier.fillMaxWidth()
            .groupCell(first, last, if (checked) C.soft else C.card, C.line)
            .semantics { stateDescription = if (checked) "เลือกแล้ว" else "ยังไม่เลือก" }
            // แตะแถว = เลือก, กดค้าง = ดูรายละเอียดไฟล์
            .combinedClickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle, onLongClick = onDetail)
            .heightIn(min = 76.dp)
            .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // แตะภาพเพื่อดูรายละเอียด
        Box(
            Modifier.width(96.dp).aspectRatio(16f / 9f).clip(ThumbShape).background(C.nSoft)
                .clickable(onClickLabel = "ดูรายละเอียดไฟล์", onClick = onDetail)
        ) {
            AsyncImage(
                model = item.uri, contentDescription = title, contentScale = ContentScale.Crop,
                // คลิปที่ส่งแล้วแสดงจางลง ให้คลิปที่ยังไม่ส่งเด่นกว่า
                modifier = Modifier.fillMaxSize().alpha(if (v.sent && !checked) 0.5f else 1f)
            )
            Text(
                humanDuration(item.durationMs),
                Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp),
                style = textStyle(11f, 16f, 600), color = Color.White, maxLines = 1
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title, style = textStyle(15f, 22f, 600), color = C.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    timeFmt.format(Date(item.dateAddedSec * 1000)) + " · " + humanBytes(item.sizeBytes),
                    style = textStyle(13f, 20f), color = C.muted, maxLines = 1
                )
                if (v.sent) {
                    Spacer(Modifier.width(8.dp))
                    Pill("ส่งแล้ว", C.okSoft, C.onOk)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        CheckMark(checked)
    }
}

/** วงกลมเลือก: ว่าง = ขอบเทา, เลือก = พื้นแดงกับเครื่องหมายถูก */
@Composable
private fun CheckMark(checked: Boolean) {
    Box(
        Modifier.size(24.dp)
            .then(if (checked) Modifier.background(C.btn, CircleShape) else Modifier.border(2.dp, C.line2, CircleShape)),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Color.White)
    }
}

/** แถบล่างติดขอบจอ โผล่เมื่อมีคลิปที่เลือกหรือกำลังคัดลอก */
@Composable
private fun SelectionBar(s: UiState, vm: MainViewModel, visible: Boolean, onSend: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
    ) {
        val line = C.line
        Column(
            Modifier.fillMaxWidth()
                .background(C.card)
                .drawBehind { drawRect(line, Offset.Zero, Size(size.width, 1.dp.toPx())) }
                // แอปวาดเต็มจอ แถบนี้จะไม่ถูกแถบนำทางทับ
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
        ) {
            val progress = s.progress
            if (s.exporting && progress != null) {
                val (done, total) = progress
                val fraction = if (total == 0) 0f else done.toFloat() / total
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("กำลังคัดลอก $done / $total", style = textStyle(16f, 24f, 600), color = C.ink)
                        Text("ออกจากแอปได้ ดูความคืบหน้าที่แจ้งเตือน", style = textStyle(13f, 20f), color = C.muted)
                    }
                    Text("${(fraction * 100).toInt()}%", style = textStyle(16f, 24f, 700), color = C.pri)
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = C.pri, trackColor = C.line, gapSize = 0.dp, drawStopIndicator = {}
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("เลือก ${s.selected.size} คลิป", style = textStyle(16f, 24f, 600), color = C.ink)
                        Text(humanBytes(s.selectedBytes), style = textStyle(13f, 20f), color = C.muted)
                    }
                    OutlineButton("ล้าง", { vm.clearSelection() }, horizontal = 16)
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton("ส่งไป USB", onSend, horizontal = 20)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSheet(state: SheetState, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val handle = C.line2
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, shape = SheetShape,
        containerColor = C.card, contentColor = C.ink, tonalElevation = 0.dp,
        scrimColor = C.scrim,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(width = 36.dp, height = 4.dp).background(handle, CircleShape))
        },
    ) {
        // แผ่นล่างเป็นหน้าต่างแยก ตั้งไอคอนแถบสถานะเองให้เป็นสีขาวเหมือนหน้าหลัก (หลังแถบหัวสีแดง)
        val view = LocalView.current
        LaunchedEffect(view) {
            ViewCompat.getWindowInsetsController(view)?.isAppearanceLightStatusBars = false
        }
        content()
    }
}

/** คู่หัวข้อ/ค่า แบบในหน้ารายละเอียดของดีไซน์ */
@Composable
private fun KeyValue(k: String, v: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(k, style = textStyle(13f, 20f), color = C.muted)
        Text(v, style = textStyle(15f, 24f), color = C.ink)
    }
}

/** รายละเอียดไฟล์ ใช้ตรวจว่าแอปแยกกลุ่มถูกหรือไม่ และเลือก/mark คลิปนี้ได้ */
@Composable
private fun DetailSheet(
    v: VideoWithStatus, checked: Boolean, enabled: Boolean,
    onToggleSent: () -> Unit, onToggleSelect: () -> Unit,
) {
    val item = v.item
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AsyncImage(
            // แสดงทั้งภาพ คลิปแนวตั้ง (อัดจอมือถือ) จะไม่ถูกตัด
            model = item.uri, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(ControlShape).background(C.nSoft)
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.displayName, style = textStyle(18f, 26f, 700), color = C.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (v.sent) Pill("ส่งแล้ว", C.okSoft, C.onOk) else Pill("ยังไม่ส่ง", C.errSoft, C.onErr)
                Pill(item.source.label + (item.gameName?.let { " · $it" } ?: ""), C.nSoft, C.muted)
            }
        }
        Column(
            Modifier.fillMaxWidth().border(1.dp, C.line, CardShape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row {
                KeyValue("อัดเมื่อ", fullFmt.format(Date(item.dateAddedSec * 1000)), Modifier.weight(1f))
                KeyValue("ความยาว", humanDuration(item.durationMs), Modifier.weight(1f))
            }
            Row {
                KeyValue("ขนาด", humanBytes(item.sizeBytes), Modifier.weight(1f))
                KeyValue("ความละเอียด", "${item.width} × ${item.height}", Modifier.weight(1f))
            }
            KeyValue("โฟลเดอร์", item.relativePath)
            v.sentAtMs?.let { KeyValue("ส่งเมื่อ", fullFmt.format(Date(it))) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton(
                if (v.sent) "ทำเครื่องหมายว่ายังไม่ส่ง" else "ทำเครื่องหมายว่าส่งแล้ว",
                onToggleSent, Modifier.weight(1f), enabled = enabled,
                style = textStyle(14f, 20f, 600), horizontal = 8
            )
            PrimaryButton(
                if (checked) "เอาออกจากที่เลือก" else "เลือกคลิปนี้",
                onToggleSelect, Modifier.weight(1f), enabled = enabled,
                style = textStyle(14f, 20f, 600), horizontal = 8
            )
        }
    }
}

/** แผ่นหลังคัดลอกเสร็จ บอกขั้นตอนต่อไปบน PC */
@Composable
private fun ReadySheet(summary: ExportSummary, onClear: () -> Unit, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(C.okSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, Modifier.size(28.dp), tint = C.ok)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("คัดลอกเสร็จแล้ว", style = textStyle(22f, 32f, 700), color = C.ink)
                Text(
                    "${summary.files} ไฟล์ · ${humanBytes(summary.bytes)}" +
                            if (summary.days > 1) " · ${summary.days} โฟลเดอร์ตามวัน" else "",
                    style = textStyle(14f, 21f), color = C.muted
                )
            }
        }
        if (summary.failed > 0) {
            Text(
                "คัดลอกไม่สำเร็จ ${summary.failed} ไฟล์ เลือกส่งอีกครั้งได้",
                Modifier.fillMaxWidth().background(C.errSoft, ControlShape).padding(horizontal = 14.dp, vertical = 10.dp),
                style = textStyle(14f, 21f, 500), color = C.onErr
            )
        }
        Column {
            Text("ขั้นตอนบน PC", style = textStyle(14f, 20f, 600), color = C.muted)
            Spacer(Modifier.height(12.dp))
            val steps = listOf(
                "เสียบสาย USB แล้วเลือกโหมด File transfer",
                "บน PC เปิด Internal storage/Movies/GameExport",
                "ลากโฟลเดอร์ตามวันของคลิปไป PC แล้วกลับมาลบไฟล์ Export เพื่อคืนพื้นที่",
            )
            steps.forEachIndexed { i, step ->
                Step(i + 1, step, last = i == steps.lastIndex)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton("ลบไฟล์ Export", onClear, Modifier.weight(1f))
            PrimaryButton("เข้าใจแล้ว", onDone, Modifier.weight(1f))
        }
    }
}

/** ขั้นตอนแบบเส้นต่อกัน: วงเลขกับเส้นเชื่อมไปขั้นถัดไป */
@Composable
private fun Step(n: Int, text: String, last: Boolean) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(24.dp).background(C.soft, CircleShape), contentAlignment = Alignment.Center) {
                Text("$n", style = textStyle(13f, 16f, 600), color = C.sel)
            }
            if (!last) Box(Modifier.weight(1f).width(2.dp).background(C.line))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text, Modifier.weight(1f).padding(bottom = if (last) 0.dp else 14.dp),
            style = textStyle(15f, 24f), color = C.ink
        )
    }
}

@Composable
private fun ClearDialog(s: UiState, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = C.card, contentColor = C.ink, shape = RoundedCornerShape(20.dp), shadowElevation = 8.dp,
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth()
        ) {
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("ลบไฟล์ในโฟลเดอร์ Export?", style = textStyle(20f, 28f, 700))
                Text(
                    "จะลบ ${s.exportFolder.first} ไฟล์ (${humanBytes(s.exportFolder.second)}) ใน Movies/GameExport",
                    style = textStyle(15f, 24f)
                )
                Text(
                    "ต้นฉบับในมือถือยังอยู่เหมือนเดิม และสถานะ \"ส่งแล้ว\" ยังถูกจำไว้",
                    style = textStyle(14f, 21f), color = C.muted
                )
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("ยกเลิก", onCancel, Modifier.weight(1f))
                    PrimaryButton("ลบ", onConfirm, Modifier.weight(1f))
                }
            }
        }
    }
}

/** หน้าขอสิทธิ์อ่านวิดีโอ แสดงก่อนเข้าหน้าหลัก */
@Composable
fun PermissionScreen(blocked: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().background(C.bg)) {
        // หัวสีแดงแบบหน้าข้อมูลของฉันในดีไซน์
        Column(
            Modifier.fillMaxWidth().background(C.hero, BarShape).statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp)
        ) {
            Text("โยนคลิป", style = textStyle(20f, 28f, 700), color = Color.White)
            Spacer(Modifier.height(20.dp))
            Text("ให้โยนคลิปเห็นวิดีโอในเครื่อง", style = textStyle(24f, 34f, 700), color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                "ใช้หาคลิปที่ยังไม่ได้ส่งไป PC แล้วคัดลอกไว้ให้ลากผ่านสาย USB",
                style = textStyle(15f, 24f), color = Color.White.copy(alpha = 0.88f)
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (blocked) {
                // ปฏิเสธซ้ำแล้ว ระบบจะไม่ถามอีก ต้องเปิดเองในหน้าตั้งค่า
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().background(C.errSoft, ControlShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Outlined.Lock, null, Modifier.padding(top = 2.dp).size(20.dp), tint = C.onErr)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "สิทธิ์นี้ถูกปิดไว้ เปิดได้ที่หน้าตั้งค่าของแอป ในหัวข้อสิทธิ์ เลือกรูปภาพและวิดีโอ แล้วกดอนุญาตทั้งหมด",
                        style = textStyle(14f, 22f, 500), color = C.onErr
                    )
                }
            }
            SectionHead { Text("แอปจะทำอะไรบ้าง", style = textStyle(14f, 20f, 600), color = C.muted) }
            val rows = listOf(
                Triple(Icons.Outlined.Search, "สแกนหาคลิปในเครื่อง", "แยกคลิปอัดจอเกม กล้อง และอื่นๆ ให้เอง"),
                Triple(Icons.Outlined.ContentCopy, "คัดลอกไปโฟลเดอร์ Export", "Movies/GameExport แยกโฟลเดอร์ตามวันที่อัด"),
                Triple(Icons.Outlined.Lock, "ไม่แตะต้นฉบับ", "ไม่ลบ ไม่แก้ไข และไม่ส่งข้อมูลออกนอกเครื่อง"),
            )
            rows.forEachIndexed { i, (icon, title, sub) ->
                Row(
                    Modifier.fillMaxWidth().groupCell(i == 0, i == rows.lastIndex, C.card, C.line)
                        .heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(icon)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = textStyle(15f, 22f, 600), color = C.ink)
                        Text(sub, style = textStyle(13f, 20f), color = C.muted)
                    }
                }
            }
        }
        val line = C.line
        Box(
            Modifier.fillMaxWidth().drawBehind { drawRect(line, Offset.Zero, Size(size.width, 1.dp.toPx())) }
                .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
        ) {
            PrimaryButton(
                if (blocked) "เปิดการตั้งค่า" else "อนุญาตให้เข้าถึงวิดีโอ",
                if (blocked) onOpenSettings else onAllow, Modifier.fillMaxWidth(),
                height = 52, style = textStyle(16f, 24f, 600)
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    height: Int = 48, horizontal: Int = 16, style: TextStyle = textStyle(15f, 22f, 600),
) = FlatButton(text, onClick, modifier, enabled, height, horizontal, style, C.btn, Color.White, null)

/** ปุ่มรอง: พื้นการ์ด ขอบเทา */
@Composable
private fun OutlineButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    height: Int = 48, horizontal: Int = 16, style: TextStyle = textStyle(15f, 22f, 600),
) = FlatButton(text, onClick, modifier, enabled, height, horizontal, style, C.card, C.ink, C.line2)

/** ปุ่มเรียบ ไม่มีเงา ความสูงเริ่มที่ค่าที่กำหนดและยืดได้ถ้าข้อความตกบรรทัด */
@Composable
private fun FlatButton(
    text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean,
    height: Int, horizontal: Int, style: TextStyle, container: Color, content: Color, border: Color?,
) {
    Button(
        onClick = onClick, enabled = enabled, shape = ControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = C.nSoft, disabledContentColor = C.muted
        ),
        border = border?.let { androidx.compose.foundation.BorderStroke(1.dp, if (enabled) it else C.line) },
        elevation = null,
        contentPadding = PaddingValues(horizontal = horizontal.dp, vertical = 4.dp),
        modifier = modifier.heightIn(min = height.dp)
    ) {
        Text(text, style = style, textAlign = TextAlign.Center)
    }
}
