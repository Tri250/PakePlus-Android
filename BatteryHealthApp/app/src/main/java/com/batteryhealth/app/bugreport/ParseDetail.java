package com.batteryhealth.app.bugreport;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析详情（等价于 digiguide C++ ParseDetail）。
 *
 * <p>用于本地分析后给其他模块提供"哪些字段拿到了、哪些没拿到"的可信度信息，
 * 电池健康、配置查询、性能分析模块会基于此决定置信度。</p>
 */
public class ParseDetail {

    public final List<String> extractedFields = new ArrayList<>();
    public final List<String> missingFields = new ArrayList<>();
    public final List<String> parseWarnings = new ArrayList<>();

    public float confidence = 0f;

    public void addExtracted(String name) { extractedFields.add(name); }
    public void addMissing(String name) { missingFields.add(name); }
    public void addWarning(String w) { parseWarnings.add(w); }

    public String summary() {
        return "成功 " + extractedFields.size() + " 字段；缺失 " + missingFields.size() + " 字段";
    }
}
