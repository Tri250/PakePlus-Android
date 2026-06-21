package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;

import com.batteryhealth.app.data.model.HealthCheckResult;

/**
 * 自检模块检测项的统一接口。
 *
 * 每个实现类负责一项独立的检查逻辑（如电池健康度、通知权限、充电
 * 协议等）。检测结果以 {@link HealthCheckResult} 的形式返回。
 */
public interface IHealthChecker {

    /** 检测项显示名称（用于 UI 与日志）。 */
    String getName();

    /** 检测项分类（用于分组与图标颜色）。 */
    String getCategory();

    /** 排序优先级，数值越小越靠前。 */
    int getPriority();

    /**
     * 执行检测。
     *
     * <p>该方法允许耗时读取，引擎内部通过线程池调用。实现类应避免
     * 直接引用外部可变状态（如 Fragment 视图），以保证线程安全。
     *
     * @param context 应用上下文（非 Activity）
     * @return 检测结果，永远非 null；发生异常时返回带有异常信息的
     *         SEVERITY_INFO 级别结果
     */
    HealthCheckResult check(Context context);
}
