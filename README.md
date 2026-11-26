# TVLive 电视直播

精简版 Android TV 直播播放器，参照 mytv 的架构思路重写，专门适配小米盒子 Android 7.0。

## 特点

- 直播源内置在 App 里（来自 vbskycn/iptv 开源项目，311 个频道），断网也能装好即用。
- **自动更新源**：盒子联网时，App 每次启动都会从 GitHub 的 CDN 镜像拉取最新源；源仓库更新后 APK 无需重装即可跟上。拉取失败自动回退到内置源，不影响观看。
- **无广告台/购物台**：解析层过滤了「支持作者」「更新时间」等推广条目，以及电视购物、商城类频道（购物、快乐购、好享购、聚鲨等关键词），内置源和在线源都生效。
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

- **自动更新**（默认）：无需操作。App 启动时后台拉取 GitHub 上的最新源，过滤广告后**直接覆盖内存中的频道列表**，不写入任何本地文件；无论拉取成功与否都会删除缓存文件和临时文件，不占用存储。拉取失败时使用 APK 内置源。源仓库每 6 小时更新，APK 不重装也能跟上。
- **改源地址**：编辑 `SourceManager.java` 里的 `REMOTE_URLS` 数组，重新编译。
- **改内置兜底源**：编辑 `app/src/main/assets/channels.m3u`，重新编译。
- 在线源地址：
  https://fastly.jsdelivr.net/gh/vbskycn/iptv@master/tv/iptv4.m3u

## 说明

本项目仅供家庭个人学习使用。直播源来自公开网络项目，可用性和合规性取决于实际使用环境。
