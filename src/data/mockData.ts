// 模拟数据 - 掌上商客 V2.0

export type Grade = 'S' | 'A' | 'B' | 'C' | 'D';
export type Role = 'rep' | 'manager' | 'hq';
export type TaskStatus = 'todo' | 'doing' | 'done';
export type CustomerStatus = 'replacement_soon' | 'contacted' | 'pending' | 'inactive';

export interface Customer {
  id: string;
  name: string;
  avatar: string; // 单字
  avatarColor: string; // 背景色
  grade: Grade;
  phone: string;
  phoneModel: string;
  intentScore: number; // 0-100
  status: CustomerStatus;
  statusText: string;
  statusSub: string;
  distance: number; // 米
  position: { x: number; y: number }; // 雷达图坐标 0-100
  lastContact: string;
  tags: string[];
}

export interface Task {
  id: string;
  title: string;
  type: 'street' | 'visit' | 'promotion';
  status: TaskStatus;
  statusLabel: string;
  statusColor: string;
  progress: number; // 0-100
  progressText: string;
  distance: string;
  customerCount?: number;
  poiCount?: number;
  doneCount?: number;
}

export interface Activity {
  id: string;
  type: 'visit' | 'visit_done' | 'task_done' | 'signal' | 'reward';
  text: string;
  time: string;
}

export interface Notification {
  id: string;
  title: string;
  body: string;
  time: string;
  type: 'geo' | 'predict' | 'task' | 'system';
  unread: boolean;
}

export interface Achievement {
  id: string;
  title: string;
  desc: string;
  icon: string;
  unlocked: boolean;
  progress?: number;
  total?: number;
}

export interface WeeklyTrend {
  day: string;
  value: number;
}

export interface Competitor {
  id: string;
  name: string;
  delta: number;
  isUp: boolean;
  badge?: string;
}

// ===== 客户数据 =====
export const customers: Customer[] = [
  {
    id: 'c1',
    name: '陈建国',
    avatar: '陈',
    avatarColor: '#10b981',
    grade: 'S',
    phone: '138****6688',
    phoneModel: '华为P40 Pro',
    intentScore: 92,
    status: 'replacement_soon',
    statusText: '预计2周内换机',
    statusSub: '高端机型用户',
    distance: 280,
    position: { x: 28, y: 32 },
    lastContact: '3天前',
    tags: ['高净值', '商务'],
  },
  {
    id: 'c2',
    name: '王芳',
    avatar: '王',
    avatarColor: '#f59e0b',
    grade: 'A',
    phone: '139****1122',
    phoneModel: '小米11',
    intentScore: 78,
    status: 'pending',
    statusText: '换机意向中等',
    statusSub: '关注中',
    distance: 450,
    position: { x: 62, y: 58 },
    lastContact: '1周前',
    tags: ['价格敏感'],
  },
  {
    id: 'c3',
    name: '张明远',
    avatar: '张',
    avatarColor: '#10b981',
    grade: 'A',
    phone: '136****5566',
    phoneModel: 'iPhone 13',
    intentScore: 85,
    status: 'replacement_soon',
    statusText: '换机周期临近',
    statusSub: '建议本周回访',
    distance: 580,
    position: { x: 45, y: 22 },
    lastContact: '5天前',
    tags: ['果粉', '高活跃'],
  },
  {
    id: 'c4',
    name: '李思雨',
    avatar: '李',
    avatarColor: '#3b82f6',
    grade: 'B',
    phone: '135****3344',
    phoneModel: 'OPPO Reno',
    intentScore: 65,
    status: 'contacted',
    statusText: '今日已沟通',
    statusSub: '待跟换机方案',
    distance: 720,
    position: { x: 18, y: 72 },
    lastContact: '今天',
    tags: ['女性', '拍照党'],
  },
  {
    id: 'c5',
    name: '刘晓东',
    avatar: '刘',
    avatarColor: '#ef4444',
    grade: 'S',
    phone: '188****7788',
    phoneModel: '华为Mate 40',
    intentScore: 95,
    status: 'replacement_soon',
    statusText: '紧急跟进',
    statusSub: '竞品已接触',
    distance: 920,
    position: { x: 75, y: 38 },
    lastContact: '昨天',
    tags: ['紧急', '高净值'],
  },
  {
    id: 'c6',
    name: '周慧敏',
    avatar: '周',
    avatarColor: '#8b5cf6',
    grade: 'B',
    phone: '137****9900',
    phoneModel: 'vivo X80',
    intentScore: 60,
    status: 'pending',
    statusText: '首次接触',
    statusSub: '需建立信任',
    distance: 1100,
    position: { x: 35, y: 80 },
    lastContact: '未联系',
    tags: ['新客户'],
  },
  {
    id: 'c7',
    name: '吴志强',
    avatar: '吴',
    avatarColor: '#06b6d4',
    grade: 'C',
    phone: '186****4422',
    phoneModel: '三星S22',
    intentScore: 45,
    status: 'inactive',
    statusText: '休眠客户',
    statusSub: '3个月未联系',
    distance: 1500,
    position: { x: 80, y: 75 },
    lastContact: '3个月前',
    tags: ['休眠'],
  },
  {
    id: 'c8',
    name: '郑佳琪',
    avatar: '郑',
    avatarColor: '#10b981',
    grade: 'A',
    phone: '189****1100',
    phoneModel: 'iPhone 14',
    intentScore: 80,
    status: 'replacement_soon',
    statusText: '电池健康下降',
    statusSub: '建议换新',
    distance: 380,
    position: { x: 50, y: 50 },
    lastContact: '2天前',
    tags: ['女性', '果粉'],
  },
];

