# 改造一：优化首次使用登录向导

## 1. 现状与问题

Delta Chat 的首次使用流程以"创建即时账号（chatmail）"为默认路径，新用户若想用自己的邮箱，需要多点几层才能找到经典登录入口；且缺少服务商预设与友好的失败重试路径。

| 问题 | 表现 |
|---|---|
| 邮箱登录入口太深 | 欢迎页 → 创建即时账号 → 使用其它服务器 → 经典登录，共 4 次点击 |
| 服务商适配靠猜 | 依赖核心自动探测，失败后用户面对一堆 IMAP/SMTP 选项无从下手 |
| 失败后无路可退 | 连接失败只弹出错误提示，用户需重新填一遍 |
| 加密无解释 | 用户不清楚"是否必须加密"、能否与普通邮件客户端互通 |

## 2. 目标体验

- 邮箱登录提升为欢迎页的**第一个主按钮**（1 次点击进入向导）；
- 常见服务商（QQ/163/126/新浪/Gmail/Outlook/iCloud/Yahoo）**输入邮箱即自动预填** IMAP/SMTP；
- 连接过程**实时展示进度**，失败时可一键返回上一步修改（保留已填内容）；
- 端到端加密**默认关闭**且解释清楚（见 [optional-e2ee.md](./optional-e2ee.md)）；
- 登录成功后可选配置 WebDAV 同步（见 [webdav-sync.md](./webdav-sync.md)）。

## 3. 实现

### 3.1 Android（apps/android）

- [ClassicLoginActivity.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/ClassicLoginActivity.java)：新增「经典邮箱登录」单页表单
  - 字段：邮箱、密码、显示名（可选）、高级折叠区（IMAP/SMTP 主机、端口、SSL）；
  - 点击「连接」后创建账号 → 写入 `addr` / `mail_pw` / 可选服务器参数 → 写入 `force_encryption=1` → 调用 `rpc.configure()`；
  - 通过 `DcEventCenter` 捕获配置错误并以 Toast 提示，失败即可直接重试；
- [WelcomeActivity.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/WelcomeActivity.java)：新增"使用自己的邮箱"入口直达该向导；
- 布局与文案：`res/layout/activity_classic_login.xml`、`values/strings.xml`（含 `classic_login_*` 系列字符串）。

### 3.2 Windows 桌面端（apps/desktop）

- [NovaWizard/index.tsx](file:///workspace/apps/desktop/packages/frontend/src/components/screens/NovaWizard/index.tsx)：四步向导（同一对话框内切换，不跳屏）
  1. **邮箱地址**：输入即匹配服务商预设（`providerPresets.ts`），显示"已识别服务商"，也支持手动选择；
  2. **密码与安全**：密码/授权码 + 「要求端到端加密」开关（默认关）+ 可展开的服务器设置；
  3. **连接测试**：调用 `addOrUpdateTransport()` 并把核心的 `ConfigureProgress` 事件实时显示为进度条与文字；失败显示错误与「返回修改」；
  4. **账号同步（可选）**：WebDAV 快速配置（地址/账号/密码/备份口令 + 测试连接），可跳过；
- [OnboardingScreen.tsx](file:///workspace/apps/desktop/packages/frontend/src/components/screens/WelcomeScreen/OnboardingScreen.tsx)：欢迎页把「使用邮箱登录」放为主按钮，即时账号与备份恢复降为次要按钮；
- 服务商预设表：[providerPresets.ts](file:///workspace/apps/desktop/packages/frontend/src/components/screens/NovaWizard/providerPresets.ts)（含各服务商的"授权码/应用专用密码"提示）。

## 4. 验收标准

- [x] 桌面端：欢迎页 1 次点击进入邮箱登录向导；
- [x] 输入 QQ/163/Gmail/Outlook/iCloud 等地址能自动预填服务器设置；
- [x] 连接失败可返回上一步修改，已填内容不丢失；
- [x] 新账号默认 `force_encryption=1`；
- [ ] 从 WebDAV 一键恢复为向导第一步的入口（当前在"恢复备份"对话框中，后续合并进向导）。

## 5. 后续可做

- 预设库改为随核心下发（避免双端两份表）；
- 连接失败的错误分级文案（凭据错误 / 网络不可达 / 未开启 IMAP / 需要授权码）；
- 向导内嵌"从 WebDAV 恢复"入口，进一步缩短换机路径。