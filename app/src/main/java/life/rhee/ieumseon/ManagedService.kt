package life.rhee.ieumseon

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

/** Runs only for an already-provisioned Device Owner, started from the visible activity. */
class ManagedService : Service() {
    private lateinit var monitor: NetworkMonitor
    private var message: String? = null
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        monitor = NetworkMonitor(applicationContext, publish = ::showStatus)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!monitor.deviceOwner || !notificationsAllowed()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == STOP) {
            stopping = true
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != START || active) return START_NOT_STICKY
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "관리형 연결 자동 전환", NotificationManager.IMPORTANCE_LOW))
        try {
            val notice = notification("약한 Wi‑Fi를 감시하고 있습니다.")
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } else {
                startForeground(ID, notice)
            }
        } catch (_: RuntimeException) {
            stopSelf()
            return START_NOT_STICKY
        }
        active = true
        monitor.start()
        monitor.setAutoMode(AutoMode.MANAGED)
        return START_NOT_STICKY
    }

    private fun notificationsAllowed(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        return Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun showStatus(view: MonitorView) {
        if (stopping) return
        if (!view.deviceOwner || !notificationsAllowed()) {
            stopSelf()
            return
        }
        state = view
        val next = when (view.radioSwitch) {
            RadioSwitch.MOBILE_DEFAULT -> "모바일 기본 경로 확인됨 · 중지하면 Wi‑Fi 복구를 요청합니다."
            RadioSwitch.REQUESTED -> "Android가 모바일 기본 경로로 바뀌는지 확인 중입니다."
            RadioSwitch.RESTORING -> "전환을 확인하지 못해 Wi‑Fi 복구를 요청했습니다."
            RadioSwitch.FAILED -> "전환 또는 복구를 확인하지 못했습니다."
            else -> if (view.restoreNeeded) "이전 Wi‑Fi 복구가 필요합니다." else "약한 Wi‑Fi를 감시하고 있습니다."
        }
        if (message == next) return
        message = next
        getSystemService(NotificationManager::class.java).notify(ID, notification(next))
    }

    private fun notification(text: String): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(this, 1, Intent(this, ManagedService::class.java).setAction(STOP), flags)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("이음선 · 관리형 자동 전환")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "감시 중지·Wi‑Fi 복구", stop).build())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopping = true
        if (active) monitor.restoreWifi()
        monitor.stop()
        state = null
        active = false
        super.onDestroy()
    }

    companion object {
        internal const val START = "life.rhee.ieumseon.START_MANAGED"
        internal const val STOP = "life.rhee.ieumseon.STOP_MANAGED"
        private const val CHANNEL = "managed-handover"
        private const val ID = 42
        internal var active = false
            private set
        internal var state: MonitorView? = null
            private set
    }
}
