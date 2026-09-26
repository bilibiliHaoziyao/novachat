# 改造三：账号支持 WebDAV 同步

## 1. 目标

让用户的 Nova Chat 账号（**账号配置、联系人、密钥备份**）可以经任意 WebDAV 服务（Nextcloud、坚果云、自建 WebDAV 等）在多台设备间同步与恢复。消息本身仍由邮箱协议（IMAP）承载，**不经过 WebDAV**。

## 2. 同步范围（边界）

| 数据 | 是否同步 | 说明 |
|---|---|---|
| 账号配置（IMAP/SMTP、显示名、头像） | ✅ | 加密后上传 |
| 联系人列表 | ✅ | 加密后上传，增量合并 |
| Autocrypt 密钥备份 | ✅ | 加密后上传，用于换机恢复 |
| 设置项（含 `e2ee_enabled`） | ✅ | 随账号配置一起 |
| 消息缓存/本地索引 | ❌ | 由 IMAP 重新同步，不占 WebDAV |
| 聊天记录全文 | ❌ | 邮箱内本来就有，无需复制 |

## 3. 服务器目录布局

```
https://<webdav-server>/nova-chat/
└── <account-id>（由邮箱地址哈希生成，如 sha256 前 16 位）/
    ├── manifest.json            # 同步清单（版本、文件哈希、时间戳）——明文，不含敏感内容
    ├── account.json.enc         # 账号配置（加密）
    ├── contacts.csv.enc         # 联系人（加密）
    ├── settings.json.enc        # 设置项（加密）
    └── keys/
        ├── <key-id>.asc.enc     # 密钥文件（加密）
        └── ...
```

## 4. manifest.json 结构

```json
{
  "schema_version": 1,
  "account_id": "a1b2c3d4e5f60718",
  "updated_at": "2026-09-26T04:30:00Z",
  "device": "nova-android",
  "files": {
    "account.json.enc": { "version": 12, "sha256": "…", "mtime": "…" },
    "contacts.csv.enc":  { "version": 34, "sha256": "…", "mtime": "…" },
    "settings.json.enc": { "version": 3,  "sha256": "…", "mtime": "…" },
    "keys/a1.asc.enc":   { "version": 1,  "sha256": "…", "mtime": "…" }
  }
}
```

## 5. 同步算法（增量 + 冲突处理）

```
每次同步（手动 / 周期 / 登录时）：
1. GET manifest.json（带 If-None-Match，利用 ETag 缓存）
2. 本地无 manifest → 全量拉取（首次从 WebDAV 恢复）
3. 逐文件对比 version / sha256：
   - 本地新 → PUT 上传（version + 1，更新 manifest 后 PUT manifest）
   - 远端新 → GET 下载，覆盖本地
   - 两边都变（冲突）→ 按文件类型处理：
     * 配置类：last-write-wins（取 mtime 新者），旧版本存 *.conflict-<ts>
     * 联系人：行级合并（按 email 去重，取各自较新修改），生成合并后版本
     * 密钥：双份共存（不同 key-id），不覆盖
4. 更新本地清单缓存
```

**并发安全**：用 `manifest` 的乐观锁——上传前先 `PROPFIND` 检查远端 `manifest.json` 的 `ETag/Last-Modified` 是否与本地缓存一致；不一致则先拉取最新并重算，避免双写丢失。

## 6. 加密方案（信任边界：不信任服务器）

- 用户提供 **同步口令**（首次配置时设置，可离线生成恢复码）；
- 派生密钥：`Argon2id(passphrase, salt) → 32B 主密钥`；
- 每个文件独立随机 nonce，用 **XChaCha20-Poly1305**（或 AES-256-GCM）加密，密文格式：
  ```
  [magic "NC1" (3B)] [salt (16B)] [nonce (24B)] [ciphertext] [tag (16B)]
  ```
- **服务器永远拿不到明文**：配置、联系人、密钥均加密存储；`manifest.json` 只含哈希与版本，不含敏感内容；
- 口令丢失 = 数据不可恢复（UI 需在首次配置时明确警示并提供恢复码备份）。

## 7. 核心引擎实现（core/src/webdav/，新增，Rust）

```
src/webdav/
├── mod.rs          # 模块入口与对外 API
├── client.rs       # WebDAV 客户端：MKCOL/PROPFIND/GET/PUT/DELETE（reqwest 实现）
├── manifest.rs     # 清单读写、ETag 乐观锁
├── sync.rs         # 同步状态机（上节算法）
├── crypto.rs       # Argon2id + XChaCha20-Poly1305 封装
└── scope.rs        # 账号配置/联系人/密钥的序列化与版本化
```

