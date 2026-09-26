# Android 构建指南

## 1. 前置环境

| 依赖 | 版本建议 | 说明 |
|---|---|---|
| JDK | 17（Android Studio 内置 JBR 亦可） | Android 编译 |
| Android SDK | API 34+ | 通过 Android Studio SDK Manager 安装 |
| Android NDK | r25+ | Rust 核心 JNI 编译需要 |
| Rust 工具链 | stable（1.7x+） | `rustup` 安装 |
| cargo-ndk | 最新 | `cargo install cargo-ndk` |
| Android Studio | 最新稳定版 | 推荐 |

## 2. 获取源码

```bash
# 在 novachat 仓库根目录
bash scripts/fork-setup.sh
cd apps/android
```

## 3. 环境变量

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/<已安装版本>"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
```

## 4. 构建

```bash
# Debug（含 Rust 核心自动编译，首次较慢）
./gradlew assembleDebug

# Release
./gradlew assembleRelease
```

产物位置：

- Debug：`apps/android/app/build/outputs/apk/debug/app-debug.apk`
- Release：`apps/android/app/build/outputs/apk/release/app-release.apk`（未签名，需配置签名）

## 5. Release 签名

在 `apps/android/app/build.gradle` 配置 `signingConfigs`（使用自有 keystore，勿提交仓库），然后：

```bash
./gradlew assembleRelease
# 或生成 AAB 上架 Google Play
./gradlew bundleRelease
```

## 6. 常见问题

| 问题 | 处理 |
|---|---|
| `cargo-ndk` 找不到 | `cargo install cargo-ndk`，确认 `~/.cargo/bin` 在 PATH |
| NDK 版本不匹配 | 对照 `apps/android/jni` 的构建脚本要求的 NDK 版本安装 |
| 首次构建很慢 | Rust 依赖需联网拉取并编译，属正常现象 |
| 子模块未初始化 | `git submodule update --init --recursive` |

## 7. 验证

- 安装 APK 到模拟器/真机：`./gradlew installDebug`；
- 完成首次使用向导 → 收发一条消息 → 检查 E2E 开关与 WebDAV 设置入口是否存在。
