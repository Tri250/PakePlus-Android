#!/bin/bash
###############################################################################
# BatteryHealthApp Android 测试运行脚本
#
# 用途：
#   统一执行稳定性 / 性能 / 安全隐私 / 安装与卸载场景的测试集合，
#   并生成 HTML 报告 + 文本报告。
#
# 用法：
#   ./run_tests.sh                 # 跑全部测试
#   ./run_tests.sh stability       # 仅稳定性
#   ./run_tests.sh performance     # 仅性能
#   ./run_tests.sh security        # 仅安全隐私
#   ./run_tests.sh install         # 仅安装/卸载集成
#   ./run_tests.sh fast            # 跳过性能压力测试
###############################################################################

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 路径
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR"
APP_DIR="$PROJECT_ROOT/app"
REPORT_DIR="$PROJECT_ROOT/build/reports"
TEST_REPORT="$REPORT_DIR/tests"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_FILE="$REPORT_DIR/test_run_${TIMESTAMP}.log"

CATEGORY="${1:-all}"
SKIP_SLOW=false

# 解析参数
case "$CATEGORY" in
    fast)
        CATEGORY="all"
        SKIP_SLOW=true
        ;;
esac

mkdir -p "$REPORT_DIR"
echo "==========================================" | tee -a "$LOG_FILE"
echo "BatteryHealthApp Android 测试运行" | tee -a "$LOG_FILE"
echo "分类: $CATEGORY" | tee -a "$LOG_FILE"
echo "跳过慢测试: $SKIP_SLOW" | tee -a "$LOG_FILE"
echo "时间戳: $TIMESTAMP" | tee -a "$LOG_FILE"
echo "==========================================" | tee -a "$LOG_FILE"

cd "$APP_DIR"

# 测试分类映射到 JUnit 测试方法/包
declare -A CATEGORY_TESTS
CATEGORY_TESTS[stability]="com.batteryhealth.app.data.model.*StabilityTest
com.batteryhealth.app.data.database.*StabilityTest
com.batteryhealth.app.domain.usecase.*Test
com.batteryhealth.app.service.*StabilityTest
com.batteryhealth.app.ui.viewmodel.*StabilityTest
com.batteryhealth.app.utils.healthcheck.*StabilityTest
com.batteryhealth.app.utils.healthcheck.*Test
com.batteryhealth.app.utils.ThreadExecutorStabilityTest
com.batteryhealth.app.utils.ConstantsTest
com.batteryhealth.app.utils.LogHelperTest
com.batteryhealth.app.utils.SystemPropertiesCompatTest
com.batteryhealth.app.data.worker.WorkerStabilityTest"

CATEGORY_TESTS[performance]="com.batteryhealth.app.data.database.AppDatabaseStabilityTest
com.batteryhealth.app.utils.BatteryDataManagerStabilityTest
com.batteryhealth.app.utils.healthcheck.HealthCheckEngineStabilityTest
com.batteryhealth.app.utils.ThreadExecutorStabilityTest
com.batteryhealth.app.utils.SystemPropertiesCompatTest"

CATEGORY_TESTS[security]="com.batteryhealth.app.data.database.DatabaseEncryptionHelperSecurityTest
com.batteryhealth.app.utils.PermissionManagerSecurityTest
com.batteryhealth.app.data.database.ConvertersStabilityTest"

CATEGORY_TESTS[install]="com.batteryhealth.app.integration.InstallUninstallIntegrationTest"

# 选择测试过滤器
TEST_FILTER=""
case "$CATEGORY" in
    all)
        TEST_FILTER="com.batteryhealth.app.**"
        ;;
    stability|performance|security|install)
        for pkg in "${CATEGORY_TESTS[$CATEGORY]}"; do
            if [ -n "$TEST_FILTER" ]; then
                TEST_FILTER="$TEST_FILTER + $pkg"
            else
                TEST_FILTER="$pkg"
            fi
        done
        ;;
    *)
        echo -e "${RED}未知分类: $CATEGORY${NC}" | tee -a "$LOG_FILE"
        echo "支持: all | stability | performance | security | install | fast" | tee -a "$LOG_FILE"
        exit 1
        ;;
esac

echo "" | tee -a "$LOG_FILE"
echo "测试过滤器: $TEST_FILTER" | tee -a "$LOG_FILE"
echo "" | tee -a "$LOG_FILE"

# 检测 gradle
if [ -x "$PROJECT_ROOT/gradlew" ]; then
    GRADLE_CMD="$PROJECT_ROOT/gradlew"
else
    GRADLE_CMD="gradle"
fi

# JVM 选项
GRADLE_OPTS="${GRADLE_OPTS:--Xmx2g -Dfile.encoding=UTF-8}"

# 执行测试
TEST_RESULT=0
echo "==========================================" | tee -a "$LOG_FILE"
echo "开始执行单元测试..." | tee -a "$LOG_FILE"
echo "==========================================" | tee -a "$LOG_FILE"

