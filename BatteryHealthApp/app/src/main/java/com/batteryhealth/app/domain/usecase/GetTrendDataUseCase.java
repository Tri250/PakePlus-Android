package com.batteryhealth.app.domain.usecase;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.domain.repository.BatteryRepository;

import java.util.ArrayList;
import java.util.List;

public class GetTrendDataUseCase {

    private final BatteryRepository batteryRepository;

    public GetTrendDataUseCase(BatteryRepository batteryRepository) {
        this.batteryRepository = batteryRepository;
    }

    public Result execute(int months) {
        Result result = new Result();
        
        long now = System.currentTimeMillis();
        long period = (long) months * 30 * 24 * 60 * 60 * 1000L;
        long startTime = now - period;

        List<BatteryInfo> history = batteryRepository.getHistorySince(startTime);
        
        if (history.isEmpty()) {
            result.hasData = false;
            result.months = new String[months];
            result.values = new float[months];
            for (int i = 0; i < months; i++) {
                result.months[i] = getMonthLabel(months - i - 1);
                result.values[i] = -1;
            }
            return result;
        }

        result.hasData = true;
        result.months = new String[months];
        result.values = new float[months];

        for (int i = 0; i < months; i++) {
            result.months[i] = getMonthLabel(i);
        }

        float[] monthlyHealth = new float[months];
        int[] monthlyCount = new int[months];

        for (BatteryInfo info : history) {
            long timestamp = info.getTimestamp();
            float health = info.getHealthPercentage();
            
            if (health < 0) continue;

            int monthIndex = (int) ((now - timestamp) / (30L * 24 * 60 * 60 * 1000));
            if (monthIndex >= 0 && monthIndex < months) {
                monthlyHealth[monthIndex] += health;
                monthlyCount[monthIndex]++;
            }
        }

        for (int i = 0; i < months; i++) {
            if (monthlyCount[i] > 0) {
                result.values[i] = monthlyHealth[i] / monthlyCount[i];
            } else {
                result.values[i] = -1;
            }
        }

        calculateStats(result, history);

        return result;
    }

    private void calculateStats(Result result, List<BatteryInfo> history) {
        float firstHealth = -1;
        float lastHealth = -1;
        float totalHealth = 0;
        int validCount = 0;

        for (BatteryInfo info : history) {
            float health = info.getHealthPercentage();
            if (health >= 0) {
                if (firstHealth < 0) firstHealth = health;
                lastHealth = health;
                totalHealth += health;
                validCount++;
            }
        }

        result.initialHealth = firstHealth;
        result.currentHealth = lastHealth;
        
        if (firstHealth > 0 && lastHealth > 0) {
            result.totalDecay = firstHealth - lastHealth;
            result.monthlyDecay = result.totalDecay / 6f;
        }

        if (validCount > 0) {
            result.averageHealth = totalHealth / validCount;
        }
    }

    private String getMonthLabel(int offset) {
        String[] months = {"1月", "2月", "3月", "4月", "5月", "6月", 
                          "7月", "8月", "9月", "10月", "11月", "12月"};
        int currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH);
        int index = (currentMonth - offset + 12) % 12;
        return months[index];
    }

    public static class Result {
        public boolean hasData;
        public String[] months;
        public float[] values;
        public float initialHealth = -1;
        public float currentHealth = -1;
        public float totalDecay;
        public float monthlyDecay;
        public float averageHealth;
    }
}