// ===== 任务数据 =====
export const tasks: Task[] = [
  {
    id: 't1',
    title: '华强北商圈扫街',
    type: 'street',
    status: 'doing',
    statusLabel: '进行中',
    statusColor: '#3b82f6',
    progress: 67,
    progressText: 'POI 8/12',
    distance: '2.3km',
    doneCount: 8,
    poiCount: 12,
  },
  {
    id: 't2',
    title: '福田CBD客户回访',
    type: 'visit',
    status: 'todo',
    statusLabel: '待开始',
    statusColor: '#94a3b8',
    progress: 0,
    progressText: '3位客户待回访',
    distance: '4.8km',
    customerCount: 3,
  },
  {
    id: 't3',
    title: '南山科技园推广',
    type: 'promotion',
    status: 'done',
    statusLabel: '已完成',
    statusColor: '#10b981',
    progress: 100,
    progressText: 'POI 15/15 · 客户6位',
    distance: '6.2km',
    doneCount: 15,
    poiCount: 15,
    customerCount: 6,
  },
  {
    id: 't4',
    title: '罗湖东门新店开业',
    type: 'promotion',
    status: 'todo',
    statusLabel: '待开始',
    statusColor: '#94a3b8',
    progress: 0,
    progressText: '活动物料已就绪',
    distance: '5.1km',
  },
  {
    id: 't5',
    title: '宝安机场VIP客户',
    type: 'visit',
    status: 'done',
    statusLabel: '已完成',
    statusColor: '#10b981',
    progress: 100,
    progressText: '2位客户已拜访',
    distance: '12.4km',
    customerCount: 2,
  },
];

// ===== 动态 =====
export const activities: Activity[] = [
  { id: 'a1', type: 'visit', text: '拜访客户 张明远，已沟通换机方案', time: '10分钟前' },
  { id: 'a2', type: 'signal', text: '检测到客户 刘晓东 进入500m范围', time: '1小时前' },
  { id: 'a3', type: 'task_done', text: '南山科技园任务已完成', time: '今天 09:42' },
  { id: 'a4', type: 'visit_done', text: '拜访客户 陈建国，预约下周回访', time: '昨天 16:30' },
  { id: 'a5', type: 'reward', text: '本周扫街里程达32公里，达成成就', time: '昨天' },
];

// ===== 通知 =====
export const notifications: Notification[] = [
  {
    id: 'n1',
    title: '地理围栏触发',
    body: '您附近500米有3位高意向客户，建议优先拜访',
    time: '10分钟前',
    type: 'geo',
    unread: true,
  },
  {
    id: 'n2',
    title: '换机周期预测',
    body: '客户 张明远 预计下周进入换机周期',
    time: '1小时前',
    type: 'predict',
    unread: true,
  },
  {
    id: 'n3',
    title: '任务提醒',
    body: '今日扫街任务还有2个未完成',
    time: '今天 15:00',
    type: 'task',
    unread: false,
  },
];

// ===== 成就 =====
export const achievements: Achievement[] = [
  { id: 'ac1', title: '步行之王', desc: '本周累计扫街32公里', icon: '🚶', unlocked: true, progress: 32, total: 50 },
  { id: 'ac2', title: '转化达人', desc: '本月成功转化12位客户', icon: '🎯', unlocked: true, progress: 12, total: 20 },
  { id: 'ac3', title: '商圈征服者', desc: '已覆盖8个核心商圈', icon: '🏆', unlocked: false, progress: 8, total: 10 },
  { id: 'ac4', title: '客户挚友', desc: '获得5位客户五星好评', icon: '⭐', unlocked: false, progress: 3, total: 5 },
];

// ===== 周趋势 =====
export const weeklyTrend: WeeklyTrend[] = [
  { day: '周一', value: 18 },
  { day: '周二', value: 25 },
  { day: '周三', value: 15 },
  { day: '周四', value: 32 },
  { day: '周五', value: 28 },
  { day: '周六', value: 42 },
  { day: '周日', value: 38 },
];

// ===== 竞品 =====
export const competitors: Competitor[] = [
  { id: 'co1', name: '华为门店客流', delta: 8.2, isUp: true },
  { id: 'co2', name: '小米新店开业', delta: 0, isUp: false, badge: '需关注' },
  { id: 'co3', name: 'OPPO 促销力度', delta: -3.5, isUp: false },
];

// ===== 角色配置 =====
export const roleConfig = {
  rep: {
    name: '王磊',
    title: '地推专员',
    avatar: '王',
    avatarColor: '#3b82f6',
  },
  manager: {
    name: '李美华',
    title: '门店店长',
    avatar: '李',
    avatarColor: '#8b5cf6',
  },
  hq: {
    name: '张志远',
    title: '总部运营',
    avatar: '张',
    avatarColor: '#10b981',
  },
} as const;

// 客户分级颜色
export const gradeColors: Record<Grade, { bg: string; text: string }> = {
  S: { bg: '#fef3c7', text: '#b45309' },
  A: { bg: '#dcfce7', text: '#15803d' },
  B: { bg: '#dbeafe', text: '#1d4ed8' },
  C: { bg: '#f3e8ff', text: '#7e22ce' },
  D: { bg: '#f1f5f9', text: '#475569' },
};
