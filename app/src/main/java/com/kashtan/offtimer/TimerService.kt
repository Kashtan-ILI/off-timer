package com.kashtan.offtimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/** カウントダウン中ずっと動き続け、時間が来たらコマンドを実行する */
class TimerService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_CANCEL = "cancel"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_AIRPLANE = "airplane"
        const val EXTRA_WIFI = "wifi"
        const val EXTRA_LOCK = "lock"

        private const val CH_COUNT = "countdown"
        private const val CH_RESULT = "result"
        private const val NID_COUNT = 1
        private const val NID_RESULT = 2

        /** 画面表示用。0なら停止中 */
        @Volatile var endAtMillis: Long = 0L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var command = ""

    private val fire = Runnable {
        val cmd = command
        Thread {
            val ok = ShizukuShell.state() == ShizukuShell.State.READY && ShizukuShell.run(cmd)
            handler.post {
                if (!ok) notifyFailure()
                stopAll()
            }
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            else -> {
                // ウィジェットから呼ばれた場合に備えて、いったん前面化してから止める
                if (endAtMillis == 0L) {
                    ensureChannels()
                    goForeground(countdownNotification())
                }
                stopAll()
            }
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        handler.removeCallbacks(fire)
        // ウィジェットからは中身なしで呼ばれるので、アプリで保存した設定を使う
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val minutes = intent.getIntExtra(EXTRA_MINUTES, prefs.getInt("minutes", 10))
        command = ShizukuShell.buildCommand(
            intent.getBooleanExtra(EXTRA_AIRPLANE, prefs.getBoolean("airplane", true)),
            intent.getBooleanExtra(EXTRA_WIFI, prefs.getBoolean("wifi", true)),
            intent.getBooleanExtra(EXTRA_LOCK, prefs.getBoolean("lock", true))
        )
        val durationMs = minutes * 60_000L
        endAtMillis = System.currentTimeMillis() + durationMs

        ensureChannels()
        goForeground(countdownNotification())
        TimerWidget.updateAll(this)
        if (command.isEmpty()) {
            stopAll()
            return
        }

        // 画面が消えていても確実に時間どおり動かすため
        wakeLock?.let { if (it.isHeld) it.release() }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OffTimer:countdown").apply {
            acquire(durationMs + 60_000L)
        }

        handler.postDelayed(fire, durationMs)
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NID_COUNT, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NID_COUNT, n)
        }
    }

    private fun stopAll() {
        handler.removeCallbacks(fire)
        endAtMillis = 0L
        TimerWidget.updateAll(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(fire)
        endAtMillis = 0L
        TimerWidget.updateAll(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun ensureChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_COUNT, "カウントダウン", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "実行できなかったとき", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun countdownNotification(): Notification {
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, TimerService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CH_COUNT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("消灯まで")
            .setWhen(endAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .addAction(Notification.Action.Builder(null, "やめる", cancel).build())
            .build()
    }

    private fun notifyFailure() {
        val n = Notification.Builder(this, CH_RESULT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("消灯できませんでした")
            .setContentText("Shizukuが止まっているか、許可がありません")
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NID_RESULT, n)
    }
}