if [ "$SKIP_SLOW" = "true" ]; then
    # 跳过性能压力测试
    SLOW_FILTER=""
    SLOW_FILTER+=" --tests \"*performance*10000*\" --tests \"*Heavy*\" --tests \"*Stress*\""
    # 跑全部但排除慢测试
    $GRADLE_CMD $GRADLE_OPTS :app:testDebugUnitTest \
        --tests "com.batteryhealth.app.**" \
        -x :app:lintAnalyzeDebug \
        --rerun-tasks 2>&1 | tee -a "$LOG_FILE" || TEST_RESULT=$?
else
    $GRADLE_CMD $GRADLE_OPTS :app:testDebugUnitTest \
        --tests "$TEST_FILTER" \
        -x :app:lintAnalyzeDebug \
        --rerun-tasks 2>&1 | tee -a "$LOG_FILE" || TEST_RESULT=$?
fi

# 检查测试结果
if [ $TEST_RESULT -eq 0 ]; then
    echo "" | tee -a "$LOG_FILE"
    echo -e "${GREEN}==========================================" | tee -a "$LOG_FILE"
    echo -e "所有测试通过${NC}" | tee -a "$LOG_FILE"
    echo -e "${GREEN}==========================================" | tee -a "$LOG_FILE"
else
    echo "" | tee -a "$LOG_FILE"
    echo -e "${RED}==========================================" | tee -a "$LOG_FILE"
    echo -e "部分测试失败 (退出码: $TEST_RESULT)${NC}" | tee -a "$LOG_FILE"
    echo -e "${RED}==========================================" | tee -a "$LOG_FILE"
fi

# 解析 XML 测试报告
echo "" | tee -a "$LOG_FILE"
echo "生成测试摘要..." | tee -a "$LOG_FILE"

SUMMARY_FILE="$REPORT_DIR/summary_${TIMESTAMP}.txt"
cat > "$SUMMARY_FILE" << EOF
========================================
BatteryHealthApp 测试运行摘要
========================================
时间戳: $TIMESTAMP
分类: $CATEGORY
跳过慢测试: $SKIP_SLOW
退出码: $TEST_RESULT

EOF

# 收集测试结果统计
if [ -d "$APP_DIR/build/test-results/testDebugUnitTest" ]; then
    TOTAL_TESTS=0
    TOTAL_FAILURES=0
    TOTAL_ERRORS=0
    TOTAL_SKIPPED=0
    TOTAL_TIME=0

    for xml in $(find "$APP_DIR/build/test-results/testDebugUnitTest" -name "TEST-*.xml"); do
        if [ -f "$xml" ]; then
            tests=$(grep -oP 'tests="\K[0-9]+' "$xml" | head -1)
            failures=$(grep -oP 'failures="\K[0-9]+' "$xml" | head -1)
            errors=$(grep -oP 'errors="\K[0-9]+' "$xml" | head -1)
            skipped=$(grep -oP 'skipped="\K[0-9]+' "$xml" | head -1)
            time=$(grep -oP 'time="\K[0-9.]+' "$xml" | head -1)

            TOTAL_TESTS=$((TOTAL_TESTS + ${tests:-0}))
            TOTAL_FAILURES=$((TOTAL_FAILURES + ${failures:-0}))
            TOTAL_ERRORS=$((TOTAL_ERRORS + ${errors:-0}))
            TOTAL_SKIPPED=$((TOTAL_SKIPPED + ${skipped:-0}))

            classname=$(basename "$xml" .xml | sed 's/^TEST-//')
            echo "  $classname: tests=$tests failures=$failures errors=$errors skipped=$skipped time=${time}s" >> "$SUMMARY_FILE"
        fi
    done

    cat >> "$SUMMARY_FILE" << EOF
----------------------------------------
总计:
  测试数:    $TOTAL_TESTS
  失败数:    $TOTAL_FAILURES
  错误数:    $TOTAL_ERRORS
  跳过数:    $TOTAL_SKIPPED
  通过数:    $((TOTAL_TESTS - TOTAL_FAILURES - TOTAL_ERRORS - TOTAL_SKIPPED))
EOF

    cat "$SUMMARY_FILE" | tee -a "$LOG_FILE"
fi

# 报告路径
echo "" | tee -a "$LOG_FILE"
echo "报告路径:" | tee -a "$LOG_FILE"
echo "  - HTML 报告: $APP_DIR/build/reports/tests/testDebugUnitTest/index.html" | tee -a "$LOG_FILE"
echo "  - XML 报告:  $APP_DIR/build/test-results/testDebugUnitTest/" | tee -a "$LOG_FILE"
echo "  - 文本摘要:  $SUMMARY_FILE" | tee -a "$LOG_FILE"
echo "  - 运行日志:  $LOG_FILE" | tee -a "$LOG_FILE"

# 打开报告（仅桌面环境）
if [ -n "$DISPLAY" ] && [ -f "$APP_DIR/build/reports/tests/testDebugUnitTest/index.html" ]; then
    echo "" | tee -a "$LOG_FILE"
    echo "正在打开 HTML 报告..." | tee -a "$LOG_FILE"
    xdg-open "$APP_DIR/build/reports/tests/testDebugUnitTest/index.html" 2>/dev/null || true
fi

exit $TEST_RESULT
