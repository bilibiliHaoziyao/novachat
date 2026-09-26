# 改造三：账号支持 WebDAV 同步

## 1. 目标

把 Nova Chat 账号（账号配置、联系人、密钥、消息库）备份后，同步到用户自己的 WebDAV 服务器（Nextcloud、坚果云、自建 WebDAV 等），实现**换机恢复**与**多设备兜底**。消息本身仍由邮箱协议（IMAP）承载，**不经过 WebDAV**。

## 2. 方案（v1：加密备份文件）

v1 不引入增量同步协议，而是复用核心的整库备份能力，在外面套一层"本地加密 + WebDAV 存取"：

```
导出：核心 exportBackup(accountId) → tar 备份（明文，仅存在于本机临时目录）
      → AES-256-GCM 加密（口令派生密钥）→ NC1 容器
      → WebDAV: MKCOL nova-chat/  →  PUT nova-chat/latest-backup.ac

恢复：WebDAV GET nova-chat/latest-backup.ac → 解密为 tar
      → 核心 importBackup(accountId, tar)
```

### 2.1 容器格式（双端一致）

| 字段 | 长度 | 说明 |
|---|---|---|
| magic | 3 B | 固定 `NC1` |
| salt | 16 B | 随机，PBKDF2 用 |
| iv | 12 B | 随机，GCM nonce |
| ciphertext + tag | 变长 | AES-256-GCM 密文与 16 B 认证标签 |

- 密钥派生：`PBKDF2-HMAC-SHA256(passphrase, salt, 120000)` → 256 bit 密钥；
- 口令错误时解密失败（GCM 认证失败），不会产生半截数据；
- 服务器只能看到密文与文件大小；目录名与文件名不含邮箱等个人信息。

### 2.2 实现位置

| 端 | 文件 | 说明 |
|---|---|---|
| Android | [WebDavSyncManager.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/connect/WebDavSyncManager.java) | `HttpURLConnection` 实现 MKCOL/PUT/GET；`javax.crypto` 实现 NC1 加解密 |
| Android | [WebDavSettingsActivity.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/WebDavSettingsActivity.java) | 设置 → WebDAV 同步：地址/账号/密码/备份口令 + 「立即备份」「从云端恢复」 |
| 桌面端 | [nova/webdav-client.ts](file:///workspace/apps/desktop/packages/target-electron/src/nova/webdav-client.ts) | 主进程 `fetch` 实现 MKCOL/PUT/GET/DELETE + 连接测试；`node:crypto` 实现同一容器格式 |
| 桌面端 | [nova/nova-sync.ts](file:///workspace/apps/desktop/packages/target-electron/src/nova/nova-sync.ts) | 编排"导出→加密→上传""下载→解密→导入"；设置持久化到 `nova-webdav.json`（0o600） |
| 桌面端 | [ipc.ts](file:///workspace/apps/desktop/packages/target-electron/src/ipc.ts) + `runtime-electron/` | `nova.webdav.*` IPC 通道与渲染进程桥接（`runtime.novaWebdav`） |
| 桌面端 | [Settings/WebdavSync.tsx](file:///workspace/apps/desktop/packages/frontend/src/components/Settings/WebdavSync.tsx) | 设置 → WebDAV 同步：字段、测试连接、立即上传、从云端恢复、启动时自动同步 |
| 桌面端 | [NovaWizard/index.tsx](file:///workspace/apps/desktop/packages/frontend/src/components/screens/NovaWizard/index.tsx) | 向导第 4 步可顺手配置 WebDAV 并立即上传首个备份 |

> 双端容器格式一致：Android 上传的备份可以在 Windows 端恢复，反之亦然。

## 3. 使用说明

1. 在 WebDAV 服务器上使用**应用密码**（不要用主账号密码）；
2. 填写服务器地址（例如 Nextcloud：`https://cloud.example.com/remote.php/dav/files/<用户名>/`）、用户名、密码；
3. 设置**备份口令**：它决定云端备份能否解开，丢失不可恢复；
4. 点「测试连接」确认可写（会创建 `nova-chat/` 目录并写探针文件）；
5. 「立即上传备份」；在另一台设备上登录同一账号后点「从云端恢复」。

## 4. 安全模型

| 威胁 | 缓解 |
|---|---|
| WebDAV 服务器被入侵 | 备份在本地加密后才上传，服务器只见密文 |
| 传输被窃听 | 建议使用 `https://`；桌面端地址需以 `http(s)://` 开头 |
| 口令离线爆破 | PBKDF2-SHA256 120000 次迭代；请使用强口令 |
| 误覆盖 | 恢复前有二次确认；恢复会以云端备份覆盖当前账号数据（核心 importBackup 语义） |

## 5. 已知限制 / 后续演进

- **Android 端尚未持久化 WebDAV 配置**：当前需每次在设置页重新填写；后续接入 `SharedPreferences`（或核心配置的私有扩展）保存；
- **无增量同步**：每次都是整库备份（受限于 `imex` 的整库导出）；后续可改为按文件清单 + ETag 乐观锁的增量方案（目录下 `manifest.json` + 分类文件）；
- **无自动定时同步**：桌面端已提供"启动时自动同步"开关；Android 端可接 WorkManager 周期任务；
- **原计划的核心内实现（Argon2id + XChaCha20-Poly1305、行级联系人合并）** 未采用：v1 优先选择"与上游备份机制兼容、双端实现一致"的简单方案。

## 6. 验收标准

- [x] 双端均可配置 WebDAV 并完成"上传 → 云端存在密文文件"；
- [x] 双端均可从云端恢复账号数据；
- [x] 服务器上不存在明文备份，口令错误时解密失败；
- [x] 无 WebDAV 时不影响任何原有功能（完全可选）；
- [ ] 跨端互操作的人工验证清单（Android ↔ Windows）纳入发布测试。