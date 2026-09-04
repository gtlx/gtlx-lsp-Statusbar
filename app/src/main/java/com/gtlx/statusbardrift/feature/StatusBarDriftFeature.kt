package com.gtlx.statusbardrift.feature

import android.os.Handler
import android.os.Looper
import android.view.View
import android.util.Log
import de.robv.android.xposed.XposedBridge
import com.gtlx.statusbardrift.config.DriftConfig

/**
 * 状态栏水平漂移功能
 */
object StatusBarDriftFeature {
    private const val TAG = "StatusBarDrift"

    private val handler = Handler(Looper.getMainLooper())
    private var statusBarView: View? = null
    private var direction = 1
    private var running = false

    private val driftRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "driftRunnable executing, direction=$direction")
            val view = statusBarView ?: run {
                Log.d(TAG, "driftRunnable: view is null, stopping")
                return
            }
            val target = if (direction > 0) DriftConfig.driftPx.toFloat()
            else -DriftConfig.driftPx.toFloat()
            direction = -direction
            try {
                view.translationX = target
                Log.d(TAG, "drift → ${target}px")
                try { XposedBridge.log("[StatusBarDrift] drift → ${target}px") } catch (_: Throwable) {}
            } catch (t: Throwable) {
                Log.e(TAG, "drift error", t)
                return
            }
            handler.postDelayed(this, DriftConfig.intervalMs)
        }
    }

    /** 状态栏 View 挂载到窗口时调用 */
    fun onViewAttached(view: View) {
        val w = view.width
        val h = view.height
        if (w <= 0) {
            view.post { onViewAttached(view) }
            return
        }
        startDrifting(view)
    }

    /** 状态栏 View 从窗口摘除时调用 */
    fun onViewDetached(view: View) {
        stopDrifting(view)
    }

    /** 配置变化时重启漂移循环（应用新周期/幅度） */
    fun onConfigChanged() {
        if (running) {
            handler.removeCallbacks(driftRunnable)
            handler.postDelayed(driftRunnable, DriftConfig.intervalMs)
            log("drift cycle restarted with new config (drift=${DriftConfig.driftPx}px, interval=${DriftConfig.intervalMs/1000}s)")
        }
    }

    @Synchronized
    private fun startDrifting(view: View) {
        if (statusBarView === view && running) return
        stopDrifting(statusBarView)
        statusBarView = view
        running = true
        log("▶ start drifting (w=${view.width} h=${view.height}, " +
                "drift=${DriftConfig.driftPx}px, interval=${DriftConfig.intervalMs / 1000}s)")
        handler.removeCallbacks(driftRunnable)
        handler.postDelayed(driftRunnable, DriftConfig.intervalMs)
    }

    @Synchronized
    private fun stopDrifting(view: View?) {
        handler.removeCallbacks(driftRunnable)
        if (view != null) {
            try { view.translationX = 0f } catch (_: Throwable) {}
        }
        if (statusBarView === view) {
            statusBarView = null
            running = false
            log("■ stop drifting")
        }
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        try { XposedBridge.log("[StatusBarDrift] $msg") } catch (_: Throwable) {}
    }
}
