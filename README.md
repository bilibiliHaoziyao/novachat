# Nova Chat · 新星聊

> 基于 [Delta Chat](https://github.com/deltachat) 的邮箱即时通讯软件，当前版本 **1.2**，仅发布 Android。
> Delta Chat 的核心理念——"以电子邮件协议为传输、无中心服务器"——被完整保留；Nova Chat 在此基础上重构了首次使用体验，并让加密与账号同步变得可选、可控。

Nova Chat（新星聊）围绕 Delta Chat 做了三项核心改造：

1. **优化首次使用登录向导** —— 邮箱登录升级为主入口，服务商智能预设（QQ / 163 / Gmail / Outlook 等），实时连接测试，失败可返回修改；
2. **端到端加密默认关闭** —— 不再强制 E2E，设置中随时开启，可与普通邮件客户端互通；
3. **账号支持 WebDAV 同步** —— 账号备份经 WebDAV（Nextcloud / 坚果云 / 自建服务等）加密上传，换机可一键恢复。

## 下载

前往 [Releases](https://github.com/bilibiliHaoziyao/novachat/releases) 下载最新版：

| 平台 | 产物 | 说明 |
|---|---|---|
| Android | `NovaChat-universal.apk` | 32 位 + 64 位二合一（armeabi-v7a / arm64-v8a），Android 5.0+（`minSdk 21`），仓库密钥固定签名，跨版本可直接覆盖安装 |

> 产物由 GitHub Actions 在打 `v*` 标签时自动构建（见 [.github/workflows/build-release.yml](.github/workflows/build-release.yml)）。
> 桌面端源码保留在 `apps/desktop/`，但自 v0.2.0 起已停止构建与发布。

## 主要特性

| 特性 | 说明 |
|---|---|
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

设计文档：[登录向导](docs/features/onboarding-wizard.md) · [可选加密](docs/features/optional-e2ee.md) · [WebDAV 同步](docs/features/webdav-sync.md) · [架构总览](docs/architecture.md) · [路线图](docs/roadmap.md)

### WebDAV 同步怎么工作

- 导出核心整库备份（tar）→ 本地用**备份口令**加密（PBKDF2-HMAC-SHA256 120000 次 + AES-256-GCM，`NC1` 容器）→ `PUT` 到 `<你的 WebDAV>/nova-chat/latest-backup.ac`；
- 恢复时反向操作：`GET` → 解密 → 核心导入备份；
- 服务器只见密文；备份口令丢失则无法恢复（请妥善保存）。

## 更新日志

### 1.2（2026-09-30）

- **深色模式适配莫奈取色**：新增 `values-night-v31` 深色调色板，深色主题下的工具栏、状态栏与整体配色跟随壁纸取色（使用调色板中较亮的色阶，保证深色背景下的可读性）；浅色模式取色保持不变；
- **关于页新增 "Powered by Delta Chat"**：在开发者信息下方展示对上游项目的致谢；
- **移除首次登录设备消息中的欢迎图片**：新账号的设备消息不再发送欢迎图片，仅保留文字欢迎消息；
- 版本号更新为 1.2（`versionCode 1200`）。

### 1.1（2026-09-27）

- 应用配色（工具栏 / 标签栏等）跟随莫奈取色；
- 更换应用图标并调小图标缩放比例，软件内所有图标替换为 Nova Chat 图标；
- 进入软件时显示后台收取消息指南（开启即时传送、后台加锁、允许自启动）；
- 初次登录在设备消息中新增账号备份提示；
- 包名改为 `com.muhan.chat`（可与 Delta Chat 共存安装）；
- 发布签名密钥入库（`apps/android/keystore/novachat-release.jks`），CI 固定签名。

### 1.0（2026-09-27）

- 全新应用图标；
- 默认聊天背景改为纯色，深色模式黑色 / 浅色模式白色，支持跟随系统；
- 分享邀请链接时提供快捷分享至 QQ / WeChat / 复制链接；
- 新增莫奈取色（默认开启），聊天背景随壁纸取色；
- 关于页新增开发者描述。

### 0.2.0（2026-09-26）

- 修复"使用自己邮箱"界面无法使用软件控件返回、状态栏不沉浸的问题；
- 新增软件内选择语言；
- 语言精简为 简体中文 / 繁體中文 / English / 日本語；
- 全局品牌替换：软件内 Delta Chat 字样改为 Nova Chat（简体中文界面显示"新星聊"）；
- 移除"发送统计数据给 Delta Chat 开发者"，移除设置页的捐赠与帮助，新增关于页；
- 停止 Windows 版本构建，Android 改为同时输出 32 位 + 64 位。

### 0.1.0（2026-09-26）

- 首个开源版本，三项核心改造全部落地：登录向导优化、端到端加密默认关闭、WebDAV 账号同步；
- 提供 Android（arm64）与 Windows（NSIS 安装包 / 便携版）双端产物。

## 项目结构

```
novachat/
├── apps/
│   ├── android/                       # Android 端（源自 deltachat-android，GPL-3.0）
│   │   ├── src/main/java/org/thoughtcrime/securesms/
│   │   │   ├── ClassicLoginActivity.java      # 邮箱登录向导
│   │   │   ├── WebDavSettingsActivity.java    # WebDAV 同步设置
│   │   │   ├── AboutActivity.java             # 关于页
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

## 已知限制

- WebDAV 同步为整库备份上传，无增量与冲突合并；Android 端 WebDAV 配置暂不持久化（每次需重新填写）；
- 仅内置四种语言（简中 / 繁中 / 英 / 日），其余语言未提供；
- APK 为自行签名（非公共 CA 证书），安装时需允许未知来源应用；
- 桌面端（Windows/macOS/Linux）源码保留但不再构建发布。

## 许可与致谢

本项目是 Delta Chat 生态的派生作品：

- 应用层代码（Android / 桌面端）：**GPL-3.0**（与上游 `deltachat-android`、`deltachat-desktop` 一致）；
- 核心代码（`apps/android/jni/deltachat-core-rust`）：**MPL-2.0**，保持上游许可不变。

完整声明见 [NOTICE.md](NOTICE.md)。感谢 Delta Chat 项目及全体贡献者：<https://github.com/deltachat>
