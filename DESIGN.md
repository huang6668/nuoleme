# 挪了么 Android App 设计文档

面向实现者：Claude Code / Android 工程师

目标：实现一个离线、轻量、低内存、尽可能可靠保活的安卓 App。当手机收到疑似“挪车/移车”短信时，立即响铃、震动、弹出提醒，提示用户处理。

App 名称：挪了么
Package 建议：`com.nuolemo.app`

---

## 1. 项目背景

用户希望在安卓手机收到挪车短信后，自动响铃提醒。要求：

1. 不联网：App 不申请 INTERNET 权限，不上传短信，不依赖服务端。
2. 占用内存小：平时不常驻进程，收到短信时由系统唤醒。
3. 尽可能保活：依赖系统短信广播唤醒，而不是常驻后台轮询。
4. 设置页面可以美观：设置页面只在用户打开时加载，不影响后台待机内存。
5. 支持 GitHub Actions 云端编译：用户没有本地安卓编译环境。

核心策略：

- 使用静态注册的 `BroadcastReceiver` 监听 `SMS_RECEIVED`。
- 平时不启动任何后台服务，不使用轮询，不使用 WorkManager。
- 收到短信后，Receiver 快速匹配关键词/车牌。
- 命中后才启动一个短生命周期 `ForegroundService` 播放报警音、震动、显示通知。
- 用户点击停止或达到设定时长后，Service 主动停止，回到 0 常驻。

---

## 2. 关键设计原则

### 2.1 设置界面和运行逻辑分离

设置界面 `SettingsActivity` 只负责配置：关键词、车牌、响铃时长、震动、音量、测试报警等。

运行逻辑由以下组件独立完成：

- `SmsReceiver`：短信到达时由系统唤醒。
- `AlarmService`：只有匹配到挪车短信时才启动。

设置页面关闭后不应保留常驻 Activity、Service 或定时任务。

### 2.2 平时 0 常驻

禁止实现：

- 常驻后台 Service 用于监听短信。
- 定时轮询短信数据库。
- WorkManager / JobScheduler 定时扫描。
- 网络心跳。

正确实现：

- AndroidManifest 静态注册 `SmsReceiver`。
- `SmsReceiver` 只在短信广播到达时运行。
- `AlarmService` 只在报警时运行。

### 2.3 不联网保证

Manifest 中不要声明：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

代码中不要引入任何 HTTP/WebSocket/远程日志 SDK。

不要添加以下依赖：

- Retrofit
- OkHttp
- Firebase
- Analytics
- Crashlytics
- Sentry
- 任何广告/统计 SDK

### 2.4 权限最小化

必须权限：

