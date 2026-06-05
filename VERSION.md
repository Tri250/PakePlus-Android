# 掌上商客 V2.0 - 二次开发基础版本

## 版本信息

- **版本号**: v2.0.0-handbiz-base
- **发布日期**: 2026-06-05
- **Git标签**: `v2.0.0-handbiz-base`
- **基础提交**: `99348f4`

## 分支结构

| 分支 | 说明 |
|------|------|
| `main` | 主分支，稳定版本 |
| `develop` | 开发分支，二次开发基于此分支 |
| `trae/solo-agent-BQaT9G` | AI代理工作分支 |

## 功能模块

### 1. 智能获客中枢 🎯
- GEO 搜索优化引擎 (新增) - 19个品牌关键词矩阵
- LBS 雷达扫描 (重构) - 四层数据融合
- 竞品热力监控 (新增) - 7大品牌监控
- 换机周期预测 (新增) - AI预测引擎

### 2. 客户资产库 👥
- 品牌潜客分层模型 - S/A/B/C/D五级分层
- 以旧换新意向评分 - 智能评分系统
- 服务事件时间轴 - 客户历史追踪
- 企业微信侧边栏 - 企微集成

### 3. 地推作战系统 🚀
- AI 智能路线规划 (重构) - 最优路径算法
- 扫街任务派发与追踪 - 任务管理系统
- 话术智能推荐 - AI话术生成
- 品牌物料一键生成 - 海报/朋友圈物料

### 4. 品牌数据中台 📈
- 总部驾驶舱 - 全局数据可视化
- 竞品热力地图 - 竞品分布展示
- 客户换机周期看板 - 换机数据分析
- 数据 API 回传 - CRM数据同步

## 技术栈

- **前端框架**: React 18 + TypeScript
- **构建工具**: Vite 6
- **样式**: Tailwind CSS
- **状态管理**: Zustand (持久化)
- **动画**: Framer Motion
- **存储**: IndexedDB (离线支持)
- **地图**: OpenStreetMap/Nominatim (免费)

## 数据源 (12个)

| 类别 | 数据源 |
|------|--------|
| 地图 | 高德、百度、腾讯 |
| 商业 | 大众点评、美团、小红书、抖音 |
| 电商 | 京东、天猫、拼多多 |
| 政策 | 国补政策 |
| 品牌 | 品牌CRM |

## 服务模块 (23个)

```
src/services/
├── ai.ts                 # AI算法服务
├── apiRouter.ts          # API路由配置
├── auth.ts               # 认证服务
├── competitorMonitor.ts  # 竞品监控
├── dataCollector.ts      # 数据采集
├── dataCrawler.ts        # 数据爬虫
├── dataSync.ts           # 数据同步
├── dynamicLoader.ts      # 动态加载
├── geoOptimization.ts    # GEO优化
├── geolocation.ts        # 地理定位
├── imageService.ts       # 图片服务
├── lbsRadar.ts           # LBS雷达
├── mapService.ts         # 地图服务
├── networkManager.ts     # 网络管理
├── replacementPredictor.ts # 换机预测
├── resolutionAdapter.ts  # 分辨率适配
├── security.ts           # 安全服务
├── storage.ts            # 存储服务
└── uiux.ts               # UI/UX服务
```

## 快速开始

```bash
# 克隆仓库
git clone https://github.com/Tri250/PakePlus-Android.git

# 切换到基础版本
git checkout v2.0.0-handbiz-base

# 或基于develop分支开发
git checkout develop

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build
```

## 二次开发指南

1. **基于develop分支开发**
   ```bash
   git checkout develop
   git checkout -b feature/your-feature
   ```

2. **添加新模块**
   - 在 `src/services/` 添加服务
   - 在 `src/pages/` 添加页面
   - 在 `src/App.tsx` 注册路由

3. **数据源扩展**
   - 修改 `src/services/dataCrawler.ts`
   - 添加新的爬虫配置

4. **API扩展**
   - 修改 `src/services/apiRouter.ts`
   - 添加新的路由配置

## 更新日志

### v2.0.0 (2026-06-05)
- 初始化二次开发基础版本
- 完成4大核心模块
- 实现23个服务模块
- 支持12个数据源
- 完成UI/UX优化
