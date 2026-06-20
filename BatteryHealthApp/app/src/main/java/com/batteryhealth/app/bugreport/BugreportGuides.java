package com.batteryhealth.app.bugreport;

import java.util.ArrayList;
import java.util.List;

/**
 * 各品牌抓取 bugreport 指南（与 digiguide 2.1.14 Release 对齐）。
 *
 * <p>提供启动开发者选项、生成 bugreport、并通过 ADB 拉取到电脑的逐步说明。
 * 每条指南都是结构化数据，UI 端可直接渲染。</p>
 */
public final class BugreportGuides {

    public static class Guide {
        public final String brand;
        public final String deviceType;
        public final List<Step> steps;
        public final String adbCommand;
        public final String filenamePattern;
        public final String notes;

        public Guide(String brand, String deviceType, List<Step> steps, String adbCommand, String filenamePattern, String notes) {
            this.brand = brand;
            this.deviceType = deviceType;
            this.steps = steps;
            this.adbCommand = adbCommand;
            this.filenamePattern = filenamePattern;
            this.notes = notes;
        }
    }

    public static class Step {
        public final int order;
        public final String title;
        public final String detail;

        public Step(int order, String title, String detail) {
            this.order = order;
            this.title = title;
            this.detail = detail;
        }
    }

    private BugreportGuides() {}

    public static List<Guide> all() {
        List<Guide> out = new ArrayList<>();
        out.add(xiaomi());
        out.add(huawei());
        out.add(oppo());
        out.add(vivo());
        out.add(honor());
        out.add(samsung());
        out.add(oneplus());
        out.add(realme());
        out.add(redmi());
        return out;
    }

    public static Guide forBrand(String brand) {
        if (brand == null) return generic();
        for (Guide g : all()) {
            if (g.brand.equalsIgnoreCase(brand)) return g;
        }
        return generic();
    }

    // ===== 各品牌指南 =====

    public static Guide xiaomi() {
        List<Step> s = steps(
            new Step(1, "进入开发者选项", "设置 → 我的设备 → 全部参数与信息 → 连续点击 7 次「OS 版本号」"),
            new Step(2, "开启 USB 调试", "设置 → 更多设置 → 开发者选项 → 打开「USB 调试」与「USB 调试（安全设置）」"),
            new Step(3, "生成 bugreport", "开发者选项 → 「Bug 报告」 → 选择「完整报告」并等待 1-3 分钟"),
            new Step(4, "导出文件", "通知栏点击「Bug 报告已生成」分享或拷贝到 U 盘/电脑"),
            new Step(5, "使用 ADB 抓取（推荐）", "电脑执行：adb bugreport D:\\bugreport.zip （需 5-15 分钟）")
        );
        return new Guide("xiaomi", "手机", s,
                "adb bugreport D:\\bugreport-xiaomi.zip",
                "bugreport-*.zip", "MIUI/HyperOS 需开启「USB 调试（安全设置）」");
    }

    public static Guide huawei() {
        List<Step> s = steps(
            new Step(1, "进入开发者选项", "设置 → 关于手机 → 连续点击 7 次「版本号」"),
            new Step(2, "开启 USB 调试", "设置 → 系统和更新 → 开发者选项 → 打开「USB 调试」"),
            new Step(3, "允许 ADB 调试", "连接电脑后在手机弹窗中勾选「一律允许使用这台计算机进行调试」"),
            new Step(4, "抓取 bugreport", "电脑执行：adb shell bugreport > bugreport-huawei.zip"),
            new Step(5, "等待完成", "EMUI/HarmonyOS 抓取需 5-20 分钟，期间请勿断开 USB")
        );
        return new Guide("huawei", "手机", s,
                "adb shell bugreport > bugreport-huawei.zip",
                "bugreport-*.zip", "部分机型需先关闭「仅充电模式下允许 ADB 调试」");
    }

    public static Guide oppo() {
        List<Step> s = steps(
            new Step(1, "进入开发者选项", "设置 → 关于手机 → 连续点击 7 次「版本号」"),
            new Step(2, "开启 USB 调试", "设置 → 系统设置 → 开发者选项 → 打开「USB 调试」"),
            new Step(3, "ColorOS 16 专属", "设置 → 电池 → 更多电池设置 → 打开「停用电池优化」以便我们读取 BMS 节点"),
            new Step(4, "抓取 bugreport", "电脑执行：adb bugreport D:\\bugreport-oppo.zip"),
            new Step(5, "提交报告", "上传 ZIP 后，系统将解析 charge_full、cycle_count 等关键节点")
        );
        return new Guide("oppo", "手机", s,
                "adb bugreport D:\\bugreport-oppo.zip",
                "bugreport-*.zip", "ColorOS 16 节点丰富，识别精度最高");
    }