- `android.permission.RECEIVE_SMS`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`，Android 14+ 需要
- `android.permission.VIBRATE`
- `android.permission.POST_NOTIFICATIONS`，Android 13+ 需要运行时申请

尽量不申请：

- `READ_SMS`：不读取历史短信，只处理广播里的当前短信。
- `SEND_SMS`：不发短信。
- `READ_CONTACTS`：不读通讯录。
- `INTERNET`：完全不联网。

可选权限：

- `USE_FULL_SCREEN_INTENT`：如果后续要做锁屏全屏报警页面再考虑；首版不建议加，避免权限复杂化。

---

## 3. 技术栈建议

### 3.1 语言

Kotlin。

如果为了极致减少依赖，也可用 Java。但建议 Kotlin，代码更简洁。

### 3.2 UI

设置界面可以做美观，使用传统 View + Material Components。

建议：

- 不使用 Jetpack Compose。原因：Compose 会显著增加包体和运行时依赖，不符合轻量目标。
- 使用 XML layout + Material Components。
- 使用单 Activity 架构。

依赖建议：

```kotlin
implementation("com.google.android.material:material:1.12.0")
implementation("androidx.appcompat:appcompat:1.7.0")
implementation("androidx.core:core-ktx:1.13.1")
```

不要引入过多 Jetpack 组件。

### 3.3 存储

使用 `SharedPreferences`。

不使用 Room / SQLite，配置量很小，没有必要。

### 3.4 构建系统

Gradle + Android Gradle Plugin。

建议版本：

- Gradle wrapper：8.9 或当前稳定版本
- Android Gradle Plugin：8.7.x 或 8.6.x
- Kotlin Android plugin：2.0.x 或 1.9.24
- compileSdk：35
- minSdk：23 或 24
- targetSdk：35

---

## 4. 项目结构

建议生成完整 Android 工程：

```text
nuolemo/
├── .github/
│   └── workflows/
│       └── android-build.yml
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/nuolemo/app/
│           │   ├── AlarmService.kt
│           │   ├── AlarmStopReceiver.kt
│           │   ├── BootReceiver.kt
│           │   ├── MainActivity.kt
│           │   ├── SmsMatcher.kt
│           │   ├── SmsReceiver.kt
│           │   └── SettingsStore.kt
│           └── res/
│               ├── drawable/
│               │   ├── ic_car.xml
│               │   ├── ic_notification.xml
│               │   └── rounded_card.xml
│               ├── layout/
│               │   └── activity_main.xml
│               ├── mipmap-hdpi/...
│               ├── values/
│               │   ├── colors.xml
│               │   ├── strings.xml
│               │   ├── styles.xml
│               │   └── themes.xml
│               └── raw/
│                   └── alarm.mp3 或 alarm.wav，可选
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/wrapper/gradle-wrapper.jar
├── gradle/wrapper/gradle-wrapper.properties
├── README.md
└── CLAUDE.md
```

如果不方便提交 Gradle wrapper jar，可以在 GitHub Actions 中安装 Gradle，但推荐提交 wrapper，保证可复现构建。

---

## 5. 核心组件设计

## 5.1 `MainActivity.kt`

职责：设置界面。

功能：

1. 首次启动时请求运行时权限：
   - `RECEIVE_SMS`
   - Android 13+ 请求 `POST_NOTIFICATIONS`
2. 展示配置项：
   - 启用/禁用挪车提醒
   - 关键词列表
   - 车牌号列表
   - 报警时长
   - 是否震动
   - 是否拉满媒体音量
   - 是否朗读提醒，首版可先不做 TTS
   - 测试报警按钮
   - 保活设置引导按钮
3. 保存配置到 `SharedPreferences`。
4. 显示当前权限状态。
5. 提供“测试报警”按钮，点击后启动 `AlarmService`，传入测试短信文本。

UI 风格要求：

- App 名：挪了么
- 风格：现代、清爽、Material 风格
- 支持深色模式，至少跟随系统
- 首页顶部放一个醒目的车图标和标题
- 卡片式设置项
- 重要状态用颜色提示：已启用/未授权/需设置后台权限

建议设置页面结构：

1. 顶部 Header：
   - 图标：车/铃铛
   - 标题：挪了么
   - 副标题：收到挪车短信后立即响铃提醒
2. 状态卡片：
   - 短信权限：已授权/未授权
   - 通知权限：已授权/未授权
   - 当前状态：已启用/已暂停
3. 规则卡片：
   - 关键词输入框：多行文本，每行一个关键词，或逗号分隔
   - 车牌输入框：可选，多个车牌用逗号/换行分隔
4. 报警卡片：
   - 响铃时长：30 秒 / 60 秒 / 120 秒 / 手动停止
   - 震动开关
   - 拉满音量开关
   - 测试报警按钮
5. 保活指引卡片：
   - 提示用户将 App 设置为“允许自启动 / 电池不优化 / 后台无限制”
   - 提供跳转系统设置按钮

注意：Activity 不要绑定后台服务，不要在 onCreate 启动常驻服务。

---

## 5.2 `SettingsStore.kt`

职责：封装 `SharedPreferences` 读写。

建议字段：

```kotlin
data class AppSettings(
    val enabled: Boolean,
    val keywords: List<String>,
    val plateNumbers: List<String>,
    val alarmDurationSeconds: Int, // 0 表示手动停止
    val vibrate: Boolean,
    val maximizeVolume: Boolean
)
```

默认值：

```text
enabled = true
keywords = ["挪车", "移车", "占道", "堵住", "堵车", "车挡", "挡住", "借过", "车主"]
plateNumbers = []
alarmDurationSeconds = 60
vibrate = true
maximizeVolume = true
```

要求：

- 提供 `load(context): AppSettings`
- 提供 `save(context, settings)`
- 输入关键词时自动 trim，过滤空字符串，去重。

---

## 5.3 `SmsReceiver.kt`

职责：静态短信广播接收器。

Manifest 中声明：

```xml
<receiver
    android:name=".SmsReceiver"
    android:exported="true"
    android:permission="android.permission.BROADCAST_SMS">
    <intent-filter android:priority="999">
        <action android:name="android.provider.Telephony.SMS_RECEIVED" />
    </intent-filter>
