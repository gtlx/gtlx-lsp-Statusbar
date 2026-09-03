package com.gtlx.statusbardrift.feature

import android.view.View
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import com.gtlx.statusbardrift.config.DriftConfig

/**
 * 状态栏水平漂移功能
 *
 * Hook PhoneStatusBarView，周期性调用 setTranslationX 让状态栏整体左右漂移，
 * 分散 OLED 像素点亮位置，延缓烧屏。
 */
object StatusBarDriftFeature {
    private const val TAG = "StatusBarDrift"

    private var statusBarView: View? = null
    private var direction = 1
    private var running = false

    private val driftRunnable = object : Runnable {
        override fun run() {
            val view = statusBarView ?: return
            val target = if (direction > 0) DriftConfig.driftPx.toFloat()
            else -DriftConfig.driftPx.toFloat()
            direction = -direction
            try {
                view.translationX = target
                try { XposedBridge.log("[StatusBarDrift] drift → ${target}px") } catch (_: Throwable) {}
            } catch (t: Throwable) {
                Log.e(TAG, "drift error", t)
                return
            }
            view.postDelayed(this, DriftConfig.intervalMs)
        }
    }

    fun init(classLoader: ClassLoader) {
        try {
            val viewClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                classLoader
            )
            // 挂上去的时候启动
            XposedHelpers.findAndHookMethod(viewClass, "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        log("PhoneStatusBarView.onAttachedToWindow")
                        view.post { startDrifting(view) }
                    }
                })
            // 摘下来的时候停止
            XposedHelpers.findAndHookMethod(viewClass, "onDetachedFromWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        stopDrifting(param.thisObject as View)
                    }
                })
            log("StatusBarDriftFeature: hooked PhoneStatusBarView")
        } catch (t: Throwable) {
            logE("StatusBarDriftFeature: hook FAILED", t)
        }
    }

    /** 配置变化时重启漂移循环（应用新周期/幅度） */
    fun onConfigChanged() {
        val view = statusBarView ?: return
        if (running) {
            view.removeCallbacks(driftRunnable)
            view.postDelayed(driftRunnable, DriftConfig.intervalMs)
            log("drift cycle restarted with new config")
        }
    }

    @Synchronized
    private fun startDrifting(view: View) {
        val w = view.width
        val h = view.height
        if (w <= 0) {
            view.post { startDrifting(view) }
            return
        }
        if (statusBarView === view && running) return
        stopDrifting(statusBarView)
        statusBarView = view
        running = true
        log("▶ start drifting (w=${w} h=${h}, " +
                "drift=${DriftConfig.driftPx}px, " +
                "interval=${DriftConfig.intervalMs / 1000}s)")
        view.removeCallbacks(driftRunnable)
        view.postDelayed(driftRunnable, DriftConfig.intervalMs)
    }

    @Synchronized
    private fun stopDrifting(view: View?) {
        if (view != null) {
            view.removeCallbacks(driftRunnable)
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

    private fun logE(msg: String, t: Throwable) {
        Log.e(TAG, msg, t)
        try { XposedBridge.log("[StatusBarDrift] $msg: $t") } catch (_: Throwable) {}
    }
}
