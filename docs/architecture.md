# Nova Chat 架构总览

## 1. 总体架构

Nova Chat 沿用 Delta Chat 的分层架构，三层分离、UI 与核心解耦：

```
┌────────────────────────────┐   ┌────────────────────────────┐
│  apps/android（Java/Kotlin） │   │  apps/desktop（TypeScript）  │
│  UI · 向导 · 设置 · 通知     │   │  UI · 向导 · 设置 · Electron  │
└─────────────┬──────────────┘   └──────────────┬─────────────┘
              │ JNI（deltachat-ffi）             │ JSON-RPC（deltachat/jsonrpc）
              ▼                                 ▼
┌─────────────────────────────────────────────────────────────┐
│                 core/（Rust 核心引擎）                          │
│  deltachat-core-rust：IMAP/SMTP · Autocrypt · 联系人 · 配置     │
│  + nova 扩展：webdav 同步模块（新增）                             │
└─────────────────────────────────────────────────────────────┘
              │
              ▼
         IMAP / SMTP（任意邮箱服务商）
         WebDAV（可选，账号配置/联系人/密钥备份同步）
```

- **无中心服务器**：消息收发走用户自己的邮箱（IMAP/SMTP），Nova Chat 不引入任何自建服务端；
- **核心引擎**：所有协议、加密、账号逻辑集中在 Rust 核心，双端共享；
- **WebDAV 同步**：作为"增值层"挂在核心之上，仅同步账号配置/联系人/密钥备份，不承载消息。

## 2. 模块划分

### 2.1 核心引擎（core/，源自 deltachat-core-rust，MPL-2.0）

| 模块 | 职责 |
|---|---|
| `src/accounts.rs` | 多账号管理、账号配置（IMAP/SMTP 参数） |
| `src/imap/`、`src/smtp/` | 邮件收发协议实现 |
| `src/autocrypt.rs`、`src/keyring.rs` | Autocrypt 端到端加密、密钥环管理 |
| `src/contact.rs` | 联系人管理 |
| `src/config.rs` | 配置键与默认值（含 `e2ee_enabled`） |
| `src/imex.rs` | 密钥/账号导入导出 |
| `src/webdav/`（**Nova Chat 新增**） | WebDAV 同步客户端：清单、增量、加密备份 |

### 2.2 Android 客户端（apps/android/，源自 deltachat-android，GPL-3.0）

| 文件/目录（以 fork 后为准） | 职责 |
|---|---|
| `OnboardingActivity`（及向导 Fragment） | 首次使用登录向导（Nova Chat 重点改造） |
| `SettingsActivity` | 设置页（新增：端到端加密开关、WebDAV 同步入口） |
| `WebDavSyncService`（**新增**） | 后台周期同步（WorkManager） |
| `jni/` | Rust 核心的 JNI 绑定（deltachat-ffi） |

### 2.3 桌面客户端（apps/desktop/，源自 deltachat-desktop，GPL-3.0）

| 文件/目录（以 fork 后为准） | 职责 |
|---|---|
| `src/renderer/components/Login/` | 登录/向导界面（Nova Chat 重点改造） |
| `src/renderer/components/Settings/` | 设置页（新增：E2E 开关、WebDAV 同步） |
| `src/main/` | Electron 主进程、核心进程管理 |

## 3. 双端共享的改造点

三项核心改造中，**端到端加密可关闭**与 **WebDAV 同步**的协议与业务逻辑全部下沉到核心引擎（Rust），双端只做 UI 与调度，保证行为一致、避免双端逻辑漂移：

- 可选 E2E：核心提供 `e2ee_enabled` 配置与密钥生成门控，双端只暴露开关与状态标识；
- WebDAV 同步：核心提供同步 API（同步、冲突解决、加密），双端提供设置页与后台调度。

## 4. 数据流示例

**发送一条消息（关闭 E2E 场景）**：
1. UI 调用核心 `dc_send_msg`；
2. 核心检查 `e2ee_enabled` 与收件人密钥：未启用则明文发送（RFC 2822 标准邮件）；
3. 核心经 SMTP 投递，经 IMAP 同步回执/回信。

**WebDAV 同步一次（周期任务）**：
1. 核心读取本地变更（设置/联系人/密钥版本）；
2. 经 WebDAV `PROPFIND` 获取远端清单与 ETag；
3. 按版本/ETag 增量拉取或上传，敏感文件解密/加密后落盘；
4. 更新本地清单，记录冲突（若有）。

## 5. 设计约束

- **许可边界**：core 保持 MPL-2.0，应用层 GPL-3.0，派生代码不得改变对应部分许可；
- **不引入自建服务**：WebDAV 只是"自带钥匙串"的同步介质，不依赖任何 Nova Chat 专属服务器；
- **兼容上游**：尽量以配置/插件方式改造，避免对核心大改，便于持续合并上游更新。
