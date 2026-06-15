import java.io.*;
import java.lang.reflect.*;
import java.util.zip.*;

/**
 * BatteryParser v2.1.10 端到端单元测试
 * - 使用真实的 Android 编译后 .class 文件
 * - 模拟华为 PMA120、小米 MIUI、三星 OneUI、OPPO、vivo 等真实 bugreport 场景
 * - 重点验证：healthd 格式、循环次数、容量、温度、电压、品牌归属
 * - 新增：healthd 短格式（l=80 v=4000000 t=285 c=6300000）
 */
public class End2EndTest {

    static int passed = 0;
    static int failed = 0;

    static Class<?> parserClass;
    static Class<?> infoClass;
    static Method processZipStream;

    public static void main(String[] args) throws Exception {
        // 加载真实的 BatteryParser class
        String classPath = "/workspace/BatteryHealthApp/app/build/intermediates/javac/release/classes";
        String androidJar = "/opt/android-sdk/platforms/android-34/android.jar";
        java.net.URLClassLoader cl = new java.net.URLClassLoader(
            new java.net.URL[]{
                new java.io.File(classPath).toURI().toURL(),
                new java.io.File(androidJar).toURI().toURL()
            },
            End2EndTest.class.getClassLoader()
        );
        parserClass = cl.loadClass("com.batteryhealth.app.BatteryParser");
        infoClass = cl.loadClass("com.batteryhealth.app.BatteryParser$BatteryInfo");
        processZipStream = parserClass.getMethod("processZipStream",
            InputStream.class, cl.loadClass("com.batteryhealth.app.BatteryParser$ProgressCallback"));

        System.out.println("================================================================");
        System.out.println("BatteryParser v2.1.10 端到端测试");
        System.out.println("================================================================\n");

        testHuaweiPMA120();
        testHuaweiHealthdFormat();
        testXiaomiMIUI();
        testSamsungOneUI();
        testOPPOColorOS();
        testVivo();
        testEdgeCases();
        testUnitConversion();
        testCcAmbiguity();

        System.out.println("\n================================================================");
        System.out.println("测试汇总：通过 " + passed + " / 失败 " + failed);
        System.out.println("================================================================");
        if (failed > 0) System.exit(1);
    }

    static Object parseZip(String zipPath) throws Exception {
        FileInputStream fis = new FileInputStream(zipPath);
        Object result = processZipStream.invoke(null, fis, null);
        fis.close();
        return result;
    }

    static int getInt(Object info, String field) throws Exception {
        if (info == null) return -1;
        Field f = infoClass.getField(field);
        return f.getInt(info);
    }
    static long getLong(Object info, String field) throws Exception {
        if (info == null) return -1;
        Field f = infoClass.getField(field);
        return f.getLong(info);
    }
    static double getDouble(Object info, String field) throws Exception {
        if (info == null) return -1;
        Field f = infoClass.getField(field);
        return f.getDouble(info);
    }
    static String getString(Object info, String field) throws Exception {
        if (info == null) return null;
        Field f = infoClass.getField(field);
        return (String) f.get(info);
    }

