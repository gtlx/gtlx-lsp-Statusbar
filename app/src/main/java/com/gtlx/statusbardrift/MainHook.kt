package com.gtlx.statusbardrift

import android.app.Application
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import android.util.Log
import com.gtlx.statusbardrift.config.DriftConfig
import com.gtlx.statusbardrift.feature.StatusBarDriftFeature

/**
 * Xposed 模块入口
 *
 * 负责：
 * 1. 识别目标进程（com.android.systemui）
 * 2. 加载配置并启动文件监听
 * 3. 初始化各 feature 模块
 */
class MainHook : IXposedHookZygoteInit, IXposedHookLoadPackage {

    private var initialized = false

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        log("initZygote called")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        log("=== target hit: ${lpparam.packageName} ===")

        // Hook Application.onCreate 来获取 Context 并初始化所有功能
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (initialized) return
                        initialized = true
                        val context = param.thisObject as Context
                        initAll(context, lpparam.classLoader)
                    }
                })
            log("hooked Application.onCreate")
        } catch (t: Throwable) {
            logE("hook Application.onCreate FAILED", t)
        }
    }

    /**
     * 初始化所有功能模块
     * 新增功能在这里加一行 init 即可
     */
    private fun initAll(context: Context, classLoader: ClassLoader) {
        log("initializing all features...")

        // 配置加载 & 热更新监听
        DriftConfig.loadAndWatch(context) {
            // 配置变化时通知各功能
            StatusBarDriftFeature.onConfigChanged()
        }

        // 功能模块（按字母排序，新增往下加）
        StatusBarDriftFeature.init(classLoader)

        log("all features initialized ✓")
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        try { XposedBridge.log("[StatusBarDrift] $msg") } catch (_: Throwable) {}
    }

    private fun logE(msg: String, t: Throwable) {
        Log.e(TAG, msg, t)
        try { XposedBridge.log("[StatusBarDrift] $msg: $t") } catch (_: Throwable) {}
    }

    companion object {
        private const val TAG = "StatusBarDrift"
    }
}
