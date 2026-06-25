# BatteryHealthApp v5.0.0 业务逻辑与健康自检模块分析报告

> 分析范围：`com.batteryhealth.app` 包下的 UseCase、Repository、健康自检引擎、ViewModel 及 Hilt 模块。  
> 依据：直接读取 `/workspace/BatteryHealthApp/app/src/main/java/com/batteryhealth/app/` 下的实际源码，未使用模拟或空实现。

---

## 1. 核心算法与计算逻辑

### 1.1 电池健康度（SOH）计算

#### 主要实现位置
- `domain/usecase/CalculateHealthUseCase.java`
- `utils/BatteryDataManager.java`（第 646–732 行的 `calculateHealth`）

#### 计算路径
`BatteryDataManager` 中的健康度计算优先级如下：

1. **Android 16+ 原生健康度 API**（`BATTERY_PROPERTY_BATTERY_HEALTH`，隐藏常量 6/8），置信度 `0.98`。
2. **当前满充容量 / 设计容量**（FCC 比值）：
   - `ratio = fullCapacity / designCapacity * 100`
   - `cycleLossPercent = max(0, 100 - ratio)`
   - 置信度 `0.95`（若用户校准过容量则为 `0.90`）。
3. **充电计数法**（电量 ≥60% 时启用）：
   - `currentMax = chargeCounter / (percentage / 100)`
   - `ratio = currentMax / designCapacity * 100`
   - 置信度随电量从 60% 到 100% 线性提升 `0.65→0.85`。
4. **使用天数兜底**：
   - `daysLoss = usageDays * 0.026`
   - `estimatedHealth = 100 - daysLoss`
   - 置信度仅 `0.35`。

`CalculateHealthUseCase.execute(int designCapacity, int currentCapacity, int cycleCount, int usageDays)` 逻辑更简单：

- 仅当 `currentCapacity > 0 && designCapacity > 0` 时按 FCC 比值计算；
- 否则按 `usageDays * 0.026` 估算；
- **传入的 `cycleCount` 完全未参与计算**，仅在 `BatteryHealthChecker` 中用于展示；
- 同样包含 5 次中值滤波（`applyMedianFilter`）。

#### 关键结论
- SOH 主要依赖 **实际容量 / 设计容量**，设计容量来源优先级为：用户校准 > 机型数据库 `DeviceDatabaseManager` > Android 16 原生 API > sysfs 节点（`BatteryDataManager` 第 364–409 行）。
- **循环次数未用于 SOH 计算**，仅作展示和来源判定参考。
- **温度未参与健康度计算**，仅在趋势分析 `GetTrendDataUseCase` 中用于生成充电建议。
- `0.026%/天` 的经验值为固定经验系数，未区分使用习惯、快充、高温等变量。

---

### 1.2 续航预估算法

#### 主要实现位置
- `utils/healthcheck/EnduranceChecker.java`
- `ui/viewmodel/EnduranceViewModel.java`（第 172–311 行）
- `utils/BatteryConsumptionAnalyzer.java`

#### 算法描述
`EnduranceChecker` 优先使用 `BatteryConsumptionAnalyzer.analyze(...)` 返回的 `systemEstimatedHours`：

```java
BatteryConsumptionAnalyzer.Result analysis =
    BatteryConsumptionAnalyzer.analyze(appCtx, 24 * 60 * 60 * 1000L);
if (analysis != null && analysis.systemEstimatedHours > 0) {
    hours = (float) analysis.systemEstimatedHours;
    dischargeRate = pct / hours;
}
```

若系统预估不可用，则回退到 `BatteryManager` 的电流与容量计算：

```java
int currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
if (currentAvg == 0 || currentAvg == Integer.MIN_VALUE) {
    currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
}
int capacityMicroAh = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
...
float remainingMah = capacityMah * (pct / 100f);
float absCurrentMa = Math.abs(currentAvg / 1000f);
hours = remainingMah / absCurrentMa;
```

`EnduranceViewModel` 中还额外通过电压计算能量（mWh）：

