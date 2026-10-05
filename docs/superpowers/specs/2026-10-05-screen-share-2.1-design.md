# Nova Chat 2.1 屏幕共享与编译时间 设计文档

- 日期：2026-10-05
- 版本：2.1（versionCode 21000 / versionName "2.1"）
- 状态：已获用户批准

## 1. 背景与目标

在 2.0 基础上交付三件事：

1. 新增屏幕共享功能，入口在会话页右上角电话图标的子菜单，必须在「设置 → 高级 → 实验性功能」开启后才可见。
2. 屏幕共享复用软件内现有视频通话接口（Delta Chat 系 VoIP / WebRTC 链路），不新建通话通道。
3. 每次发起屏幕共享前弹出 10 秒防诈骗警告（红色感叹号图标），倒计时结束才可确认，但用户连点可跳过。
4. 关于页版本号下方单独一行显示编译日期时间。

## 2. 已确认的决策

| 决策点 | 结论 |
|---|---|
| 警告确认方式 | 倒计时结束按钮才可点；倒计时期间累计点击 ≥3 次视为连点跳过，立即进入下一步 |
| 警告频次 | 每次共享前都弹 |
| 入口位置 | 会话页电话图标子菜单第三项（与语音通话、视频通话并列） |
| 视频流接入方式 | 替换摄像头轨：共享通话的本地视频轨直接由屏幕采集创建，不启摄像头 |
| 发起方式 | 点「屏幕共享」→ 警告 → 系统录屏授权 → 自动发起一路视频通话并共享屏幕 |
| 编译时间显示 | 版本号 chip 下方单独一行，格式 `yyyy-MM-dd HH:mm`，按设备本地时区显示 |

## 3. 架构与数据流

```
ConversationActivity（菜单第三项 menu_start_screen_share，仅实验性开关开启时可见）
  → ScreenShareWarningDialog（10s 倒计时 + 红色感叹号 + 连点≥3 跳过）
  → MediaProjectionManager.createScreenCaptureIntent()（系统录屏授权，拿 resultCode + data）
  → CallUtil.startScreenShareCall(context, chatId, resultCode, data)
  → CallCoordinator.initiateOutgoingCall(accId, chatId, video=true, withScreenShare=true, projection)
  → CallSession 携带 withScreenShare / projectionResultCode / projectionData
  → CallService：startForeground(..., mediaProjection 类型)
  → MediaStreamManager：本地视频轨改用 ScreenCapturerAndroid 创建（不启摄像头）
  → WebRTCClient：照常 addTrack + createOffer → placeOutgoingCall(video=true)
```

远端兼容性：远端仍收到同一条 video track，只是画面内容变成屏幕，旧版本天然可看。共享状态经已有 muted-state DataChannel（`DC_ID_MUTED_STATE=3`）的 JSON 追加 `"screenShare": true/false` 字段同步，旧版本解析时忽略未知字段，2.1 对端据此显示「对方正在共享屏幕」。

### 复用与扩展点

- 通话链路完全复用：`CallUtil → CallCoordinator.initiateOutgoingCall → CallSession → CallService → MediaStreamManager / WebRTCClient`，仅在 `CallSession` 增加屏幕共享标志与授权数据。
- muted-state DataChannel 现有 JSON `{"audioEnabled":..,"videoEnabled":..}` 扩展 `screenShare` 字段。
- 本地视频源创建点：`MediaStreamManager` 的 `createVideoCapturer()`（当前仅 Camera2），新增屏幕采集分支。

## 4. 组件设计

### 4.1 实验性开关

- `res/xml/preferences_advanced.xml`：`pref_experimental_features` 分类内、`pref_location_streaming_enabled` 之后新增 `SwitchPreferenceCompat`，key `pref_screen_share_enabled`，默认 `false`。
- `Prefs.java`：新增 `public static final String SCREEN_SHARE_PREF = "pref_screen_share_enabled";` 与 `public static final boolean SCREEN_SHARE_DEFAULT = false;`，提供 `isScreenShareEnabled(context)` 读取方法（走 `getBooleanPreference`，与现有惯例一致）。
- strings：`pref_screen_share`（Screen Share / 屏幕共享 / 螢幕共享），补 `values`、`values-zh-rCN`、`values-zh-rTW` 三套（`values-ja` 现有通话 key 亦未翻译，保持回落英文惯例）。

### 4.2 会话页入口

