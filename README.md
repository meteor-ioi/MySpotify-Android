# MySpotify for Android

基于 Spotify Web 播放器核心原理构建的轻量 Android 客户端，支持免费账号整首点播、无音频广告、后台前台服务保活及系统通知栏/锁屏播控。

## 核心特性

- **免费账号整首播放**：模拟 Windows 桌面端 Edge/Chrome 浏览器的请求头（UA），触发 Spotify 官方针对桌面网页版的整首点播特权。
- **系统硬件级 DRM 解密**：基于 Android 原生 Chromium WebView + Google Widevine L3 DRM 模块，合法请求并解密播放 Spotify 官方加密音频流（128k AAC）。
- **后台保活与息屏播放**：集成 Android `MediaPlaybackService` 前台服务与 `WakeLock` / `WifiLock`，防止设备息屏后被系统后台杀进程。
- **系统级 MediaSession 播控**：支持在 Android 下拉通知栏、锁屏界面以及蓝牙耳机、车机中控制播放、暂停、上一曲、下一曲，并实时同步专辑封面与歌曲信息。
- **自适应移动端排版**：内置响应式 CSS 样式与 DOM 脚本注入，针对手机竖屏优化桌面端布局，自动折叠冗余侧边栏与推广横幅。

## GitHub Actions 自动构建

本项目已配置标准 GitHub Actions 自动化构建流程：
- 每次代码推送（Push）或手动触发（workflow_dispatch）会自动编译 Release APK。
- 编译产物可在 GitHub 仓库的 **Actions › 对应构建任务 › Artifacts** 中下载 `MySpotify-Android-APK`。

## 许可证

本项目仅供个人学习与技术原理研究使用。
