# Nova Chat · 新星聊

> 基于 [Delta Chat](https://github.com/deltachat) 的即时通讯软件，支持 Android 与 Windows。
> Delta Chat 的核心理念——"以电子邮件协议为传输、无中心服务器"——被完整保留；Nova Chat 在此基础上重构了首次使用体验，并让加密与账号同步变得可选、可控。

Nova Chat（新星聊）围绕 Delta Chat 做了三项核心改造：

1. **优化首次使用登录向导** —— 邮箱登录升级为主入口，服务商智能预设，实时连接测试，失败可返回修改；
2. **端到端加密默认关闭** —— 不再强制 E2E，双端设置中随时开启，未加密会话有明确标识；
3. **账号支持 WebDAV 同步** —— 账号备份经 WebDAV（Nextcloud / 坚果云 / 自建服务等）加密上传，换机可一键恢复。

## 下载

前往 [Releases](https://github.com/bilibiliHaoziyao/novachat/releases) 下载：

| 平台 | 产物 | 说明 |
|---|---|---|
| Android | `NovaChat-arm64-v8a.apk` | arm64-v8a，CI 自签名（Android 8.0+，`minSdk 21`） |
| Windows | `Nova Chat-<版本>-Setup.x64.exe` | NSIS 安装包（另有 Portable 免安装版） |

> 产物由 GitHub Actions 在打 `v*` 标签时自动构建（见 [.github/workflows/build-release.yml](.github/workflows/build-release.yml)）。

## 三项改造的实现状态

| 改造 | Android | Windows 桌面端 |
|---|---|---|
| 登录向导优化 | [ClassicLoginActivity.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/ClassicLoginActivity.java)：邮箱/密码/显示名 + 高级折叠，欢迎页直达 | [NovaWizard](file:///workspace/apps/desktop/packages/frontend/src/components/screens/NovaWizard/index.tsx)：四步向导（邮箱 → 安全 → 连接测试 → 可选同步） |
| 端到端加密可选 | 新账号写入 `force_encryption=0`，高级设置保留开关 | 向导开关（默认关）+ 设置 → 高级 → 端到端加密 |
| WebDAV 账号同步 | [WebDavSyncManager.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/connect/WebDavSyncManager.java) + [WebDavSettingsActivity.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/WebDavSettingsActivity.java) | 主进程 [nova/](file:///workspace/apps/desktop/packages/target-electron/src/nova) + 设置页 [WebdavSync.tsx](file:///workspace/apps/desktop/packages/frontend/src/components/Settings/WebdavSync.tsx)（含"启动时自动同步"） |

设计文档：[登录向导](docs/features/onboarding-wizard.md) · [可选加密](docs/features/optional-e2ee.md) · [WebDAV 同步](docs/features/webdav-sync.md) · [架构总览](docs/architecture.md) · [路线图](docs/roadmap.md)

### WebDAV 同步怎么工作

- 导出核心整库备份（tar）→ 本地用**备份口令**加密（PBKDF2-HMAC-SHA256 120000 次 + AES-256-GCM，`NC1` 容器）→ `PUT` 到 `<你的 WebDAV>/nova-chat/latest-backup.ac`；
- 恢复时反向操作：`GET` → 解密 → 核心导入备份；
- **双端容器格式完全一致**，Android 上传的备份可在 Windows 端恢复，反之亦然；
- 服务器只见密文；备份口令丢失则无法恢复（请妥善保存）。

## 项目结构

```
novachat/
├── apps/
│   ├── android/                       # Android 端（源自 deltachat-android，GPL-3.0）
│   │   ├── src/main/java/org/thoughtcrime/securesms/
│   │   │   ├── ClassicLoginActivity.java      # 新增：邮箱登录向导
│   │   │   ├── WebDavSettingsActivity.java    # 新增：WebDAV 同步设置
│   │   │   └── connect/WebDavSyncManager.java # 新增：WebDAV 客户端 + 备份加解密
│   │   └── jni/deltachat-core-rust/           # Rust 核心（MPL-2.0，force_encryption 默认值已改）
│   └── desktop/                       # Windows/macOS/Linux 桌面端（源自 deltachat-desktop，GPL-3.0）
│       ├── packages/frontend/src/components/screens/NovaWizard/   # 新增：四步登录向导
│       ├── packages/frontend/src/components/Settings/WebdavSync.tsx
│       └── packages/target-electron/src/nova/                     # 新增：主进程 WebDAV 同步
├── docs/                              # 架构、三项改造设计文档、路线图、构建指南
├── scripts/fork-setup.sh              # 从上游重新组装开发树（可选）
└── .github/workflows/build-release.yml
```

## 本地构建

**Android**（需要 Android SDK 37 + NDK + Rust，详见 [docs/build-android.md](docs/build-android.md)）：

```bash
cd apps/android
bash scripts/install-toolchains.sh
bash scripts/ndk-make.sh arm64-v8a          # 编译 Rust 核心
./gradlew -PABI_FILTER=arm64-v8a assembleFossRelease
```

**Windows 桌面端**（Node 22 + pnpm，详见 [docs/build-windows.md](docs/build-windows.md)）：

```bash
cd apps/desktop
pnpm install
pnpm --filter=@deltachat-desktop/target-electron build
cd packages/target-electron
pnpm run pack:generate_config && pnpm run pack:patch-node-modules
pnpm electron-builder --win nsis portable --publish never
```

## 已知限制

- Android `applicationId` 仍为 `com.b44t.messenger`，与 Delta Chat 无法在同一设备共存安装（计划改为 Nova 专属包名）；
- WebDAV 同步为 v1：整库备份上传，无增量与冲突合并；Android 端尚未持久化 WebDAV 配置（每次需重新填写）；
- 安装包未做代码签名（Android 使用 CI 自签密钥，Windows 未签名，首次安装可能有系统提示）；
- 除中/英外的语言包尚未包含 `nova_*` 文案（会回退显示英文）。

## 许可与致谢

本项目是 Delta Chat 生态的派生作品：

- 应用层代码（Android / 桌面端）：**GPL-3.0**（与上游 `deltachat-android`、`deltachat-desktop` 一致）；
- 核心代码（`apps/android/jni/deltachat-core-rust`）：**MPL-2.0**，保持上游许可不变。

完整声明见 [NOTICE.md](NOTICE.md)。感谢 Delta Chat 项目及全体贡献者：<https://github.com/deltachat>