    public static Guide vivo() {
        List<Step> s = steps(
            new Step(1, "开发者选项", "设置 → 系统管理 → 关于手机 → 连续点击 7 次「软件版本号」"),
            new Step(2, "USB 调试", "设置 → 系统管理 → 开发者选项 → 打开「USB 调试」"),
            new Step(3, "抓取", "电脑执行：adb bugreport D:\\bugreport-vivo.zip"),
            new Step(4, "等待", "OriginOS / FunTouchOS 抓取通常 5-15 分钟"),
            new Step(5, "上传", "上传后系统将解析 BMS 节点中的 charge_full、cycle_count 等")
        );
        return new Guide("vivo", "手机", s,
                "adb bugreport D:\\bugreport-vivo.zip",
                "bugreport-*.zip", "vivo BMS 节点 path 多为 /sys/class/power_supply/bms/");
    }

    public static Guide honor() {
        List<Step> s = steps(
            new Step(1, "开发者选项", "设置 → 关于手机 → 连续点击 7 次「版本号」"),
            new Step(2, "USB 调试", "设置 → 系统和更新 → 开发者选项 → 打开「USB 调试」"),
            new Step(3, "抓取", "电脑执行：adb bugreport D:\\bugreport-honor.zip"),
            new Step(4, "上传", "等待完成，上传解析")
        );
        return new Guide("honor", "手机", s,
                "adb bugreport D:\\bugreport-honor.zip",
                "bugreport-*.zip", "MagicOS 与原华为 EMUI 解码规则相同");
    }

    public static Guide samsung() {
        List<Step> s = steps(
            new Step(1, "开发者选项", "设置 → 关于手机 → 软件信息 → 连续点击 7 次「编译编号」"),
            new Step(2, "USB 调试", "设置 → 开发者选项 → 打开「USB 调试」"),
            new Step(3, "抓取", "电脑执行：adb bugreport D:\\bugreport-samsung.zip"),
            new Step(4, "上传", "三星 SN 编码规则：倒数第 7 位为年份")
        );
        return new Guide("samsung", "手机", s,
                "adb bugreport D:\\bugreport-samsung.zip",
                "bugreport-*.zip", "三星 SN 解析时末位 7 为年份码");
    }

    public static Guide oneplus() {
        List<Step> s = steps(
            new Step(1, "开发者选项", "设置 → 关于手机 → 连续点击 7 次「版本号」"),
            new Step(2, "USB 调试", "设置 → 系统 → 开发者选项 → 打开「USB 调试」"),
            new Step(3, "抓取", "adb bugreport D:\\bugreport-oneplus.zip")
        );
        return new Guide("oneplus", "手机", s,
                "adb bugreport D:\\bugreport-oneplus.zip",
                "bugreport-*.zip", "OnePlus OxygenOS 节点与 OPPO 同源");
    }

    public static Guide realme() {
        List<Step> s = steps(
            new Step(1, "开发者选项", "设置 → 关于手机 → 连续点击 7 次「版本号」"),
            new Step(2, "USB 调试", "设置 → 其他设置 → 开发者选项 → 打开「USB 调试」"),
            new Step(3, "抓取", "adb bugreport D:\\bugreport-realme.zip")
        );
        return new Guide("realme", "手机", s,
                "adb bugreport D:\\bugreport-realme.zip",
                "bugreport-*.zip", "realme UI 节点与 OPPO 同源");
    }

    public static Guide redmi() {
        List<Step> s = steps(
            new Step(1, "开发者选项", "设置 → 我的设备 → 全部参数与信息 → 连续点击 7 次「OS 版本号」"),
            new Step(2, "USB 调试", "设置 → 更多设置 → 开发者选项 → 打开「USB 调试」"),
            new Step(3, "抓取", "adb bugreport D:\\bugreport-redmi.zip")
        );
        return new Guide("redmi", "手机", s,
                "adb bugreport D:\\bugreport-redmi.zip",
                "bugreport-*.zip", "Redmi MIUI/HyperOS 与小米相同");
    }

    public static Guide generic() {
        List<Step> s = steps(
            new Step(1, "开启开发者选项", "设置 → 关于手机 → 连续点击 7 次「版本号」"),
            new Step(2, "开启 USB 调试", "设置 → 开发者选项 → 打开「USB 调试」"),
            new Step(3, "抓取 bugreport", "电脑执行：adb bugreport D:\\bugreport.zip"),
            new Step(4, "等待并上传", "等待生成完成，将 ZIP 上传至本应用")
        );
        return new Guide("generic", "手机", s,
                "adb bugreport D:\\bugreport.zip",
                "bugreport-*.zip", "通用流程；不同品牌部分路径命名略有差异");
    }

    private static List<Step> steps(Step... s) {
        List<Step> out = new ArrayList<>();
        for (Step step : s) out.add(step);
        return out;
    }
}