```java
double voltageV = voltageMicroV / 1_000_000.0;
double currentMa = Math.abs(currentAvg / 1000.0);
double powerMw = currentMa * voltageV;
double energyMwh = capacityMah * voltageV * (batteryPct / 100.0);
return (float) (energyMwh / powerMw);
```

充电时间计算在 `EnduranceViewModel.calculateChargeTime` 中：

```java
float remainingMah = capacityMah * ((100 - batteryPct) / 100f);
float efficiency = batteryPct < 80 ? 0.85f : 0.55f;
float hours = remainingMah / (chargingCurrentMa * efficiency);
```

#### 关键结论
- 放电续航基于 **当前电量、实时电流、容量（及电压）**，未使用固定基准值。
- `BatteryConsumptionAnalyzer` 通过反射调用隐藏 `BatteryStatsManager` 服务获取系统级耗电统计；在普通第三方应用上可能返回空列表或系统服务不可见，回退路径会生效。
- 温度未直接修正续航，仅作为省电建议的触发条件。

---

### 1.3 充电功率与协议识别

#### 主要实现位置
- `utils/BatteryDataManager.java`（第 355–358 行的 `calculatePower`）
- `utils/healthcheck/ChargingProtocolChecker.java`
- `ui/viewmodel/PowerViewModel.java`

#### 功率计算
```java
return (voltageMv * Math.abs(currentMa)) / 1_000_000.0f;
```
电压/电流来自 BatteryManager 或 sysfs（`current_now`、`voltage_now` 节点）。

#### 协议识别
`ChargingProtocolChecker` **并未识别具体快充协议**（如 PD、QC、PPS、VOOC 等），而是按功率分级：

| 功率范围 | 状态 |
|---------|------|
| ≥65 W   | 超快充 |
| ≥25 W   | 快充 |
| ≥10 W   | 普通充电 |
| <10 W   | 慢速充电 |

仅通过 `BatteryManager.EXTRA_PLUGGED` 区分 AC / USB / 无线充电。

#### 关键结论
- 充电功率是真实计算的；
- “协议”识别实为 **功率档位判断**，存在名不副实的伪算法问题。

---

### 1.4 电池来源（原装 / 第三方 / 未知）判定

#### 主要实现位置
- `utils/BatteryOriginDetector.java`
- `domain/usecase/DetermineBatterySourceUseCase.java`
- `utils/BatteryDataManager.java`（第 533–611 行）

#### 判定维度
`BatteryOriginDetector.detect()` 采集以下 9 个维度：
1. 电池综合信息（`uevent` 等 sysfs 文件）
2. 电池厂商
3. 生产日期
4. 序列号
5. 健康状态
6. 循环次数
7. 设计容量 vs 当前满充容量
8. 出厂标识（`psy_info` / `oem_info` / `factory_serial`）
9. 电池技术类型

数据来源包括 Android 16 原生 API 反射调用和大量 sysfs 节点。

#### 评分逻辑
- `analyzeOriginal(OriginResult)`：通过正/负信号计数判断是否原装（`positiveSigns > negativeSigns`）。
- `calculateConfidence(OriginResult)`：基础分 30，按序列号长度、厂商是否在已知 OEM 列表、生产日期、容量比等加减分，最终 clamp 到 `[0,100]`。

```java
// 容量比是强信号
if (ratio >= 85f && ratio <= 105f) {
    positiveSigns += 3;
} else if (ratio > 115f) {
    negativeSigns += 3; // 容量显著高于设计，疑似第三方大电池
}
```

`DetermineBatterySourceUseCase` 优先委托 `BatteryOriginDetector`，失败时回退到简化的信号叠加逻辑：厂商关键字、序列号格式、容量比、设备数据库匹配。

#### 关键结论
- 判定逻辑依赖 sysfs 可读性和厂商私有节点，在 Android 10+ 高版本设备上多数节点可能因 SELinux 不可读。
- 所谓“原装/第三方”判定本质是 **启发式评分**，并非与 OEM 服务器或加密芯片的真实校验，只能给出“可能性”参考。
- 序列号格式校验（`looksLikeOemSerial`）过于宽松：8–64 位、允许空格/下划线/连字符，几乎任何字符串都可能通过。

---

