package com.gtlx.statusbardrift

import android.content.Context
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.gtlx.statusbardrift.config.DriftConfig
import com.gtlx.statusbardrift.feature.StatusBarDriftFeature

/**
 * Xposed 模块入口
 *
 * Hook PhoneStatusBarView，从其 Context 初始化配置和漂移功能。
 * （不 hook Application.onCreate，因为 Kotlin 编译后 class 引用与目标进程 classLoader 不一致）
 */
class MainHook : IXposedHookZygoteInit, IXposedHookLoadPackage {

    private var initialized = false

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        log("initZygote called")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return
        log("=== target hit ===")

        try {
            val viewClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(viewClass, "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as android.view.View
                        log("PhoneStatusBarView.onAttachedToWindow")

                        if (!initialized) {
                            initialized = true
                            try {
                                val context = view.context.applicationContext
                                initAll(context, lpparam.classLoader)
                            } catch (t: Throwable) {
                                logE("initAll FAILED", t)
                            }
                        }

                        view.post { StatusBarDriftFeature.onViewAttached(view) }
                    }
                })

            XposedHelpers.findAndHookMethod(viewClass, "onDetachedFromWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as android.view.View
                        StatusBarDriftFeature.onViewDetached(view)
                    }
                })

            log("hooked PhoneStatusBarView OK")
        } catch (t: Throwable) {
            logE("hook FAILED", t)
        }
    }

    private fun initAll(context: Context, classLoader: ClassLoader) {
        log("initializing all features...")

        // 配置加载 & 热更新监听（ContentProvider 方案，无 root）
        DriftConfig.loadAndWatchFromProvider(context) {
            StatusBarDriftFeature.onConfigChanged()
        }
        log("config loaded OK")

        // 功能模块初始化（这里放不需要 Context 的 hook 注册）
        // 注意：PhoneStatusBarView 的 hook 已经在上面注册了
        log("all features initialized OK")
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
