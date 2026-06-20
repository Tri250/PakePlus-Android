# 电池健康 App Web 展示版 V4.4.6 - 技术架构文档

## 1. 技术选型

| 项 | 选择 | 原因 |
|---|---|---|
| 框架 | 纯 HTML + CSS + 原生 JavaScript | 单文件即可运行，零构建零依赖 |
| 字体 | 系统字体 -apple-system, SF Pro | 与 iOS 2026 风格一致 |
| 图标 | 内联 SVG + Unicode 符号 | 无需图标库依赖 |
| 图表 | 原生 SVG 折线图 | 趋势图自实现足够 |
| 弹窗 | 原生 CSS Modal + JS | 报告/Bugreport 弹窗 |

## 2. 目录结构

```
battery-health-web/
├── index.html          # 单文件，包含 HTML + CSS + JS
```

## 3. 数据模型（前端模拟数据）

```js
const mockData = {
  battery: {
    percentage: 92.4,
    grade: 'A+',
    status: '电池状态极佳',
    capacity: { current: 4352, design: 4687 },
    cycleCount: 287,
    temperature: 32.5,
    voltage: 4.287,
    current: 1250,
    source: '原装电池',
    technology: 'Li-ion',
    manufacturer: 'ATL',
    serial: 'SN20240115A001'
  },
  device: {
    name: 'Xiaomi 14 Pro',
    androidVersion: 'Android 15',
    cpu: '8核 3300MHz',
    memory: 12,
    storage: 256,
    screen: '1200 x 2670',
    activationDate: '2024-01-15',
    daysUsed: 521
  },
  performance: {
    cpu: 45,
    memory: 68,
    storage: 62,
    score: 85200,
    gpu: 'Adreno 750',
    fps: 59.8,
    jankStatus: '流畅',
    jankCount: 3
  },
  power: {
    watt: 67.5,
    type: 'super',
    voltage: 9.02,
    current: 7.48,
    phase: '恒流充电',
    level: 68,
    eta: 18,
    todayCount: 2,
    avgWatt: 58.3,
    duration: 42
  }
};
```

## 4. 关键模块

### 4.1 状态栏组件
- 显示实时时间（居中）
- 右侧三个图标（信号、Wi-Fi、电池）

### 4.2 页面切换
- 使用 `data-tab` 属性绑定 Tab 目标
- 切换时 CSS transition opacity + transform

### 4.3 电池健康 Tab
- 健康度大数字 + 渐变进度条
- 详细信息列表（容量/循环/温度/电压/来源/技术）
- 按钮组：周报/月报/趋势入口
- 周报/月报点击弹出 Modal 展示报告

### 4.4 充电功率 Tab
- 当前功率大数字 + 充电类型徽章
- 详情列表（电压/电流/阶段/电量/预计充满）
- 今日充电历史卡片

### 4.5 电池江湖 Tab
- 电池养护小贴士卡片（4条建议）
- 社区入口按钮

### 4.6 配置查询 Tab
- 设备信息列表
- 关于区域：隐私政策/用户协议/Bugreport指南
- Bugreport 点击弹出 Modal

### 4.7 性能分析 Tab
- CPU/内存/存储使用率卡片（数值 + 进度条）
- 性能评分 + 进度条
- GPU 信息
- 实时帧率 + 卡顿检测双卡片

### 4.8 实时数据模拟
- `setInterval(update, 3000)`：更新 CPU/内存/FPS/功率
- 数值在合理范围内随机波动

## 5. 响应式策略

| 断点 | 行为 |
|---|---|
| `< 768px` | 占满屏幕，无手机外壳框架 |
| `>= 768px` | 居中显示 390×844 iPhone 外壳（圆角 48、阴影） |

## 6. 性能与可访问性
- 无外部 JS 库，单文件
- 关键交互元素 `cursor: pointer`
- 颜色对比度遵循 WCAG AA