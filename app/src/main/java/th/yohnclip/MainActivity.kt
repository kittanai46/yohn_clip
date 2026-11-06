package th.yohnclip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import th.yohnclip.export.ExportRunner
import th.yohnclip.ui.MainScreen
import th.yohnclip.ui.MainViewModel
import th.yohnclip.ui.PermissionScreen
import th.yohnclip.ui.YohnClipTheme

/** สิทธิ์อ่านวิดีโอ: ทั้งเครื่อง / เฉพาะที่ผู้ใช้เลือก (Android 14+) / ไม่มี */
enum class VideoAccess { FULL, PARTIAL, NONE }

const val PREFS = "yohnclip"
private const val KEY_ASKED_VIDEO = "asked_video"

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private val videoPermission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    // Android 14+ ผู้ใช้เลือก "อนุญาตบางส่วน" ได้ ต้องขอทั้งสองสิทธิ์พร้อมกัน
    private val requested: Array<String> =
        if (Build.VERSION.SDK_INT >= 34)
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        else arrayOf(videoPermission)

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun access(): VideoAccess = when {
        has(videoPermission) -> VideoAccess.FULL
        Build.VERSION.SDK_INT >= 34 && has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> VideoAccess.PARTIAL
        else -> VideoAccess.NONE
    }

    /** ปฏิเสธซ้ำจนระบบไม่ถามอีกแล้ว ต้องไปเปิดในหน้าตั้งค่าเอง */
    private fun blocked() =
        prefs.getBoolean(KEY_ASKED_VIDEO, false) && !shouldShowRequestPermissionRationale(videoPermission)

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // แถบหัวเป็นสีแดงทุกหน้า ไอคอนแถบสถานะจึงเป็นสีขาวเสมอ ส่วนแถบนำทางตามโหมดสว่าง/มืด
        val clear = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(clear),
            navigationBarStyle = SystemBarStyle.auto(clear, clear),
        )
        super.onCreate(savedInstanceState)
        setContent {
            YohnClipTheme {
                var access by remember { mutableStateOf(access()) }
                var blocked by remember { mutableStateOf(blocked()) }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    prefs.edit { putBoolean(KEY_ASKED_VIDEO, true) }
                    access = access()
                    blocked = blocked()
                }
                val request = { launcher.launch(requested) }

                LaunchedEffect(Unit) {
                    if (access == VideoAccess.NONE && !prefs.getBoolean(KEY_ASKED_VIDEO, false)) request()
                }
                // กลับเข้าแอปทุกครั้ง (รวมถึงหลังปิดหน้าขอสิทธิ์หรือหน้าตั้งค่า): เช็กสิทธิ์ใหม่แล้วสแกนคลิปใหม่
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    access = access()
                    blocked = blocked()
                    if (access != VideoAccess.NONE) vm.refresh()
                }

                if (access == VideoAccess.NONE) {
                    PermissionScreen(blocked = blocked, onAllow = request, onOpenSettings = ::openAppSettings)
                } else {
                    MainScreen(vm, partialAccess = access == VideoAccess.PARTIAL, onRequestFullAccess = request)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ExportRunner.uiVisible = true
    }

    override fun onStop() {
        ExportRunner.uiVisible = false
        super.onStop()
    }
}
