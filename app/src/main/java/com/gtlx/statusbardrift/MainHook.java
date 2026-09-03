package com.gtlx.statusbardrift;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XposedBridge;

import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.os.FileObserver;
import android.view.View;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * 状态栏漂流瓶 —— 周期性水平偏移状态栏内容，防 OLED 烧屏。
 * 仅作用于 com.android.systemui。
 */
public class MainHook implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    private static final String TAG = "StatusBarDrift";
    private static final String CONFIG_FILE = "status_bar_drift.conf";
    private static final String KEY_DRIFT_PX = "drift_px";
    private static final String KEY_INTERVAL_SEC = "interval_sec";

    private static int sDriftPx = 3;
    private static long sIntervalMs = 30_000L;

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
                try { XposedBridge.log("[StatusBarDrift] drift → " + target + "px"); } catch (Throwable ignored) {}
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

    private static File getConfigFile(Context ctx) {
        // 优先读外部存储 app 私有目录的配置（App 端写在这里）
        try {
            File extDir = new File(
                    Environment.getExternalStorageDirectory(),
                    "Android/data/com.gtlx.statusbardrift/files/" + CONFIG_FILE);
            if (extDir.exists()) return extDir;
        } catch (Throwable ignored) {}
        // 兜底：App 的私有 files 目录
        try {
            File appDir = new File(
                    "/data/data/com.gtlx.statusbardrift/files/" + CONFIG_FILE);
            if (appDir.exists()) return appDir;
        } catch (Throwable ignored) {}
        return null;
    }

    private static synchronized void loadConfig(Context ctx) {
        try {
            File f = getConfigFile(ctx);
            if (f == null || !f.exists()) {
                log("no config file found, using defaults (drift=3px, interval=30s)");
                return;
            }
            Properties props = new Properties();
            FileInputStream fis = new FileInputStream(f);
            props.load(fis);
            fis.close();
            sDriftPx = Integer.parseInt(props.getProperty(KEY_DRIFT_PX, "3"));
            int intervalSec = Integer.parseInt(props.getProperty(KEY_INTERVAL_SEC, "30"));
            sIntervalMs = intervalSec * 1000L;
            log("config loaded: drift=" + sDriftPx + "px, interval=" + intervalSec + "s");
        } catch (Throwable t) {
            logE("load config FAILED", t);
        }
    }

    private static void startWatchingConfig(Context ctx) {
        try {
            File f = getConfigFile(ctx);
            if (f == null) {
                // 没有配置文件，监听外部存储目录等它出现
                File watchDir = new File(
                        Environment.getExternalStorageDirectory(),
                        "Android/data/com.gtlx.statusbardrift/files/");
                if (!watchDir.exists()) {
                    log("config dir not found, watching skipped");
                    return;
                }
                f = new File(watchDir, CONFIG_FILE);
            }
            final File finalFile = f;
            final Context fCtx = ctx;
            FileObserver observer = new FileObserver(finalFile.getParent(),
                    FileObserver.MODIFY | FileObserver.CLOSE_WRITE | FileObserver.CREATE) {
                @Override
                public void onEvent(int event, String path) {
                    if (path != null && path.contains("status_bar_drift")) {
                        log("config file changed, reloading");
                        loadConfig(fCtx);
                        if (sStatusBarView != null) {
                            sStatusBarView.removeCallbacks(sDriftRunnable);
                            sStatusBarView.postDelayed(sDriftRunnable, sIntervalMs);
                        }
                    }
                }
            };
            observer.startWatching();
            log("config watcher started on " + finalFile.getAbsolutePath());
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
