# NOTICE — 上游归属与许可声明

Nova Chat（新星聊）基于以下上游项目派生。本项目保留上游版权声明与许可义务，特此声明。

## 上游项目

| 组件 | 上游仓库 | 许可证 | 在本项目中的用途 |
|---|---|---|---|
| 核心引擎 | <https://github.com/deltachat/deltachat-core-rust> | Mozilla Public License 2.0（MPL-2.0） | IMAP/SMTP 收发、Autocrypt 加密、联系人、配置管理 |
| Android 客户端 | <https://github.com/deltachat/deltachat-android> | GNU General Public License v3.0 | Android 应用 |
| 桌面客户端 | <https://github.com/deltachat/deltachat-desktop> | GNU General Public License v3.0 | Windows/macOS/Linux 桌面应用（Electron） |

## 许可说明

- 本仓库根目录 `LICENSE` 为 GPL-3.0 全文，适用于本仓库自有代码以及派生自 Android / Desktop 客户端（GPL-3.0）的代码。
- 派生自 `deltachat-core-rust` 的代码**保持 MPL-2.0 许可不变**；对该部分代码的修改与再分发需遵守 MPL-2.0 的源码可得性要求（对应源文件应保留 MPL-2.0 头注释，并在项目内提供完整许可文本）。
- 由 `scripts/fork-setup.sh` 组装出的 `core/`、`apps/` 各目录，必须保留上游各自的 `LICENSE` 文件与版权头。
- Delta Chat、Autocrypt 等名称与标识归其各自所有者所有，本项目不主张对其的任何所有权。

## 致谢

- Delta Chat 项目及全体贡献者：<https://github.com/deltachat>
- Autocrypt 规范：<https://autocrypt.org>
- 邮件协议生态（IMAP/SMTP 等）的所有实现与规范维护者