## 2. UseCase 与 Repository 模式

### 2.1 领域层与数据层分离

- `BatteryRepository` / `DeviceRepository` 定义了清晰的接口契约。
- `BatteryRepositoryImpl` 实现了电池数据获取、历史记录、性能数据持久化。
- `DeviceRepositoryImpl` 委托给 `DeviceInfoManager` 与 `DeviceDatabaseManager`。
- 三个 UseCase（`CalculateHealthUseCase`、`DetermineBatterySourceUseCase`、`GetTrendDataUseCase`）均面向接口编程。

**但 ViewModel 层并未真正使用 Repository 接口**：

```java
// BatteryHealthViewModel.java 第 36–45 行
public BatteryHealthViewModel() {
    BatteryHealthApplication app = BatteryHealthApplication.getInstance();
    DeviceInfoManager deviceInfoManager = new DeviceInfoManager(app.getApplicationContext());
    batteryRepository = new BatteryRepositoryImpl(app);   // 直接 new 实现类
    DeviceRepository deviceRepository = new DeviceRepositoryImpl(deviceInfoManager);
    ...
}
```

其它 ViewModel（`PowerViewModel`、`TrendViewModel`、`PerformanceViewModel`）同样直接 `new BatteryRepositoryImpl(app)`。

因此，**领域层与数据层在代码结构上已分离，但在 ViewModel 中通过具体实现类耦合，削弱了 Repository 模式的价值**。

### 2.2 Repository 实现完整性

#### BatteryRepositoryImpl
- `observeBatteryInfo()`：返回内部 `MutableLiveData`。
- `getCurrentBatteryInfo()`：调用 `BatteryDataManager.refreshFromStickyIntent()` 并 post 值。
- `saveBatteryInfo()`：在 **new Thread()** 中执行 Room 插入。
- `getHistorySince()` / `getAverageHealthSince()` / `getHistoryCountSince()`：直接调用 DAO，**未切换线程**，调用方必须自己保证后台执行。
- `deleteOlderThan()`：new Thread。
- `savePerformanceData()`：new Thread。

问题：
- 历史/聚合查询未内置线程切换，若被误在主线程调用会触发 Room 异常或 ANR。
- `saveBatteryInfo()` 每次创建新线程，未复用线程池。

#### DeviceRepositoryImpl
- 仅包含 `getDeviceConfig()`、`getDesignCapacity()`、`getTypicalChargePower()`、`getUsageDays()` 四个读接口。
- 无写入、无缓存策略，功能较薄。

### 2.3 Hilt 依赖注入配置

`di/AppModule.java` 正确声明了：

```java
@Provides @Singleton BatteryRepository provideBatteryRepository(BatteryHealthApplication app)
@Provides @Singleton DeviceRepository provideDeviceRepository(DeviceInfoManager deviceInfoManager)
@Provides @Singleton CalculateHealthUseCase provideCalculateHealthUseCase(...)
...
```

配置本身无误，但 **没有任何 ViewModel 使用 `@HiltViewModel` 或构造函数注入**。所有 ViewModel 都是无参构造函数并手动 `new` 依赖，导致 AppModule 实际上未被利用。

---

## 3. 健康自检引擎

### 3.1 调度方式

`HealthCheckEngine.java`：

- **单例**（`INSTANCE`）。
- 使用固定大小为 4 的线程池 `Executors.newFixedThreadPool(4)`。
- `startCheck()` 内部先对 `checkers` 按 `priority` 升序排序，然后为每个 `IHealthChecker` 提交一个 `Callable`，通过 `Future.get()` 并发收集结果。
- `AtomicBoolean running` 防止并发检测。
- 结果收集后按严重度降序、评分升序排序。
- 支持进度回调 `onProgress`。

### 3.2 每个 Checker 的检查项与真实性