**对外 API（供双端调用）**：

```rust
pub struct WebDavConfig {
    pub url: String,
    pub username: Option<String>,
    pub password: Option<String>,   // WebDAV 凭据（仅用于同步，不入库明文）
    pub sync_passphrase: String,    // 用于加密备份（可空 = 只同步明文项）
}

pub fn webdav_configure(ctx: &Context, cfg: &WebDavConfig) -> Result<(), Error>;
pub fn webdav_sync_once(ctx: &Context, cfg: &WebDavConfig) -> Result<SyncReport, Error>;
pub fn webdav_export_keys(ctx: &Context, cfg: &WebDavConfig) -> Result<(), Error>;
pub fn webdav_restore(ctx: &Context, cfg: &WebDavConfig) -> Result<(), Error>; // 登录向导/换机用
```

**核心逻辑草图（sync.rs 骨架）**：

```rust
pub fn sync_once(ctx: &Context, cfg: &WebDavConfig) -> Result<SyncReport> {
    let client = WebDavClient::new(&cfg.url, cfg.username.as_deref(), cfg.password.as_deref())?;
    client.ensure_root(&["nova-chat", &account_id(ctx)])?;

    let remote = client.get_manifest()?;                       // Option<Manifest>
    let local = local_manifest(ctx)?;                          // 本地缓存
    let plan = diff_plans(&local, &remote, &ctx)?;             // 逐文件计划

    for action in plan {
        match action {
            Plan::Upload(path, data) => {
                let enc = encrypt(cfg, &data)?;
                client.put(path, &enc)?;                       // PUT 加密后内容
            }
            Plan::Download(path) => {
                let enc = client.get(path)?;
                let data = decrypt(cfg, &enc)?;
                apply(ctx, &path, &data)?;                     // 写回本地
            }
            Plan::Conflict(path, local, remote) => resolve(ctx, &path, local, remote)?,
        }
    }
    client.put_manifest(&merged_manifest)?;                    // 乐观锁已校验
    save_local_manifest(ctx, &merged_manifest)?;
    Ok(report)
}
```

## 8. 双端 UI 与调度

### Android（apps/android/）

- **设置 → 同步 → WebDAV**：URL / 账号 / 口令 / 「立即同步」/ 上次同步时间；
- **WebDavSyncService**（WorkManager）：`PeriodicWorkRequest`（默认每 6 小时，可配置；仅在 Wi-Fi 与充电时可选项）；
- **登录向导**：提供「从 WebDAV 恢复账号」入口（调用 `webdav_restore`，M3 与 M1 联动）。

### Windows 桌面端（apps/desktop/）

- **设置 → 同步 → WebDAV**：与 Android 相同字段；
- 调度：主进程定时器 + 应用启动/退出时各同步一次；
- 登录页同样提供「从 WebDAV 恢复」。

## 9. 安全模型小结

| 威胁 | 缓解 |
|---|---|
| WebDAV 服务器窃取数据 | 全敏感数据端到端加密（口令派生密钥），服务器只见密文 |
| 传输被窃听 | 强制 HTTPS（明文 HTTP 直接拒绝配置） |
| 同步口令泄露 | 密钥由 Argon2id 派生，离线可离线爆破但成本高；提示用户使用强口令并保管恢复码 |
| 恶意覆盖 | 乐观锁 + 冲突保留 `.conflict-*` 副本 |
| 账号标识泄露 | 目录名用邮箱哈希，不出现明文邮箱 |

## 10. 验收标准

- [ ] 配置 WebDAV 后，双端账号配置/联系人/设置一致；
- [ ] A 端改联系人，B 端同步后一致；冲突时生成 `.conflict-*` 不丢数据；
- [ ] 换机场景：新设备经 WebDAV 恢复账号并解开密钥；
- [ ] 服务器端存储的文件均为密文，无法还原明文；
- [ ] 无 WebDAV 时不影响任何原有功能（完全可选特性）。

## 11. 依赖与许可注意

- 加密：`argon2` / `chacha20poly1305`（Rust crates，宽松许可，需登记进核心依赖清单）；
- HTTP：复用核心已有 `reqwest` 依赖；
- 新代码遵循所在组件许可（core → MPL-2.0），上游组件许可不变。
