# Windows 构建指南

Nova Chat 的 Windows 端基于 Delta Chat 桌面端（Electron + TypeScript，核心经 `deltachat-node` / JSON-RPC 绑定 Rust 核心）。

## 1. 前置环境

| 依赖 | 版本建议 | 说明 |
|---|---|---|
| Node.js | 18 LTS 或 20 LTS | 前端与 Electron |
| npm | 随 Node 安装 | 包管理 |
| Rust 工具链 | stable（1.7x+） | 编译核心原生绑定（如无预编译产物） |
| Visual Studio Build Tools | 2022（含 C++ 桌面开发） | Windows 原生编译需要 |
| Git | 最新 | 拉取源码 |

> 若上游 `deltachat-desktop` 提供预编译核心（`@deltachat/...` npm 包），可不装 Rust/VS Build Tools，直接 `npm install` 即可。

## 2. 获取源码

```bash
# 在 novachat 仓库根目录
bash scripts/fork-setup.sh
cd apps/desktop
```

## 3. 开发模式运行

```bash
npm install
npm start
```

启动后会打开 Electron 窗口，可完成向导、收发消息，验证改造功能。

## 4. 打包安装包（NSIS）

```bash
npm run build
npx electron-builder --win nsis
```

产物位置：`apps/desktop/dist/` 下的 `.exe` 安装包（默认输出到 `dist/`，具体以项目 `electron-builder.yml` 为准）。

> 桌面端默认面向 Windows/macOS/Linux；如需仅构建 Windows，可在 `electron-builder` 命令加 `--win`。

## 5. 常见问题

| 问题 | 处理 |
|---|---|
| `node-gyp` 编译失败 | 安装 VS Build Tools（C++ 桌面开发）后重试；确认已开"开发者模式"或执行 `npm config set msvs_version 2022` |
| 原生模块下载慢/失败 | 配置镜像源（如 `ELECTRON_MIRROR`、npm 镜像）后重试 |
| Electron 下载慢 | 设置 `ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/` |
| 核心绑定与系统不匹配 | 删除 `node_modules` 与锁文件后重新 `npm install` |

## 6. 验证

- `npm start` 后完成首次使用向导；
- 发送/接收一条消息；
- 设置页出现「端到端加密」开关与「WebDAV 同步」入口；
- `electron-builder` 产物可在干净的 Windows 机器上安装运行。
