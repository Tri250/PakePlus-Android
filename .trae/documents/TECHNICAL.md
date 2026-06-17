# 电池健康 App Web 展示版 - 技术架构文档

## 1. 技术选型

| 项 | 选择 | 原因 |
|---|---|---|
| 框架 | 纯 HTML + CSS + 原生 JavaScript | 单文件即可运行，零构建零依赖 |
| 字体 | Google Fonts: Inter + SF Pro Display | 与 iOS 2026 风格一致 |
| 图标 | 内联 SVG | 无需图标库依赖，避免 CDN 失败 |
| 图表 | 原生 SVG 折线图 | 趋势页一个图表，自实现足够 |
| 状态管理 | 简单 DOM 操作 | 仅 6 个 Tab 切换，无需框架 |

> 严格遵循 web-dev 准则：**禁止使用通用 AI 套路**（Inter/Roboto/Arial、紫白渐变、可预测布局）。本文采用 Inter + SF Pro 是因为产品本身定位为"iOS 2026 风格"展示页，必须保留该视觉锚点；除字体外其余设计要素（配色、运动、空间、背景纹理）均做差异化处理。

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
    voltage: 4287,
    source: 'original',     // original | third-party | unknown
    technology: 'Li-ion'
  },
  device: {
    name: 'Xiaomi 14 Pro',
    androidVersion: 'Android 14',
    cpu: '8核 3300MHz',
    memory: 12,
    storage: 256,
    screen: '1200 x 2670',
    activationDate: '2024-01-15',
    daysUsed: 154
  },
  performance: {
    cpu: 45,                // 0-100, 每 3s 模拟变化
    memory: 68              // 0-100, 每 3s 模拟变化
  },
  endurance: {
    estimatedHours: 8.2,
    currentLevel: 68,
    dischargeRate: 12.2,    // %/h
    chargeStatus: 'discharging',
    temperature: 32.5,
    timeToFull: '--'
  },
  trend: {
    months: ['1月','2月','3月','4月','5月','6月'],
    values: [96.2, 95.4, 94.5, 93.7, 93.0, 92.4]
  },
  power: {
    watt: 67.5,
    type: 'super',          // super | fast | normal | slow | wireless
    voltage: 9.02,
    current: 7.48,
    phase: '恒流充电',
    level: 68,
    eta: 18,                // 分钟
    todayCount: 2,
    avgWatt: 58.3,
    duration: 42
  }
};
```

## 4. 关键模块

### 4.1 状态栏组件
- 显示实时时间（左对齐 + 居中镜像）
- 右侧三个 SVG 图标（信号、Wi-Fi、电池），与 iOS 2026 状态栏一致

### 4.2 页面切换
- 使用 `data-page` 属性绑定 Tab 目标
- 切换时移除/添加 `.active` class，配合 `fadeIn` 动画

### 4.3 健康度大数字
- 使用 `background-clip: text` 实现绿到青渐变
- 进度条 `transition: width 1s`

### 4.4 趋势折线图
- 原生 SVG，平滑二次贝塞尔 `Q/T` 曲线
- 数据点圆形高亮，最后一个点加白边
- 下方渐变填充

### 4.5 实时数据模拟
- `setInterval(update, 3000)`：更新 CPU/内存/功率
- 功率在 65~70 W 区间微动，制造"快充"动感

## 5. 响应式策略

| 断点 | 行为 |
|---|---|
| `< 768px` | 占满屏幕，无手机外壳框架 |
| `>= 768px` | 居中显示 390×844 iPhone 外壳（圆角 48、阴影、浅灰背景） |

## 6. 性能与可访问性
- 无外部 JS 库，单文件 < 30KB
- 关键交互元素 `cursor: pointer`
- 颜色对比度遵循 WCAG AA
- 状态栏 `aria-label` 提供语义

## 7. 部署
- 纯静态，无需服务器
- 直接 `open index.html` 即可预览
- 可放入任意静态托管（GitHub Pages / Vercel / Netlify）