| Checker | 检查项 | 是否调用真实 API | 评分/阈值说明 |
|---------|--------|------------------|---------------|
| `BatteryHealthChecker` | 电池健康度百分比、循环次数、来源 | ✅ 读取 `BatteryDataManager.getBatteryInfo()` | ≥90→100 分；75–89→85；60–74→60；<60→35 |
| `CapacityHealthChecker` | 设计容量 vs 当前容量 | ✅ 读取 `BatteryDataManager` | 衰减 ≤10%→100；≤20%→85；≤35%→65；>35%→35 |
| `BatteryTemperatureChecker` | 电池温度 | ✅ `registerReceiver(null, ACTION_BATTERY_CHANGED)` 读 `EXTRA_TEMPERATURE` | ≤35°C→100；35–45°C→70；>45°C→30 |
| `EnduranceChecker` | 剩余可用时间、放电速率 | ✅ BatteryManager + `BatteryConsumptionAnalyzer` | 按小时/电量分级 |
| `ChargingProtocolChecker` | 充电功率档位 | ✅ 读 plugged + `BatteryDataManager` 功率 | 按 ≥65/25/10/<10 W 分级 |
| `ChargingLimitChecker` | 是否开启智能充电上限 | ✅ 读 Settings.Global/Secure/System 的 `charge_limit_percent` 等键 | 已启用→100；未启用且健康<80→55；否则 70 |
| `ChargingProtectionChecker` | 高温充电、过充、异常电流 | ✅ 读 status/level/temp/power | 综合取最坏状态 |
| `PerformanceHealthChecker` | CPU/内存/存储占用 | ✅ `/proc/stat`、`ActivityManager.MemoryInfo`、`StatFs` | 加权 0.4/0.3/0.3 |
| `MemoryHealthChecker` | 内存使用率、lowMemory | ✅ `ActivityManager.MemoryInfo` | <60%→100；<80%→75；<90%→55；≥90%→25 |
| `StorageHealthChecker` | 内部存储使用率 | ✅ `StatFs` | <70%→100；<85%→75；<95%→50；≥95%→20 |
| `NetworkHealthChecker` | 网络连通性 | ✅ `ConnectivityManager.getNetworkCapabilities` | 有互联网→100；无→50/30 |
| `BatteryOptimizationChecker` | 是否在电池优化白名单 | ✅ `PowerManager.isIgnoringBatteryOptimizations` | 已忽略→100；未忽略→55 |
| `NotificationPermissionChecker` | 通知权限与总开关 | ✅ `checkSelfPermission(POST_NOTIFICATIONS)` + `areNotificationsEnabled` | 正常→100；受限→45 |

**结论：13 个 Checker 均非空实现，均调用系统真实 API 或读取 sysfs/proc 节点。** 但部分 Checker 的输出受系统限制较大（如 `ChargingLimitChecker` 在多数 ROM 上读不到限制百分比）。

### 3.3 “一键修复”机制

`HealthCheckEngine.applyFix()`：

```java
Intent intent = buildFixIntent(context, result);
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
context.startActivity(intent);
```

