# 状态栏漂流瓶 —— 开发手册

> 项目定位：LSPosed / Vector 模块，针对 SystemUI 状态栏做各种定制与优化。
> 首发功能：状态栏周期性水平漂移，防 OLED 烧屏。

---

## 📂 项目结构

```
app/src/main/java/com/gtlx/statusbardrift/
├── MainHook.kt            # Xposed 入口（IXposedHookLoadPackage + IXposedHookZygoteInit）
├── config/
│   ├── DriftConfig.kt     # 配置读写（ContentProvider 跨进程，无 root）
│   └── ConfigProvider.kt  # ContentProvider，目标进程读配置的入口
├── feature/
│   ├── StatusBarDrift.kt  # 【核心】状态栏漂移逻辑
│   └── README.md          # 每个功能一个文件，便于扩展
└── ui/
    └── MainActivity.kt    # 设置界面（桌面图标入口）

app/src/main/res/
├── mipmap-xxxhdpi/        # 图标
├── values/
│   ├── strings.xml        # LSPosed 描述
│   └── arrays.xml         # 作用域声明
└── xml/xposed_init.xml    # xposed 元数据

assets/xposed_init         # Xposed 入口类列表
```

**扩展新功能的原则**：每个独立功能在 `feature/` 下建一个文件，暴露 `init(classLoader)` 方法，由 `MainHook` 统一调用。

---

## 🛠️ 构建环境

- **编译机**：arch 虚拟机（ssh arch）
- **Android SDK**：`~/android-sdk`（build-tools 34.0.0, platforms android-35/36）
- **Gradle**：8.11.1（`~/code/gradle-8.11.1/bin/gradle`）
- **Kotlin**：1.9.22
- **项目路径**：`~/prj/code/status-bar-drift`（arch 上的软链，中文路径需要英文软链）

### 快速构建命令

```bash
ssh arch "cd ~/prj/code/status-bar-drift \
  && export ANDROID_HOME=~/android-sdk \
  && export PATH=~/code/gradle-8.11.1/bin:\$PATH \
  && export LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 \
  && export JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8' \
  && gradle assembleDebug --no-daemon"
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### 安装到手机

```bash
adb install -r app-debug.apk
```

### 模块管理（Vector CLI）

```bash
# 查看模块列表
adb shell su -c '/data/adb/lspd/cli modules ls'
# 设置作用域
adb shell su -c '/data/adb/lspd/cli scope set com.gtlx.statusbardrift com.android.systemui/0'
# 查看日志
adb shell su -c '/data/adb/lspd/cli log cat'
# 重启 SystemUI
adb shell su -c 'killall com.android.systemui'
```

---

## 🚀 功能 Roadmap

> 版本规则：新项目 0.1.x 起步（语义化版本，功能演进加 minor，修复加 patch）

### 已完成（历史积累）
- [x] 桌面图标 + 设置界面
- [x] 偏移幅度 / 切换周期可调
- [x] 配置文件热更新（修改后自动生效）
- [x] Kotlin 化 + 模块化架构（config/feature/ui 三层）
- [x] 状态栏水平漂移（防烧屏核心）
- [x] 设置界面 Soft UI + Glassmorphism 风格美化
- [x] 配置同步 ContentProvider 化（**无 root**，核心功能零权限）
- [x] root 增强保留（一键重启 SystemUI，无 root 降级手动）

### 当前版本：v0.2.0

### v0.3+ 规划（按优先级排序）

| 优先级 | 功能 | 说明 | 难度 |
|--------|------|------|------|
| ★★★ | **灵动岛 Dynamic Island** | 见下方专项规划 | 中-高 |
| ★★★ | 导航栏漂移 | 底部导航栏也加漂移，全面防烧屏 | 低 |
| ★★☆ | 状态栏毛玻璃/透明 | 自定义状态栏背景样式 | 中 |
| ★★☆ | 双击状态栏锁屏 | 手势快捷操作 | 低 |
| ★★☆ | 状态栏显示网速 | 实时网速显示 | 中 |
| ★☆☆ | 隐藏指定图标 | 自定义隐藏状态栏图标 | 低 |
| ★☆☆ | 状态栏时间秒数 | 时间精确到秒 | 低 |

### ✨ 灵动岛专项规划（讨论稿，未开工）

**可行性**：✅ 手机为居中挖孔屏，天然适配灵动岛。SystemUI 进程内 hook 状态栏窗口，在挖孔区挂自定义 View。

**实现原理**：
```
hook StatusBarWindowView
→ 挖孔周围挂岛 View（与状态栏同窗口，免权限）
→ 监听事件源：通知 / 媒体 / 通话 / 充电 / 录音定位指示
→ 有事件膨胀成"岛"，无事件缩回胶囊
```

**事件源方案**：
- 通知：`NotificationListenerService`（需授权）或 hook NotificationManager
- 媒体/通话：hook SystemUI 内部状态
- 充电/录音/定位：系统广播/现有指示器

**分期**：
- v1：静态岛 + 通知/充电指示（可控）
- v2：媒体控制 + 动画流畅化（磨状态机与动画）
- 难点在动画跟手自然，功能本身不难

**防烧屏联动**：★ 岛是常驻显示区，可复用漂流瓶漂移逻辑防烧。

### 远期设想
- [ ] 主题引擎（状态栏样式包）
- [ ] 多维度防烧屏（颜色/亮度动态调整）
- [ ] LSPosed 资源钩子（替换图标、布局）

---

## 🧩 新增功能的开发流程

1. 在 `feature/` 下新建 `XxxFeature.kt`，实现 `init(classLoader)`
2. 在 `MainHook.kt` 的 `handleLoadPackage` 中调用新功能的 `init`
3. 如果需要用户配置：
   - 在 `DriftConfig.kt` 新增配置项
   - 在 `MainActivity.kt` 加 UI 控件
   - 配置文件热更新由 `ConfigWatcher` 统一处理
4. 在本文件 Roadmap 中勾选完成

---

## 🔧 常见坑

| 坑 | 解法 |
|---|---|
| 中文路径 Gradle 编译炸 | 用英文软链：`ln -s ~/项目 ~/prj`，编译走软链路径 |
| `MODE_WORLD_READABLE` 在高版本 Android 不可用 | 配置文件放外部存储 App 私有目录（`getExternalFilesDir`） |
| Vector 日志里只有 `XposedBridge.log` 的输出 | 调试日志同时打 `Log.d` 和 `XposedBridge.log` |
| `onAttachedToWindow` 时 View 还没测量 | 用 `view.post { ... }` 等下一轮再启动 |
| Kotlin  hook `Application.onCreate` 不触发回调 | Kotlin 的 `Application::class.java` 类对象与目标进程 classLoader 不一致，改用 `XposedHelpers.findClass("android.app.Application", lpparam.classLoader)` 或直接 hook 目标 app 内的类获取 Context |
| LSPosed 管理器里看不到模块 | 检查 `AndroidManifest.xml` 里的 `xposedmodule` meta-data |
| App 调的参数 SystemUI 读不到 | App 的 `getExternalFilesDir(null)` 可能返回 null，配置 fallback 到内部私有目录，SystemUI 读不到。参考 lsposed-module-dev 技能的「配置同步方案」 |

---

## 📄 许可证

AGPL-3.0