</receiver>
```

实现要点：

1. 在 `onReceive(context, intent)` 中判断 action 是否为 `Telephony.Sms.Intents.SMS_RECEIVED_ACTION`。
2. 使用 `Telephony.Sms.Intents.getMessagesFromIntent(intent)` 获取短信。
3. 拼接 multipart 短信正文。
4. 读取 `SettingsStore.load(context)`。
5. 如果 `enabled=false`，立即 return。
6. 调用 `SmsMatcher.matches(settings, sender, body)`。
7. 命中则启动 `AlarmService`。

启动 Service 兼容写法：

```kotlin
val serviceIntent = Intent(context, AlarmService::class.java).apply {
    action = AlarmService.ACTION_START
    putExtra(AlarmService.EXTRA_SMS_BODY, body)
    putExtra(AlarmService.EXTRA_SENDER, sender)
}
ContextCompat.startForegroundService(context, serviceIntent)
```

重要：

- Receiver 中不要做耗时操作。
- 不要访问网络。
- 不要弹 Toast，后台 Toast 体验差且可能受限。
- 不要读取短信数据库。

---

## 5.4 `SmsMatcher.kt`

职责：纯函数匹配短信内容。

建议接口：

```kotlin
object SmsMatcher {
    fun matches(settings: AppSettings, sender: String?, body: String): Boolean
}
```

匹配规则：

1. 关键词命中：短信正文包含任一关键词。
2. 车牌命中：短信正文包含用户配置的任一车牌。
3. 默认关键词不要过宽，避免普通短信误报。
4. 忽略大小写仅对英文/车牌有意义。
5. 去除空格和常见分隔符后再匹配车牌。

车牌归一化：

- 原文转大写。
- 去除空格、短横线、冒号、中文冒号。
- 用户输入车牌也同样归一化。

建议匹配逻辑：

```text
if any keyword in body -> true
if any normalizedPlate in normalizedBody -> true
else false
```

首版不要做复杂 NLP，不引入模型。

建议单元测试覆盖：

- “您的车辆挡住了别人，请及时挪车” -> true
- “请车主移车” -> true
- “验证码 123456” -> false
- 配置车牌“鄂A12345”，短信“鄂 A12345 请挪车” -> true
- 关键词为空但车牌命中 -> true
- disabled 时 Receiver 层不调用或不报警

---

## 5.5 `AlarmService.kt`

职责：报警播放、震动、通知。

Service 类型：短生命周期前台服务。

Manifest：

```xml
<service
    android:name=".AlarmService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback" />
