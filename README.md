# TVLive 电视直播

精简版 Android TV 直播播放器，参照 mytv 的架构思路重写，专门适配小米盒子 Android 7.0。

## 特点

- 直播源内置在 App 里（来自 vbskycn/iptv 开源项目，每 6 小时更新，339 个频道），不依赖外部网络地址，装好即用。
- 使用 ExoPlayer (Media3) 播放 HLS，支持软解/硬解。
- 遥控器操作：上下键换台，左右键切换线路，菜单/帮助/信息键开关频道列表。
- 最低支持 Android 7.0（minSdk 24）。
- 支持开机自启。

## 编译

使用 GitHub Actions 云编译：把本目录推送到 GitHub 仓库的 main 分支，自动触发编译。
编译完成后在 Actions 运行记录的 Artifacts 里下载 `TVLive-release-apk`，解压得到 APK。

本地编译（需自行安装 Android Studio / SDK）：
```
gradle assembleRelease
```
APK 输出在 `app/build/outputs/apk/release/`。

## 安装到小米盒子

1. 把 APK 拷到 U 盘，插到盒子上，用文件管理器打开安装；或用adb安装。
2. 小米盒子系统会拦截第三方自启，需手动放行：
   设置 → 应用 → 应用管理 → 电视直播 → 自启动 → 开启
3. 开机自启在 App 内无需额外设置（已内置 BOOT_COMPLETED 接收器），系统放行后即可开机自动进入。

## 换源 / 更新频道

编辑 `app/src/main/assets/channels.m3u`，替换成任意标准 m3u 播放列表，重新编译即可。
内置源地址（可手动下载更新）：
https://raw.githubusercontent.com/vbskycn/iptv/master/tv/iptv4.m3u

## 说明

本项目仅供家庭个人学习使用。直播源来自公开网络项目，可用性和合规性取决于实际使用环境。
