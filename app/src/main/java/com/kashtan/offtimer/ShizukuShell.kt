package com.kashtan.offtimer

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Shizuku経由でシェルコマンドを実行する */
object ShizukuShell {

    enum class State { NOT_RUNNING, NO_PERMISSION, READY }

    fun state(): State {
        return try {
            if (!Shizuku.pingBinder()) State.NOT_RUNNING
            else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) State.READY
            else State.NO_PERMISSION
        } catch (e: Throwable) {
            State.NOT_RUNNING
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder() && !Shizuku.shouldShowRequestPermissionRationale()) {
                Shizuku.requestPermission(1)
            }
        } catch (_: Throwable) {
        }
    }

    /** 成功したら true */
    fun run(command: String): Boolean {
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            p.waitFor() == 0
        } catch (e: Throwable) {
            false
        }
    }

    fun buildCommand(airplane: Boolean, wifi: Boolean, lock: Boolean, power: Boolean): String {
        val parts = mutableListOf<String>()
        if (airplane) parts += "cmd connectivity airplane-mode enable"
        if (wifi) parts += "svc wifi disable"
        if (lock) parts += "input keyevent 223" // KEYCODE_SLEEP = 画面を消してロック
        if (power) parts += "svc power shutdown" // 最後に電源オフ
        return parts.joinToString("; ")
    }
}
