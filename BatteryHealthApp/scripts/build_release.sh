#!/usr/bin/env bash
# 正式版构建脚本
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f "keystore.properties" ]]; then
    echo "错误: 未找到 keystore.properties，请先运行 ./scripts/setup_release_keystore.sh"
    exit 1
fi

if [[ ! -f "local.properties" ]]; then
    echo "错误: 未找到 local.properties，请配置 sdk.dir"
    exit 1
fi

# 优先使用 JDK 17，兼容性更好
if [[ -d "/root/.local/share/mise/installs/java/17.0.2" ]]; then
    export JAVA_HOME="/root/.local/share/mise/installs/java/17.0.2"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

GRADLE_CMD="./gradlew"
if [[ ! -f "gradle/wrapper/gradle-wrapper.jar" ]]; then
    echo "提示: 未找到 gradle wrapper，使用系统 gradle"
    GRADLE_CMD="gradle"
fi

$GRADLE_CMD clean assembleRelease

echo ""
echo "正式版构建完成，产物如下:"
echo "  APK:   app/build/outputs/apk/release/app-release.apk"
echo "  Mapping: app/build/outputs/mapping/release/mapping.txt"
