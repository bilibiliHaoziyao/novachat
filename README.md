# Nova Chat · 新星聊

> 基于 [Delta Chat](https://github.com/deltachat) 的邮箱即时通讯软件，当前版本 **1.3**，仅发布 Android。
> Delta Chat 的核心理念——"以电子邮件协议为传输、无中心服务器"——被完整保留；Nova Chat 在此基础上重构了首次使用体验，并让加密与账号同步变得可选、可控。

Nova Chat（新星聊）围绕 Delta Chat 做了三项核心改造：

1. **开箱向导（OOBE）** —— 首次安装即进入 HyperOS 风格全屏向导：语言 → 国家或地区 → 创建账户 / 登录 → WebDAV 备份（可跳过）→ 慕寒智能 → 设置完毕；邮箱登录入口保留服务商智能预设（QQ / 163 / Gmail / Outlook 等）与实时连接测试；
2. **端到端加密默认关闭** —— 不再强制 E2E，设置中随时开启，可与普通邮件客户端互通；
3. **账号支持 WebDAV 同步** —— 账号备份经 WebDAV（Nextcloud / 坚果云 / 自建服务等）加密上传，换机可一键恢复。

## 下载

前往 [Releases](https://github.com/bilibiliHaoziyao/novachat/releases) 下载最新版：

| 平台 | 产物 | 说明 |
|---|---|---|
| Android | `NovaChat-universal.apk` | 32 位 + 64 位二合一（armeabi-v7a / arm64-v8a），Android 5.0+（`minSdk 21`），仓库密钥固定签名，跨版本可直接覆盖安装 |

> 产物由 GitHub Actions 在打 `v*` 标签时自动构建并发布（见 [.github/workflows/build-release.yml](.github/workflows/build-release.yml)）。
> 桌面端源码保留在 `apps/desktop/`，但自 v0.2.0 起已停止构建与发布。

## 主要特性

| 特性 | 说明 |
|---|---|
| 开箱向导（OOBE） | [OobeActivity.java](apps/android/src/main/java/org/thoughtcrime/securesms/oobe/OobeActivity.java)：首次安装自动进入，语言 → 国家或地区 → 创建账户 → WebDAV → 慕寒智能 → 完成；可在 关于页 → 重新运行开箱向导 再次体验 |
| 邮箱登录向导 | [ClassicLoginActivity.java](apps/android/src/main/java/org/thoughtcrime/securesms/ClassicLoginActivity.java)：邮箱/密码/显示名 + 高级折叠，欢迎页直达；服务商预设自动填写 IMAP/SMTP |
| 端到端加密可选 | 新账号写入 `force_encryption=0`，高级设置保留开关，关闭后可与普通邮件客户端互通 |
| WebDAV 账号同步 | [WebDavSyncManager.java](apps/android/src/main/java/org/thoughtcrime/securesms/connect/WebDavSyncManager.java) + [WebDavSettingsActivity.java](apps/android/src/main/java/org/thoughtcrime/securesms/WebDavSettingsActivity.java) |
| 莫奈取色（Monet） | 默认开启：聊天背景、工具栏与配色随壁纸取色，深浅色模式分别适配（Android 12+），可在 设置 → 外观 中关闭 |
| 纯色聊天背景 | 默认背景为纯色，深浅色跟随系统 |
| 快捷分享邀请 | 二维码页分享邀请链接时提供 快捷分享至 QQ / WeChat / 复制链接 |
| 软件内切换语言 | 设置 → 语言，支持 简体中文 / 繁體中文 / English / 日本語（跟随系统为默认） |
| 后台收取消息指南 | 进入软件时提示开启即时传送、后台加锁与自启动，保证离线收信 |
| 账号备份提示 | 初次登录在设备消息中提示备份账号 |
| 关于页 | 软件介绍、版本号、开发者与 **Powered by Delta Chat** 致谢、源码链接 |
| MuHan Intelligence（慕寒智能） | 内置 AI 助手，默认置顶在会话列表，可在 设置 → MuHan Intelligence 中关闭；支持多会话，对话界面与常规会话保持一致，可发送文字 / 图片 / 语音，对话历史仅保存在本机 |

### 慕寒智能（MuHan Intelligence）

- **API 来源可选**：设置中可在 **内置 API** 与 **自定义 API** 之间切换。
  - 内置 API：开箱即用，无需填写地址与密钥（编译期注入默认端点与模型）；
  - 自定义 API：兼容 OpenAI 格式，可填写任意 `POST {baseUrl}/chat/completions` 地址、密钥与模型，支持流式（SSE）回复，附「测试连接」。
- **每日额度**：使用内置 API 时，每天可发送 **5** 条消息，额度按自然日自动重置；用完后可在设置中切换为自定义 API 继续使用。
- **会话管理**：对话页右上角可 **新建会话**，**长按该按钮** 可在已有会话间切换。
- **本地存储**：对话历史按账号保存在本机（`filesDir/muhan-ai/`），仅发送至所选 API，可随时在设置或对话页清空。

设计文档：[登录向导](docs/features/onboarding-wizard.md) · [可选加密](docs/features/optional-e2ee.md) · [WebDAV 同步](docs/features/webdav-sync.md) · [架构总览](docs/architecture.md) · [路线图](docs/roadmap.md)

### WebDAV 同步怎么工作

- 导出核心整库备份（tar）→ 本地用**备份口令**加密（PBKDF2-HMAC-SHA256 120000 次 + AES-256-GCM，`NC1` 容器）→ `PUT` 到 `<你的 WebDAV>/nova-chat/latest-backup.ac`；
- 恢复时反向操作：`GET` → 解密 → 核心导入备份；
- 服务器只见密文；备份口令丢失则无法恢复（请妥善保存）。

## 版本与更新日志

各版本的更新日志统一维护在 [Releases](https://github.com/bilibiliHaoziyao/novachat/releases) 页面，本 README 不再重复记录。

## 项目结构

```
novachat/
├── apps/
│   ├── android/                       # Android 端（源自 deltachat-android，GPL-3.0）
│   │   ├── src/main/java/org/thoughtcrime/securesms/
│   │   │   ├── ClassicLoginActivity.java      # 邮箱登录向导
│   │   │   ├── WebDavSettingsActivity.java    # WebDAV 同步设置
│   │   │   ├── AboutActivity.java             # 关于页
│   │   │   ├── MuhanIntelligenceActivity.java # MuHan Intelligence AI 对话界面
│   │   │   ├── muhan/                         # AI 配置 / OpenAI 兼容客户端 / 本地多会话存储
│   │   │   └── connect/WebDavSyncManager.java # WebDAV 客户端 + 备份加解密
│   │   ├── src/main/res/values-night-v31/      # 深色莫奈取色调色板
│   │   ├── keystore/novachat-release.jks      # 发布签名密钥（CI 使用）
│   │   └── jni/deltachat-core-rust/           # Rust 核心（MPL-2.0）
│   └── desktop/                       # 桌面端源码（源自 deltachat-desktop，GPL-3.0，暂不构建发布）
├── docs/                              # 架构、三项改造设计文档、路线图、构建指南
├── scripts/fork-setup.sh              # 从上游重新组装开发树（可选）
└── .github/workflows/build-release.yml
```

## 本地构建

**Android**（需要 Android SDK 37 + NDK + Rust，详见 [docs/build-android.md](docs/build-android.md)）：

```bash
cd apps/android
bash scripts/install-toolchains.sh
bash scripts/ndk-make.sh arm64-v8a          # 64 位核心
bash scripts/ndk-make.sh armeabi-v7a        # 32 位核心
./gradlew assembleFossRelease               # 使用仓库内 keystore 签名
```

> 内置 API 的默认端点与模型在 `apps/android/build.gradle` 中定义；其密钥通过 `MUHAN_AI_API_KEY`（Gradle 属性或环境变量，CI 使用 Secret）在构建期注入，不会进入仓库。

## 已知限制

- 内置 API 每日限额 5 条消息，超出后需切换为自定义 API；
- WebDAV 同步为整库备份上传，无增量与冲突合并；Android 端 WebDAV 配置暂不持久化（每次需重新填写）；
- 仅内置四种语言（简中 / 繁中 / 英 / 日），其余语言未提供；
- APK 为自行签名（非公共 CA 证书），安装时需允许未知来源应用；
- 桌面端（Windows/macOS/Linux）源码保留但不再构建发布。

## 许可与致谢

本项目是 Delta Chat 生态的派生作品：

- 应用层代码（Android / 桌面端）：**GPL-3.0**（与上游 `deltachat-android`、`deltachat-desktop` 一致）；
- 核心代码（`apps/android/jni/deltachat-core-rust`）：**MPL-2.0**，保持上游许可不变。

完整声明见 [NOTICE.md](NOTICE.md)。感谢 Delta Chat 项目及全体贡献者：<https://github.com/deltachat>