# 状态栏漂流瓶 StatusBarDrift

LSPosed 模块，周期性水平偏移 SystemUI 状态栏内容（时间、图标等），防止 OLED 屏幕状态栏区域烧屏。

## 原理
- Hook `com.android.systemui` 的 `PhoneStatusBarView`
- 亮屏时每 30 秒将状态栏整体水平偏移 ±3px
- 肉眼几乎不可察，但像素点亮位置不断变化，有效避免固定像素长期老化

## 作用域
仅 `com.android.systemui`

## 构建
```bash
./gradlew assembleDebug
```
安装后在 LSPosed 管理器中启用模块并重启 SystemUI。