```

常量：

```kotlin
const val ACTION_START = "com.nuolemo.app.action.START_ALARM"
const val ACTION_STOP = "com.nuolemo.app.action.STOP_ALARM"
const val EXTRA_SMS_BODY = "extra_sms_body"
const val EXTRA_SENDER = "extra_sender"
```

启动流程：

1. `onStartCommand` 收到 `ACTION_START`。
2. 立即调用 `startForeground(notificationId, notification)`，避免后台启动限制。
3. 创建通知渠道：
   - Channel ID: `move_car_alarm`
   - 重要性：`IMPORTANCE_HIGH`
   - 名称：`挪车提醒`
4. 构建通知：
   - 标题：`收到挪车短信`
   - 文本：短信摘要
   - 按钮：`停止响铃`
   - 点击通知打开 MainActivity
5. 如果设置 `maximizeVolume=true`：
   - 使用 `AudioManager` 保存当前媒体音量。
   - 设置媒体音量到最大。
6. 播放报警音：
   - 优先使用内置 raw 音频。
   - 若不提供 raw 音频，则使用系统默认闹钟铃声：`RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)`，为空则 fallback 到 `TYPE_NOTIFICATION`。
   - 使用 `MediaPlayer`，looping=true。
7. 震动：
   - Android O+ 使用 `VibrationEffect.createWaveform(...)`。
   - 旧版本使用 deprecated vibrate，但封装兼容。
8. 自动停止：
   - 如果 `alarmDurationSeconds > 0`，使用 `Handler(Looper.getMainLooper()).postDelayed` 调用 stop。
   - 如果为 0，则一直响到用户点停止。

停止流程：

1. 停止 MediaPlayer 并 release。
2. cancel 震动。
3. 如曾修改音量，恢复之前媒体音量。
4. `stopForeground(STOP_FOREGROUND_REMOVE)`。
5. `stopSelf()`。

注意：

- 必须处理重复短信/重复启动：如果服务已在响，再收到命中短信，可以更新通知文本，但不要创建多个播放器。
- 避免内存泄漏：release MediaPlayer，移除 Handler callbacks。
- `onDestroy` 也要调用清理逻辑。

---

## 5.6 `AlarmStopReceiver.kt`

职责：处理通知按钮“停止响铃”。

Manifest：

```xml
<receiver
    android:name=".AlarmStopReceiver"
    android:exported="false" />
```

实现：

- 收到停止 action 后，启动/发送 Intent 到 `AlarmService`，action 为 `ACTION_STOP`。
- 或者直接 `context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))`。

注意 Android 8+ 后台限制：由于目标是停止一个已存在的前台服务，通常可行。也可以用 PendingIntent.getService 直接指向 AlarmService，省掉这个 Receiver。

推荐更简单方案：通知按钮 PendingIntent 直接发给 `AlarmService`：

```kotlin
val stopIntent = Intent(this, AlarmService::class.java).apply { action = ACTION_STOP }
val stopPendingIntent = PendingIntent.getService(...)
```

如果这样实现，就不需要 `AlarmStopReceiver.kt`。

---

## 5.7 `BootReceiver.kt`（可选）

原则上不需要开机自启动后台服务，因为短信广播能唤醒 App。

但可以实现一个非常轻量的 BootReceiver，只用于：

- 开机后显示一次静默/低优先级通知，提示用户“挪了么已启用”。
- 或完全不实现。

首版建议不做 BootReceiver，减少权限和复杂度。

不要申请 `RECEIVE_BOOT_COMPLETED`，除非有明确需求。

---

## 6. AndroidManifest 设计

Manifest 要点：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature
        android:name="android.hardware.telephony"
        android:required="false" />

    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="挪了么"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Nuolemo">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver
            android:name=".SmsReceiver"
            android:exported="true"
            android:permission="android.permission.BROADCAST_SMS">
            <intent-filter android:priority="999">
                <action android:name="android.provider.Telephony.SMS_RECEIVED" />
            </intent-filter>
        </receiver>

        <service
            android:name=".AlarmService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />

    </application>
</manifest>
```

注意：

- 不要加 INTERNET 权限。
- 如果 targetSdk 35，前台服务权限要正确。
- `POST_NOTIFICATIONS` 只在 Android 13+ 运行时请求。

---

## 7. 设置页详细 UI 设计

### 7.1 视觉风格

名称：“挪了么”有一点口语化，UI 可以轻松但可靠。

建议：

- 主色：蓝色或青绿色，例如 `#2563EB`。
- 背景：浅色 `#F6F8FB`，深色适配 `#101318`。
- 卡片圆角 20dp。
- 顶部大图标：车 + 铃铛。
- 按钮文案直接：`测试报警`、`保存设置`、`去开启后台权限`。

### 7.2 页面交互

用户修改设置后：

- 可自动保存，也可点保存。
- 建议自动保存 + Snackbar 提示。
- 对关键词输入框失焦时保存。
- 开关立即保存。

### 7.3 权限引导

如果 SMS 权限未授权：

- 状态卡片显示红/橙色提示。
- 提供按钮：`授权短信权限`。

