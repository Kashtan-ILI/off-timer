package com.kashtan.offtimer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

/** ホーム画面のウィジェット。設定はアプリ側で決めた分数・項目を使う */
class TimerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ADJUST) {
            // カウントダウン中は変えない
            if (TimerService.endAtMillis == 0L) {
                val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val now = prefs.getInt("minutes", 10)
                val next = step(now, intent.getIntExtra(EXTRA_DIR, 1))
                prefs.edit().putInt("minutes", next).apply()
            }
            updateAll(context)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_ADJUST = "com.kashtan.offtimer.ADJUST"
        private const val EXTRA_DIR = "dir"

        /** ウィジェットの－／＋で選べる分数 */
        private val STEPS = listOf(1, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180)

        private fun step(now: Int, dir: Int): Int {
            return if (dir > 0) STEPS.firstOrNull { it > now } ?: STEPS.last()
            else STEPS.lastOrNull { it < now } ?: STEPS.first()
        }

        private fun adjustIntent(context: Context, dir: Int, code: Int): PendingIntent {
            val i = Intent(context, TimerWidget::class.java)
                .setAction(ACTION_ADJUST)
                .putExtra(EXTRA_DIR, dir)
            return PendingIntent.getBroadcast(
                context, code, i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget)
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val minutes = prefs.getInt("minutes", 10)
            val endAt = TimerService.endAtMillis
            val running = endAt != 0L

            if (running) {
                val base = SystemClock.elapsedRealtime() + (endAt - System.currentTimeMillis())
                v.setViewVisibility(R.id.w_minutes, View.GONE)
                v.setViewVisibility(R.id.w_chrono, View.VISIBLE)
                v.setChronometer(R.id.w_chrono, base, null, true)
                v.setChronometerCountDown(R.id.w_chrono, true)
                v.setTextViewText(R.id.w_label, "消灯まで")
                v.setTextViewText(R.id.w_button, "やめる")
                v.setInt(R.id.w_button, "setBackgroundResource", R.drawable.fill_ink)
            } else {
                v.setViewVisibility(R.id.w_chrono, View.GONE)
                v.setViewVisibility(R.id.w_minutes, View.VISIBLE)
                v.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime(), null, false)
                v.setTextViewText(R.id.w_minutes, "${minutes}分")
                v.setTextViewText(R.id.w_label, "消灯タイマー")
                v.setTextViewText(R.id.w_button, "スタート")
                v.setInt(R.id.w_button, "setBackgroundResource", R.drawable.fill_accent)
            }

            // ボタン：スタート／やめる
            val action = if (running) TimerService.ACTION_CANCEL else TimerService.ACTION_START
            val svc = Intent(context, TimerService::class.java).setAction(action)
            val pi = PendingIntent.getForegroundService(
                context, if (running) 21 else 20, svc,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.w_button, pi)

            // －／＋（カウントダウン中は薄く表示して無効）
            v.setOnClickPendingIntent(R.id.w_minus, adjustIntent(context, -1, 30))
            v.setOnClickPendingIntent(R.id.w_plus, adjustIntent(context, 1, 31))
            val c = context.getColor(if (running) R.color.muted else R.color.ink)
            v.setTextColor(R.id.w_minus, c)
            v.setTextColor(R.id.w_plus, c)

            // 左側を押したらアプリを開いて分数を変えられる
            val open = PendingIntent.getActivity(
                context, 22, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.w_info, open)
            return v
        }
    }
}
