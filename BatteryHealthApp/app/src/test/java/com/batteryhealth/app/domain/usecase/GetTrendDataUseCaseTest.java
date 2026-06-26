package com.batteryhealth.app.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.test.TestUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GetTrendDataUseCase 单元测试 (使用 mock BatteryRepository)。
 */
public class GetTrendDataUseCaseTest {

    @Test
    public void testExecute_emptyHistory_returnsNoData() {
        BatteryRepository mockRepo = new MockBatteryRepository(new ArrayList<>());
        GetTrendDataUseCase useCase = new GetTrendDataUseCase(mockRepo);
        GetTrendDataUseCase.Result result = useCase.execute(GetTrendDataUseCase.RANGE_7D);
        assertFalse(result.hasData);
        assertEquals(0, result.dailyPoints.size());
        assertEquals(-1f, result.initialHealth, 0.01f);
    }

    @Test
    public void testExecute_normalHistory_calculatesDecay() {
        List<BatteryInfo> history = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 30; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setTimestamp(now - (30 - i) * 86400_000L);
            info.setHealthPercentage(95f - i * 0.1f); // 30天从95%降到92%
            info.setTemperature(30f);
            info.setCycleCount(i * 2);
            history.add(info);
        }
        BatteryRepository mockRepo = new MockBatteryRepository(history);
        GetTrendDataUseCase useCase = new GetTrendDataUseCase(mockRepo);
        GetTrendDataUseCase.Result result = useCase.execute(GetTrendDataUseCase.RANGE_30D);
        assertTrue(result.hasData);
        assertEquals(30, result.dailyPoints.size());
        assertTrue(result.totalDecay > 0);
        assertTrue(result.avgTemperature > 0);
    }

    @Test
    public void testExecute_anomalyDetection_healthDrop() {
        List<BatteryInfo> history = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setTimestamp(now - (10 - i) * 86400_000L);
            // 第 5 天健康度骤降 2% (异常)
            info.setHealthPercentage(i == 5 ? 92f : (95f - i * 0.1f));
            info.setTemperature(30f);
            info.setCycleCount(i * 2);
            history.add(info);
        }
        BatteryRepository mockRepo = new MockBatteryRepository(history);
        GetTrendDataUseCase useCase = new GetTrendDataUseCase(mockRepo);
        GetTrendDataUseCase.Result result = useCase.execute(GetTrendDataUseCase.RANGE_30D);
        assertTrue(result.hasData);
        // 检测到至少 1 个异常
        assertNotNull(result.anomalies);
    }

    @Test
    public void testExecute_highTemperature_adviceGenerated() {
        List<BatteryInfo> history = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setTimestamp(now - (10 - i) * 86400_000L);
            info.setHealthPercentage(95f - i * 0.1f);
            info.setTemperature(45f + i); // 高温
            info.setCycleCount(i * 2);
            history.add(info);
        }
        BatteryRepository mockRepo = new MockBatteryRepository(history);
        GetTrendDataUseCase useCase = new GetTrendDataUseCase(mockRepo);
        GetTrendDataUseCase.Result result = useCase.execute(GetTrendDataUseCase.RANGE_30D);
        assertNotNull(result.chargingAdvice);
        assertFalse(result.chargingAdvice.isEmpty());
    }

    @Test
    public void testExecute_lowTemperature_advice() {
        List<BatteryInfo> history = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setTimestamp(now - (10 - i) * 86400_000L);
            info.setHealthPercentage(95f - i * 0.1f);
            info.setTemperature(30f);
            info.setCycleCount(i * 2);
            history.add(info);
        }
        BatteryRepository mockRepo = new MockBatteryRepository(history);
        GetTrendDataUseCase useCase = new GetTrendDataUseCase(mockRepo);
        GetTrendDataUseCase.Result result = useCase.execute(GetTrendDataUseCase.RANGE_30D);
        assertNotNull(result.chargingAdvice);
        assertFalse(result.chargingAdvice.isEmpty());
    }

    @Test
    public void testExecute_fastDecay_advice() {
        List<BatteryInfo> history = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 30; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setTimestamp(now - (30 - i) * 86400_000L);
            info.setHealthPercentage(95f - i * 0.3f); // 月降 9%
            info.setTemperature(30f);
            info.setCycleCount(i * 2);
            history.add(info);
        }
        BatteryRepository mockRepo = new MockBatteryRepository(history);
        GetTrendDataUseCase useCase = new GetTrendDataUseCase(mockRepo);
        GetTrendDataUseCase.Result result = useCase.execute(GetTrendDataUseCase.RANGE_30D);
        assertNotNull(result.chargingAdvice);
    }

    @Test
    public void testExecute_nullHealth_recordsSkipped() {
        List<BatteryInfo> history = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setTimestamp(now - (10 - i) * 86400_000L);
            info.setHealthPercentage(-1); // 无效
            info.setTemperature(30f);
            history.add(info);
        }
        BatteryRepository mockRepo = new MockBatteryRepository(history);
        GetTrendDataUseCase useCase = new GetTrendDataUseCase(mockRepo);
        GetTrendDataUseCase.Result result = useCase.execute(GetTrendDataUseCase.RANGE_30D);
        // 无有效数据
        assertFalse(result.hasData);
    }

    @Test
    public void testExecute_negativeTemperature_skipped() {
        List<BatteryInfo> history = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setTimestamp(now - (10 - i) * 86400_000L);
            info.setHealthPercentage(95f - i * 0.1f);
            info.setTemperature(-1); // 无效温度
            history.add(info);
        }
        BatteryRepository mockRepo = new MockBatteryRepository(history);
        GetTrendDataUseCase useCase = new GetTrendDataUseCase(mockRepo);
        GetTrendDataUseCase.Result result = useCase.execute(GetTrendDataUseCase.RANGE_30D);
        // 有效健康度数据应被识别
        assertTrue(result.hasData);
        // 无有效温度
        assertEquals(-1f, result.avgTemperature, 0.01f);
    }

    @Test
    public void testPerformance_largeHistory() {
        List<BatteryInfo> history = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setTimestamp(now - (10000 - i) * 60_000L);
            info.setHealthPercentage(95f - (i % 100) * 0.05f);
            info.setTemperature(30f + (i % 10));
            info.setCycleCount(i);
            history.add(info);
        }
        BatteryRepository mockRepo = new MockBatteryRepository(history);
        GetTrendDataUseCase useCase = new GetTrendDataUseCase(mockRepo);
        long elapsed = TestUtils.measureExecutionTime("Trend.large", () -> {
            GetTrendDataUseCase.Result r = useCase.execute(GetTrendDataUseCase.RANGE_180D);
            assertTrue(r.hasData);
        });
        assertTrue("Trend analysis too slow: " + elapsed + "ms", elapsed < 5000);
    }

    @Test
    public void testPerformance_allRanges() {
        List<BatteryInfo> history = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5000; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setTimestamp(now - (5000 - i) * 60_000L);
            info.setHealthPercentage(95f - (i % 100) * 0.05f);
            info.setTemperature(30f);
            history.add(info);
        }
        BatteryRepository mockRepo = new MockBatteryRepository(history);
        GetTrendDataUseCase useCase = new GetTrendDataUseCase(mockRepo);
        long elapsed = TestUtils.measureExecutionTime("Trend.allRanges", () -> {
            for (int range = 0; range < 4; range++) {
                useCase.execute(range);
            }
        });
        assertTrue("All ranges too slow: " + elapsed + "ms", elapsed < 3000);
    }

    @Test
    public void testLifespanPrediction_normal() {
        List<BatteryInfo> history = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 60; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setTimestamp(now - (60 - i) * 86400_000L);
            info.setHealthPercentage(95f - i * 0.5f); // 60天降30%
            info.setTemperature(30f);
            history.add(info);
        }
        BatteryRepository mockRepo = new MockBatteryRepository(history);
        GetTrendDataUseCase useCase = new GetTrendDataUseCase(mockRepo);
        GetTrendDataUseCase.Result result = useCase.execute(GetTrendDataUseCase.RANGE_90D);
        assertTrue(result.remainingMonths >= 0);
        assertFalse(result.lifespanPrediction.isEmpty());
    }

    @Test
    public void testLifespanPrediction_belowThreshold() {
        List<BatteryInfo> history = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 60; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setTimestamp(now - (60 - i) * 86400_000L);
            info.setHealthPercentage(60f - i * 0.1f); // 已经低于 60%
            info.setTemperature(30f);
            history.add(info);
        }
        BatteryRepository mockRepo = new MockBatteryRepository(history);
        GetTrendDataUseCase useCase = new GetTrendDataUseCase(mockRepo);
        GetTrendDataUseCase.Result result = useCase.execute(GetTrendDataUseCase.RANGE_90D);
        assertEquals(0f, result.remainingMonths, 0.01f);
        assertTrue(result.lifespanPrediction.contains("尽快更换"));
    }

    @Test
    public void testDataSpanDays_calculated() {
        List<BatteryInfo> history = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 30; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setTimestamp(now - (30 - i) * 86400_000L);
            info.setHealthPercentage(95f - i * 0.1f);
            info.setTemperature(30f);
            history.add(info);
        }
        BatteryRepository mockRepo = new MockBatteryRepository(history);
        GetTrendDataUseCase useCase = new GetTrendDataUseCase(mockRepo);
        GetTrendDataUseCase.Result result = useCase.execute(GetTrendDataUseCase.RANGE_30D);
        assertTrue(result.dataSpanDays >= 29);
    }

    // ==================== 内部类 Mock ====================

    private static class MockBatteryRepository implements BatteryRepository {
        private final List<BatteryInfo> history;

        MockBatteryRepository(List<BatteryInfo> history) {
            this.history = history;
        }

        @Override
        public androidx.lifecycle.LiveData<BatteryInfo> observeBatteryInfo() {
            return null;
        }

        @Override
        public BatteryInfo getCurrentBatteryInfo() {
            return history.isEmpty() ? null : history.get(history.size() - 1);
        }

        @Override
        public void saveBatteryInfo(BatteryInfo info) {}

        @Override
        public List<BatteryInfo> getHistorySince(long startTime) {
            List<BatteryInfo> result = new ArrayList<>();
            for (BatteryInfo info : history) {
                if (info.getTimestamp() >= startTime) {
                    result.add(info);
                }
            }
            return result;
        }

        @Override
        public int getHistoryCountSince(long timestamp) {
            int count = 0;
            for (BatteryInfo info : history) {
                if (info.getTimestamp() >= timestamp) count++;
            }
            return count;
        }

        @Override
        public float getAverageHealthSince(long timestamp) {
            float sum = 0f;
            int count = 0;
            for (BatteryInfo info : history) {
                if (info.getTimestamp() >= timestamp && info.getHealthPercentage() >= 0) {
                    sum += info.getHealthPercentage();
                    count++;
                }
            }
            return count == 0 ? -1f : sum / count;
        }

        @Override
        public void deleteOlderThan(long timestamp) {}

        @Override
        public void savePerformanceData(com.batteryhealth.app.data.model.PerformanceData data) {}
    }
}
