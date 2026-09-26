# Nova Chat · 新星聊

> 基于 [Delta Chat](https://github.com/deltachat) 的即时通讯软件，支持 Android 与 Windows。
> Delta Chat 的核心理念——"以电子邮件协议为传输、无中心服务器"——被完整保留；Nova Chat 在此基础上重构了首次使用体验，并让加密与同步变得可选、可控。

Nova Chat（新星聊）围绕 Delta Chat 做了三项核心改造：

1. **优化首次使用登录向导** —— 单页化向导、邮箱服务商智能预设、实时连接测试，几步即可完成配置；
2. **端到端加密默认可关闭** —— 不再强制 E2E，随时可在设置中开启，未加密会话有清晰标识；
3. **账号支持 WebDAV 同步** —— 账号配置、联系人、密钥备份经 WebDAV（Nextcloud / 坚果云 / 自建 WebDAV 等）加密同步。

## 为什么基于 Delta Chat

- **成熟稳定**：Rust 核心 + 原生 Android / Electron 桌面客户端，多年生产验证；
- **无中心服务器**：直接使用 IMAP/SMTP 邮箱协议收发消息，与全球邮箱生态天然互通；
- **双端基础齐全**：`deltachat-core-rust`（核心引擎）、`deltachat-android`（Android）、`deltachat-desktop`（Windows/macOS/Linux）。

## 项目结构（本仓库）

```
novachat/
├── README.md
├── LICENSE               # GPL-3.0（应用层）；核心代码沿用 MPL-2.0，见 NOTICE.md
├── NOTICE.md             # 上游归属与许可声明
├── .gitignore
├── docs/
│   ├── architecture.md   # 架构总览与模块划分
│   ├── roadmap.md        # 里程碑路线图
│   ├── features/         # 三项核心改造的设计文档
│   │   ├── onboarding-wizard.md
│   │   ├── optional-e2ee.md
│   │   └── webdav-sync.md
│   ├── build-android.md  # Android 构建指南
│   └── build-windows.md  # Windows 构建指南
└── scripts/
    └── fork-setup.sh     # 一键拉取 Delta Chat 上游并组装开发树
```

> **仓库定位说明**：本仓库当前是**协调仓库**（文档 + 脚本 + 后续补丁），实际源码由 `scripts/fork-setup.sh` 从 Delta Chat 上游克隆到本地后改造，以保持仓库轻量、可随时跟踪上游更新。完整落地三个改造后，源码会逐步并入本仓库。

## 快速开始

### 1. 获取源码

```bash
bash scripts/fork-setup.sh
```

脚本会克隆 `deltachat-core-rust`、`deltachat-android`、`deltachat-desktop`，组装成 `core/`、`apps/android/`、`apps/desktop/` 开发树，并初始化子模块。

### 2. 构建 Android

见 [docs/build-android.md](docs/build-android.md)（需要 Android SDK/NDK + Rust 工具链）。

### 3. 构建 Windows

见 [docs/build-windows.md](docs/build-windows.md)（需要 Node.js + Rust 工具链，Electron 打包）。

## 三项核心改造

| 改造 | 设计文档 | 核心思路 |
|---|---|---|
| 登录向导优化 | [docs/features/onboarding-wizard.md](docs/features/onboarding-wizard.md) | 单页向导、服务商预设、实时连接测试 |
| 端到端加密可关闭 | [docs/features/optional-e2ee.md](docs/features/optional-e2ee.md) | 默认关闭 E2E，设置中一键开启，未加密标识 |
| WebDAV 账号同步 | [docs/features/webdav-sync.md](docs/features/webdav-sync.md) | 配置/联系人/密钥经 WebDAV 加密同步、增量冲突处理 |

## 路线图

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 上游源码组装、双端可构建基线 | 进行中 |
| M1 | 登录向导优化 | 规划中 |
| M2 | 端到端加密可关闭 | 规划中 |
| M3 | WebDAV 账号同步 | 规划中 |
| M4 | 双端打包、正式发布 | 规划中 |

详见 [docs/roadmap.md](docs/roadmap.md)。

## 许可与致谢

本项目是 Delta Chat 生态的派生作品：

- 应用层代码（Android / Desktop 客户端）：**GPL-3.0**（与上游 `deltachat-android`、`deltachat-desktop` 一致）；
- 核心代码（源自 `deltachat-core-rust`）：**MPL-2.0**（保持上游许可不变）。

完整声明见 [NOTICE.md](NOTICE.md)。
