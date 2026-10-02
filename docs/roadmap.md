# Nova Chat 路线图

> 目标：在保持 Delta Chat 核心能力的前提下，完成登录向导优化、默认开启的端到端加密、WebDAV 账号同步三项改造，实现 Android 与 Windows 双端可用并开源发布。

## M0 —— 上游组装与构建基线 ✅

- [x] 建立仓库 `novachat`，把改造后的源码整体入库（`apps/android` 含内嵌 Rust 核心 `jni/deltachat-core-rust`、`apps/desktop` 为完整桌面端工作区）
- [x] CI 中完成 Android 核心编译（`scripts/ndk-make.sh arm64-v8a`）与 APK 构建
- [x] CI 中完成桌面端 `pnpm build` + electron-builder（NSIS + Portable）
- **验收**：双端能基于本仓库源码构建出可安装产物（见 M4 的 Release 资产）

## M1 —— 登录向导优化 ✅

- [x] Android：新增 [ClassicLoginActivity.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/ClassicLoginActivity.java)（邮箱/密码/显示名 + 高级折叠），[WelcomeActivity.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/WelcomeActivity.java) 提供入口
- [x] 桌面端：[NovaWizard](file:///workspace/apps/desktop/packages/frontend/src/components/screens/NovaWizard/index.tsx) 四步向导，欢迎页「使用邮箱登录」为主按钮
- [x] 服务商预设：QQ / 163 / 126 / 新浪 / Gmail / Outlook / iCloud / Yahoo（桌面端 [providerPresets.ts](file:///workspace/apps/desktop/packages/frontend/src/components/screens/NovaWizard/providerPresets.ts)）
- [x] 实时连接测试（复用核心 `ConfigureProgress` 事件）+ 失败返回修改
- [ ] 向导内嵌「从 WebDAV 恢复」入口（当前在备份恢复对话框中）
- **验收**：桌面端 1 次点击进入向导；常见服务商自动预填；失败可重试不丢输入

## M2 —— 端到端加密默认开启 ✅

- [x] 核心：[config.rs](file:///workspace/apps/android/jni/deltachat-core-rust/src/config.rs) 中 `force_encryption` 默认值显式声明为 `1`（v1.4）
- [x] Android：登录向导写入 `force_encryption=1`；既有账号首次启动一次性迁移开启；设置 → 隐私与安全 保留开关
- [x] 桌面端：向导开关（默认关）+ 设置 → 高级 → 端到端加密开关（未同步为默认开启）
- [x] 文案说明"默认开启，关闭后可与普通邮件客户端互通"
- [ ] 会话页"未加密"提示的强化（沿用上游标识）
- **验收**：新账号与既有账号默认强制加密；可随时开关

## M3 —— WebDAV 账号同步（v1 加密备份）✅

- [x] 容器格式（双端一致）：`NC1 + salt + iv + AES-256-GCM`，PBKDF2-HMAC-SHA256 120000 次
- [x] Android：[WebDavSyncManager](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/connect/WebDavSyncManager.java) + [WebDavSettingsActivity](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/WebDavSettingsActivity.java)
- [x] 桌面端：主进程 [webdav-client.ts](file:///workspace/apps/desktop/packages/target-electron/src/nova/webdav-client.ts) / [nova-sync.ts](file:///workspace/apps/desktop/packages/target-electron/src/nova/nova-sync.ts) + 设置页 [WebdavSync.tsx](file:///workspace/apps/desktop/packages/frontend/src/components/Settings/WebdavSync.tsx)
- [x] 桌面端"启动时自动同步"（后台静默上传，失败写日志）
- [x] 向导第 4 步可顺手配置并上传首个备份
- [ ] Android 端持久化 WebDAV 配置（当前每次填写）
- [ ] 增量同步（manifest + ETag 乐观锁）
- **验收**：双端可上传/恢复；服务器只见密文；不带 WebDAV 也能正常用

## M4 —— 双端打包与发布 🚧

- [x] GitHub Actions：`.github/workflows/build-release.yml`，打 `v*` 标签即构建双端产物
- [x] Android：CI 编译 arm64-v8a 核心 → `assembleFossRelease` → CI 自签 APK
- [x] Windows：electron-builder 产出 `Nova Chat-<version>-Setup.x64.exe` 与 Portable 版
- [x] 品牌落地：应用名 `Nova Chat 新星聊`、Electron appId `chat.nova.desktop.electron`、托盘与窗口标题
- [ ] 应用包名与 Android `applicationId` 改为 Nova 专属（当前仍沿用 `com.b44t.messenger`，与 Delta Chat 不能共存安装）
- [ ] 代码签名（Windows 证书 / Android 正式密钥）
- **验收**：Release 页面同时提供 APK 与 Windows 安装包

## 后续候选

- 核心内增量同步模块（清单、冲突处理、行级联系人合并）
- Android：WorkManager 周期同步；WebDAV 配置持久化
- 自动更新（electron-updater / Android 应用内更新提示）
- 多语言：补齐 `nova_*` 文案到其他语言包
- 跨端互操作测试（Android ↔ Windows 备份互恢复）

## 协作说明

- 每个里程碑独立可交付；
- 核心改动保持"配置驱动、最小侵入"，便于合并上游更新；
- 修改双端共用的线上格式（如 WebDAV 容器）时必须同步双端并更新 [webdav-sync.md](./features/webdav-sync.md)。