实际只跳转到系统设置页：
- 通知权限 → 应用通知设置
- 电池优化 → `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- 充电限制 → 电池设置页（系统无统一 API）
- 网络 → 无线设置

**没有真正的程序化修复能力**。例如：
- 无法直接帮用户开启充电上限；
- 无法降低电池温度；
- 无法替换第三方电池；
- 无法强制授予通知权限（仍需用户手动确认）。

修复后是否生效完全取决于用户是否在系统设置中完成操作，应用不会自动重试检测。

### 3.4 自检结果展示与持久化

- 结果通过 `Callback.onCompleted(List<HealthCheckResult>)` 一次性返回给 UI。
- `HealthCheckEngine` 本身 **不持久化**结果。
- `BatteryOriginViewModel` 会将自己的检测结果保存到 `BatteryOriginRecord`（Room），但健康自检引擎的结果未看到持久化代码。
- 支持 `exportCsv(...)` 生成 CSV 报告。

---

## 4. ViewModel 分析

### 4.1 各 ViewModel 管理的 LiveData

| ViewModel | 主要 LiveData/StateFlow | 说明 |
|-----------|------------------------|------|
| `BatteryHealthViewModel` | `batteryInfo`、`isLoading`、`healthGrade`、`healthStatus`、`batterySource` | 读取当前电池信息并展示 |
| `BatteryOriginViewModel` | `originResult`、`isDetecting`、`historyRecords`、`reportText`、`detectionError` | 电池来源检测、历史记录、报告生成 |
| `DeviceConfigViewModel` | `deviceConfig`、`isLoading`、`usageDays`、`errorMessage` | 设备配置与激活天数 |
| `EnduranceViewModel` | `batteryLevel`、`temperature`、`isCharging`、`dischargeRate`、`estimatedEnduranceHours`、`estimatedChargeHours`、`enduranceGrade`、`topConsumers`、`powerSavingTips`、`screenOnTimeMs`、`hasUsageAccess` | 续航分析核心 |
| `PerformanceViewModel` | `cpuUsage`、`memoryUsage`、`storageUsage`、`performanceScore`、`isLoading`、`appCpuUsage`、`appMemoryUsage`、`foregroundServiceRunning`、`anrResult`、`performanceSuggestions` | 性能分析并持久化 |
| `PowerViewModel` | `batteryInfo`、`isLoading`、`chargeType` | 充电功率展示 |
| `TrendViewModel` | `trendData`、`isLoading`、`errorMessage`、`currentRange` | 趋势数据 |

### 4.2 数据转换与业务逻辑位置

- **UseCase 中放置了合理业务逻辑**：`CalculateHealthUseCase`、`DetermineBatterySourceUseCase`、`GetTrendDataUseCase` 都包含算法。
- **但大量计算仍留在 ViewModel**：
  - `EnduranceViewModel` 自行实现了放电速率、续航、充电时间、省电建议等完整算法，未通过 UseCase 复用 `EnduranceChecker`。
  - `BatteryHealthViewModel` 中 `CalculateHealthUseCase` 被创建但 `refreshData()` 实际只调用 `batteryRepository.getCurrentBatteryInfo()`，随后用 `BatteryInfo.getHealthGrade()`/`getHealthDescription()` 直接展示，UseCase 未在刷新路径中被调用。
  - `PerformanceViewModel` 把评分和建议生成全部交给 `PerformanceAnalyzer`，领域层未参与。

结论：**业务逻辑分散在 UseCase、ViewModel、Utility 类三层，边界不够清晰**。

### 4.3 生命周期与内存泄漏

每个 ViewModel 都有：

```java
private final AtomicBoolean isCleared = new AtomicBoolean(false);
@Override protected void onCleared() { isCleared.set(true); }
```

后台任务通过 `ThreadExecutor.execute(...)` 提交，并在执行前检查 `isCleared`，避免在 ViewModel 销毁后继续 post 值。

- 没有直接持有 Activity/Fragment 引用。
- LiveData 由 UI 层观察，Lifecycle 自动管理。
- `ThreadExecutor.IO_EXECUTOR` 是静态单例，任务即使 post 也被 AtomicBoolean 拦截。

**不存在明显内存泄漏风险**。

---

## 5. 发现的问题

### 5.1 算法科学性

1. **SOH 过于依赖 FCC 比值**。
   - 真实电池健康度受温度、放电倍率、老化曲线影响，仅用 `fullCapacity/designCapacity` 是粗略近似。
2. **循环次数未参与健康度计算**。
   - `cycleCount` 被采集和展示，但对 SOH 无权重。
3. **温度未参与 SOH 计算**。
   - 高温是电池老化的重要因素，目前只用于告警和建议。
4. **使用天数估算系数固定**。
   - `0.026%/天` 对所有用户、所有快充习惯、所有气候条件一视同仁，科学性不足。
5. **续航算法基于瞬时电流**。
   - `BATTERY_PROPERTY_CURRENT_AVERAGE` 在不同设备上含义不稳定，且未考虑未来负载变化。
6. **充电协议Checker名不副实**。
   - 仅按功率分级，没有解析 PD/QC/PPS/VOOC 等协议。

### 5.2 伪算法 / 经验估算

1. `BatteryOriginDetector` 的“原装/第三方”判定是启发式评分，不是权威验证。
2. `BatteryConsumptionAnalyzer.buildResult` 中，屏幕/系统/应用耗电占比对未统计部分按 `60%/25%/15%` 强行分配，属于明显估算。
3. `PerformanceAnalyzer.calculateSocScore` 基于 `Build.HARDWARE` 字符串关键字打分，属于经验映射。
4. `PerformanceHealthChecker.readCpuUsage()` 仅读取一次 `/proc/stat`，没有两次采样差值，得到的是累计占用率而非瞬时占用率（实际 `PerformanceAnalyzer.getCpuUsage()` 做了差值，但 `PerformanceHealthChecker` 没有）。

### 5.3 Checker 是否空实现

- **没有纯空实现或固定返回**。所有 Checker 都尝试读取真实系统状态。
- 但在 sysfs/SELinux 受限设备上，多个 Checker 会进入“无数据”分支，返回 INFO 级别结果，UI 上表现为“无法读取”，而非伪造数据。

### 5.4 评分合理性

- 单项评分阈值基本符合常见认知（温度 35/45°C、存储 70/85/95%、健康度 60/75/90）。
- `HealthCheckEngine.getOverallScore()` 按严重度加权再次计算综合分，但单项 `itemScore` 已经按区间离散化，综合分可能受离散化影响不够平滑。
- `ChargingProtocolChecker` 在“未充电”时给 75 分，对未连接充电器的场景仍计入平均分，可能拉高总分。

### 5.5 线程安全

- `HealthCheckEngine` 使用 `CopyOnWriteArrayList`、`AtomicBoolean`、`Future.get()`，线程安全。
- `BatteryDataManager.healthBuffer` 使用 `synchronized` 保护。
- `BatteryRepositoryImpl.getHistorySince()` 等方法未主动切线程，依赖调用方。
- `BatteryRepositoryImpl.saveBatteryInfo()` 使用 `new Thread()`，未复用线程池，存在线程创建开销。
- `ThreadExecutor` 是静态单例，未提供关闭接口，应用退出时由 JVM/系统回收。

### 5.6 架构与依赖注入

- Hilt `AppModule` 已配置，但 **ViewModel 未使用**。
- 多个 ViewModel 各自 `new BatteryRepositoryImpl(app)`，导致 Repository 和内部 `BatteryDataManager` 可能存在多实例。
- `BatteryOriginViewModel` 直接访问 `BatteryHealthApplication.getInstance().getDatabase()`，绕过 Repository。

---

## 6. 综合评分

| 维度 | 得分 | 扣分原因 |
|------|------|----------|
| **算法准确性** | **62/100** | SOH 仅依赖 FCC 比值，未引入温度、循环次数、老化曲线；`0.026%/天` 为固定经验值；续航基于瞬时电流，未预测负载；充电“协议”实为功率分级；电池来源判定为启发式而非权威验证。 |
| **架构规范性** | **55/100** | Repository/UseCase 结构存在，但 ViewModel 直接 new 实现类、未使用 Hilt；业务逻辑分散在 ViewModel/Utility/UseCase；部分 DB 查询未内置线程切换；`BatteryOriginViewModel` 绕过 Repository 直接访问 Database。 |
| **自检完整性** | **70/100** | 13 项 Checker 均调用真实 API，覆盖电池、充电、性能、系统权限；但缺少真正的程序化修复（仅跳转设置）、缺少自检结果持久化、缺少电池校准/标定功能、高版本 Android 上 sysfs 可读性差导致部分项“无数据”。 |

---

## 7. 结论

`BatteryHealthApp v5.0.0` 的健康自检模块 **没有使用模拟数据或空实现**，各 Checker 均基于 Android BatteryManager、Settings、ActivityManager、ConnectivityManager、StatFs 及 sysfs/proc 节点进行真实读取。核心算法以 **容量比值法** 计算 SOH，以 **电流/容量/电压** 估算续航，架构上采用了 Repository + UseCase 分层，但 ViewModel 层未真正接入 Hilt，导致依赖关系耦合在实现类上。

主要风险在于：
- 算法科学性不足，部分功能名不副实（“协议识别”实为功率分级）；
- 架构落地不彻底，DI 与分层边界混乱；
- 一键修复仅为系统设置跳转，无法真正闭环。

报告输出文件：`/workspace/business_logic_analysis.md`
