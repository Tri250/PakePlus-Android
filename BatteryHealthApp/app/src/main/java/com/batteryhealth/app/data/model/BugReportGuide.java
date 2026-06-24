package com.batteryhealth.app.data.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BugReportGuide {

    public static class BrandGuide {
        public String brand;
        public String brandZh;
        public String[] steps;
        public String[] adbCommands;
        public String[] notes;
        public String screenshotGuide;

        public BrandGuide(String brand, String brandZh, String[] steps,
                         String[] adbCommands, String[] notes, String screenshotGuide) {
            this.brand = brand;
            this.brandZh = brandZh;
            this.steps = steps;
            this.adbCommands = adbCommands;
            this.notes = notes;
            this.screenshotGuide = screenshotGuide;
        }
    }

    public static class AnalysisResult {
        public List<BatteryEvent> batteryEvents = new ArrayList<>();
        public List<ChargeSession> chargeSessions = new ArrayList<>();
        public List<HealthCheck> healthChecks = new ArrayList<>();
        public List<Anomaly> anomalies = new ArrayList<>();
        public List<AppWakelock> wakelocks = new ArrayList<>();
        public DeviceInfo deviceInfo;
        public Summary summary;
        public String rawFileName;
        public long analysisTimestamp;
        public String parseDetail;

        public static class BatteryEvent {
            public long timestamp;
            public String type;
            public String detail;

            public BatteryEvent(long timestamp, String type, String detail) {
                this.timestamp = timestamp;
                this.type = type;
                this.detail = detail;
            }
        }

        public static class ChargeSession {
            public long startTime;
            public long endTime;
            public int startLevel;
            public int endLevel;
            public String chargeType;
            public float avgPower;
            public float maxPower;

            public ChargeSession(long startTime, long endTime, int startLevel,
                                int endLevel, String chargeType, float avgPower, float maxPower) {
                this.startTime = startTime;
                this.endTime = endTime;
                this.startLevel = startLevel;
                this.endLevel = endLevel;
                this.chargeType = chargeType;
                this.avgPower = avgPower;
                this.maxPower = maxPower;
            }
        }

        public static class HealthCheck {
            public long timestamp;
            public String checkType;
            public String status;
            public String detail;

            public HealthCheck(long timestamp, String checkType, String status, String detail) {
                this.timestamp = timestamp;
                this.checkType = checkType;
                this.status = status;
                this.detail = detail;
            }
        }

        public static class Anomaly {
            public long timestamp;
            public String severity;
            public String type;
            public String description;
            public String suggestion;

            public Anomaly(long timestamp, String severity, String type, String description, String suggestion) {
                this.timestamp = timestamp;
                this.severity = severity;
                this.type = type;
                this.description = description;
                this.suggestion = suggestion;
            }
        }

        public static class AppWakelock {
            public String packageName;
            public String appName;
            public long durationMs;
            public int count;

            public AppWakelock(String packageName, String appName, long durationMs, int count) {
                this.packageName = packageName;
                this.appName = appName;
                this.durationMs = durationMs;
                this.count = count;
            }
        }

        public static class DeviceInfo {
            public String model;
            public String brand;
            public String androidVersion;
            public String buildNumber;
            public int batteryCapacity;
            public int cycleCount;
            public float healthPercentage;
            public String serialNumber;
            public String manufacturingDate;
            public int designCapacityMah;
            public int currentCapacityMah;
            public float temperatureCelsius;
            public int screenOnTimeHours;

            public DeviceInfo(String model, String brand, String androidVersion, String buildNumber,
                             int batteryCapacity, int cycleCount, float healthPercentage) {
                this.model = model;
                this.brand = brand;
                this.androidVersion = androidVersion;
                this.buildNumber = buildNumber;
                this.batteryCapacity = batteryCapacity;
                this.cycleCount = cycleCount;
                this.healthPercentage = healthPercentage;
            }
        }

        public static class Summary {
            public int totalChargeSessions;
            public long totalChargeDurationMs;
            public float avgChargePower;
            public int anomalyCount;
            public int criticalAnomalyCount;
            public String overallHealth;
            public int extractedFieldCount;
            public int missingFieldCount;
            public List<String> extractedFields;
            public List<String> missingFields;

            public Summary(int totalChargeSessions, long totalChargeDurationMs, float avgChargePower,
                          int anomalyCount, int criticalAnomalyCount, String overallHealth) {
                this.totalChargeSessions = totalChargeSessions;
                this.totalChargeDurationMs = totalChargeDurationMs;
                this.avgChargePower = avgChargePower;
                this.anomalyCount = anomalyCount;
                this.criticalAnomalyCount = criticalAnomalyCount;
                this.overallHealth = overallHealth;
                this.extractedFields = new ArrayList<>();
                this.missingFields = new ArrayList<>();
            }
        }
    }

    /**
     * 分析历史记录条目
     */
    public static class HistoryRecord {
        public long timestamp;
        public String fileName;
        public String deviceModel;
        public String overallHealth;
        public int anomalyCount;
        public int criticalAnomalyCount;
        public float healthPercentage;
        public int cycleCount;
        public int designCapacity;
        public int currentCapacity;

        public HistoryRecord(long timestamp, String fileName, String deviceModel,
                            String overallHealth, int anomalyCount, int criticalAnomalyCount,
                            float healthPercentage, int cycleCount, int designCapacity, int currentCapacity) {
            this.timestamp = timestamp;
            this.fileName = fileName;
            this.deviceModel = deviceModel;
            this.overallHealth = overallHealth;
            this.anomalyCount = anomalyCount;
            this.criticalAnomalyCount = criticalAnomalyCount;
            this.healthPercentage = healthPercentage;
            this.cycleCount = cycleCount;
            this.designCapacity = designCapacity;
            this.currentCapacity = currentCapacity;
        }

        /**
         * 序列化为可存储的字符串
         */
        public String serialize() {
            return timestamp + "|" +
                   (fileName != null ? fileName : "") + "|" +
                   (deviceModel != null ? deviceModel : "") + "|" +
                   (overallHealth != null ? overallHealth : "") + "|" +
                   anomalyCount + "|" +
                   criticalAnomalyCount + "|" +
                   healthPercentage + "|" +
                   cycleCount + "|" +
                   designCapacity + "|" +
                   currentCapacity;
        }

        /**
         * 从序列化字符串反序列化
         */
        public static HistoryRecord deserialize(String data) {
            if (data == null || data.isEmpty()) return null;
            String[] parts = data.split("\\|", -1);
            if (parts.length < 10) return null;
            try {
                return new HistoryRecord(
                    Long.parseLong(parts[0]),
                    parts[1],
                    parts[2],
                    parts[3],
                    Integer.parseInt(parts[4]),
                    Integer.parseInt(parts[5]),
                    Float.parseFloat(parts[6]),
                    Integer.parseInt(parts[7]),
                    Integer.parseInt(parts[8]),
                    Integer.parseInt(parts[9])
                );
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private static final Map<String, BrandGuide> BRAND_GUIDES = new HashMap<>();

    static {
        BRAND_GUIDES.put("xiaomi", new BrandGuide(
                "xiaomi", "小米",
                new String[]{
                        "1. 打开 \"设置\" → \"我的设备\"",
                        "2. 连续点击 \"MIUI 版本\" 或 \"HyperOS 版本\" 7次，开启开发者选项",
                        "3. 返回设置主页，进入 \"更多设置\" → \"开发者选项\"",
                        "4. 开启 \"USB 调试\" 和 \"USB 安装\"",
                        "5. HyperOS 2.0 需额外开启 \"USB 调试（安全设置）\"",
                        "6. 电脑安装小米助手或使用 ADB 工具",
                        "7. 连接手机后在电脑命令行执行 adb 命令"
                },
                new String[]{
                        "adb devices",
                        "adb bugreport /path/to/save/bugreport.zip",
                        "adb shell dumpsys battery",
                        "adb shell dumpsys batterystats --reset",
                        "adb shell dumpsys batterystats",
                        "adb shell cat /sys/class/power_supply/battery/cycle_count",
                        "adb shell cat /sys/class/power_supply/battery/constant_charge_count"
                },
                new String[]{
                        "需要先在手机上授权电脑连接",
                        "MIUI 14+ / HyperOS 需要在开发者选项中开启 \"USB 调试（安全设置）\"",
                        "部分机型需要在开发者选项中开启 \"USB 调试（无障碍）\"",
                        "HyperOS 2.0 路径变更：设置 → 更多设置 → 开发者选项",
                        "小米14/15系列支持通过拨号盘 *#*#6485#*#* 查看电池信息"
                },
                "在开发者选项中找到 \"提交错误报告\"，可以直接生成报告并分享"
        ));

        BRAND_GUIDES.put("oppo", new BrandGuide(
                "oppo", "OPPO",
                new String[]{
                        "1. 打开 \"设置\" → \"关于手机\"",
                        "2. 连续点击 \"版本号\" 7次，开启开发者选项",
                        "3. 返回设置主页，进入 \"系统设置\" → \"开发者选项\"",
                        "4. 开启 \"USB 调试\"",
                        "5. ColorOS 16 需额外开启 \"USB 调试（安全设置）\"",
                        "6. 连接电脑，在手机上确认授权",
                        "7. 在电脑命令行执行 adb 命令"
                },
                new String[]{
                        "adb devices",
                        "adb bugreport",
                        "adb shell dumpsys battery",
                        "adb shell getprop | grep battery",
                        "adb shell cat /sys/class/power_supply/battery/cycle_count",
                        "adb shell cat /sys/class/power_supply/battery/battery_capacity"
                },
                new String[]{
                        "ColorOS 12+ 需要关闭 \"USB 安装安全验证\"",
                        "ColorOS 16 新增电池健康度查看入口：设置 → 电池 → 电池健康",
                        "部分机型需要在开发者选项中开启 \"网络安全调试\"",
                        "建议使用原装数据线",
                        "OPPO Find X8 系列支持拨号盘 *#899# 进入工程模式查看电池详情"
                },
                "ColorOS 自带 \"反馈\" 应用，可以直接提交问题和日志"
        ));

        BRAND_GUIDES.put("vivo", new BrandGuide(
                "vivo", "vivo",
                new String[]{
                        "1. 打开 \"设置\" → \"系统管理\" → \"关于手机\"",
                        "2. 连续点击 \"版本号\" 7次，开启开发者选项",
                        "3. 返回设置主页，进入 \"系统管理\" → \"开发者选项\"",
                        "4. 开启 \"USB 调试\"",
                        "5. OriginOS 5 需额外开启 \"USB 调试（安全设置）\"",
                        "6. 连接电脑，在手机上确认授权",
                        "7. 在电脑命令行执行 adb 命令"
                },
                new String[]{
                        "adb devices",
                        "adb bugreport",
                        "adb shell dumpsys battery",
                        "adb shell dumpsys batterystats",
                        "adb shell cat /sys/class/power_supply/battery/cycle_count",
                        "adb shell cat /sys/class/power_supply/battery/health"
                },
                new String[]{
                        "OriginOS 需要在开发者选项中开启 \"USB 调试（安全设置）\"",
                        "OriginOS 5 新增电池健康度查看：设置 → 电池 → 更多设置 → 电池健康",
                        "部分机型需要在设置中开启 \"OTG 功能\"",
                        "建议在连接电脑前先重启手机",
                        "vivo X200 系列支持拨号 *#*#4837#*#* 查看电池信息"
                },
                "OriginOS 自带 \"i 管家\"，可以查看电池健康和使用情况"
        ));

        BRAND_GUIDES.put("huawei", new BrandGuide(
                "huawei", "华为",
                new String[]{
                        "1. 打开 \"设置\" → \"关于手机\"",
                        "2. 连续点击 \"版本号\" 7次，开启开发者选项",
                        "3. 返回设置主页，进入 \"系统和更新\" → \"开发者选项\"",
                        "4. 开启 \"USB 调试\"",
                        "5. HarmonyOS 4+ 需额外开启 \"USB 调试（安全设置）\"",
                        "6. 连接电脑，在手机上确认授权",
                        "7. 在电脑命令行执行 adb 命令"
                },
                new String[]{
                        "adb devices",
                        "adb bugreport",
                        "adb shell dumpsys battery",
                        "adb shell dumpsys batterystats",
                        "adb shell cat /sys/class/power_supply/battery/cycle_count",
                        "adb shell cat /sys/class/power_supply/battery/brand"
                },
                new String[]{
                        "HarmonyOS 需要在开发者选项中开启 \"USB 调试（安全设置）\"",
                        "HarmonyOS NEXT (纯血鸿蒙) 不支持传统 ADB，需使用 DevEco Studio 连接",
                        "部分机型需要在设置中开启 \"允许 HiSuite 连接\"",
                        "建议使用华为官方数据线",
                        "华为 Mate 70 系列可在 \"我的华为\" App 中查看电池健康度"
                },
                "HarmonyOS 自带 \"我的华为\" 应用，可以提交问题反馈"
        ));

        BRAND_GUIDES.put("honor", new BrandGuide(
                "honor", "荣耀",
                new String[]{
                        "1. 打开 \"设置\" → \"关于手机\"",
                        "2. 连续点击 \"版本号\" 7次，开启开发者选项",
                        "3. 返回设置主页，进入 \"系统和更新\" → \"开发者选项\"",
                        "4. 开启 \"USB 调试\"",
                        "5. MagicOS 8+ 需额外开启 \"USB 调试（安全设置）\"",
                        "6. 连接电脑，在手机上确认授权",
                        "7. 在电脑命令行执行 adb 命令"
                },
                new String[]{
                        "adb devices",
                        "adb bugreport",
                        "adb shell dumpsys battery",
                        "adb shell dumpsys batterystats",
                        "adb shell cat /sys/class/power_supply/battery/cycle_count",
                        "adb shell cat /sys/class/power_supply/battery/brand"
                },
                new String[]{
                        "MagicOS 8+ 需要在开发者选项中开启 \"USB 调试（安全设置）\"",
                        "荣耀 Magic7 系列可在 \"我的荣耀\" App 中查看电池健康度",
                        "部分机型需要在设置中开启 \"允许 HiSuite 连接\"",
                        "建议使用荣耀官方数据线",
                        "MagicOS 9 新增电池健康度查看入口：设置 → 电池 → 电池健康"
                },
                "MagicOS 自带 \"我的荣耀\" 应用，可以提交问题反馈和查看电池状态"
        ));

        BRAND_GUIDES.put("samsung", new BrandGuide(
                "samsung", "三星",
                new String[]{
                        "1. 打开 \"设置\" → \"关于手机\"",
                        "2. 连续点击 \"软件信息\" → \"版本号\" 7次",
                        "3. 返回设置主页，进入 \"开发者选项\"",
                        "4. 开启 \"USB 调试\"",
                        "5. One UI 7+ 需额外开启 \"USB 调试（安全设置）\"",
                        "6. 连接电脑，在手机上确认授权",
                        "7. 在电脑命令行执行 adb 命令"
                },
                new String[]{
                        "adb devices",
                        "adb bugreport",
                        "adb shell dumpsys battery",
                        "adb shell dumpsys batterystats",
                        "adb shell cat /sys/class/power_supply/battery/battery_cycle",
                        "adb shell cat /sys/class/power_supply/battery/health"
                },
                new String[]{
                        "One UI 需要在开发者选项中开启 \"USB 调试（安全设置）\"",
                        "One UI 7 新增电池健康度查看：设置 → 电池 → 电池健康",
                        "部分机型需要在开发者选项中开启 \"模拟位置信息\"",
                        "建议使用三星官方数据线",
                        "Samsung Members 可查看电池状态和运行诊断"
                },
                "One UI 自带 \"Samsung Members\" 应用，可以提交问题反馈"
        ));

        BRAND_GUIDES.put("realme", new BrandGuide(
                "realme", "realme",
                new String[]{
                        "1. 打开 \"设置\" → \"关于手机\"",
                        "2. 连续点击 \"版本号\" 7次，开启开发者选项",
                        "3. 返回设置主页，进入 \"系统设置\" → \"开发者选项\"",
                        "4. 开启 \"USB 调试\"",
                        "5. 连接电脑，在手机上确认授权",
                        "6. 在电脑命令行执行 adb 命令"
                },
                new String[]{
                        "adb devices",
                        "adb bugreport",
                        "adb shell dumpsys battery",
                        "adb shell dumpsys batterystats",
                        "adb shell cat /sys/class/power_supply/battery/cycle_count"
                },
                new String[]{
                        "realme UI 需要关闭 \"USB 安装安全验证\"",
                        "realme UI 6 新增电池健康度查看入口",
                        "部分机型需要在开发者选项中开启 \"网络安全调试\"",
                        "建议使用原装数据线"
                },
                "realme UI 自带 \"反馈\" 应用，可以直接提交问题和日志"
        ));

        BRAND_GUIDES.put("meizu", new BrandGuide(
                "meizu", "魅族",
                new String[]{
                        "1. 打开 \"设置\" → \"关于手机\"",
                        "2. 连续点击 \"版本号\" 7次，开启开发者选项",
                        "3. 返回设置主页，进入 \"辅助功能\" → \"开发者选项\"",
                        "4. 开启 \"USB 调试\"",
                        "5. 连接电脑，在手机上确认授权",
                        "6. 在电脑命令行执行 adb 命令"
                },
                new String[]{
                        "adb devices",
                        "adb bugreport",
                        "adb shell dumpsys battery",
                        "adb shell dumpsys batterystats",
                        "adb shell cat /sys/class/power_supply/battery/cycle_count"
                },
                new String[]{
                        "Flyme OS 需要在开发者选项中开启 \"USB 调试（安全设置）\"",
                        "部分机型需要在设置中开启 \"OTG 功能\"",
                        "建议使用原装数据线",
                        "魅族21系列可在拨号盘输入 *#*#4636#*#* 查看电池信息"
                },
                "Flyme OS 自带 \"用户反馈\" 应用，可以提交问题反馈"
        ));

        BRAND_GUIDES.put("oneplus", new BrandGuide(
                "oneplus", "一加",
                new String[]{
                        "1. 打开 \"设置\" → \"关于手机\"",
                        "2. 连续点击 \"版本号\" 7次，开启开发者选项",
                        "3. 返回设置主页，进入 \"系统设置\" → \"开发者选项\"",
                        "4. 开启 \"USB 调试\"",
                        "5. ColorOS 融合版需额外开启 \"USB 调试（安全设置）\"",
                        "6. 连接电脑，在手机上确认授权",
                        "7. 在电脑命令行执行 adb 命令"
                },
                new String[]{
                        "adb devices",
                        "adb bugreport",
                        "adb shell dumpsys battery",
                        "adb shell dumpsys batterystats",
                        "adb shell cat /sys/class/power_supply/battery/cycle_count",
                        "adb shell cat /sys/class/power_supply/battery/constant_charge_count"
                },
                new String[]{
                        "OxygenOS 15 / ColorOS 融合版需开启 \"USB 调试（安全设置）\"",
                        "一加13 系列国内版已融合 ColorOS，路径与 OPPO 一致",
                        "部分机型需要在开发者选项中开启 \"网络安全调试\"",
                        "建议使用原装数据线",
                        "一加13 支持拨号 *#899# 进入工程模式查看电池详情"
                },
                "OxygenOS / ColorOS 融合版自带 \"反馈\" 应用，可以直接提交问题和日志"
        ));

        BRAND_GUIDES.put("google", new BrandGuide(
                "google", "Google",
                new String[]{
                        "1. 打开 \"设置\" → \"系统\" → \"关于手机\"",
                        "2. 连续点击 \"版本号\" 7次，开启开发者选项",
                        "3. 返回设置主页，进入 \"系统\" → \"开发者选项\"",
                        "4. 开启 \"USB 调试\"",
                        "5. 连接电脑，在手机上确认授权",
                        "6. 在电脑命令行执行 adb 命令"
                },
                new String[]{
                        "adb devices",
                        "adb bugreport",
                        "adb shell dumpsys battery",
                        "adb shell dumpsys batterystats",
                        "adb shell dumpsys batteryproperties",
                        "adb shell cat /sys/class/power_supply/battery/cycle_count"
                },
                new String[]{
                        "Android 14+ 需要在开发者选项中开启 \"USB 调试（安全设置）\"",
                        "Android 16 原生支持 BatteryManager 健康度 API",
                        "Pixel 手机需要安装 USB 驱动",
                        "建议使用原装数据线",
                        "Pixel 9 系列可在 设置 → 电池 → 电池健康 查看健康度"
                },
                "Pixel 手机自带 \"反馈\" 应用，可以直接提交问题和日志"
        ));

        BRAND_GUIDES.put("sony", new BrandGuide(
                "sony", "索尼",
                new String[]{
                        "1. 打开 \"设置\" → \"系统\" → \"关于手机\"",
                        "2. 连续点击 \"版本号\" 7次，开启开发者选项",
                        "3. 返回设置主页，进入 \"系统\" → \"开发者选项\"",
                        "4. 开启 \"USB 调试\"",
                        "5. 连接电脑，在手机上确认授权",
                        "6. 在电脑命令行执行 adb 命令"
                },
                new String[]{
                        "adb devices",
                        "adb bugreport",
                        "adb shell dumpsys battery",
                        "adb shell dumpsys batterystats"
                },
                new String[]{
                        "Xperia 需要在开发者选项中开启 \"USB 调试（安全设置）\"",
                        "部分机型需要安装 PC Companion",
                        "建议使用原装数据线"
                },
                "Xperia 自带 \"Xperia Companion\" 应用，可以提交问题反馈"
        ));

        BRAND_GUIDES.put("lenovo", new BrandGuide(
                "lenovo", "联想",
                new String[]{
                        "1. 打开 \"设置\" → \"关于手机\"",
                        "2. 连续点击 \"版本号\" 7次，开启开发者选项",
                        "3. 返回设置主页，进入 \"系统\" → \"开发者选项\"",
                        "4. 开启 \"USB 调试\"",
                        "5. ZUI 16+ 需额外开启 \"USB 调试（安全设置）\"",
                        "6. 连接电脑，在手机上确认授权",
                        "7. 在电脑命令行执行 adb 命令"
                },
                new String[]{
                        "adb devices",
                        "adb bugreport",
                        "adb shell dumpsys battery",
                        "adb shell dumpsys batterystats",
                        "adb shell cat /sys/class/power_supply/battery/cycle_count"
                },
                new String[]{
                        "ZUI 需要在开发者选项中开启 \"USB 调试（安全设置）\"",
                        "部分机型需要在设置中开启 \"OTG 功能\"",
                        "建议使用原装数据线",
                        "联想拯救者系列可在拨号 *#*#4636#*#* 查看电池信息"
                },
                "ZUI 自带 \"用户反馈\" 应用，可以提交问题反馈"
        ));

        BRAND_GUIDES.put("zte", new BrandGuide(
                "zte", "中兴",
                new String[]{
                        "1. 打开 \"设置\" → \"关于手机\"",
                        "2. 连续点击 \"版本号\" 7次，开启开发者选项",
                        "3. 返回设置主页，进入 \"系统\" → \"开发者选项\"",
                        "4. 开启 \"USB 调试\"",
                        "5. MyOS 14+ 需额外开启 \"USB 调试（安全设置）\"",
                        "6. 连接电脑，在手机上确认授权",
                        "7. 在电脑命令行执行 adb 命令"
                },
                new String[]{
                        "adb devices",
                        "adb bugreport",
                        "adb shell dumpsys battery",
                        "adb shell dumpsys batterystats"
                },
                new String[]{
                        "MyOS 需要在开发者选项中开启 \"USB 调试（安全设置）\"",
                        "部分机型需要在设置中开启 \"OTG 功能\"",
                        "建议使用原装数据线",
                        "红魔游戏手机系列可在拨号 *#*#4636#*#* 查看电池信息"
                },
                "MyOS 自带 \"用户反馈\" 应用，可以提交问题反馈"
        ));
    }

    public static BrandGuide getGuideForBrand(String brand) {
        if (brand == null) return null;
        String lowerBrand = brand.toLowerCase();
        if (BRAND_GUIDES.containsKey(lowerBrand)) {
            return BRAND_GUIDES.get(lowerBrand);
        }
        for (Map.Entry<String, BrandGuide> entry : BRAND_GUIDES.entrySet()) {
            if (lowerBrand.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return BRAND_GUIDES.get("google");
    }

    public static List<BrandGuide> getAllGuides() {
        List<BrandGuide> guides = new ArrayList<>();
        // 按国内市场占有率排序
        String[] order = {"xiaomi", "huawei", "honor", "oppo", "vivo", "samsung",
                          "realme", "oneplus", "meizu", "lenovo", "zte", "google", "sony"};
        for (String brand : order) {
            BrandGuide guide = BRAND_GUIDES.get(brand);
            if (guide != null) {
                guides.add(guide);
            }
        }
        return guides;
    }

    public static String getCurrentBrand() {
        return android.os.Build.BRAND.toLowerCase();
    }

    public static BrandGuide getCurrentBrandGuide() {
        return getGuideForBrand(getCurrentBrand());
    }
}
