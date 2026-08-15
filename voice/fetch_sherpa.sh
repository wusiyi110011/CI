#!/usr/bin/env bash
# 拉取 sherpa-onnx 的 Android AAR 到 app/libs/。
# sherpa-onnx 不在 Maven Central，只能走 GitHub Release 分发。
# 优先 curl 直连 GitHub CDN；失败时若安装了 gh CLI，走 gh release download（API 通道）。
# 部分网络环境（如直连受限）curl 会连不上 CDN，但 git/gh 的 api.github.com 通道可用。
set -euo pipefail

VERSION="1.13.5"
AAR_NAME="sherpa-onnx-${VERSION}.aar"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/${AAR_NAME}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIBS_DIR="${SCRIPT_DIR}/../app/libs"
DEST="${LIBS_DIR}/${AAR_NAME}"

mkdir -p "${LIBS_DIR}"

if [ -f "${DEST}" ]; then
    echo "已存在，跳过下载：${DEST}（$(du -h "${DEST}" | cut -f1)）"
    exit 0
fi

echo "尝试 1/2：curl 直连 GitHub CDN（${URL}）"
if curl -fL --connect-timeout 15 --retry 2 -o "${DEST}.tmp" "${URL}"; then
    mv "${DEST}.tmp" "${DEST}"
elif command -v gh >/dev/null 2>&1 && gh release download "v${VERSION}" -R k2-fsa/sherpa-onnx --pattern "${AAR_NAME}" --dir "${LIBS_DIR}"; then
    echo "gh release download 成功"
else
    echo "两种方式都失败。请检查网络；或安装 gh CLI（brew install gh）并登录（gh auth login）后重试。"
    rm -f "${DEST}.tmp"
    exit 1
fi

echo "下载完成：${DEST}（$(du -h "${DEST}" | cut -f1)）"