    static void assertEq(String name, long expected, long actual) {
        if (expected == actual) {
            System.out.println("  [PASS] " + name + " = " + actual);
            passed++;
        } else {
            System.out.println("  [FAIL] " + name + " expected=" + expected + " got=" + actual);
            failed++;
        }
    }
    static void assertEqDouble(String name, double expected, double actual) {
        if (Math.abs(expected - actual) < 0.01) {
            System.out.println("  [PASS] " + name + " = " + actual);
            passed++;
        } else {
            System.out.println("  [FAIL] " + name + " expected=" + expected + " got=" + actual);
            failed++;
        }
    }
    static void assertEqStr(String name, String expected, String actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            System.out.println("  [PASS] " + name + " = '" + actual + "'");
            passed++;
        } else {
            System.out.println("  [FAIL] " + name + " expected='" + expected + "' got='" + actual + "'");
            failed++;
        }
    }

    static void createZip(String path, String entryName, String content) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path))) {
            ZipEntry e = new ZipEntry(entryName);
            zos.putNextEntry(e);
            zos.write(content.getBytes("UTF-8"));
            zos.closeEntry();
        }
    }

    static void testHuaweiPMA120() throws Exception {
        System.out.println("--- 测试 1: 华为 PMA120 bugreport (标准格式) ---");
        String content = "====== dumpstate ======\n" +
            "Build fingerprint: 'HUAWEI/PMA120-HARMONYOS/HUAWEIPMA120:14/BP2A.250605.015'\n" +
            "------ DUMP OF SERVICE batterystats ------\n" +
            "  Charge type: 1\n" +
            "  Charge status: 2\n" +
            "  Current now: -350000 ua\n" +
            "  Battery level: 80\n" +
            "  Charge counter: 6300000\n" +
            "  Full charge capacity: 6300 mAh\n" +
            "  Design capacity: 7050 mAh\n" +
            "  Cycle count: 628\n" +
            "  Temperature: 285\n" +
            "  Voltage: 4000000\n" +
            "  Technology: Li-poly\n" +
            "------ END BATTERY ------\n" +
            "Healthd:\n" +
            "  Battery present: 1\n" +
            "  Battery charge counter: 6300000\n" +
            "  Battery full charge capacity: 6300 mAh\n" +
            "  Battery design capacity: 7050 mAh\n" +
            "  Battery cycle count: 628\n" +
            "  Battery temperature: 285\n" +
            "------ END HEALTHD ------\n" +
            "====== END ======";
        createZip("/tmp/test_huawei.zip", "dumpstate.txt", content);
        Object info = parseZip("/tmp/test_huawei.zip");

        assertEq("Huawei currentCapacity (mAh)", 6300, getInt(info, "currentCapacity"));
        assertEq("Huawei chargeCounter", 6300000, getInt(info, "chargeCounter"));
        assertEq("Huawei designCapacity (mAh)", 7050, getInt(info, "designCapacity"));
        assertEq("Huawei cycleCount", 628, getInt(info, "cycleCount"));
        assertEqDouble("Huawei batteryTemp (°C)", 28.5, getDouble(info, "batteryTemp"));
        assertEq("Huawei voltage (mV)", 4000, getInt(info, "voltage"));
        assertEqStr("Huawei brand", "huawei", getString(info, "brand"));
        assertEqStr("Huawei technology", "Li-poly", getString(info, "technology"));

        long cc = getInt(info, "currentCapacity");
        long dc = getInt(info, "designCapacity");
        double health = (double) cc / dc * 100.0;
        System.out.println("  [INFO] 期望健康度: " + String.format("%.1f", health) + "%");
        if (health >= 89.0 && health <= 90.0) {
            System.out.println("  [PASS] 期望健康度计算正确 (89.4%)");
            passed++;
        } else {
            System.out.println("  [FAIL] 期望健康度异常: " + health);
            failed++;
        }
        System.out.println();
    }

    static void testHuaweiHealthdFormat() throws Exception {
        System.out.println("--- 测试 2: 华为 healthd 短格式 (关键场景) ---");
        // 这是最常见的华为 bugreport 格式
        String content = "====== dumpstate ======\n" +
            "Build: HUAWEI/PMA120 HARMONYOS\n" +
            "healthd:\n" +
            "  battery: l=80 v=4000000 t=285 h=2 st=2 c=6300000 chg=a\n" +
            "  cc: 628\n" +
            "  Design capacity: 7050\n" +
            "  Full charge capacity: 6300\n" +
            "------ END ------\n";
        createZip("/tmp/test_huawei_healthd.zip", "dumpstate.txt", content);
        Object info = parseZip("/tmp/test_huawei_healthd.zip");

        assertEq("Healthd currentCapacity (mAh)", 6300, getInt(info, "currentCapacity"));
        assertEq("Healthd designCapacity (mAh)", 7050, getInt(info, "designCapacity"));
        assertEq("Healthd cycleCount", 628, getInt(info, "cycleCount"));
        assertEqDouble("Healthd batteryTemp (°C)", 28.5, getDouble(info, "batteryTemp"));
        assertEq("Healthd voltage (mV)", 4000, getInt(info, "voltage"));
        assertEqStr("Healthd brand", "huawei", getString(info, "brand"));
        System.out.println();
    }

    static void testXiaomiMIUI() throws Exception {
        System.out.println("--- 测试 3: 小米 MIUI bugreport ---");
        String content = "====== dumpstate ======\n" +
            "MIUI Build: OS1.0.5\n" +
            "Battery Service:\n" +
            "  Charge counter: 4250000 uAh\n" +
            "  Min learned battery capacity: 4150\n" +
            "  Maximum learned battery capacity: 4500\n" +
            "  Cycle count: 312\n" +
            "  Temperature: 295\n" +
            "  Voltage: 3980000\n" +
            "  Technology: Li-poly\n" +
            "  Current now: -320000 ua\n" +
            "====== END ======";
        createZip("/tmp/test_xiaomi.zip", "dumpstate.txt", content);
        Object info = parseZip("/tmp/test_xiaomi.zip");

        assertEq("Xiaomi currentCapacity (use min_learned)", 4150, getInt(info, "currentCapacity"));
        assertEq("Xiaomi cycleCount", 312, getInt(info, "cycleCount"));
        assertEqDouble("Xiaomi batteryTemp (°C)", 29.5, getDouble(info, "batteryTemp"));
        assertEq("Xiaomi voltage (mV)", 3980, getInt(info, "voltage"));
        assertEqStr("Xiaomi brand", "xiaomi", getString(info, "brand"));
        assertEqStr("Xiaomi technology", "Li-poly", getString(info, "technology"));
        System.out.println();
    }

    static void testSamsungOneUI() throws Exception {
        System.out.println("--- 测试 4: 三星 OneUI bugreport ---");
        String content = "====== dumpstate ======\n" +
            "Build: OneUI 6.0\n" +
            "------ DUMP OF SERVICE batterystats ------\n" +
            "  Charge counter: 4500000 uAh\n" +
            "  Full charge capacity: 4500 mAh\n" +
            "  Cycle count: 156\n" +
            "  Temperature: 278\n" +
            "  Voltage: 3950000\n" +
            "  Technology: Li-ion\n" +
            "  battery_health: 90%\n" +
            "====== END ======";
        createZip("/tmp/test_samsung.zip", "dumpstate.txt", content);
        Object info = parseZip("/tmp/test_samsung.zip");

        assertEq("Samsung currentCapacity", 4500, getInt(info, "currentCapacity"));
        assertEq("Samsung cycleCount", 156, getInt(info, "cycleCount"));
        assertEqDouble("Samsung batteryTemp (°C)", 27.8, getDouble(info, "batteryTemp"));
        assertEq("Samsung voltage (mV)", 3950, getInt(info, "voltage"));
        assertEqStr("Samsung brand", "samsung", getString(info, "brand"));
        assertEqStr("Samsung technology", "Li-ion", getString(info, "technology"));
        System.out.println();
    }

    static void testOPPOColorOS() throws Exception {
        System.out.println("--- 测试 5: OPPO ColorOS bugreport ---");
        String content = "====== dumpstate ======\n" +
            "ColorOS: 14\n" +
            "Battery Health:\n" +
            "  charge_counter: 5000000\n" +
            "  full charge capacity: 5000\n" +
            "  cycle count: 89\n" +
            "  temperature: 280\n" +
            "  voltage: 4100\n" +
            "  technology: li-po\n" +
            "====== END ======";
        createZip("/tmp/test_oppo.zip", "dumpstate.txt", content);
        Object info = parseZip("/tmp/test_oppo.zip");

        assertEq("OPPO currentCapacity", 5000, getInt(info, "currentCapacity"));
        assertEq("OPPO cycleCount", 89, getInt(info, "cycleCount"));
        assertEqDouble("OPPO batteryTemp (°C)", 28.0, getDouble(info, "batteryTemp"));
        assertEqStr("OPPO brand", "oppo", getString(info, "brand"));
        System.out.println();
    }

    static void testVivo() throws Exception {
        System.out.println("--- 测试 6: vivo OriginOS bugreport ---");
        String content = "====== dumpstate ======\n" +
            "OriginOS: 4\n" +
            "  CHARGE_COUNTER: 4800000\n" +
            "  Full charge capacity: 4800 mAh\n" +
            "  Cycle count: 245\n" +
            "  Temperature: 27.5\n" +
            "  Voltage: 3.98\n" +
            "  Technology: Li-ion\n" +
            "====== END ======";
        createZip("/tmp/test_vivo.zip", "dumpstate.txt", content);
        Object info = parseZip("/tmp/test_vivo.zip");

        assertEq("vivo currentCapacity", 4800, getInt(info, "currentCapacity"));
        assertEq("vivo cycleCount", 245, getInt(info, "cycleCount"));
        assertEqDouble("vivo batteryTemp (°C)", 27.5, getDouble(info, "batteryTemp"));
        assertEq("vivo voltage (mV)", 3980, getInt(info, "voltage"));
        assertEqStr("vivo brand", "vivo", getString(info, "brand"));
        System.out.println();
    }

    static void testEdgeCases() throws Exception {
        System.out.println("--- 测试 7: 边界情况 ---");

        // 仅 cycle_count
        String c1 = "Battery Info: Cycle count: 200";
        createZip("/tmp/test_edge1.zip", "log.txt", c1);
        Object i1 = parseZip("/tmp/test_edge1.zip");
        assertEq("Edge1 仅 cycle count", 200, getInt(i1, "cycleCount"));

        // 温度 0.01°C (2850)
        String c2 = "Charge counter: 7050000\nCycle count: 100\nTemperature: 2850\n";
        createZip("/tmp/test_edge2.zip", "log.txt", c2);
        Object i2 = parseZip("/tmp/test_edge2.zip");
        assertEqDouble("Edge2 温度 0.01°C 单位", 28.5, getDouble(i2, "batteryTemp"));

        // 中文
        String c3 = "Charge counter: 7050000\n充电循环次数：628\n电池温度：28.5℃\n";
        createZip("/tmp/test_edge3.zip", "log.txt", c3);
        Object i3 = parseZip("/tmp/test_edge3.zip");
        assertEq("Edge3 中文循环次数", 628, getInt(i3, "cycleCount"));
        assertEqDouble("Edge3 中文温度", 28.5, getDouble(i3, "batteryTemp"));

        // 大写
        String c4 = "CHARGE_COUNTER: 7050000\nCYCLE_COUNT: 100\n";
        createZip("/tmp/test_edge4.zip", "log.txt", c4);
        Object i4 = parseZip("/tmp/test_edge4.zip");
        assertEq("Edge4 大写 CHARGE_COUNTER", 7050, getInt(i4, "currentCapacity"));
        assertEq("Edge4 大写 CYCLE_COUNT", 100, getInt(i4, "cycleCount"));

        // Android 成员变量格式 mChargeCounter
        String c5 = "mChargeCounter: 6300000\nmDesignCapacity: 7050\nmCycleCount: 628\nmBatteryTemperature: 285\n";
        createZip("/tmp/test_edge5.zip", "log.txt", c5);
        Object i5 = parseZip("/tmp/test_edge5.zip");
        assertEq("Edge5 mChargeCounter", 6300, getInt(i5, "currentCapacity"));
        assertEq("Edge5 mDesignCapacity", 7050, getInt(i5, "designCapacity"));
        assertEq("Edge5 mCycleCount", 628, getInt(i5, "cycleCount"));
        assertEqDouble("Edge5 mBatteryTemperature", 28.5, getDouble(i5, "batteryTemp"));

        System.out.println();
    }

    static void testUnitConversion() throws Exception {
        System.out.println("--- 测试 8: 单位转换精度 ---");

        // 关键场景: 7050000 uAh → 7050 mAh
        String c1 = "Charge counter: 7050000";
        createZip("/tmp/test_unit1.zip", "log.txt", c1);
        Object i1 = parseZip("/tmp/test_unit1.zip");
        assertEq("7050000 uAh → 7050 mAh", 7050, getInt(i1, "currentCapacity"));

        // 7000000 uAh → 7000 mAh
        String c2 = "Charge counter: 7000000";
        createZip("/tmp/test_unit2.zip", "log.txt", c2);
        Object i2 = parseZip("/tmp/test_unit2.zip");
        assertEq("7000000 uAh → 7000 mAh", 7000, getInt(i2, "currentCapacity"));

        // 7050 直接 mAh
        String c3 = "Charge counter: 7050";
        createZip("/tmp/test_unit3.zip", "log.txt", c3);
        Object i3 = parseZip("/tmp/test_unit3.zip");
        assertEq("7050 mAh 保持不变", 7050, getInt(i3, "currentCapacity"));

        // 关键: cc: 628 不能被误识别为 charge_counter
        String c4 = "Charge counter: 7050000\nCycle count: 628";
        createZip("/tmp/test_unit4.zip", "log.txt", c4);
        Object i4 = parseZip("/tmp/test_unit4.zip");
        assertEq("cc: 628 不能误识别为 charge_counter", 7050, getInt(i4, "currentCapacity"));
        assertEq("cycleCount 仍正确为 628", 628, getInt(i4, "cycleCount"));

        System.out.println();
    }

    static void testCcAmbiguity() throws Exception {
        System.out.println("--- 测试 9: cc 歧义处理（关键场景） ---");

        // 场景1: cc: 628 应识别为循环次数，不是充电计数
        String c1 = "cc: 628\nDesign capacity: 7050\nFull charge capacity: 6300\n";
        createZip("/tmp/test_cc1.zip", "log.txt", c1);
        Object i1 = parseZip("/tmp/test_cc1.zip");
        assertEq("cc:628 → cycleCount", 628, getInt(i1, "cycleCount"));
        assertEq("cc:628 → currentCapacity=6300", 6300, getInt(i1, "currentCapacity"));

        // 场景2: cc: 6300000 应识别为充电计数
        String c2 = "cc: 6300000\nCycle count: 628\nDesign capacity: 7050\n";
        createZip("/tmp/test_cc2.zip", "log.txt", c2);
        Object i2 = parseZip("/tmp/test_cc2.zip");
        assertEq("cc:6300000 → currentCapacity=6300", 6300, getInt(i2, "currentCapacity"));
        assertEq("cc:6300000 → cycleCount=628", 628, getInt(i2, "cycleCount"));

        // 场景3: healthd 格式 c=6300000 cc=628
        String c3 = "healthd:\n  battery: l=80 v=4000000 t=285 h=2 st=2 c=6300000 chg=a\n  cc=628\n";
        createZip("/tmp/test_cc3.zip", "dumpstate.txt", c3);
        Object i3 = parseZip("/tmp/test_cc3.zip");
        assertEq("healthd c=6300000 → currentCapacity=6300", 6300, getInt(i3, "currentCapacity"));
        assertEq("healthd cc=628 → cycleCount=628", 628, getInt(i3, "cycleCount"));
        assertEqDouble("healthd t=285 → batteryTemp=28.5", 28.5, getDouble(i3, "batteryTemp"));
        assertEq("healthd v=4000000 → voltage=4000", 4000, getInt(i3, "voltage"));

        System.out.println();
    }
}