- `res/menu/conversation.xml`：`menu_start_video_call` 之后新增 `menu_start_screen_share`，title `@string/start_screen_share`，icon 复用 `@drawable/ic_cast`，iconTint 与兄弟项一致。
- `ConversationActivity.onPrepareOptionsMenu`：在父项 `menu_start_call` 可见的基础上，追加 `menu_start_screen_share.setVisible(Prefs.isScreenShareEnabled(this))`。
- `ConversationActivity.onOptionsItemSelected`：新增分支，SDK ≥ O 时先弹警告，授权成功后走 `CallUtil.startScreenShareCall`。
- strings：`start_screen_share` 三套（Screen Share / 屏幕共享 / 螢幕共享）。

### 4.3 10 秒防诈骗警告对话框

- 新建 `ScreenShareWarningDialog`（`org.thoughtcrime.securesms.calls` 包，AlertDialog 封装或静态工厂方法），在 `ConversationActivity` 中调用。
- UI：红色感叹号图标（自绘 drawable `ic_screen_share_warning`，红色圆形底 + 白色感叹号，或复用现有 warning 风格着红）+ 标题 + 防诈骗文案 + 确认按钮。
- 确认按钮文案：倒计时中显示 `开始屏幕共享 (10)` → `(9)` …，归零后为 `开始屏幕共享` 且可点击。
- 倒计时中按钮**可点击但单击不生效**；累计点击 ≥3 次立即取消倒计时并进入授权流程；归零后单击正常进入。
- 取消按钮随时可用。
- 文案 key：`screen_share_warning_title`（警惕屏幕共享诈骗）、`screen_share_warning_message`（请勿向陌生人共享屏幕…勿泄露验证码/支付信息等）、`screen_share_confirm`（开始屏幕共享）、`screen_share_cancel`（取消），三套 strings。

### 4.4 MediaProjection 授权与通话发起

- `ConversationActivity` 使用 `ActivityResultLauncher` 调起 `createScreenCaptureIntent()`；授权成功（`RESULT_OK`）后把 `resultCode`、`Intent data` 交给 `CallUtil.startScreenShareCall`；拒绝则 toast 提示后结束。
- `CallUtil` 新增 `startScreenShareCall(Context, int chatId, int resultCode, Intent projectionData)`；复用现有 `startCall` 的「已在通话中→跳转 CallActivity」「无网络→确认弹窗」逻辑，扩展内部签名携带屏幕共享参数。
- `CallCoordinator.initiateOutgoingCall` 扩展重载，新增参数 `withScreenShare`、`projectionResultCode`、`projectionData`；写入 `CallSession` 新字段。
- `CallSession` 新增：`boolean withScreenShare; int projectionResultCode; Intent projectionData;`（Intent 需可序列化传递，CallSession 若跨进程传递需 Parcelable，按现有实现处理）。
- `video` 语义不变：`placeOutgoingCall(..., hasVideo=true)`，对端按视频通话接听，看到的即是屏幕画面。

### 4.5 屏幕采集与恢复摄像头

- `MediaStreamManager`：`createVideoCapturer()` 增加屏幕共享分支——`withScreenShare` 时用 `new ScreenCapturerAndroid(projectionData, projectionCallback)` 替代 Camera2 capturer；视频轨创建、720p 目标、`setVideoEnabled` 逻辑保持不变。
- `MediaProjection.Callback.onStop`（用户从系统通知栏点停止）：回调到 `CallService → CallCoordinator`，自动恢复摄像头（有 `CAMERA` 权限且视频开关开启时）或关闭视频轨，并通过 DataChannel 同步 `screenShare:false`，`CallActivity` 提示「屏幕共享已停止」。
- 通话内手动停止：`video_button` 在共享期间显示屏幕图标，点击 = 停止共享 → 同样走恢复逻辑。
- 恢复摄像头 = 停止屏幕 capturer、启动 Camera2 capturer，复用同一 video track（capturer 切换不触发重协商）。

### 4.6 前台服务与 Manifest

- `AndroidManifest.xml` 权限区新增 `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION`。
- `CallService` 的 `foregroundServiceType` 由 `camera|microphone|phoneCall` 追加 `|mediaProjection`；共享通话期间 `startForeground` 传入 `mediaProjection` 类型（Android 14+ 强制，Android 10+ 生效，低版本忽略）。
- 权限要求：`RECORD_AUDIO` 照常必需；`CAMERA` 非必需（拒绝不影响共享，停止共享后视频轨关闭）；`FOREGROUND_SERVICE_MEDIA_PROJECTION` 为普通安装时授权。

