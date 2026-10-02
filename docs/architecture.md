# Nova Chat 架构总览

## 1. 总体架构

Nova Chat 沿用 Delta Chat 的分层架构：Rust 核心负责协议，客户端只做 UI 与调度。

```
┌────────────────────────────┐   ┌─────────────────────────────┐
│  apps/android（Java）        │   │  apps/desktop（TypeScript）   │
│  UI · 向导 · 设置 · 通知      │   │  UI · 向导 · 设置 · Electron   │
└─────────────┬──────────────┘   └──────────────┬──────────────┘
              │ JSON-RPC（chat.delta.rpc.Rpc）    │ JSON-RPC（@deltachat/jsonrpc-client）
              ▼                                 ▼
┌─────────────────────────────────────────────────────────────┐
│  deltachat-core-rust（Rust 核心引擎，内嵌于 apps/android/jni/） │
│  IMAP/SMTP · Autocrypt · 联系人 · 配置 · imex（备份导入导出）    │
└─────────────────────────────────────────────────────────────┘
              │                                        │
              ▼                                        ▼
      IMAP / SMTP（任意邮箱服务商）              WebDAV（可选，账号备份同步）
```

- **无中心服务器**：消息收发全部走用户自己的邮箱，Nova Chat 不引入任何自建服务端；
- **核心引擎**：双端都通过 JSON-RPC 调用同一个 Rust 核心（桌面端为 `deltachat-rpc-server` 子进程，Android 为本地 JNI/JSON-RPC）；
- **WebDAV 同步**：实现位于**客户端层**（而非核心），复用核心的备份导出/导入能力（`exportBackup` / `importBackup`），把加密后的备份文件放到用户自己的 WebDAV 服务器上。

## 2. 三项改造的实现位置

| 改造 | 核心（Rust） | Android | 桌面端（Windows） |
|---|---|---|---|
| 登录向导优化 | 无需改动（复用 `configure` / `add_or_update_transport`） | [ClassicLoginActivity.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/ClassicLoginActivity.java) | [NovaWizard/index.tsx](file:///workspace/apps/desktop/packages/frontend/src/components/screens/NovaWizard/index.tsx) |
| 端到端加密默认开启 | [config.rs](file:///workspace/apps/android/jni/deltachat-core-rust/src/config.rs)（`force_encryption` 默认值 `1`） | ClassicLoginActivity（新账号写入 `force_encryption=1`）、ApplicationContext（既有账号一次性迁移）、PrivacyPreferenceFragment（开关） | 未同步（桌面端向导仍默认关闭，Settings/Advanced.tsx 提供开关） |
| WebDAV 账号同步 | 复用 `imex` 备份导出/导入 | [WebDavSyncManager.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/connect/WebDavSyncManager.java) + [WebDavSettingsActivity.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/WebDavSettingsActivity.java) | [nova/webdav-client.ts](file:///workspace/apps/desktop/packages/target-electron/src/nova/webdav-client.ts) + [nova/nova-sync.ts](file:///workspace/apps/desktop/packages/target-electron/src/nova/nova-sync.ts) + Settings/WebdavSync.tsx |

## 3. 桌面端模块划分（apps/desktop）

| 路径 | 职责 |
|---|---|
| `packages/frontend/src/components/screens/NovaWizard/` | 首次使用向导：服务商预设、连接测试、可选加密、可选 WebDAV |
| `packages/frontend/src/components/screens/NovaWizard/providerPresets.ts` | 服务商预设表（QQ/163/126/新浪/Gmail/Outlook/iCloud/Yahoo/自定义） |
| `packages/frontend/src/components/Settings/WebdavSync.tsx` | WebDAV 同步设置页（地址/账号/口令、测试、上传、恢复） |
| `packages/target-electron/src/nova/webdav-client.ts` | WebDAV 客户端（MKCOL/PUT/GET/DELETE）+ 备份容器加解密 |
| `packages/target-electron/src/nova/nova-sync.ts` | 备份导出 → 加密 → 上传；下载 → 解密 → 导入 |
| `packages/target-electron/src/ipc.ts` | 新增 `nova.webdav.*` IPC 通道（渲染进程无文件/网络权限，网络操作全部在主进程完成） |
| `packages/runtime/runtime.ts` | `novaWebdav` 运行时桥接接口（仅 Electron 实现，Web/Tauri 目标为 `undefined`） |

## 4. 数据流示例

**登录向导（普通邮箱）**：

1. 用户输入邮箱地址 → 前端按域名匹配服务商预设，自动填充 IMAP/SMTP 主机与端口；
2. 用户输入密码（或授权码）→ 可选择是否要求端到端加密（Android 默认开启，桌面端默认关闭）；
3. 前端调用 `addOrUpdateTransport(accountId, credentials)` → 核心执行配置并持续发出 `ConfigureProgress` 事件；
4. 向导实时展示进度；失败时返回第二步修改，成功后写入 `force_encryption`（Android 为 `1`）并进入 WebDAV 步骤。

**WebDAV 同步一次**：

1. 主进程调用核心 `exportBackup(accountId, tmpDir, null)` 得到 tar 备份；
2. 用用户口令派生密钥（PBKDF2-HMAC-SHA256，120000 次）做 AES-256-GCM 加密，得到 `NC1` 容器；
3. `MKCOL nova-chat/`（如不存在）后 `PUT nova-chat/latest-backup.ac`；
4. 恢复时反向执行：`GET` → 解密 → `importBackup(accountId, tar, null)`。

## 5. 设计约束

- **许可边界**：核心保持 MPL-2.0（`apps/android/jni/deltachat-core-rust/LICENSE`），Android/桌面端应用层保持 GPL-3.0；派生代码不改变对应部分许可；
- **不引入自建服务**：WebDAV 只是用户自带的同步介质，Nova Chat 不依赖任何专属服务器；
- **兼容上游**：改造尽量集中在客户端层，核心仅有"默认值"级别的小改动，便于持续合并上游更新；
- **双端一致的线上格式**：向导预填表、WebDAV 容器格式（`NC1` + AES-256-GCM + `nova-chat/latest-backup.ac`）在双端保持一致，Android 上传的备份可直接在 Windows 端恢复，反之亦然。