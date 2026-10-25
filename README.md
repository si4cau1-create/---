# 卡点 · Android 打包工程

把 PWA 源码（[kadian-computer](https://github.com/si4cau1-create/kadian-computer)）打包成安卓安装包（APK）。
纯 WebView 壳，**零运行时依赖、零 AndroidX、零原生权限**（除 INTERNET），AI 请求由前端直接发起。

**许可证**：GPL-3.0（见 [LICENSE](LICENSE)）。

---

## ⚠️ 版本说明（下载前必读）

**1.x 系列安装包（含 1.1.0）体现的是早期错误的产品理念，请勿作为学习主力下载使用。**

早期版本把「拍照搜题 / 整题解析」当成了产品核心，还内置了约 23MB 的本地 OCR 引擎。这条路已被实测证伪：轻量 OCR 错题百出，比不过任何云端视觉模型；要拍照求解解析，直接用豆包等免费工具更好。

**正确理念是 2.0.Beta 的「全程无断点做题流」**（做题 → 不会 → 了解 → 学会；从"不会"到"了解"直达讲解、零跳转，拖延症没有可乘之机），即将发布。
本仓库存档 1.1.0 仅为留档；想认真用这个工具学习的，请等 2.0.Beta，**不要安装 1.x 版本**。

## 一键构建

```bat
build_apk.bat          :: 构建 debug 包，产物在 dist\卡点-<版本>.apk
build_apk.bat release  :: 构建 release 包（未配自有签名时用 debug 签名）
```

脚本自动完成：同步 PWA 资源 → Gradle 构建 → 按 `versionName` 命名收集到 `dist/`。
每次改完 PWA 代码后重跑一次即可，APK 里永远是最新代码。

## 环境要求

| 组件 | 要求 |
| --- | --- |
| JDK | 17（设置 `JAVA_HOME`） |
| Android SDK | platform 35 + build-tools 35.0.0 + platform-tools（设置 `ANDROID_HOME`） |
| Gradle | 8.9（wrapper 自动拉取，已配腾讯云镜像） |
| Python | 3.8+（同步资源脚本用；找不到时会尝试 `py -3`，也可用 `KADIAN_PYTHON` 指定 python.exe 路径） |

换机器不用改任何文件：设好 `JAVA_HOME` / `ANDROID_HOME` 环境变量即可
（`build_apk.bat` 里保留的本机默认路径只是未设环境变量时的兜底）。

`local.properties`（不进仓库）里的 `sdk.dir` 指向本机 SDK；可复制 `local.properties.example` 改成自己的路径，
或直接依赖 `ANDROID_HOME` 环境变量。

## 关于 assets/public/（重要）

`app/src/main/assets/public/` 是 **PWA 的构建快照**，由 `tools/sync_assets.py` 从 PWA 工程同步生成，**已随本仓库提交**——
所以单独 clone 本仓库就能直接构建 APK，不需要再去 clone PWA 源码。

- 只改 APK 壳（Java / res / gradle）：直接构建即可
- 改 PWA 代码：把改动同步到 [kadian-computer 仓库](https://github.com/si4cau1-create/kadian-computer)，
  然后在本仓库同级放一份 PWA 检出，重跑 `build_apk.bat`（sync 步骤会刷新快照）
- 当前快照为 1.1.0：内含本地 OCR 引擎（tesseract.js + 三语语言包，约 23MB 解压后），因此 APK 约 13MB；2.0.Beta 将移除 OCR，体积会显著下降

## 工程结构

```
kadian-phone/
├── build_apk.bat            :: 一键构建
├── settings.gradle          :: 仓库（google + mavenCentral）
├── gradle.properties        :: JVM 参数，不用 AndroidX
├── local.properties.example :: sdk.dir 示例（复制为 local.properties 使用）
├── app/
│   ├── build.gradle         :: compileSdk 35 / minSdk 24 / targetSdk 35；versionCode/versionName 在这里升
│   └── src/main/
│       ├── AndroidManifest.xml      :: 仅 INTERNET 权限；竖屏；FileProvider 声明
│       ├── java/com/kadian/app/
│       │   ├── MainActivity.java    :: WebView 壳（见下）
│       │   └── WebAssetProvider.java:: 给系统相机提供拍照输出文件
│       ├── res/                     :: 主题（浅色状态栏）、启动图标（各密度 + 自适应）
│       └── assets/public/           :: PWA 快照（sync_assets.py 生成，已提交）
└── tools/
    ├── sync_assets.py       :: PWA → assets/public 同步（排除 tools/README/md）
    ├── collect_apk.py       :: 按 versionName 收集产物到 dist\
    └── mkicons.py           :: 生成启动图标（源图在 PWA 工程内）
```

## MainActivity 做了什么

- **本地源**：拦截 `https://appassets.androidplatform.net/*` 请求，从 `assets/public/` 读取返回。与真实 HTTPS 站点同源，CORS / IndexedDB / fetch 全部正常，**完全离线可用**（wasm / 语言包同样走这套加载，MIME 已覆盖 `.wasm` / `.gz`）。
- **拍照问**（1.x 遗留功能，2.0.Beta 移除）：实现 `onShowFileChooser`——相机（全尺寸原图输出到自家 provider）+ 相册。原图直接进前端已有的 `compressImage` 流程，OCR 清晰度不受影响。
- **备份**：JS 桥 `KadianAndroid.exportChunk/exportEnd`（base64 分片防 Binder 1MB 限制）→ Q+ 写系统 Download 目录，旧系统写应用外部目录。导入走系统文件选择器，分片回传 JS。
- **后退键**：WebView 历史回退，退不出才退出 App（hash 路由天然支持）。
- **UA 注入 `KadianAndroid/1.0`**：PWA 的 `main.js` 检测到它就不注册 Service Worker（壳内不需要）。

## 安装到手机

1. 把 `dist/卡点-<版本>.apk` 传到手机（微信文件传输 / USB / 网盘均可）
2. 点击安装，需允许「未知来源」
3. 首次打开进「设置」：AI 服务按需配置；「拍照转文字」默认本地离线识别是 **1.x 遗留行为**，2.0.Beta 将改为做题流 + API 出题

数据存在应用私有目录（IndexedDB），卸载即清空——**换机前先在 App 内「设置 → 导出备份」**，备份文件在系统 Download 目录。

## 已知限制

- debug 签名（Android Debug 证书）。长期使用建议在 `app/build.gradle` 配自己的 keystore 后构建 release。
- minSdk 24（Android 7.0+）。1.x 的本地 OCR 需要 WebAssembly，更老的系统 WebView 内核可能不支持且无法更新；2.0.Beta 移除 OCR 后此限制随之消失。
- 未接应用内更新：PWA 代码变了要重新出包。真需要热更新再上 TWA + Digital Asset Links 方案（需要公网 HTTPS）。
