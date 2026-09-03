package com.gtlx.statusbardrift;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XposedBridge;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.FileObserver;
import android.view.View;
import android.util.Log;

import java.io.File;

/**
 * 状态栏漂流瓶 —— 周期性水平偏移状态栏内容，防 OLED 烧屏。
 * 仅作用于 com.android.systemui。
 */
public class MainHook implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    private static final String TAG = "StatusBarDrift";
    private static final String PREFS_NAME = "com.gtlx.statusbardrift_preferences";
    private static final String PREFS_NAME_X = "status_bar_drift";
    private static final String KEY_DRIFT_PX = "drift_px";
    private static final String KEY_INTERVAL_SEC = "interval_sec";

    private static int sDriftPx = 3;
    private static long sIntervalMs = 30_000L;
    private static boolean sConfigLoaded = false;

    private static View sStatusBarView;
    private static int sDirection = 1;

    private static final Runnable sDriftRunnable = new Runnable() {
        @Override
        public void run() {
            if (sStatusBarView == null) return;
            float target = (sDirection > 0) ? sDriftPx : -sDriftPx;
            sDirection = -sDirection;
            try {
                sStatusBarView.setTranslationX(target);
            } catch (Throwable t) {
                Log.e(TAG, "drift error", t);
                return;
            }
            sStatusBarView.postDelayed(this, sIntervalMs);
        }
    };

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        log("initZygote called");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        log("=== target hit: " + lpparam.packageName + " ===");

        // Hook Application.onCreate 来加载配置
        try {
            XposedHelpers.findAndHookMethod(
                    Application.class,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Context ctx = (Application) param.thisObject;
                            loadConfig(ctx);
                            // 监听配置文件变化
                            startWatchingConfig(ctx);
                        }
                    });
            log("hooked Application.onCreate OK");
        } catch (Throwable t) {
            logE("hook Application.onCreate FAILED", t);
        }

        // Hook PhoneStatusBarView
        try {
            Class<?> viewClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                    lpparam.classLoader);
            XposedHelpers.findAndHookMethod(
                    viewClass,
                    "onAttachedToWindow",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            View view = (View) param.thisObject;
                            log("PhoneStatusBarView.onAttachedToWindow, post start drift");
                            // 用 view.post 确保在正确的线程且 layout 完成
                            view.post(() -> startDrifting(view));
                        }
                    });
            XposedHelpers.findAndHookMethod(
                    viewClass,
                    "onDetachedFromWindow",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            View view = (View) param.thisObject;
                            stopDrifting(view);
                        }
                    });
            log("hooked PhoneStatusBarView OK");
        } catch (Throwable t) {
            logE("hook PhoneStatusBarView FAILED", t);
        }
    }

    private static synchronized void loadConfig(Context ctx) {
        try {
            // 尝试两种 prefs 名字
            SharedPreferences prefs = null;
            try {
                prefs = ctx.getSharedPreferences(PREFS_NAME_X, Context.MODE_WORLD_READABLE);
            } catch (Throwable ignored) {}
            if (prefs == null) {
                prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            }
            sDriftPx = prefs.getInt(KEY_DRIFT_PX, 3);
            int intervalSec = prefs.getInt(KEY_INTERVAL_SEC, 30);
            sIntervalMs = intervalSec * 1000L;
            sConfigLoaded = true;
            log("config loaded: drift=" + sDriftPx + "px, interval=" + intervalSec + "s");
        } catch (Throwable t) {
            logE("load config FAILED", t);
        }
    }

    private static void startWatchingConfig(Context ctx) {
        try {
            File prefsDir = new File(ctx.getFilesDir().getParent(), "shared_prefs");
            File prefsFile = new File(prefsDir, PREFS_NAME_X + ".xml");
            if (!prefsFile.exists()) {
                prefsFile = new File(prefsDir, PREFS_NAME + ".xml");
            }
            final File finalFile = prefsFile;
            final Context fCtx = ctx;
            FileObserver observer = new FileObserver(finalFile.getParent(), FileObserver.MODIFY | FileObserver.CLOSE_WRITE) {
                @Override
                public void onEvent(int event, String path) {
                    if (path != null && path.contains("status_bar_drift")) {
                        log("config file changed, reloading");
                        loadConfig(fCtx);
                        // 重启循环以应用新周期
                        if (sStatusBarView != null) {
                            sStatusBarView.removeCallbacks(sDriftRunnable);
                            sStatusBarView.postDelayed(sDriftRunnable, sIntervalMs);
                        }
                    }
                }
            };
            observer.startWatching();
            log("config watcher started on " + prefsFile.getParent());
        } catch (Throwable t) {
            logE("start config watcher FAILED", t);
        }
    }

    private static void log(String msg) {
        Log.i(TAG, msg);
        try { XposedBridge.log("[StatusBarDrift] " + msg); } catch (Throwable ignored) {}
    }

    private static void logE(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        try { XposedBridge.log("[StatusBarDrift] " + msg + ": " + t); } catch (Throwable ignored) {}
    }

    private static synchronized void startDrifting(View view) {
        if (view == null) return;
        int w = view.getWidth();
        int h = view.getHeight();
        if (w <= 0) {
            // 还没测量好，再等一次
            view.post(() -> startDrifting(view));
            return;
        }
        if (sStatusBarView == view) return;
        stopDrifting(sStatusBarView);
        sStatusBarView = view;
        log("▶ start drifting (w=" + w + " h=" + h
                + ", drift=" + sDriftPx + "px, interval=" + (sIntervalMs/1000) + "s)");
        view.removeCallbacks(sDriftRunnable);
        view.postDelayed(sDriftRunnable, sIntervalMs);
    }

    private static synchronized void stopDrifting(View view) {
        if (view != null) {
            view.removeCallbacks(sDriftRunnable);
            try { view.setTranslationX(0f); } catch (Throwable ignored) {}
        }
        if (sStatusBarView == view) {
            sStatusBarView = null;
            log("■ stop drifting");
        }
    }
}
