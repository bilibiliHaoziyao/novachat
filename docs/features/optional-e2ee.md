# 改造二：端到端加密默认开启（可关闭）

> 状态：自 v1.4 起，Android 端 `force_encryption` 默认开启；仍保留开关，必要时可关闭以与普通邮件客户端互通。

## 1. 事实与目标

Delta Chat 的机会式加密（Autocrypt）本身并不"拦截明文"：对没有密钥的收件人，消息仍以普通邮件发送。真正决定"是否强制加密"的是配置项 `force_encryption`——开启后**拒绝发送明文，也不处理收到的明文**。

Nova Chat 的目标：**`force_encryption` 默认开启，同时保留开关**——默认安全，必要时可放开。

| 场景 | 行为 |
|---|---|
| 新账号（默认） | `force_encryption = 1`：只收发加密消息 |
| 既有账号（升级到 v1.4） | 首次启动一次性迁移，写入 `force_encryption = 1` |
| 用户手动关闭 | 设置 → 隐私与安全 → 端到端加密，关闭后可与任意邮件客户端互通，明文消息照常收发 |
| 与 Nova Chat 用户通信 | 双方都有密钥时自动端到端加密（Autocrypt 机会式加密，与开关无关） |
| 状态可见 | 会话/消息上沿用核心提供的加密状态标识（未加密会话有明确标记） |

## 2. 实现

### 2.1 核心（Rust）

[config.rs](file:///workspace/apps/android/jni/deltachat-core-rust/src/config.rs)：`Config::ForceEncryption` 显式声明默认值 `1`，保证配置缺失时默认"强制加密"：

```rust
/// Nova Chat: end-to-end encryption is on by default.
#[strum(props(default = "1"))]
ForceEncryption,
```

其余加密相关逻辑（密钥生成、Autocrypt 头、明文回退）保持上游实现，避免引入加密回归风险。

### 2.2 Android

- [ClassicLoginActivity.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/ClassicLoginActivity.java)：新建账号时写入 `force_encryption=1`；
- [ApplicationContext.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java)：升级后首次启动对**既有账号**做一次性迁移（由 [Prefs](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/util/Prefs.java) 的 `isE2eeDefaultOnApplied` 标志位保证只执行一次）；
- [PrivacyPreferenceFragment.java](file:///workspace/apps/android/src/main/java/org/thoughtcrime/securesms/preferences/PrivacyPreferenceFragment.java)：设置 → 隐私与安全 中的「端到端加密」开关，随时可切换并持久化。

### 2.3 Windows 桌面端

桌面端**未同步**本次改动，仍为"向导开关默认关闭 + 设置 → 高级 → 端到端加密开关"：

- [NovaWizard/index.tsx](file:///workspace/apps/desktop/packages/frontend/src/components/screens/NovaWizard/index.tsx)：向导第二步的开关默认关闭，登录成功后写入 `force_encryption`；
- [Settings/Advanced.tsx](file:///workspace/apps/desktop/packages/frontend/src/components/Settings/Advanced.tsx)：设置 → 高级 → 「端到端加密」分组，`CoreSettingsSwitch`（配置键 `force_encryption`）；
- 文案（`_locales/en.xml` / `zh_CN.xml`）：`nova_e2ee_*` 系列字符串。

## 3. 隐私说明（如实告知）

- 关闭后消息以**明文邮件**形式收发，邮箱服务商与网络链路可读；这是"与现有邮件生态互通"的代价；
- 开启时与普通邮件客户端通信会被拒收/无法发送，属于预期行为；
- 无论开关如何，双方都使用 Nova Chat/Delta Chat 时都会自动加密（Autocrypt），敏感通信建议保持开启。

## 4. 验收标准

- [x] 新账号默认 `force_encryption=1`（Android 显式写入、核心默认值兜底）；
- [x] 既有账号升级后一次性迁移为 `1`；
- [x] 设置中可随时开关，重启后保持；
- [x] 关闭状态下可以与普通邮件客户端正常互发明文邮件；
- [ ] 会话页"未加密"标识的 UI 强化（当前沿用上游标识）。

## 5. 后续可做

- 首次发送给"无密钥联系人"时的轻提示（"本次消息未加密"）；
- 按账号/按聊天覆盖全局开关（目前 `force_encryption` 为账号级，上游语义如此）；
- 桌面端同步为默认开启。