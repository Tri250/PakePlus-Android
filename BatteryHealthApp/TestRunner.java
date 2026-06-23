import java.util.ArrayList;
import java.util.List;

/**
 * 独立测试运行器 - 验证核心逻辑
 * 不依赖Android SDK，纯Java运行
 */
public class TestRunner {

    private static int passed = 0;
    private static int failed = 0;
    private static List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  BatteryHealthApp 核心逻辑测试套件");
        System.out.println("========================================\n");

        long startTime = System.currentTimeMillis();

        // 1. 健康度计算测试
        runHealthCalculationTests();

        // 2. 电池来源判断测试
        runBatterySourceTests();

        // 3. 边界条件测试
        runBoundaryTests();

        // 4. 性能测试
        runPerformanceTests();

        // 5. 稳定性测试
        runStabilityTests();

        // 6. UI/UX测试
        runUiUxTests();

        long duration = System.currentTimeMillis() - startTime;

        // 输出结果
        System.out.println("\n========================================");
        System.out.println("  测试结果汇总");
        System.out.println("========================================");
        System.out.println("总测试数: " + (passed + failed));
        System.out.println("通过: " + passed);
        System.out.println("失败: " + failed);
        System.out.println("耗时: " + duration + "ms");
        System.out.println("通过率: " + String.format("%.1f%%", (passed * 100.0 / (passed + failed))));

        if (!failures.isEmpty()) {
            System.out.println("\n失败详情:");
            for (String failure : failures) {
                System.out.println("  - " + failure);
            }
        }

        System.out.println("\n========================================");
        if (failed == 0) {
            System.out.println("  所有测试通过！");
        } else {
            System.out.println("  存在 " + failed + " 个失败测试");
        }
        System.out.println("========================================");

