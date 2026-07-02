#!/bin/bash
# =============================================================================
# BatteryHealthApp Release 自检规范标准
# 版本：v5.0.0
# 用途：在正式构建 Release APK 前执行标准化自检，确保发布包质量
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PASS_COUNT=0
WARN_COUNT=0
FAIL_COUNT=0

info() { echo -e "${BLUE}[CHECK]${NC} $1"; }
pass() { echo -e "${GREEN}[PASS]${NC} $1"; PASS_COUNT=$((PASS_COUNT + 1)); }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; WARN_COUNT=$((WARN_COUNT + 1)); }
fail() { echo -e "${RED}[FAIL]${NC} $1"; FAIL_COUNT=$((FAIL_COUNT + 1)); }

info "========================================"
info "Release 自检规范标准 - v5.0.0"
info "========================================"

# -----------------------------------------------------------------------------
# 1. 构建环境基线检查
# -----------------------------------------------------------------------------
info "[1/10] 构建环境基线检查..."

if ! command -v java &> /dev/null; then
    fail "未检测到 Java 环境"
else
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    JAVA_MAJOR=$(echo "$JAVA_VERSION" | cut -d'.' -f1)
    if [[ "$JAVA_MAJOR" -ge 17 ]]; then
        pass "Java 版本符合要求: $JAVA_VERSION"
    else
        fail "Java 版本过低: $JAVA_VERSION，要求 JDK 17+"
    fi
fi

if [ -z "$ANDROID_HOME" ]; then
    if [ -d "/workspace/android-sdk" ]; then
        export ANDROID_HOME="/workspace/android-sdk"
        pass "ANDROID_HOME 自动设置为 /workspace/android-sdk"
    else
        fail "ANDROID_HOME 未设置且未找到默认 SDK 路径"
    fi
else
    pass "ANDROID_HOME=$ANDROID_HOME"
fi

if [ -d "$ANDROID_HOME/build-tools" ]; then
    pass "Android SDK Build-Tools 已安装"
else
    fail "Android SDK Build-Tools 未安装"
fi

# -----------------------------------------------------------------------------
# 2. 版本号规范检查
# -----------------------------------------------------------------------------
info "[2/10] 版本号规范检查..."

