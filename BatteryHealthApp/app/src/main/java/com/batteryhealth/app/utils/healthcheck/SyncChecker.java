package com.batteryhealth.app.utils.healthcheck;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import com.batteryhealth.app.data.model.HealthCheckResult;

/**
 * 同步设置检测
 * 检测自动同步是否开启，过多同步会增加耗电
 */
public class SyncChecker implements IHealthChecker {

    private static final String NAME = "自动同步";
    private static final String CATEGORY = HealthCheckResult.CATEGORY_SYSTEM;
    private static final int PRIORITY = 80;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public HealthCheckResult check(Context context) {
        HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                .setId("auto_sync")
                .setTitle(NAME)
                .setCategory(CATEGORY);

        try {
            boolean autoSync = ContentResolver.getMasterSyncAutomatically();
            int accountCount = 0;

            try {
                AccountManager accountManager = AccountManager.get(context);
                Account[] accounts = accountManager.getAccounts();
                accountCount = accounts != null ? accounts.length : 0;
            } catch (Exception ignored) {}

            if (!autoSync) {
                builder.setStatus("已关闭");
                builder.setSeverity(HealthCheckResult.SEVERITY_GOOD);
                builder.setItemScore(95);
                builder.setDescription("自动同步已关闭。应用不会在后台自动同步数据，有助于节省电量。");
                builder.setAdvice("关闭自动同步可延长续航时间，需要时手动同步即可。");
                builder.setValue("关闭");
            } else {
                builder.setStatus("已开启");
                builder.setSeverity(HealthCheckResult.SEVERITY_INFO);
                builder.setItemScore(70);
                builder.setDescription(String.format("自动同步已开启，当前有 %d 个账户。自动同步会在后台定期更新数据，消耗一定电量。",
                        accountCount));
                builder.setAdvice("如果不需要实时同步，建议减少同步频率或关闭不常用账户的同步。");
                builder.setValue("开启");
                builder.setRepairable(true);
                builder.setFixAction(HealthCheckResult.FIX_ACTION_ACCOUNT_SYNC_SETTINGS);
            }

            builder.addExtraData("account_count", String.valueOf(accountCount));

        } catch (Exception e) {
            builder.setStatus("无法检测");
            builder.setSeverity(HealthCheckResult.SEVERITY_INFO);
            builder.setItemScore(70);
            builder.setDescription("无法检测自动同步状态。");
            builder.setAdvice("您可以在系统设置中手动检查账户同步设置。");
        }

        return builder.build();
    }
}
