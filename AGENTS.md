# 挪了么 Codex Working Instructions

本文件是本仓库给 Codex 的主协作说明。`CLAUDE.md` 仅保留为兼容入口，真实约束以本文件为准。

## 项目定位

- 这是一个轻量、离线、低打扰的原生 Android App。
- 目标是“平时零常驻，命中短信后高可靠报警”，不是做通用 IM、云服务或后台保活框架。

## 产品与架构硬约束

- 不要新增 `INTERNET` 权限。
- 不要接入统计、崩溃上报、广告 SDK、远程配置或任何联网依赖。
- 保持后台运行最小化：除报警进行中的短生命周期 `AlarmService` 外，不新增永久后台 `Service`。
- 不要新增 `WorkManager`、轮询任务、定时心跳、自启动常驻保活。
- 必须保留静态 `SmsReceiver` 监听 `SMS_RECEIVED`。
- `SmsReceiver` 在前台服务启动失败时，必须兜底到高优先级/全屏提醒路径。
- `AlarmActivity` 是锁屏/全屏兜底入口，必须继续隐藏最近任务。
- 设置继续使用 `SharedPreferences`，不要引入 Room 或其他数据库。
- UI 继续使用 XML Views，不要迁移到 Jetpack Compose。

## 报警语义约束

- 报警优先使用系统默认闹铃铃声。
- 报警按“闹铃语义”处理，不要自动修改用户的媒体音量。
- 通知可以作为展示、停止操作和全屏兜底入口，但不要把“真正发声”重新做成普通通知音。

## 构建与验证约束

- 默认在 GitHub 上打包，不在本地新增构建环境。
- 不要在本机安装或下载新的 JDK、Android SDK、Command Line Tools、Homebrew 包或其他构建依赖。
- 不要在 `/tmp`、用户目录或项目目录中放置临时 JDK / Android SDK 工具链。
- 不要修改 `~/.zshrc`、`~/.zprofile`、`~/.bashrc`、`~/.gradle`、全局 Git 配置等本地持久化环境。
- 需要产出 APK、跑正式构建、跑 CI 等，优先走 GitHub Actions。
- 本地只做只读检查、代码修改、轻量静态检查；如果缺少本地运行环境，不要自行补装，直接说明即可。

## GitHub 与网络约束

- 仓库的正式打包渠道是 GitHub Actions。
- 若 GitHub 访问异常，优先使用本机 `127.0.0.1:10808` 代理。
- 代理只允许作为“当前命令的临时参数”使用，不要写入全局 Git 配置或 shell 配置。
- 例如 Git 操作应优先使用临时参数：
  - `git -c http.proxy=http://127.0.0.1:10808 -c https.proxy=http://127.0.0.1:10808 ...`
  - 或命令级环境变量，而不是 `git config --global http.proxy ...`

## 提交与工作区规则

- 工作区可能有用户未提交的改动，不能擅自回退。
- 提交或推送前，先确认只包含当前任务需要的文件。
- 如果仓库中有历史兼容文件（如 `CLAUDE.md`），可以维护，但 Codex 应优先遵循 `AGENTS.md`。

## 完成前检查

- 修改完成后，优先说明：
  - 改了什么
  - 是否影响短信触发、报警、全屏兜底
  - 是否需要去 GitHub Actions 打包验证
- 如果用户明确要求“不要打包 / 不要推送”，只停在代码修改与说明，不自行发布。
