# 状态栏漂流瓶 —— 开发手册

> 项目定位：LSPosed / Vector 模块，针对 SystemUI 状态栏做各种定制与优化。
> 首发功能：状态栏周期性水平漂移，防 OLED 烧屏。

---

## 📂 项目结构

```
app/src/main/java/com/gtlx/statusbardrift/
├── MainHook.kt            # Xposed 入口（IXposedHookLoadPackage + IXposedHookZygoteInit）
├── config/
│   └── DriftConfig.kt     # 配置读写（外部存储文件，跨进程）
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

### v1.x 已完成
- [x] 状态栏水平漂移（防烧屏核心）
- [x] 桌面图标 + 设置界面
- [x] 偏移幅度 / 切换周期可调
- [x] 配置文件热更新（修改后自动生效）
- [x] Kotlin 化 + 模块化架构

### v2.x 规划（按优先级排序）

| 优先级 | 功能 | 说明 | 难度 |
|--------|------|------|------|
| ★★★ | **导航栏漂移** | 底部导航栏也加漂移，全面防烧屏 | 低 |
| ★★★ | **状态栏图标随机微移** | 图标各自独立偏移，防烧屏效果更好 | 中 |
| ★★☆ | **状态栏毛玻璃/透明** | 自定义状态栏背景样式 | 中 |
| ★★☆ | **双击状态栏锁屏** | 手势快捷操作 | 低 |
| ★★☆ | **状态栏显示网速** | 左上角/右上角显示实时网速 | 中 |
| ★☆☆ | **隐藏指定图标** | 自定义隐藏哪些状态栏图标 | 低 |
| ★☆☆ | **状态栏时间秒数** | 时间显示精确到秒 | 低 |
| ★☆☆ | **通知栏行数自定义** | QS 磁排列数/通知显示行数 | 中 |
| ★☆☆ | **息屏 AOD 漂移** | AOD 时钟内容也周期移动 | 中 |
| ★☆☆ | **模块内一键重启 SystemUI** | Hook 侧也能触发重启（需 root） | 低 |

### v3.x 设想
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
| LSPosed 管理器里看不到模块 | 检查 `AndroidManifest.xml` 里的 `xposedmodule` meta-data |

---

## 📄 许可证

AGPL-3.0
