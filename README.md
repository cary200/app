# 清屏 · Android 个人测试版

清屏利用 Android 无障碍服务处理用户勾选的应用。首页提供连接状态、暂停开关、应用选择、入口防误触开关和统计。最低 Android 8.0（API 26），目标 API 35。工程含完整 Java 源码、一个独立的测试 App 和生成调试 APK 的工作流。

## 安装前，了解授权与边界

- 你需要在清屏内阅读说明，再到系统无障碍设置手动开启「清屏防护」。Android 13 及以上部分侧载来源会触发「受限设置」。确认安装包来源可信后，可按手机提供的入口，在清屏应用详情的更多菜单允许受限设置，再返回无障碍页面。工作资料、设备管理策略或厂商限制可能禁止启用。
- 系统授予的无障碍权限较广。服务接收窗口事件的包名来识别切换，程序根据所选包名核验活动窗口，在匹配的应用中检查可见控件，并可执行点击和局部无障碍遮挡。设置开关可随时撤销权限。不同用户或工作资料需分别处理。
- 清屏未申请联网、普通悬浮窗、使用情况访问、开机广播、后台定位、截图、读取存储或电池白名单权限。按包记录名称、选择状态及分类计数，保存在应用私有空间，已关闭 Android 应用备份。没有广告 SDK、统计 SDK 和网络规则订阅。
- 重启并首次解锁后，依靠系统重新绑定已启用的无障碍服务。清理最近任务通常只会关闭界面。部分手机还会同时清理后台进程，可在该机型提供的应用详情里允许自启动和后台运行。重启后以首页「防护已开启」和实际测试结果为准。用户强行停止、禁用服务或系统策略阻止绑定后，应用无法自行恢复权限。

## 获得 APK

### Windows 本地构建