### 4.7 状态同步与双端 UI

- **发送端**：`CallSession.withScreenShare` 变化时，经 muted-state DataChannel 发送 `{"audioEnabled":..,"videoEnabled":..,"screenShare":true|false}`；`CallActivity` 本地预览 `setMirror(false)`，预览角标显示「屏幕共享中」，`video_button` 图标切换为屏幕共享图标。
- **接收端**：解析 muted-state JSON 中 `screenShare` 字段（缺省 false），`CallActivity` 顶部提示条显示「对方正在共享屏幕」。
- PiP：现有 PiP 隐藏 `call_controls_layout` 的逻辑不变，共享角标保留在预览区域。

### 4.8 关于页编译时间

- `apps/android/build.gradle` `defaultConfig` 新增 `buildConfigField("long", "BUILD_TIME_MS", "${System.currentTimeMillis()}")`（构建机绝对时刻 epoch 毫秒；`buildFeatures.buildConfig` 已开启）。
- `res/layout/activity_about.xml`：版本号 chip（`about_version`）下方新增 `about_build_time` TextView（居中、小字号、低强调色）。
- `AboutActivity.onCreate`：`BuildConfig.BUILD_TIME_MS` 按设备本地时区格式化 `yyyy-MM-dd HH:mm`，文案 key `about_build_time`（`编译于 %1$s` / `Built %1$s`），四套 strings（values、zh-rCN、zh-rTW、ja）补齐。
- 注意：`System.currentTimeMillis()` 在 Gradle 配置阶段求值，每次构建变化，正常但不可复现构建——可接受，符合「编译日期时间」语义。

### 4.9 版本号

- `versionCode 21000`、`versionName "2.1"`（编码规则 M*10000+m*1000+p*100+r → 2.1.0.0 = 21000）。

## 5. 错误处理

| 场景 | 处理 |
|---|---|
| 用户拒绝系统录屏授权 | toast「未能开始屏幕共享」，不发起通话 |
| 录屏授权数据失效（SecurityException） | 捕获，恢复摄像头或关闭视频，提示共享失败 |
| 录屏期间应用转后台/服务被杀 | `MediaProjection.Callback.onStop` 触发恢复逻辑 |
| 无麦克风权限 | 沿用现有通话权限检查（`hasMicrophonePermission`），警告在授权之前弹出不浪费用户时间 |
| 无 CAMERA 权限 | 共享正常；停止共享后视频轨 `setEnabled(false)`，UI 提示需相机权限才能恢复摄像头 |
| 已在通话中再点「屏幕共享」 | 沿用 `CallUtil.startCall` 现有分支：跳转现有 CallActivity（不中途切换到共享，保持简单） |
| 实验性开关关闭时 | 菜单项不可见；通话内共享 UI 不出现（无入口，状态恒 false） |
| 旧版本对端 | 忽略 JSON 未知字段，正常显示屏幕画面（同一 video track），无共享状态提示 |

## 6. 不做的事（YAGNI）

- 不做屏幕共享作为第二条视频轨（双轨/画中画）——2.1 只交付替换式，DataChannel 字段为将来升级预留。
- 不做共享中选择共享单个应用窗口（仅全屏）。
- 不做共享时的画笔/远程控制。
- 不在通话控制栏新增常驻按钮（入口只在会话页菜单）。
- 不处理「屏幕安全（FLAG_SECURE）」与共享的冲突（共享时自己 App 窗口对端显示黑色属隐私保护合理行为）。

## 7. 验证计划

1. 本地 `assembleFossDebug` 构建成功；APK badging 核对 `versionCode='21000' versionName='2.1'`。
2. `spotlessJavaCheck` / `spotlessXmlCheck` 对本次改动文件通过。
3. 代码审查清单：开关默认关、菜单可见性条件、警告倒计时与连点逻辑、Manifest 权限与 foregroundServiceType、DataChannel JSON 向后兼容（新字段可选）、屏幕 capturer 泄漏释放（stopCapture/dispose）。
4. 真机验证由用户执行：菜单出现/隐藏、警告弹窗、录屏授权、双端画面、停止共享恢复摄像头、关于页编译时间。
