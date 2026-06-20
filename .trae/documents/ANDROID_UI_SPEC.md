# 电池健康 Android App V4.4.6 UI/UX 设计规范

## 1. 设计目标

参考用户提供的五张宣传图（绿色清新 iOS 2026 风格），将 Android 端界面统一升级为：**大标题、白卡片、绿强调、轻拟物、数据可视化** 的设计风格。

## 2. 色彩体系

### 主色调
| 名称 | 色值 | 用途 |
|------|------|------|
| Primary Green | `#34C759` | 主按钮、强调数字、选中态、进度条、图表 |
| Primary Light | `#E8F9ED` | 浅绿背景、标签底色 |
| Background | `#F5F7FA` | 页面底色（浅灰白，非纯白） |
| Card | `#FFFFFF` | 卡片底色 |
| Text Primary | `#1C1C1E` | 主标题、重要数据 |
| Text Secondary | `#6B7280` | 副标题、说明文字 |
| Text Tertiary | `#9CA3AF` | 占位、弱化文字 |
| Divider | `#E5E7EB` | 分隔线 |
| Orange | `#FF9500` | 警告、等级 B/C |
| Red | `#FF3B30` | 危险、等级 D/E |
| Blue | `#007AFF` | 链接、标签 |
| Purple | `#AF52DE` | 特殊强调 |

### 渐变
- 主按钮渐变：`#30D158` → `#34C759`
- 健康度优秀渐变：`#34C759` → `#30D158`
- 卡片顶部光晕：白色 8% 透明度叠加

## 3. 字体与字号

| 用途 | 字号 | 字重 | 颜色 |
|------|------|------|------|
| 页面大标题 | 28-32sp | Bold | Text Primary |
| 模块标题 | 18-20sp | Bold | Text Primary |
| 数据大数字 | 48-60sp | Bold | Primary Green / Text Primary |
| 数据中等数字 | 32-40sp | Bold | Text Primary |
| 正文 | 15-16sp | Regular | Text Primary |
| 副文/标签 | 12-14sp | Medium | Text Secondary |
| 小标签 | 11-12sp | Medium | Primary Green / White |

**字体族**：中文使用系统默认（Roboto + Noto Sans CJK）；英文/数字优先使用 `sans-serif-medium` 或 DIN Alternate（如可嵌入）。

## 4. 卡片与圆角

- 大卡片：圆角 20dp，内边距 20dp，阴影 4dp
- 中卡片：圆角 16dp，内边距 16dp，阴影 2dp
- 小标签/按钮：圆角 12-24dp（胶囊形）
- 列表项：圆角 12dp，白色背景，左侧图标 40dp 圆形浅绿底

## 5. 间距

- 页面水平边距：16-20dp
- 卡片间距：12-16dp
- 卡片内部模块间距：12-16dp
- 文字行间距：1.2-1.4 倍

## 6. 底部导航

- 5 个 Tab：电池健康、充电功率、电池江湖、配置查询、性能分析
- 图标 + 文字，选中绿色 `#34C759`，未选中灰色 `#9CA3AF`
- 顶部圆角 24dp，白色背景，轻微阴影
- 图标尺寸 24dp，文字 12sp

## 7. 动画效果

- 页面入场：卡片从下方 60dp 平移 + 透明度 0→1，duration 400-600ms，OvershootInterpolator
- 数字变化：ValueAnimator 滚动效果
- 进度条：平滑过渡 800ms
- Tab 切换：ViewPager2 默认滑动 + 内容 fadeIn
- 按钮点击：scale 0.96 反馈
- 列表项入场： stagger 100ms 逐个出现

## 8. 图标风格

- 使用 Material Design Icons（Outlined）
- 左侧图标置于 40dp 圆形浅绿背景中（`#E8F9ED`）
- 图标颜色 Primary Green `#34C759`
- 装饰性 3D emoji 暂不实现，用文字/图标替代

## 9. 图表规范

- 柱状图：绿色填充 `#34C759`，圆角顶部 4dp，间距 4dp
- 折线图：绿色线条 `#34C759`，宽度 2dp，填充渐变 `#34C759` 20% 透明度
- 坐标轴/网格：浅灰色 `#E5E7EB`
- 使用 MPAndroidChart

## 10. 按钮规范

- 主按钮：绿色渐变，圆角 24dp，文字白色 16sp Bold，高度 52dp
- 次按钮：白色背景，绿色边框 1dp，圆角 24dp，文字绿色
- 小按钮/标签：浅绿背景 `#E8F9ED`，绿色文字，圆角 12dp

## 11. 状态栏与导航栏

- 状态栏：透明/白色，深色图标
- 底部导航栏：白色，深色图标
- 页面顶部大标题不显示 ActionBar

## 12. 整体布局原则

- 顶部状态栏下方开始内容
- 首屏大标题 + 1-2 个核心卡片
- 信息按卡片分组，避免纯文本堆砌
- 关键数据使用大字号 + 绿色强调
- 每个 Tab 保持一致的卡片间距和圆角
