#!/usr/bin/env bash
# 正式版签名配置脚本
# 用法：将正式签名文件放到 app/ 目录后，按提示运行
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE_FILE="${1:-}"
STORE_PASS="${2:-}"
KEY_ALIAS="${3:-}"
KEY_PASS="${4:-}"

if [[ -z "$KEYSTORE_FILE" || -z "$STORE_PASS" || -z "$KEY_ALIAS" || -z "$KEY_PASS" ]]; then
    echo "用法: $0 <keystore文件名> <storePassword> <keyAlias> <keyPassword>"
    echo "示例: $0 batteryhealth-release.keystore MyStorePass my_alias MyKeyPass"
    exit 1
fi

cat > "${ROOT_DIR}/keystore.properties" <<EOF
storeFile=${KEYSTORE_FILE}
storePassword=${STORE_PASS}
keyAlias=${KEY_ALIAS}
keyPassword=${KEY_PASS}
EOF

echo "已生成 ${ROOT_DIR}/keystore.properties"
echo "请确认正式签名文件已放置到 ${ROOT_DIR}/app/${KEYSTORE_FILE}"
echo "然后执行: ./scripts/build_release.sh"
