package com.gtlx.statusbardrift.config

import android.content.Context
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import de.robv.android.xposed.XposedBridge
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * 漂移配置 —— 基于外部存储文件，跨进程（App ↔ SystemUI）共享
 *
 * App 端写入到 /sdcard/Android/data/com.gtlx.statusbardrift/files/status_bar_drift.conf
 * SystemUI 端读取同一文件，并用 FileObserver 监听变化实现热更新。
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

    private var watcher: ConfigWatcher? = null
    private var listeners = mutableListOf<() -> Unit>()

    /** 从 UI 端调用，保存配置到外部存储 */
    fun saveFromUi(context: Context, driftPx: Int, intervalSec: Int) {
        Thread {
            try {
                // 优先写外部存储 App 私有目录（SystemUI 可以读）
                val extDir = context.getExternalFilesDir(null)
                val dir = extDir ?: context.filesDir
                val file = File(dir, CONFIG_FILE)
                val props = Properties()
                props.setProperty(KEY_DRIFT_PX, driftPx.toString())
                props.setProperty(KEY_INTERVAL_SEC, intervalSec.toString())
                FileOutputStream(file).use { props.store(it, "StatusBarDrift config") }
                file.setReadable(true, false) // 全局可读，SystemUI 才能读

                // 复制一份到 /data/local/tmp/ 当兜底（SystemUI 一定能读到）
                try {
                    val proc = Runtime.getRuntime().exec(arrayOf(
                        "su", "-c",
                        "cp ${file.absolutePath} /data/local/tmp/$CONFIG_FILE && chmod 644 /data/local/tmp/$CONFIG_FILE"
                    ))
                    val code = proc.waitFor()
                    log("sync config to /data/local/tmp exitCode=$code")
                } catch (t: Throwable) {
                    logE("sync config to tmp FAILED", t)
                }
                log("config saved: drift=${driftPx}px, interval=${intervalSec}s, path=${file.absolutePath}")
            } catch (t: Throwable) {
                logE("save config FAILED", t)
            }
        }.start()
    }

    /** 从 Hook 端调用，加载配置并启动监听 */
    fun loadAndWatch(context: Context, onChanged: () -> Unit) {
        load(context)
        listeners.add(onChanged)
        if (watcher == null) {
            watcher = ConfigWatcher(getConfigFile(context)?.parent ?: return) {
                load(context)
                listeners.forEach { it() }
            }
            watcher?.startWatching()
            log("config watcher started")
        }
    }

    /** 仅加载一次 */
    fun load(context: Context) {
        try {
            val file = getConfigFile(context)
            if (file == null || !file.exists()) {
                log("no config file, using defaults")
                return
            }
            val props = Properties()
            FileInputStream(file).use { props.load(it) }
            driftPx = props.getProperty(KEY_DRIFT_PX, "3").toIntOrNull() ?: 3
            val intervalSec = props.getProperty(KEY_INTERVAL_SEC, "30").toIntOrNull() ?: 30
            intervalMs = intervalSec * 1000L
            log("config loaded: drift=${driftPx}px, interval=${intervalSec}s")
        } catch (t: Throwable) {
            logE("load config FAILED", t)
        }
    }

    private fun getConfigFile(context: Context): File? {
        // 1. 外部存储 App 私有目录
        try {
            val extDir = File(
                Environment.getExternalStorageDirectory(),
                "Android/data/com.gtlx.statusbardrift/files/$CONFIG_FILE"
            )
            if (extDir.exists()) return extDir
        } catch (_: Throwable) {}
        // 2. /data/local/tmp/ 兜底（App 有 root 时会复制一份到这）
        try {
            val tmpFile = File("/data/local/tmp/$CONFIG_FILE")
            if (tmpFile.exists()) return tmpFile
        } catch (_: Throwable) {}
        // 3. App 内部私有目录（只有 SystemUI 有 root 时才能读，一般读不到）
        try {
            val appDir = File("/data/data/com.gtlx.statusbardrift/files/$CONFIG_FILE")
            if (appDir.exists()) return appDir
        } catch (_: Throwable) {}
        // 4. 从 context 拿
        try {
            val f = File(context.getExternalFilesDir(null), CONFIG_FILE)
            if (f.exists()) return f
        } catch (_: Throwable) {}
        return null
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        try { XposedBridge.log("[StatusBarDrift] $msg") } catch (_: Throwable) {}
    }

    private fun logE(msg: String, t: Throwable) {
        Log.e(TAG, msg, t)
        try { XposedBridge.log("[StatusBarDrift] $msg: $t") } catch (_: Throwable) {}
    }

    /** 文件监听器 */
    private class ConfigWatcher(path: String, private val onChange: () -> Unit) :
        FileObserver(path, MODIFY or CLOSE_WRITE or CREATE) {
        override fun onEvent(event: Int, path: String?) {
            if (path?.contains("status_bar_drift") == true) {
                log("config file changed")
                onChange()
            }
        }
    }
}
