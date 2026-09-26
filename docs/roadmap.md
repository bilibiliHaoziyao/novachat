# Nova Chat 路线图

> 目标：在保持 Delta Chat 核心能力的前提下，完成登录向导优化、可选端到端加密、WebDAV 账号同步三项改造，实现 Android 与 Windows 双端可用并开源发布。

## M0 —— 上游组装与构建基线（当前）

- [x] 建立协调仓库 `novachat`（文档 + 脚本）
- [ ] `scripts/fork-setup.sh` 拉取 `deltachat-core-rust` / `deltachat-android` / `deltachat-desktop` 并初始化子模块
- [ ] Android：完成一次 `assembleDebug` 构建
- [ ] Windows：完成 `deltachat-desktop` 本地 `npm start` 运行
- **验收**：双端能基于上游源码构建出可运行的基线应用

## M1 —— 登录向导优化

- [ ] 双端向导单页化重构（Android `OnboardingActivity` / Desktop `LoginScreen`）
- [ ] 邮箱服务商智能预设（Gmail / QQ邮箱 / 网易 / Outlook / iCloud 等）
- [ ] 实时"测试连接"与错误分级提示
- [ ] 从 WebDAV 导入已有账号的入口（与 M3 联动）
- **验收**：新用户在 4 个输入项内完成配置，常见服务商自动填充成功率 ≥ 90%

## M2 —— 端到端加密可关闭

- [ ] 核心：默认 `e2ee_enabled = 0`（Nova Chat 构建默认值）
- [ ] 核心：未开启 E2E 前不生成 Autocrypt 密钥
- [ ] 双端：设置页新增「端到端加密」开关与风险提示
- [ ] 双端：未加密会话"未加密"标识
- **验收**：新账号默认明文收发；开启 E2E 后恢复 Autocrypt 行为

## M3 —— WebDAV 账号同步

- [ ] 核心：`src/webdav/` 同步模块（清单 + 增量 + 冲突处理）
- [ ] 核心：敏感数据加密备份（密钥、配置）
- [ ] Android：WebDAV 设置页 + WorkManager 周期同步
- [ ] Windows：WebDAV 设置页 + 同步调度
- [ ] 登录向导集成「WebDAV 导入」
- **验收**：同一账号在双端配置一致；A 端改联系人，B 端同步后一致；密钥可从 WebDAV 恢复

## M4 —— 双端打包与发布

- [ ] Android：Release 签名、AAB 产出
- [ ] Windows：electron-builder NSIS 安装包
- [ ] 应用改名落地（Nova Chat / 新星聊，包名 `chat.nova.app` 等）
- [ ] GitHub Releases + 使用文档
- **验收**：双端安装包可安装、首次使用全流程顺畅

## 后续候选

- 消息级 WebDAV 备份（导出/导入 MBOX）
- 多账号 WebDAV 目录隔离
- 语言包：中/英
- CI（GitHub Actions）双端自动构建

## 协作说明

- 每个里程碑独立可交付，可并行推进 UI 与核心逻辑；
- 所有核心逻辑变更需附自动化测试（Rust `cargo test`）；
- 合并上游更新前，先核对三项改造对应的代码是否有冲突。
