#!/bin/bash
# =============================================================================
# BatteryHealthApp Release 构建脚本
# 支持国内镜像加速，一键完成Release包构建
# =============================================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的信息
info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# 检查环境
info "检查构建环境..."

# 检查Java
if ! command -v java &> /dev/null; then
    error "未找到Java，请先安装JDK 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
info "Java版本: $JAVA_VERSION"

# 检查Android SDK
if [ -z "$ANDROID_HOME" ]; then
    warn "ANDROID_HOME 未设置，尝试查找..."
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    elif [ -d "/usr/local/android-sdk" ]; then
        export ANDROID_HOME="/usr/local/android-sdk"
    elif [ -d "/workspace/android-sdk" ]; then
        export ANDROID_HOME="/workspace/android-sdk"
    else
        error "未找到Android SDK，请设置 ANDROID_HOME 环境变量"
        exit 1
    fi
    info "自动设置 ANDROID_HOME=$ANDROID_HOME"
fi

# 检查Gradle Wrapper
if [ ! -f "./gradlew" ]; then
    warn "未找到gradlew，尝试使用系统Gradle..."
    if command -v gradle &> /dev/null; then
        GRADLE_CMD="gradle"
    else
        error "未找到Gradle，请先安装或下载Gradle Wrapper"
        exit 1
    fi
else
    GRADLE_CMD="./gradlew"
fi

# 检查签名配置
if [ ! -f "keystore.properties" ]; then
    warn "未找到 keystore.properties，Release构建将使用debug签名"
    warn "正式发版前请创建 keystore.properties 文件"
fi

info "========================================"
info "开始 Release 构建"
info "========================================"

# 清理旧构建
info "清理旧构建..."
$GRADLE_CMD clean

# 执行Lint检查
info "执行Lint检查..."
$GRADLE_CMD lintRelease || warn "Lint检查发现问题，继续构建..."

# 执行Release构建
info "开始构建Release APK..."
$GRADLE_CMD assembleRelease

# 检查构建结果
APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    success "Release APK构建成功!"
    success "路径: $APK_PATH"
    success "大小: $APK_SIZE"
else
    error "构建失败，未找到APK文件"
    exit 1
fi

# 输出APK信息
info "========================================"
info "APK信息"
info "========================================"

# 使用aapt2获取APK信息（如果可用）
AAPT2="$ANDROID_HOME/build-tools/35.0.0/aapt2"
if [ -f "$AAPT2" ]; then
    info "包名: $($AAPT2 dump packagename "$APK_PATH" 2>/dev/null || echo 'N/A')"
    info "版本信息:"
    $AAPT2 dump badging "$APK_PATH" 2>/dev/null | grep -E "versionCode|versionName" | head -2 || true
else
    warn "aapt2 不可用，跳过APK详细信息"
fi

info "========================================"
success "构建完成!"
info "========================================"
