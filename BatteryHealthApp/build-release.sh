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

# 检查Java：项目要求 JDK 17，优先使用系统安装的 JDK 17
if [ -z "$JAVA_HOME" ] || [[ ! "$JAVA_HOME" == *"java-17"* ]]; then
    # 尝试查找 JDK 17
    for jdk_path in /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17-openjdk /usr/lib/jvm/jdk-17; do
        if [ -d "$jdk_path" ] && [ -x "$jdk_path/bin/java" ]; then
            export JAVA_HOME="$jdk_path"
            export PATH="$JAVA_HOME/bin:$PATH"
            info "已设置 JAVA_HOME=$JAVA_HOME"
            break
        fi
    done
fi

if ! command -v java &> /dev/null; then
    error "未找到Java，请先安装JDK 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
info "Java版本: $JAVA_VERSION"

# 校验主版本是否为 17
JAVA_MAJOR=$(echo "$JAVA_VERSION" | cut -d'.' -f1)
if [ "$JAVA_MAJOR" != "17" ]; then
    warn "当前 Java 主版本为 $JAVA_MAJOR，项目要求 JDK 17"
    warn "请设置 JAVA_HOME 指向 JDK 17 路径后再试"
fi

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

# 优先使用系统 Gradle，不存在时回退到 Gradle Wrapper
if command -v gradle &> /dev/null; then
    GRADLE_CMD="gradle"
    info "使用系统 Gradle: $(gradle --version 2>&1 | head -n 1)"
elif [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
else
    error "未找到 Gradle，请先安装或下载 Gradle Wrapper"
    exit 1
fi

# 检查签名配置
if [ ! -f "keystore.properties" ]; then
    warn "未找到 keystore.properties，Release构建将使用debug签名"
    warn "正式发版前请创建 keystore.properties 文件"
fi

# 执行 Release 自检规范标准
info "========================================"
info "执行 Release 自检规范标准"
info "========================================"
if [ -f "./release-checklist.sh" ]; then
    chmod +x ./release-checklist.sh
    ./release-checklist.sh || { error "Release 自检未通过，终止构建"; exit 1; }
else
    warn "未找到 release-checklist.sh，跳过自检"
fi

info "========================================"
info "开始 Release 构建"
info "========================================"

# 清理旧构建
info "清理旧构建..."
$GRADLE_CMD clean

# 执行Release构建
info "开始构建Release APK..."
$GRADLE_CMD assembleRelease

# 检查构建结果并复制为带版本号的发布包
APK_PATH="app/build/outputs/apk/release/app-release.apk"
RELEASE_APK="app-release-5.0.0.apk"
if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    cp "$APK_PATH" "$RELEASE_APK"
    success "Release APK构建成功!"
    success "路径: $APK_PATH"
    success "发布包: $RELEASE_APK"
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
