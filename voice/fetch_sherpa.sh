#!/usr/bin/env bash
# 拉取 sherpa-onnx 的 Android AAR 到 app/libs/。
# sherpa-onnx 不在 Maven Central，只能走 GitHub Release 分发。
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

echo "下载 ${URL}"
curl -fL --retry 3 -o "${DEST}.tmp" "${URL}"
mv "${DEST}.tmp" "${DEST}"

echo "下载完成：${DEST}（$(du -h "${DEST}" | cut -f1)）"
