package com.gtlx.statusbardrift.config

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import de.robv.android.xposed.XposedBridge
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * 漂移配置 —— ContentProvider 跨进程共享（无 root 标准方案）
 *
 * - App 端：通过 saveFromUi 写本地文件 + 更新 Provider
 * - 目标进程（SystemUI）：通过 ContentResolver 读配置，ContentObserver 监听热更新
 * - 兜底：本地文件（App 私有目录，644 权限），Provider 读这个文件
 */
object DriftConfig {
    private const val TAG = "StatusBarDrift"
    private const val CONFIG_FILE = "status_bar_drift.conf"
    private const val KEY_DRIFT_PX = "drift_px"
    private const val KEY_INTERVAL_SEC = "interval_sec"

    var driftPx: Int = 3
        private set
    var intervalMs: Long = 30_000L
        private set

    private var observer: ConfigContentObserver? = null
    private var listeners = mutableListOf<() -> Unit>()

    // ===== 目标进程（SystemUI）端调用 =====

    /** 从目标进程调用：通过 ContentProvider 加载配置 + 注册监听 */
    fun loadAndWatchFromProvider(context: android.content.Context, onChanged: () -> Unit) {
        loadFromProvider(context)
        listeners.add(onChanged)
        if (observer == null) {
            observer = ConfigContentObserver(Handler(Looper.getMainLooper())) {
                loadFromProvider(context)
                listeners.forEach { it() }
            }
            context.contentResolver.registerContentObserver(
                ConfigProvider.CONTENT_URI, true, observer!!
            )
            log("config ContentObserver registered")
        }
    }

    private fun loadFromProvider(context: android.content.Context) {
        try {
            val result = context.contentResolver.call(
                ConfigProvider.CONTENT_URI,
                ConfigProvider.METHOD_GET_CONFIG,
                null, null
            )
            if (result != null) {
                driftPx = result.getInt(ConfigProvider.KEY_DRIFT_PX, 3)
                val intervalSec = result.getInt(ConfigProvider.KEY_INTERVAL_SEC, 30)
                intervalMs = intervalSec * 1000L
                log("config loaded from provider: drift=${driftPx}px, interval=${intervalSec}s")
                return
            }
        } catch (t: Throwable) {
            log("loadFromProvider failed: ${t.message}")
        }
        // Provider 起不来时（App 可能被杀了），兜底用默认值
        log("fallback to defaults")
    }

    // ===== App 端调用 =====

    /** 从 UI 端调用，保存配置（写本地文件 + Provider 会自动读） */
    fun saveFromUi(context: android.content.Context, driftPx: Int, intervalSec: Int) {
        // 1. 写本地文件
        Thread {
            try {
                val dir = context.filesDir
                val file = File(dir, CONFIG_FILE)
                val props = Properties()
                props.setProperty(KEY_DRIFT_PX, driftPx.toString())
                props.setProperty(KEY_INTERVAL_SEC, intervalSec.toString())
                FileOutputStream(file).use { props.store(it, "StatusBarDrift config") }
                file.setReadable(true, false)
                log("config saved to file: drift=${driftPx}px, interval=${intervalSec}s")
            } catch (t: Throwable) {
                logE("save config file FAILED", t)
            }
        }.start()

        // 2. 更新内存值
        this.driftPx = driftPx
        this.intervalMs = intervalSec * 1000L

        // 3. 通知 ContentObserver（Provider 进程内更新）
        try {
            context.contentResolver.notifyChange(ConfigProvider.CONTENT_URI, null)
        } catch (_: Throwable) {}
    }

    /** Provider 调用：从文件加载配置到内存 */
    fun loadFromFile(context: android.content.Context) {
        try {
            val file = File(context.filesDir, CONFIG_FILE)
            if (!file.exists()) return
            val props = Properties()
            FileInputStream(file).use { props.load(it) }
            driftPx = props.getProperty(KEY_DRIFT_PX, "3").toIntOrNull() ?: 3
            val intervalSec = props.getProperty(KEY_INTERVAL_SEC, "30").toIntOrNull() ?: 30
            intervalMs = intervalSec * 1000L
        } catch (_: Throwable) {}
    }

    /** Provider 调用：保存配置 */
    fun saveFromProvider(context: android.content.Context, driftPx: Int, intervalSec: Int) {
        saveFromUi(context, driftPx, intervalSec)
    }

    // ===== 内部 =====

    private fun log(msg: String) {
        Log.i(TAG, msg)
        try { XposedBridge.log("[$TAG] $msg") } catch (_: Throwable) {}
    }

    private fun logE(msg: String, t: Throwable) {
        Log.e(TAG, msg, t)
        try { XposedBridge.log("[$TAG] $msg: $t") } catch (_: Throwable) {}
    }

    /** ContentObserver —— 监听配置变化 */
    private class ConfigContentObserver(
        handler: Handler,
        private val onChange: () -> Unit
    ) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            Log.d(TAG, "config ContentObserver onChange")
            onChange()
        }
    }
}
