# 掌上商客 V2.0 (HandBiz Radar)

> 智能获客中枢 - 基于 PakePlus 的 LBS 雷达移动应用

## 项目概述

掌上商客 V2.0 是一款专为品牌体验店设计的智能获客辅助 App，集成 LBS 雷达扫描、地理围栏、客户分层管理、地推作战等功能。基于 PakePlus WebView 壳引擎，将 Web 应用打包为原生 Android 应用。

## 核心功能

### 📡 智能获客中枢
- **LBS 雷达扫描**: 基于浏览器 Geolocation API 的实时定位和周边 POI 扫描
- **GEO 搜索优化**: 智能搜索 + POI 评分 + 客流密度分析
- **竞品热力监控**: 实时监控竞品门店分布与客流热点
- **换机周期预测**: AI 预测客户换机时间，精准触达

### 👥 客户资产库
- **品牌潜客分层**: S/A/B/C/D 智能分层模型
- **以旧换新意向**: 评估客户换机意向
- **服务事件时间轴**: 客户互动历史
- **企业微信侧边栏**: CRM 对接

### 🎯 地推作战系统
- **AI 智能路线规划**: 基于 POI 密度和客户价值的最优路径
- **扫街任务派发**: 任务追踪、进度管理
- **话术智能推荐**: AI 生成个性化销售话术
- **品牌物料一键生成**: 海报、视频、手册快速生成

### 📊 品牌数据中台
- **总部驾驶舱**: 全国门店运营数据
- **竞品热力地图**: 区域竞争态势
- **客户换机周期看板**: 销售预测
- **数据 API 回传**: 与品牌 CRM 对接

## 技术架构

### Android 端 (PakePlus 壳)
- **WebView 引擎**: 基于系统 WebView 加载 LBS 应用
- **定位权限管理**: 集成 Android 6.0+ 运行时权限申请
- **JS 桥接**: JsBridge 支持 Web 端调用原生能力
- **下载管理**: 支持 HTTP/HTTPS/data/blob 多协议文件下载

### Web 端 (React + TypeScript)
- **React 18 + TypeScript**: 现代化前端框架
- **Vite 6**: 快速构建工具
- **Tailwind CSS**: 原子化样式
- **Zustand**: 轻量级状态管理
- **Lucide React**: 图标库

## 权限说明

| 权限 | 用途 | 是否必需 |
|------|------|----------|
| ACCESS_FINE_LOCATION | 精确定位（LBS雷达） | ✅ 必需 |
| ACCESS_COARSE_LOCATION | 粗略定位 | ✅ 必需 |
| INTERNET | 网络访问 | ✅ 必需 |
| CAMERA | 拍照/视频通话 | 可选 |
| RECORD_AUDIO | 录音 | 可选 |
| READ_MEDIA_* | 媒体文件选择 | 可选 |

## 快速开始

### Android 构建
```bash
# 构建 debug APK
./gradlew assembleDebug

# 构建 release APK
./gradlew assembleRelease
```

### Web 端开发
```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build
```

## 应用信息

- **应用名称**: 掌上商客
- **版本**: 2.0.0
- **版本号**: 2
- **包名**: com.handbiz.radar
- **最小 SDK**: 24 (Android 7.0)
- **目标 SDK**: 34 (Android 14)
- **支持架构**: armeabi-v7a, arm64-v8a, x86, x86_64

## 许可证

本项目基于 PakePlus 开源协议。
