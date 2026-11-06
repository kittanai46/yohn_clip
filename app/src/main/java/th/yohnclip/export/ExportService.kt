package th.yohnclip.export

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import th.yohnclip.data.AppDatabase
import th.yohnclip.data.ExportSummary
import th.yohnclip.data.Exporter
import th.yohnclip.data.SentFile
import th.yohnclip.data.VideoItem

/**
 * คัดลอกคลิปเป็น foreground service พร้อมแจ้งเตือนความคืบหน้า
 * ระบบจึงไม่ปิดแอปกลางทางเมื่อผู้ใช้ออกจากแอปหรือจอดับ
 */
class ExportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val items = ExportRunner.pending
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "การคัดลอกคลิป", NotificationManager.IMPORTANCE_LOW)
        )
        // ต้องเรียกภายในไม่กี่วินาทีหลังเริ่ม service ไม่งั้นระบบปิดแอป
        ServiceCompat.startForeground(
            this, NOTIF_PROGRESS, progressNotification(0, items.size),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (job?.isActive == true) return START_NOT_STICKY
        if (items.isEmpty()) {
            finish()
            return START_NOT_STICKY
        }
        job = scope.launch { copy(items) }
        return START_NOT_STICKY
    }

    private suspend fun copy(items: List<VideoItem>) {
        // กัน CPU หลับระหว่างคัดลอกตอนจอดับ
        val wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yohnclip:export")
        wake.acquire(3 * 60 * 60 * 1000L)
        try {
            val exporter = Exporter(this)
            val result = exporter.export(items) { done, total ->
                ExportRunner.state.value = ExportRunner.State.Running(done, total)
                notify(NOTIF_PROGRESS, progressNotification(done, total))
            }
            val now = System.currentTimeMillis()
            AppDatabase.get(this).sentFileDao().upsertAll(result.ok.map { (v, name) ->
                SentFile(v.fingerprint, v.id, v.displayName, v.sizeBytes, now, name)
            })
            val summary = result.ok.takeIf { it.isNotEmpty() }?.let { ok ->
                ExportSummary(
                    files = ok.size,
                    bytes = ok.sumOf { (v, _) -> v.sizeBytes },
                    days = ok.map { (v, _) -> exporter.exportDay(v) }.distinct().size,
                    failed = result.failed.size,
                )
            }
            ExportRunner.state.value = ExportRunner.State.Done(summary, result.failed.size)
            if (!ExportRunner.uiVisible) notify(NOTIF_DONE, doneNotification(summary, result.failed.size))
        } catch (e: CancellationException) {
            ExportRunner.state.value = ExportRunner.State.Done(null, items.size)
            throw e
        } catch (e: Exception) {
            ExportRunner.state.value = ExportRunner.State.Done(null, items.size)
        } finally {
            if (wake.isHeld) wake.release()
            ExportRunner.pending = emptyList()
            finish()
        }
    }

    /** Android 15 จำกัดเวลา service แบบ dataSync ไว้ 6 ชั่วโมงต่อวัน หมดเวลาแล้วต้องหยุดทันที */
    override fun onTimeout(startId: Int, fgsType: Int) {
        job?.cancel() ?: finish()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Android 13+ ถ้าผู้ใช้ไม่ให้สิทธิ์แจ้งเตือน ก็แค่ไม่แสดง การคัดลอกยังทำต่อได้ */
    private fun notify(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        notifications.notify(id, notification)
    }

    private fun openApp(): PendingIntent {
        // intent เดียวกับไอคอนแอป จึงพากลับไปหน้าเดิม ไม่เปิดหน้าซ้อน
        val intent = packageManager.getLaunchIntentForPackage(packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun progressNotification(done: Int, total: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("กำลังคัดลอกคลิป $done / $total")
            .setContentText("ไปที่ Movies/GameExport")
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun doneNotification(summary: ExportSummary?, failed: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(if (summary != null) "คัดลอกเสร็จ ${summary.files} ไฟล์" else "คัดลอกไม่สำเร็จ")
            .setContentText(
                when {
                    summary == null -> "แตะเพื่อเปิดแอปแล้วลองอีกครั้ง"
                    failed > 0 -> "พร้อมลากไป PC · ไม่สำเร็จ $failed ไฟล์"
                    else -> "พร้อมลากไป PC"
                }
            )
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()

    private companion object {
        const val CHANNEL = "export"
        const val NOTIF_PROGRESS = 1
        const val NOTIF_DONE = 2
    }
}
