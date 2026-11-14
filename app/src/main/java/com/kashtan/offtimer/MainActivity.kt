package com.kashtan.offtimer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private val presets = listOf(5, 10, 15, 30, 60)

    private var minutes = 10
    private var airplane = true
    private var wifi = true
    private var lock = true
    private var power = false

    private lateinit var bigTime: TextView
    private lateinit var caption: TextView
    private lateinit var status: TextView
    private lateinit var message: TextView
    private lateinit var startButton: TextView
    private lateinit var optAirplane: TextView
    private lateinit var optWifi: TextView
    private lateinit var optLock: TextView
    private lateinit var optPower: TextView
    private lateinit var adjustBox: View
    private lateinit var presetRow: LinearLayout
    private val presetViews = mutableListOf<TextView>()

    private val ui = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            render()
            ui.postDelayed(this, 250)
        }
    }

    private val onBinder = Shizuku.OnBinderReceivedListener { runOnUiThread { render() } }
    private val onDead = Shizuku.OnBinderDeadListener { runOnUiThread { render() } }
    private val onPerm = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bigTime = findViewById(R.id.bigTime)
        caption = findViewById(R.id.caption)
        status = findViewById(R.id.shizukuStatus)
        message = findViewById(R.id.message)
        startButton = findViewById(R.id.startButton)
        optAirplane = findViewById(R.id.optAirplane)
        optWifi = findViewById(R.id.optWifi)
        optLock = findViewById(R.id.optLock)
        optPower = findViewById(R.id.optPower)
        adjustBox = findViewById(R.id.adjustBox)
        presetRow = findViewById(R.id.presetRow)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        minutes = prefs.getInt("minutes", 10)
        airplane = prefs.getBoolean("airplane", true)
        wifi = prefs.getBoolean("wifi", true)
        lock = prefs.getBoolean("lock", true)
        power = prefs.getBoolean("power", false)

        buildPresets()

        findViewById<View>(R.id.plus).setOnClickListener { setMinutes(minutes + 1) }
        findViewById<View>(R.id.minus).setOnClickListener { setMinutes(minutes - 1) }
        optAirplane.setOnClickListener { airplane = !airplane; save(); render() }
        optWifi.setOnClickListener { wifi = !wifi; save(); render() }
        optLock.setOnClickListener { lock = !lock; save(); render() }
        optPower.setOnClickListener { power = !power; save(); render() }
        status.setOnClickListener {
            if (ShizukuShell.state() == ShizukuShell.State.NO_PERMISSION) ShizukuShell.requestPermission()
        }
        startButton.setOnClickListener { onStartPressed() }

        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPerm)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    override fun onResume() {
        super.onResume()
        // ウィジェットで分数を変えた場合に合わせる
        minutes = getSharedPreferences("settings", MODE_PRIVATE).getInt("minutes", minutes)
        ui.post(ticker)
    }

    override fun onPause() {
        ui.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onDead)
        Shizuku.removeRequestPermissionResultListener(onPerm)
        super.onDestroy()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun buildPresets() {
        presets.forEachIndexed { i, m ->
            val tv = TextView(this).apply {
                text = m.toString()
                gravity = Gravity.CENTER
                textSize = 17f
                typeface = android.graphics.Typeface.MONOSPACE
                setOnClickListener { setMinutes(m) }
            }
            val lp = LinearLayout.LayoutParams(0, dp(48), 1f)
            if (i > 0) lp.marginStart = -dp(2) // 枠線を重ねて1本に見せる
            presetRow.addView(tv, lp)
            presetViews += tv
        }
        val unit = TextView(this).apply {
            text = "分"
            textSize = 14f
            setTextColor(getColor(R.color.muted))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        presetRow.addView(unit, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)))
    }

    private fun setMinutes(m: Int) {
        if (TimerService.endAtMillis != 0L) return
        minutes = m.coerceIn(1, 180)
        save()
        render()
    }

    private fun save() {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putInt("minutes", minutes)
            .putBoolean("airplane", airplane)
            .putBoolean("wifi", wifi)
            .putBoolean("lock", lock)
            .putBoolean("power", power)
            .apply()
        TimerWidget.updateAll(this)
    }

    private fun onStartPressed() {
        if (TimerService.endAtMillis != 0L) {
            startService(Intent(this, TimerService::class.java).setAction(TimerService.ACTION_CANCEL))
            TimerService.endAtMillis = 0L
            render()
            return
        }
        if (!airplane && !wifi && !lock && !power) {
            showMessage("やることが1つも選ばれていません")
            return
        }
        when (ShizukuShell.state()) {
            ShizukuShell.State.NOT_RUNNING -> {
                showMessage("Shizukuが動いていません。Shizukuアプリで「開始」してから戻ってきてください")
                return
            }
            ShizukuShell.State.NO_PERMISSION -> {
                showMessage("Shizukuの許可が必要です。出てきた画面で「許可」を押してください")
                ShizukuShell.requestPermission()
                return
            }
            ShizukuShell.State.READY -> Unit
        }
        showMessage(null)
        val i = Intent(this, TimerService::class.java)
            .setAction(TimerService.ACTION_START)
            .putExtra(TimerService.EXTRA_MINUTES, minutes)
            .putExtra(TimerService.EXTRA_AIRPLANE, airplane)
            .putExtra(TimerService.EXTRA_WIFI, wifi)
            .putExtra(TimerService.EXTRA_LOCK, lock)
            .putExtra(TimerService.EXTRA_POWER, power)
        startForegroundService(i)
        TimerService.endAtMillis = System.currentTimeMillis() + minutes * 60_000L
        render()
    }

    private fun showMessage(text: String?) {
        if (text == null) {
            message.visibility = View.GONE
        } else {
            message.text = text
            message.visibility = View.VISIBLE
        }
    }

    private fun render() {
        val endAt = TimerService.endAtMillis
        val running = endAt != 0L

        if (running) {
            val leftSec = ((endAt - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000
            bigTime.text = "%d:%02d".format(leftSec / 60, leftSec % 60)
            caption.text = "消灯まで"
            startButton.text = "やめる"
            startButton.setBackgroundResource(R.drawable.fill_ink)
        } else {
            bigTime.text = "%d:00".format(minutes)
            caption.text = "この時間のあとに切る"
            startButton.text = "スタート"
            startButton.setBackgroundResource(R.drawable.fill_accent)
        }

        val lockedAlpha = if (running) 0.35f else 1f
        adjustBox.alpha = lockedAlpha
        presetRow.alpha = lockedAlpha
        listOf(optAirplane, optWifi, optLock, optPower).forEach { it.isEnabled = !running; it.alpha = lockedAlpha }

        presets.forEachIndexed { i, m ->
            val v = presetViews[i]
            val on = m == minutes
            v.setBackgroundResource(if (on) R.drawable.fill_ink else R.drawable.box)
            v.setTextColor(getColor(if (on) R.color.paper else R.color.ink))
        }

        setCheck(optAirplane, airplane)
        setCheck(optWifi, wifi)
        setCheck(optLock, lock)
        setCheck(optPower, power)

        when (ShizukuShell.state()) {
            ShizukuShell.State.READY -> {
                status.text = "Shizuku OK"
                status.setTextColor(getColor(R.color.ok))
                status.setBackgroundResource(0)
            }
            ShizukuShell.State.NO_PERMISSION -> {
                status.text = "Shizuku 要許可 ›"
                status.setTextColor(getColor(R.color.paper))
                status.setBackgroundResource(R.drawable.fill_accent)
            }
            ShizukuShell.State.NOT_RUNNING -> {
                status.text = "Shizuku 停止中"
                status.setTextColor(getColor(R.color.accent))
                status.setBackgroundResource(0)
            }
        }
    }

    private fun setCheck(v: TextView, on: Boolean) {
        v.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (on) R.drawable.check_on else R.drawable.check_off, 0, 0, 0
        )
    }
}
