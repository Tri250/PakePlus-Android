# Android APP功能界面展示网页 - 技术架构文档

## 1. 技术选型

### 1.1 前端技术栈
| 分类 | 技术 | 版本 | 说明 |
| :--- | :--- | :--- | :--- |
| 框架 | React | 18+ | 现代化前端框架，支持组件化开发 |
| 构建工具 | Vite | 6+ | 快速构建工具，支持热更新 |
| 样式 | Tailwind CSS | 3+ | 原子化CSS框架，快速开发 |
| 图标 | Lucide React | 0.263+ | 精美的图标库 |
| 动画 | Framer Motion | 10+ | 强大的动画库 |

### 1.2 项目结构

```
/
├── index.html          # 入口HTML文件
├── package.json        # 项目依赖配置
├── vite.config.js      # Vite配置
├── tailwind.config.js  # Tailwind CSS配置
├── postcss.config.js   # PostCSS配置
├── src/
│   ├── main.jsx        # 应用入口
│   ├── App.jsx         # 主应用组件
│   ├── index.css       # 全局样式
│   ├── components/     # 组件目录
│   │   ├── DeviceFrame.jsx    # Android设备框架组件
│   │   ├── HomeScreen.jsx     # 主屏幕组件
│   │   ├── FeatureCard.jsx    # 功能卡片组件
│   │   ├── Navigation.jsx     # 导航组件
│   │   └── ThemeToggle.jsx    # 主题切换组件
│   ├── pages/          # 页面目录
│   │   ├── HomePage.jsx       # 首页
│   │   ├── FeaturesPage.jsx   # 功能展示页
│   │   └── AboutPage.jsx      # 关于页
│   └── data/           # 模拟数据
│       └── features.js # 功能模块数据
└── public/             # 静态资源
    └── favicon.ico
```

## 2. 核心组件设计

### 2.1 DeviceFrame 组件
- **功能**: 模拟Android设备外观框架
- **props**:
  - `children`: 设备屏幕内容
  - `isRotated`: 是否旋转
  - `theme`: 主题模式

### 2.2 HomeScreen 组件
- **功能**: 展示Android应用主屏幕
- **props**:
  - `onFeatureClick`: 功能模块点击回调

### 2.3 FeatureCard 组件
- **功能**: 展示单个功能模块卡片
- **props**:
  - `icon`: 图标名称
  - `title`: 功能名称
  - `description`: 功能描述
  - `onClick`: 点击事件

## 3. 页面路由设计

| 路径 | 页面组件 | 功能描述 |
| :--- | :--- | :--- |
| `/` | HomePage | 首页，展示设备模拟和主屏幕 |
| `/features` | FeaturesPage | 功能模块详细展示 |
| `/about` | AboutPage | 关于应用介绍 |

## 4. 状态管理

- **主题状态**: 使用React useState管理深色/浅色主题
- **当前页面**: 使用React useState管理路由状态
- **设备旋转**: 使用React useState管理设备方向

## 5. 样式设计

### 5.1 颜色方案

**浅色主题**:
- 主色: #6200EE (Android紫色)
- 辅助色: #03DAC6 (青色)
- 背景: #FFFFFF
- 文字: #121212

**深色主题**:
- 主色: #BB86FC
- 辅助色: #03DAC6
- 背景: #121212
- 文字: #FFFFFF

### 5.2 字体方案
- 标题字体: Roboto Slab
- 正文字体: Roboto

## 6. 动画效果

- 页面切换: 淡入淡出 + 滑动
- 功能卡片: 悬停缩放 + 阴影
- 设备旋转: 3D旋转动画
- 功能模块图标: 脉冲动画

## 7. 部署方案

- 构建命令: `npm run build`
- 输出目录: `dist/`
- 静态资源托管: 支持Netlify、Vercel、GitHub Pages