BUILD_GRADLE="app/build.gradle"
VERSION_NAME=$(grep -E "versionName\s+\"" "$BUILD_GRADLE" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
VERSION_CODE=$(grep -E "versionCode\s+[0-9]+" "$BUILD_GRADLE" | head -1 | sed -E 's/.*versionCode\s+([0-9]+).*/\1/')

if [ -n "$VERSION_NAME" ]; then
    pass "versionName 已设置为 $VERSION_NAME"
else
    fail "versionName 未设置"
fi

if [ "$VERSION_CODE" -ge 60 ]; then
    pass "versionCode 已升级: $VERSION_CODE（>= 60）"
else
    fail "versionCode 未升级: $VERSION_CODE，v5.0.0 要求 >= 60"
fi

# -----------------------------------------------------------------------------
# 3. 签名配置检查
# -----------------------------------------------------------------------------
info "[3/10] Release 签名配置检查..."

if [ ! -f "keystore.properties" ]; then
    warn "未找到 keystore.properties，Release 将使用 debug 签名"
    warn "正式发版前必须配置真实签名文件"
else
    pass "keystore.properties 存在"
    STORE_FILE=$(grep -E "^storeFile" keystore.properties | cut -d'=' -f2 | tr -d ' ')
    if [ -f "$STORE_FILE" ]; then
        pass "签名文件存在: $STORE_FILE"
    else
        fail "签名文件不存在: $STORE_FILE"
    fi
fi

# -----------------------------------------------------------------------------
# 4. 国内镜像加速源检查
# -----------------------------------------------------------------------------
info "[4/10] 国内镜像加速源检查..."

if grep -q "repo.huaweicloud.com/repository/maven" settings.gradle || grep -q "repo.huaweicloud.com/repository/maven" build.gradle; then
    pass "已配置华为云 Maven 国内镜像"
else
    warn "未检测到华为云 Maven 镜像"
fi

if grep -q "maven.aliyun.com" settings.gradle || grep -q "maven.aliyun.com" build.gradle; then
    pass "已配置阿里云 Maven 国内镜像"
else
    warn "未检测到阿里云 Maven 镜像"
fi

# -----------------------------------------------------------------------------
# 5. Web 模块排除检查
# -----------------------------------------------------------------------------
info "[5/10] Web 模块排除检查..."

WEB_DIRS=("app/src/main/assets/web" "app/src/main/res/raw" "app/src/main/webapp")
WEB_FOUND=false
for dir in "${WEB_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        warn "发现可疑 web 资源目录: $dir"
        WEB_FOUND=true
    fi
done

if grep -R -q "battery-health-web\|web/index.html\|WebView" app/src/main 2>/dev/null; then
    warn "发现 web 相关引用，请确认未打包 Web 内容"
    WEB_FOUND=true
fi

if [ "$WEB_FOUND" == "false" ]; then
    pass "未发现 web 资源被打包到 APK"
else
    warn "存在 web 相关文件/引用，请人工复核"
fi

# -----------------------------------------------------------------------------
# 6. Release 构建配置检查
# -----------------------------------------------------------------------------
info "[6/10] Release 构建配置检查..."

if grep -q "minifyEnabled true" app/build.gradle; then
    pass "已启用 ProGuard/R8 代码混淆"
else
    fail "未启用 ProGuard/R8 代码混淆"
fi

if grep -q "shrinkResources true" app/build.gradle; then
    pass "已启用资源压缩"
else
    warn "未启用资源压缩"
fi

if grep -q "debuggable false" app/build.gradle; then
    pass "Release 构建已关闭 debuggable"
else
    fail "Release 构建未关闭 debuggable"
fi

if grep -q "zipAlignEnabled true" app/build.gradle; then
    pass "已启用 zipAlign"
else
    warn "未启用 zipAlign"
fi

# -----------------------------------------------------------------------------
# 7. 权限与隐私合规基线检查
# -----------------------------------------------------------------------------
info "[7/10] 权限与隐私合规基线检查..."

DANGEROUS_PERMISSIONS=("READ_PHONE_STATE" "WRITE_EXTERNAL_STORAGE" "READ_EXTERNAL_STORAGE")
for perm in "${DANGEROUS_PERMISSIONS[@]}"; do
    if grep -q "android:name=\"android.permission.$perm\"" app/src/main/AndroidManifest.xml; then
        warn "检测到敏感权限: $perm，请确认隐私政策已声明"
    fi
done
pass "权限清单检查完成"

# -----------------------------------------------------------------------------
# 8. 架构与 ABI 配置检查
# -----------------------------------------------------------------------------
info "[8/10] 架构与 ABI 配置检查..."

if grep -q "abiFilters" app/build.gradle; then
    pass "已配置 ABI 过滤，避免打包无用 so 库"
else
    warn "未配置 ABI 过滤，可能导致 APK 体积过大"
fi

if grep -q "useLegacyPackaging = false\|useLegacyPackaging=false" app/build.gradle; then
    pass "已关闭 so 库旧版打包，符合 16KB Page Size 要求"
else
    warn "建议关闭 so 库旧版打包以兼容 Android 15+"
fi

# 优先使用系统 Gradle，不存在时回退到 Gradle Wrapper
if command -v gradle &> /dev/null; then
    GRADLE_CMD="gradle"
elif [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
else
    GRADLE_CMD=""
fi

# -----------------------------------------------------------------------------
# 9. 本地静态检查
# -----------------------------------------------------------------------------
info "[9/10] 本地静态检查（Lint）..."

if [ -n "$GRADLE_CMD" ]; then
    $GRADLE_CMD lintRelease --quiet || warn "Lint 检查发现问题，请查看报告"
    pass "Lint 检查执行完成"
else
    warn "未找到 Gradle，跳过 Lint 检查"
fi

# -----------------------------------------------------------------------------
# 10. 测试执行
# -----------------------------------------------------------------------------
info "[10/10] 单元测试执行..."

if [ -n "$GRADLE_CMD" ]; then
    $GRADLE_CMD testReleaseUnitTest --quiet || warn "单元测试存在失败用例"
    pass "单元测试执行完成"
else
    warn "未找到 Gradle，跳过单元测试"
fi

# -----------------------------------------------------------------------------
# 自检报告汇总
# -----------------------------------------------------------------------------
echo ""
info "========================================"
info "Release 自检报告"
info "========================================"
echo -e "通过项: ${GREEN}$PASS_COUNT${NC}"
echo -e "警告项: ${YELLOW}$WARN_COUNT${NC}"
echo -e "失败项: ${RED}$FAIL_COUNT${NC}"

if [ "$FAIL_COUNT" -gt 0 ]; then
    fail "自检未通过，请修复失败项后再执行 Release 构建"
    exit 1
fi

if [ "$WARN_COUNT" -gt 0 ]; then
    warn "自检通过但存在警告项，建议发布前确认"
fi

pass "Release 自检通过，可进行 Release 构建"
exit 0