如果通知权限未授权且 Android 13+：

- 提供按钮：`授权通知权限`。

后台保活引导：

- 文案说明：“为避免系统拦截短信提醒，请在系统设置中允许挪了么自启动、后台运行，并关闭电池优化。”
- 按钮打开应用详情设置页：

```kotlin
Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.parse("package:$packageName")
}
```

不要试图自动修改厂商保活设置，安卓不允许。

---

## 8. 报警体验设计

### 8.1 报警触发

命中短信时：

- 即使手机静音，首版先尝试拉高媒体音量播放。
- 不要修改铃声音量/闹钟音量，避免影响系统。使用媒体音量即可。
- 保存原媒体音量，停止后恢复。

可选增强：使用 `AudioAttributes.USAGE_ALARM`，让系统按闹钟音频策略播放。

### 8.2 通知内容

通知标题：`收到挪车短信`

通知正文：短信前 80 个字符，过长省略。

通知按钮：`停止响铃`

通知点击：打开设置页/主页面。

### 8.3 测试报警

设置页必须有“测试报警”按钮，用于验证：

- 通知是否能显示。
- 铃声是否能播放。
- 震动是否正常。
- 停止按钮是否有效。

测试短信内容：

```text
测试：有人请求您挪车，请及时处理。
```

---

## 9. GitHub Actions 云端编译

用户没有本地 Android 编译环境，因此必须提供 GitHub Actions workflow。

文件：`.github/workflows/android-build.yml`

目标：push 或手动触发后构建 debug APK，并上传 artifact。

示例：

```yaml
name: Android Build

on:
  push:
    branches: [ main, master ]
  pull_request:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Make gradlew executable
        run: chmod +x ./gradlew

      - name: Build debug APK
        run: ./gradlew :app:assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: nuolemo-debug-apk
          path: app/build/outputs/apk/debug/*.apk
```

验收：Actions 页面可以下载 `nuolemo-debug-apk`。

---

## 10. README 要求

生成 README.md，包含：

1. App 简介。
2. 隐私说明：不联网、不上传短信、不申请 INTERNET 权限。
3. 安装方式：从 GitHub Actions 下载 APK。
4. 首次使用：授权短信权限、通知权限。
5. 保活设置指引：小米/华为/OPPO/vivo/三星通用说明。
6. 测试方法：点击“测试报警”。
7. 常见问题：
   - 收不到提醒怎么办？
   - 为什么需要短信权限？
   - 为什么没有网络权限？
   - 是否需要设置成默认短信 App？答案：不需要。

---

## 11. CLAUDE.md 要求

在项目根目录生成 CLAUDE.md，指导后续 Claude Code：

```markdown
# 挪了么 Project Instructions

- This is a lightweight offline Android app.
- Do not add INTERNET permission.
- Do not add analytics, crash reporting, ad SDKs, or network libraries.
- Keep background runtime minimal: no persistent service except AlarmService while actively alarming.
- Use static SmsReceiver for SMS_RECEIVED.
- Use SharedPreferences, not Room/database.
- Use XML Views, not Jetpack Compose.
- Run `./gradlew test` and `./gradlew :app:assembleDebug` before reporting completion.
```

---

## 12. 测试计划

### 12.1 单元测试

至少给 `SmsMatcher` 写 JVM 单元测试。

文件：`app/src/test/java/com/nuolemo/app/SmsMatcherTest.kt`

覆盖：

1. 中文关键词命中。
2. 车牌命中。
3. 普通验证码不命中。
4. 空关键词不崩溃。
5. 大小写/空格归一化。

### 12.2 构建验证

必须通过：

```bash
./gradlew test
./gradlew :app:assembleDebug
```

### 12.3 手工测试

在真机上：

1. 安装 APK。
2. 授权短信权限和通知权限。
3. 点击“测试报警”。
4. 确认响铃、震动、通知出现。
5. 点击通知停止按钮，确认停止并恢复音量。
6. 用另一台手机发送包含“挪车”的短信，确认触发。
7. 发送验证码短信，确认不触发。

---

## 13. 实现顺序建议

Claude Code 应按以下顺序实现：