1. 安装 Android Studio、JDK 17（推荐）及 Android SDK Platform 35、Build-Tools 34.0.0、Platform-Tools。在 SDK Manager 接受所需许可。设置好 JAVA_HOME、ANDROID_HOME。如果安装在 Windows 常见默认位置，脚本也会查找 Android Studio 自带的 Java 和本机 SDK。
2. 解压工程，在含 build.ps1 的 QingPing 目录打开 PowerShell，执行：
   
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
   ```
   
   ExecutionPolicy 参数只作用于这一次 PowerShell 进程。首次联网下载官方 Gradle Wrapper、Gradle 和 Google Maven 依赖。脚本校验 Wrapper JAR 和 Gradle 发行包的 SHA-256。完成首次引导后，也可以直接在 Android Studio 打开此目录并运行 app 模块。
3. 安装 `app/build/outputs/apk/debug/app-debug.apk`。可选的测试 App 在 `fixture/build/outputs/apk/debug/fixture-debug.apk`。有 adb 时可用 `adb install -r 文件路径`。调试包适合个人测试，长期分发需单独配置并保管自己的签名。

### macOS、Linux 或 GitHub Actions

本机构建命令为 `bash gradlew :app:assembleDebug :fixture:assembleDebug`，同样需要 JDK 和 Android SDK。若依赖下载失败，检查网络是否可以访问 GitHub、Gradle、Google Maven 和 Maven Central。

无需在电脑安装 Android SDK 的做法：把 QingPing **目录内**的文件（含 .github）放在自己的 GitHub 仓库根目录，启用 Actions。在 “Build QingPing APK” 中选择 “Run workflow”。APK 构建与 lint 完成后，从运行页面的 Artifacts 下载并解压 QingPing-debug。测试场安装包单独发布。随后该工作流会在临时 API 30 模拟器运行跨应用检查。请查看模拟器步骤是否通过。这里提供了工作流定义，交付时尚未远程执行。

## 手机上怎么用

1. 打开清屏，点「了解权限并启用」，在系统设置启用「清屏防护」。
2. 返回清屏，点「选择要保护的应用」。默认全不勾选。选中后系统里的应用仍需从桌面实际打开，供清屏观察到进入前台的事件。
3. 开屏前 8 秒内，遇到独立的「跳过广告」，或广告标记和同组「跳过 5s」等可点击控件，会尝试一次点击。广告仍可能短暂可见，或已完成加载。
4. 若开屏有明确广告标记和同组「点击查看详情」等入口，可显示绿色「防误触」覆盖那个按钮。轻点被拦，长按释放本次开屏的保护，再点才会进入。主页可关闭这项功能。若按钮位置无法对齐则放弃遮挡。
5. 遇到原应用内明确写有「是否打开广告链接？」或「即将打开广告链接」且有同组「取消」等按钮的提示，服务会尝试取消。普通外链确认、支付、授权、其他应用接管后的系统弹窗均不作为广告外跳处理。

## 统计的含义

按来源包名分别记录「跳过开屏」「取消外跳」「挡住触摸」。计数可在首页进入统计页查看，能清空。暂停、重启和取消选择不清除历史计数，卸载清屏或清除应用数据会删除计数。

前两项要求按钮点击被系统接受，且随后 1.8 秒内在同一来源应用连续两次观察到相应标记已消失，两次观察至少相隔 160 毫秒。控件树无法完整读取、原应用离开、或按钮无效果时，宁可漏计。计数是可观察的界面结果，恰好自然结束的广告也可能与点击重合。挡住触摸只计入口仍有效时发生的轻点。总数是处理动作次数，同一条广告可能产生多个动作。

## 明确的限制

- 规则依赖可访问的文本或描述、控件分组、可点击状态和可校验边界。多个候选、控件树过大、无语义画布、视频画面、未暴露按钮的 WebView 或改版文案都可能不命中。当前词表见 Rules.java。窗口进入前台的前 8 秒是开屏的近似判断，包括从桌面返回已有应用，无法精确区分所有热启动。
- 单次点击、局部遮挡与明确弹窗取消适用于可观察界面。摇一摇直接唤起其他 App、整屏热区、广告 SDK 直接发起 Intent 而无可见确认，都无法普遍拦截。清屏不接管浏览器、系统返回键或其他应用的传感器。
- 无障碍服务可以被关闭，用户强停和厂商清理无法由普通 App 绕开。当前没有机型覆盖或成功率数据。工程供侧载测试；面向应用商店分发还需单独评估商店政策。

## 用独立测试场验收

同时安装两个 APK，在清屏勾选「清屏测试场」。从桌面打开测试场，会先显示模拟开屏。进入「选择下次测试场景」，点选情形后测试场关闭。请从桌面重新打开它，这样每次都有独立前台会话。

| 场景 | 应观察到的结果 |
|---|---|
| 开屏跳过 | 自动进入测试首页，测试场名下的跳过次数加 1 |
| 广告外跳确认 | 留在原应用，取消外跳加 1 |
| 广告入口防误触 | 打开后立即轻点绿色遮挡，挡住触摸加 1。长按放行后再次轻点可进入详情 |
| 正常外链 / 引导页跳过 | 提示保留，统计不增加 |
| 无效跳过按钮 | 即使按钮接受点击，广告仍显示，统计不增加 |
| 暂停或取消勾选 | 重新打开模拟开屏，清屏不操作 |
| 划掉清屏 / 重启后解锁 | 不先打开清屏，直接启动选中的测试场，观察结果，再打开清屏核对次数与连接状态 |
| 系统强行停止清屏 | 手动重新打开清屏并检查无障碍开关，然后再次验证 |

自动化入口是 checks/emulator.py，仅适用于可丢弃的干净模拟器。它通过 adb 为该测试环境预置同意与选中状态，正式应用没有相应的外部开关。交付前已在 JVM 运行 36 项匹配和计数断言，并检查 Java、XML、脚本与打包结构。由于当前环境缺少 SDK、adb 和模拟器，尚未在这里构建 APK 或完成设备端验收。

## 平台资料

- [Android 无障碍服务的授权与生命周期](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [无障碍节点操作](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo)
- [无障碍覆盖窗口](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY)
- [Android 受限设置](https://support.google.com/android/answer/12623953)
- [Android 15 强停状态说明](https://developer.android.com/about/versions/15/behavior-changes-all#enhanced-stop-states)
- [Intent 与 intent filter](https://developer.android.com/guide/components/intents-filters)
