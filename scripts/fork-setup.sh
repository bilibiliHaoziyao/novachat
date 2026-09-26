#!/usr/bin/env bash
# =============================================================================
# Nova Chat · 源码组装脚本
# -----------------------------------------------------------------------------
# 作用：从 Delta Chat 上游克隆核心与双端源码，组装成本地开发树：
#
#   novachat/
#   ├── core/           # deltachat-core-rust  (MPL-2.0)
#   ├── apps/
#   │   ├── android/    # deltachat-android    (GPL-3.0)
#   │   └── desktop/    # deltachat-desktop    (GPL-3.0)
#   └── patches/        # （预留）Nova Chat 补丁目录
#
# 用法： bash scripts/fork-setup.sh [--full]
#       --full 表示克隆完整历史（默认 --depth 1 浅克隆）
#
# 注意：克隆下来的源码保留上游各自 LICENSE 与版权声明，请勿删除。
# =============================================================================
set -euo pipefail

UPSTREAM_CORE="https://github.com/deltachat/deltachat-core-rust.git"
UPSTREAM_ANDROID="https://github.com/deltachat/deltachat-android.git"
UPSTREAM_DESKTOP="https://github.com/deltachat/deltachat-desktop.git"
BRANCH="main"
DEPTH=(--depth 1)
if [[ "${1:-}" == "--full" ]]; then
  DEPTH=()
  echo ">> 使用完整克隆（含历史）"
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mkdir -p core apps patches

clone() { # $1=url $2=dest
  local url="$1" dest="$2"
  if [[ -d "$dest/.git" ]]; then
    echo ">> $dest 已存在，跳过克隆"
    return 0
  fi
  echo ">> 克隆 $url -> $dest"
  git clone -b "$BRANCH" "${DEPTH[@]}" "$url" "$dest"
}

clone "$UPSTREAM_CORE"    "core"
clone "$UPSTREAM_ANDROID" "apps/android"
clone "$UPSTREAM_DESKTOP" "apps/desktop"

echo ">> 初始化子模块（Android 可能依赖核心子模块）"
git -C apps/android submodule update --init --recursive || true

cat <<'EOF'

────────────────────────────────────────────────────────────
  组装完成。开发树如下：
    core/            deltachat-core-rust（MPL-2.0）
    apps/android/    deltachat-android（GPL-3.0）
    apps/desktop/    deltachat-desktop（GPL-3.0）
    patches/         补丁目录（预留）

  下一步：
    * Android 构建：见 docs/build-android.md
    * Windows 构建：见 docs/build-windows.md
    * 三项改造设计：docs/features/（onboarding-wizard / optional-e2ee / webdav-sync）

  许可提醒：各子目录请保留上游 LICENSE 与版权声明；对 core/ 的修改保持
  MPL-2.0，对 apps/ 的修改保持 GPL-3.0（详见仓库 NOTICE.md）。
────────────────────────────────────────────────────────────
EOF