        System.exit(failed > 0 ? 1 : 0);
    }

    private static void runHealthCalculationTests() {
        System.out.println("【健康度计算测试】");

        // 测试1: 完美健康度
        test("完美健康度 (100%)", () -> {
            float health = calculateHealth(4000, 4000, 0, 0);
            assertEquals(100f, health, 0.5f);
        });

        // 测试2: 良好健康度
        test("良好健康度 (90%)", () -> {
            float health = calculateHealth(4000, 3600, 0, 0);
            assertEquals(90f, health, 0.5f);
        });

        // 测试3: 一般健康度
        test("一般健康度 (75%)", () -> {
            float health = calculateHealth(4000, 3000, 0, 0);
            assertEquals(75f, health, 0.5f);
        });

        // 测试4: 较差健康度
        test("较差健康度 (60%)", () -> {
            float health = calculateHealth(4000, 2400, 0, 0);
            assertEquals(60f, health, 0.5f);
        });

        // 测试5: 极差健康度
        test("极差健康度 (50%)", () -> {
            float health = calculateHealth(4000, 2000, 0, 0);
            assertEquals(50f, health, 0.5f);
        });

        // 测试6: 超出设计容量应被限制到100%
        test("超出设计容量限制", () -> {
            float health = calculateHealth(4000, 4500, 0, 0);
            assertEquals(100f, health, 0.1f);
        });

        // 测试7: 使用天数估算
        test("使用天数估算", () -> {
            float health = calculateHealth(4000, 0, 0, 100);
            assertTrue(health > 0 && health < 100);
        });

        System.out.println();
    }

    private static void runBatterySourceTests() {
        System.out.println("【电池来源判断测试】");

        // 测试1: 原厂电池
        test("原厂电池判断", () -> {
            String source = determineSource("BYD1234567890ABC", "byd", "ABC123456789", 4400, 4500);
            assertEquals("original", source);
        });

        // 测试2: 第三方电池
        test("第三方电池判断", () -> {
            String source = determineSource("NO_NAME!!", "unknown", "123", 5000, 4500);
            assertEquals("third_party", source);
        });

        // 测试3: 信息不足
        test("信息不足判断", () -> {
            String source = determineSource(null, null, null, 0, 0);
            assertEquals("unknown", source);
        });

        // 测试4: 已知制造商
        test("已知制造商 - BYD", () -> {
            String source = determineSource(null, "byd", null, 0, 0);
            assertTrue(source.equals("original") || source.equals("unknown"));
        });

        // 测试5: 容量比例正常
        test("容量比例正常", () -> {
            String source = determineSource(null, null, null, 4300, 4500);
            assertTrue(source.equals("original") || source.equals("unknown"));
        });

        System.out.println();
    }

    private static void runBoundaryTests() {
        System.out.println("【边界条件测试】");

        // 测试1: 零值处理
        test("零设计容量", () -> {
            float health = calculateHealth(0, 4200, 0, 0);
            assertEquals(-1f, health, 0.01f);
        });

        // 测试2: 负值处理
        test("负设计容量", () -> {
            float health = calculateHealth(-1, 4200, 0, 0);
            assertEquals(-1f, health, 0.01f);
        });

        // 测试3: 极大值
        test("极大容量值", () -> {
            float health = calculateHealth(Integer.MAX_VALUE, 4200, 0, 0);
            assertTrue(health >= 0 && health <= 100);
        });

        // 测试4: 极小值
        test("极小容量值", () -> {
            float health = calculateHealth(1, 1, 0, 0);
            assertEquals(100f, health, 0.01f);
        });

        // 测试5: 健康度等级边界
        test("健康度等级边界 - 95%", () -> {
            assertEquals("A+", calculateGrade(95f));
        });

        test("健康度等级边界 - 85%", () -> {
            assertEquals("A-", calculateGrade(85f));
        });

        test("健康度等级边界 - 75%", () -> {
            assertEquals("B", calculateGrade(75f));
        });

        test("健康度等级边界 - 60%", () -> {
            assertEquals("C", calculateGrade(60f));
        });

        System.out.println();
    }

    private static void runPerformanceTests() {
        System.out.println("【性能测试】");

        // 测试1: 单次计算性能
        test("单次计算性能 (<1ms)", () -> {
            long start = System.nanoTime();
            calculateHealth(4500, 4200, 100, 365);
            long duration = (System.nanoTime() - start) / 1000;
            assertTrue(duration < 1000);
        });

        // 测试2: 批量计算性能
        test("批量计算性能 (1000次 <100ms)", () -> {
            long start = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                calculateHealth(4500, 4000 + i % 500, i % 200, 365);
            }
            long duration = (System.nanoTime() - start) / 1_000_000;
            assertTrue(duration < 100);
        });

        // 测试3: 内存分配
        test("内存分配测试", () -> {
            Runtime runtime = Runtime.getRuntime();
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            long before = runtime.totalMemory() - runtime.freeMemory();

            for (int i = 0; i < 1000; i++) {
                calculateHealth(4500, 4000 + i % 500, i % 200, 365);
            }

            long after = runtime.totalMemory() - runtime.freeMemory();
            long growth = (after - before) / 1024;
            assertTrue(growth < 500);
        });

        System.out.println();
    }

    private static void runStabilityTests() {
        System.out.println("【稳定性测试】");

        // 测试1: 并发安全
        test("并发计算安全", () -> {
            final boolean[] success = {true};
            Thread[] threads = new Thread[10];
            for (int t = 0; t < 10; t++) {
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < 100; i++) {
                        try {
                            calculateHealth(4500, 4000 + i % 500, i % 200, 365);
                        } catch (Exception e) {
                            success[0] = false;
                        }
                    }
                });
                threads[t].start();
            }
            for (Thread thread : threads) {
                try { thread.join(5000); } catch (InterruptedException e) {}
            }
            assertTrue(success[0]);
        });

        // 测试2: 快速连续调用
        test("快速连续调用", () -> {
            for (int i = 0; i < 10000; i++) {
                calculateHealth(4500, 4000 + i % 500, i % 200, 365);
            }
            assertTrue(true);
        });

        System.out.println();
    }

    private static void runUiUxTests() {
        System.out.println("【UI/UX测试】");

        // 测试1: 充电类型标签
        test("充电类型 - 超级快充", () -> {
            assertEquals("超级快充", getChargeTypeLabel(120f));
        });

        test("充电类型 - 快充", () -> {
            assertEquals("快充", getChargeTypeLabel(45f));
        });

        test("充电类型 - 未充电", () -> {
            assertEquals("未充电", getChargeTypeLabel(0f));
        });

        // 测试2: 健康度等级
        test("健康度等级 - A+", () -> {
            assertEquals("A+", calculateGrade(97f));
        });

        test("健康度等级 - D", () -> {
            assertEquals("D", calculateGrade(50f));
        });

        // 测试3: 进度条计算
        test("进度条计算 - 50%", () -> {
            assertEquals(50, calculateProgress(50f, 0f, 100f));
        });

        test("进度条计算 - 越界限制", () -> {
            assertEquals(0, calculateProgress(-10f, 0f, 100f));
            assertEquals(100, calculateProgress(110f, 0f, 100f));
        });

        System.out.println();
    }

    // ============== 核心逻辑实现 ==============

    private static float calculateHealth(int designCapacity, int currentCapacity, int cycleCount, int usageDays) {
        if (currentCapacity > 0 && designCapacity > 0) {
            float ratio = (currentCapacity / (float) designCapacity) * 100f;
            return clampHealth(ratio);
        }

        if (usageDays > 0 && designCapacity > 0) {
            float daysLoss = usageDays * 0.026f;
            return clampHealth(100f - daysLoss);
        }

        return -1;
    }

    private static float clampHealth(float v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private static String determineSource(String vendorInfo, String manufacturer, String serial,
                                          int fullCapacity, int designCapacity) {
        float total = 0f;

        if (vendorInfo != null && !vendorInfo.isEmpty()) {
            if (looksLikeOemSerial(vendorInfo)) total += 0.4f;
            else total -= 0.3f;
        }

        if (manufacturer != null && !manufacturer.isEmpty()) {
            if (manufacturer.toLowerCase().matches(".*(coslight|sunwoda|byd|lg|chem|sanyo|tdk).*")) {
                total += 0.3f;
            } else if (manufacturer.equalsIgnoreCase("unknown") || manufacturer.equalsIgnoreCase("0")) {
                total -= 0.1f;
            }
        }

        if (serial != null && !serial.isEmpty() && !serial.equalsIgnoreCase("unknown")) {
            if (isValidOemSerialFormat(serial)) total += 0.25f;
            else total -= 0.35f;
        }

        if (designCapacity > 0 && fullCapacity > 0) {
            float ratio = fullCapacity / (float) designCapacity;
            if (ratio >= 0.85f && ratio <= 1.05f) total += 0.3f;
            else if (ratio >= 0.55f && ratio <= 1.25f) total += 0f;
            else total -= 0.5f;
        }

        if (total >= 0.5f) return "original";
        else if (total <= -0.3f) return "third_party";
        else return "unknown";
    }

    private static boolean isValidOemSerialFormat(String serial) {
        if (serial == null || serial.length() < 10 || serial.length() > 24) return false;
        int letters = 0, digits = 0;
        for (int i = 0; i < serial.length(); i++) {
            char c = serial.charAt(i);
            if (Character.isLetter(c)) letters++;
            else if (Character.isDigit(c)) digits++;
            else return false;
        }
        return letters >= 3 && digits >= 3;
    }

    private static boolean looksLikeOemSerial(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < 8 || t.length() > 64) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\n' || c == '\r') continue;
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ') continue;
            return false;
        }
        return true;
    }

    private static String calculateGrade(float health) {
        if (health >= 95) return "A+";
        if (health >= 90) return "A";
        if (health >= 85) return "A-";
        if (health >= 80) return "B+";
        if (health >= 75) return "B";
        if (health >= 70) return "B-";
        if (health >= 60) return "C";
        return "D";
    }

    private static String getChargeTypeLabel(float powerW) {
        if (powerW >= 100) return "超级快充";
        if (powerW >= 60) return "极速快充";
        if (powerW >= 30) return "快充";
        if (powerW >= 10) return "普通充电";
        if (powerW > 0) return "慢速充电";
        return "未充电";
    }

    private static int calculateProgress(float value, float min, float max) {
        if (value <= min) return 0;
        if (value >= max) return 100;
        return (int) ((value - min) / (max - min) * 100);
    }

    // ============== 测试框架 ==============

    private static void test(String name, TestCase testCase) {
        try {
            testCase.run();
            passed++;
            System.out.println("  [PASS] " + name);
        } catch (AssertionError e) {
            failed++;
            failures.add(name + ": " + e.getMessage());
            System.out.println("  [FAIL] " + name + " - " + e.getMessage());
        } catch (Exception e) {
            failed++;
            failures.add(name + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            System.out.println("  [FAIL] " + name + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertEquals(float expected, float actual, float delta) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError("Expected: " + expected + ", Actual: " + actual + ", Delta: " + delta);
        }
    }

    private static void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected true but was false");
        }
    }

    @FunctionalInterface
    interface TestCase {
        void run() throws Exception;
    }
}