1. 创建 Android 工程骨架和 Gradle 配置。
2. 添加 Manifest 权限和组件声明。
3. 实现 `SettingsStore` 和 `SmsMatcher`。
4. 添加 `SmsMatcher` 单元测试并跑通。
5. 实现 `SmsReceiver`。
6. 实现 `AlarmService`，包括通知、音频、震动、停止逻辑。
7. 实现 `MainActivity` 设置页和 XML 布局。
8. 添加测试报警按钮。
9. 添加 GitHub Actions workflow。
10. 添加 README.md 和 CLAUDE.md。
11. 运行 `./gradlew test`。
12. 运行 `./gradlew :app:assembleDebug`。
13. 修复构建/测试问题，直到通过。

---

## 14. 验收标准

项目完成必须满足：

1. 仓库可直接通过 GitHub Actions 构建 APK。
2. 本地或 CI 中 `./gradlew test` 通过。
3. 本地或 CI 中 `./gradlew :app:assembleDebug` 通过。
4. Manifest 中没有 INTERNET 权限。
5. App 平时不启动常驻后台服务。
6. 收到匹配短信后能启动前台服务报警。
7. 报警可以通过通知按钮停止。
8. 停止后释放 MediaPlayer、取消震动、恢复音量。
9. 设置页能保存关键词、车牌、时长、震动、音量设置。
10. 设置页有测试报警按钮。
11. README 清楚说明权限和保活设置。

---

## 15. 重要坑点和规避

### 15.1 Android 8+ 后台限制

不要在后台直接启动普通 Service。使用：

```kotlin
ContextCompat.startForegroundService(context, intent)
```

并在 `AlarmService.onStartCommand` 尽快调用 `startForeground(...)`。

### 15.2 Android 13+ 通知权限

如果未授权 `POST_NOTIFICATIONS`，前台服务通知行为可能受影响。首启时请求通知权限。

### 15.3 Android 14+ 前台服务类型

如果 targetSdk 较高，使用 media playback 前台服务时要声明：

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

并在 service 上声明：

```xml
android:foregroundServiceType="mediaPlayback"
```

### 15.4 国产 ROM 保活

本设计不依赖常驻进程，但部分 ROM 可能阻止自启动/后台唤醒。必须在 README 和设置页中提示用户手动开启：

- 允许自启动
- 电池设置为无限制/不优化
- 允许后台运行
- 锁定最近任务，避免一键清理

### 15.5 不要申请 READ_SMS

只处理广播中的短信内容即可，降低权限敏感度。

### 15.6 不要拦截/中断短信

不要调用 `abortBroadcast()`。只监听，不影响系统短信 App 正常接收。

---

## 16. 可选二期功能，不要首版实现

以下功能可以以后再做，首版不要增加复杂度：

1. TTS 朗读短信内容。
2. 锁屏全屏提醒 Activity。
3. 白名单/黑名单联系人。
4. 不同关键词对应不同铃声。
5. 通知历史记录。
6. Wear OS 手表提醒。
7. 桌面小组件。
8. 导入/导出配置。

首版目标是稳定、轻量、离线、可构建。

---

## 17. 给 Claude Code 的任务提示词

可以把下面这段直接交给 Claude Code：

```text
请根据 DESIGN.md 实现“挪了么”Android App 的完整可构建工程。

要求：
- Kotlin + XML View + Material Components。
- 不使用 Jetpack Compose。
- 不申请 INTERNET 权限。
- 使用静态 SmsReceiver 监听 SMS_RECEIVED。
- 使用短生命周期 AlarmService 在命中短信时播放报警音、震动、显示前台通知。
- 设置页美观，支持关键词、车牌、报警时长、震动、拉满音量、测试报警。
- 使用 SharedPreferences 保存设置。
- 添加 SmsMatcher 单元测试。
- 添加 GitHub Actions 构建 debug APK 并上传 artifact。
- 添加 README.md 和 CLAUDE.md。
- 最后运行 ./gradlew test 和 ./gradlew :app:assembleDebug，修复直到通过。

验收时请确认 AndroidManifest.xml 中没有 INTERNET 权限。
```
