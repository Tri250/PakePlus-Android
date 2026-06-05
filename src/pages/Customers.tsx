import { useMemo, useState } from 'react';
import {
  Search,
  Filter,
  Plus,
  Phone,
  MessageSquare,
  Crown,
  X,
  ChevronRight,
  TrendingUp,
  Users,
  MapPin,
  Wrench,
  Target,
  Calendar,
  Smartphone,
  Gift,
  Megaphone,
  Shield,
  Sparkles,
  Eye,
  BarChart3,
  RefreshCw,
  type LucideIcon,
} from 'lucide-react';

/* -------------------------------------------------------------------------- */
/*  客户资产库 V2.0 — 品牌视角「潜客分层模型」                                  */
/*  放弃公海/私海二元结构，改为 S/A/B/C/D 五层品牌专属分层                       */
/* -------------------------------------------------------------------------- */

type Tier = 'S' | 'A' | 'B' | 'C' | 'D';

interface TierMeta {
  code: Tier;
  name: string;
  subtitle: string;
  gradient: string;          // 卡片渐变背景
  badge: string;             // 标签背景
  ring: string;              // 描边颜色
  text: string;              // 文字主色
  icon: LucideIcon;
  definition: string;
  dataSource: string;
  threshold: string;
  touchStrategy: string;
  touchChannels: { name: string; icon: LucideIcon; color: string }[];
  expectedConversion: number;  // % 预估转化率
}

const TIER_META: Record<Tier, TierMeta> = {
  S: {
    code: 'S',
    name: '换机倒计时',
    subtitle: '本品牌高价值 · 黄金窗口',
    gradient: 'from-rose-500 to-pink-600',
    badge: 'bg-rose-100 text-rose-700 border-rose-200',
    ring: 'ring-rose-300',
    text: 'text-rose-600',
    icon: Crown,
    definition: '本品牌客户 · 购机 > 28 个月或旗舰机 > 20 个月',
    dataSource: '品牌 CRM · 购机记录',
    threshold: '购机月数 ≥ 28 OR (旗舰机 AND 购机月数 ≥ 20)',
    touchStrategy: '企微 1v1 私聊 + 以旧换新专属价',
    touchChannels: [
      { name: '企微私聊', icon: MessageSquare, color: 'text-blue-600' },
      { name: '以旧换新', icon: RefreshCw, color: 'text-green-600' },
    ],
    expectedConversion: 32,
  },
  A: {
    code: 'A',
    name: '合约到期',
    subtitle: '运营商合约机 · 续约黄金期',
    gradient: 'from-amber-500 to-orange-500',
    badge: 'bg-amber-100 text-amber-700 border-amber-200',
    ring: 'ring-amber-300',
    text: 'text-amber-600',
    icon: Calendar,
    definition: '运营商合约机客户 · 合约剩余 < 3 个月',
    dataSource: '运营商数据接口（商务合作）',
    threshold: '合约到期剩余天数 < 90',
    touchStrategy: '企微 + 短信双通道提醒',
    touchChannels: [
      { name: '企微', icon: MessageSquare, color: 'text-blue-600' },
      { name: '短信', icon: Phone, color: 'text-orange-600' },
    ],
    expectedConversion: 24,
  },
  B: {
    code: 'B',
    name: '服务高粘性',
    subtitle: '高频到店 · 转化窗口',
    gradient: 'from-emerald-500 to-teal-500',
    badge: 'bg-emerald-100 text-emerald-700 border-emerald-200',
    ring: 'ring-emerald-300',
    text: 'text-emerald-600',
    icon: Wrench,
    definition: '近 6 个月到店服务 ≥ 2 次但未购机',
    dataSource: '门店服务记录系统',
    threshold: '近 180 天到店次数 ≥ 2 AND 30 天内未购机',
    touchStrategy: '新品体验邀请 + 专属服务升级',
    touchChannels: [
      { name: '新品体验', icon: Sparkles, color: 'text-purple-600' },
      { name: '专属服务', icon: Gift, color: 'text-emerald-600' },
    ],
    expectedConversion: 18,
  },
  C: {
    code: 'C',
    name: '周边潜客',
    subtitle: 'LBS 热力圈 · 圈层外延',
    gradient: 'from-sky-500 to-blue-500',
    badge: 'bg-sky-100 text-sky-700 border-sky-200',
    ring: 'ring-sky-300',
    text: 'text-sky-600',
    icon: MapPin,
    definition: '门店 3km 内目标 POI 中的潜在客群',
    dataSource: '地图 POI + LBS 热力图',
    threshold: 'POI 距离 ≤ 3km AND 客群画像匹配',
    touchStrategy: '朋友圈广告精准投放 + 到店有礼',
    touchChannels: [
      { name: '朋友圈广告', icon: Megaphone, color: 'text-pink-600' },
      { name: '到店有礼', icon: Gift, color: 'text-sky-600' },
    ],
    expectedConversion: 6,
  },
  D: {
    code: 'D',
    name: '竞品用户',
    subtitle: '高换机意向 · 抢夺窗口',
    gradient: 'from-violet-500 to-purple-600',
    badge: 'bg-violet-100 text-violet-700 border-violet-200',
    ring: 'ring-violet-300',
    text: 'text-violet-600',
    icon: Target,
    definition: '使用竞品旗舰机且已用 > 20 个月的周边用户',
    dataSource: '以旧换新回收数据 + 市场调研',
    threshold: '设备品牌 ≠ 本品牌 AND (旗舰机) AND 购机月数 ≥ 20',
    touchStrategy: '竞品换新专项补贴',
    touchChannels: [
      { name: '专项补贴', icon: TrendingUp, color: 'text-violet-600' },
      { name: '定向触达', icon: Megaphone, color: 'text-purple-600' },
    ],
    expectedConversion: 11,
  },
};

interface Lead {
  id: string;
  name: string;
  phone: string;
  tier: Tier;
  avatarColor: string;
  device: string;
  months: number;
  isFlagship: boolean;
  matchRule: string;
  intentScore: number;       // 0-100 意向分
  lastTouchAt: string;
  crmTags: string[];
  details: {
    flagshipRatio?: number;
    avgDeviceAge?: number;
    serviceCount?: number;
    contractDays?: number;
    distance?: number;
    competitor?: string;
    tradeInValue?: number;
  };
  suggestedScript: string;
  subsidyEstimate?: { old: number; gov: number; brand: number; final: number };
}

const LEADS: Lead[] = [
  // S 级 - 3 位本品牌高价值客户
  {
    id: 'L001',
    name: '王先生',
    phone: '138****8888',
    tier: 'S',
    avatarColor: 'bg-rose-100 text-rose-600',
    device: '华为 Mate40 Pro',
    months: 26,
    isFlagship: true,
    matchRule: '旗舰机 + 购机 ≥ 20 月',
    intentScore: 92,
    lastTouchAt: '2026-05-28',
    crmTags: ['即将换机', '旗舰机用户', '高净值'],
    details: { flagshipRatio: 0.68, avgDeviceAge: 26 },
    suggestedScript: '王哥好，Mate40 Pro 陪伴您 26 个月了，正好 Mate70 系列上市，旗舰直降 ¥800 + 以旧换新最高补 ¥1200，给您留一台到店体验。',
    subsidyEstimate: { old: 1800, gov: 500, brand: 800, final: 4999 },
  },
  {
    id: 'L002',
    name: '陈女士',
    phone: '139****2222',
    tier: 'S',
    avatarColor: 'bg-rose-100 text-rose-600',
    device: 'iPhone 14 Pro',
    months: 29,
    isFlagship: true,
    matchRule: '购机 ≥ 28 月（高换机意向）',
    intentScore: 88,
    lastTouchAt: '2026-05-30',
    crmTags: ['即将换机', '高价值', 'iOS 忠诚'],
    details: { flagshipRatio: 0.81, avgDeviceAge: 29 },
    suggestedScript: '陈姐好，iPhone 14 Pro 已用 29 个月，电池效率可能下降了，门店免费检测 + 换新最高补贴 ¥1500，新机到手价更划算。',
    subsidyEstimate: { old: 3200, gov: 500, brand: 1000, final: 6299 },
  },
  {
    id: 'L003',
    name: '刘先生',
    phone: '137****1234',
    tier: 'S',
    avatarColor: 'bg-rose-100 text-rose-600',
    device: '小米 13 Ultra',
    months: 21,
    isFlagship: true,
    matchRule: '旗舰机 + 购机 ≥ 20 月',
    intentScore: 79,
    lastTouchAt: '2026-06-01',
    crmTags: ['旗舰机用户', '摄影爱好者'],
    details: { flagshipRatio: 0.55, avgDeviceAge: 21 },
    suggestedScript: '刘老师好，13U 陪伴您 21 个月了，14 Ultra 新上市影像再升级，门店到店免费体验 + 以旧换新专属价。',
    subsidyEstimate: { old: 2200, gov: 500, brand: 600, final: 4299 },
  },
  // A 级 - 3 位合约到期
  {
    id: 'L004',
    name: '李女士',
    phone: '139****6666',
    tier: 'A',
    avatarColor: 'bg-amber-100 text-amber-600',
    device: 'vivo X90 Pro',
    months: 22,
    isFlagship: false,
    matchRule: '合约剩余 < 90 天',
    intentScore: 71,
    lastTouchAt: '2026-05-26',
    crmTags: ['合约到期', '套餐高价值'],
    details: { contractDays: 67 },
    suggestedScript: '李姐好，合约还剩 67 天到期，现在续约 + 升 5G 套餐，最高直降 ¥1500，再送 1 年碎屏险。',
  },
  {
    id: 'L005',
    name: '张先生',
    phone: '135****0001',
    tier: 'A',
    avatarColor: 'bg-amber-100 text-amber-600',
    device: 'OPPO Find X6',
    months: 24,
    isFlagship: false,
    matchRule: '合约剩余 < 90 天',
    intentScore: 68,
    lastTouchAt: '2026-05-29',
    crmTags: ['合约到期', '商务用户'],
    details: { contractDays: 54 },
    suggestedScript: '张总好，合约还剩 54 天，续约 Find X8 + 商务套餐，立省 ¥2200，到店专人对接。',
  },
  {
    id: 'L006',
    name: '周女士',
    phone: '136****9999',
    tier: 'A',
    avatarColor: 'bg-amber-100 text-amber-600',
    device: '荣耀 Magic5',
    months: 23,
    isFlagship: false,
    matchRule: '合约剩余 < 90 天',
    intentScore: 64,
    lastTouchAt: '2026-05-25',
    crmTags: ['合约到期', '家庭用户'],
    details: { contractDays: 81 },
    suggestedScript: '周姐好，合约还剩 81 天到期，续约 + 家庭融合套餐，全家共享流量+视频会员。',
  },
  // B 级 - 3 位服务高粘性
  {
    id: 'L007',
    name: '赵女士',
    phone: '136****4444',
    tier: 'B',
    avatarColor: 'bg-emerald-100 text-emerald-600',
    device: '—（未在本店购机）',
    months: 0,
    isFlagship: false,
    matchRule: '近 180 天到店 ≥ 2 次 + 未购机',
    intentScore: 58,
    lastTouchAt: '2026-05-31',
    crmTags: ['高频到店', '咨询过机型', '未转化'],
    details: { serviceCount: 4 },
    suggestedScript: '赵姐好，您 3 个月内到店 4 次了，新品体验夜欢迎您再来，赠送专属服务 VIP 一年。',
  },
  {
    id: 'L008',
    name: '吴先生',
    phone: '133****7777',
    tier: 'B',
    avatarColor: 'bg-emerald-100 text-emerald-600',
    device: '—（维修客户）',
    months: 0,
    isFlagship: false,
    matchRule: '近 180 天到店 ≥ 2 次 + 未购机',
    intentScore: 52,
    lastTouchAt: '2026-05-24',
    crmTags: ['维修客户', '贴膜 3 次', '咨询过'],
    details: { serviceCount: 3 },
    suggestedScript: '吴哥好，您多次到店贴膜/咨询，本周新机发布会 VIP 席位预留给您，到店有礼。',
  },
  {
    id: 'L009',
    name: '孙先生',
    phone: '132****0000',
    tier: 'B',
    avatarColor: 'bg-emerald-100 text-emerald-600',
    device: '—（体验客户）',
    months: 0,
    isFlagship: false,
    matchRule: '近 180 天到店 ≥ 2 次 + 未购机',
    intentScore: 47,
    lastTouchAt: '2026-05-20',
    crmTags: ['体验 2 次', '犹豫中'],
    details: { serviceCount: 2 },
    suggestedScript: '孙哥好，X 系列体验过 2 次了，周末店长面对面帮您选机，到店送蓝牙耳机。',
  },
  // C 级 - 2 位周边潜客（来自 LBS）
  {
    id: 'L010',
    name: '陆女士',
    phone: '130****3322',
    tier: 'C',
    avatarColor: 'bg-sky-100 text-sky-600',
    device: 'iPhone 13',
    months: 36,
    isFlagship: false,
    matchRule: '门店 3km 内 + 写字楼客群匹配',
    intentScore: 34,
    lastTouchAt: '未触达',
    crmTags: ['写字楼白领', 'LBS 命中'],
    details: { distance: 0.8 },
    suggestedScript: '陆姐好，您公司附近门店，本周三到店免费贴膜 + 1v1 选机咨询（30 分钟即送咖啡券）。',
  },
  {
    id: 'L011',
    name: '钱先生',
    phone: '131****5566',
    tier: 'C',
    avatarColor: 'bg-sky-100 text-sky-600',
    device: '华为 P50',
    months: 30,
    isFlagship: false,
    matchRule: '门店 3km 内 + 高校职校命中',
    intentScore: 28,
    lastTouchAt: '未触达',
    crmTags: ['高校教师', 'LBS 命中'],
    details: { distance: 1.5 },
    suggestedScript: '钱老师好，距您 1.5km 的门店，本周末教师节专场，凭工作证到店最高补贴 ¥800。',
  },
  // D 级 - 2 位竞品用户
  {
    id: 'L012',
    name: '冯先生',
    phone: '189****0001',
    tier: 'D',
    avatarColor: 'bg-violet-100 text-violet-600',
    device: 'iPhone 14 Pro Max',
    months: 24,
    isFlagship: true,
    matchRule: '竞品旗舰 + 购机 ≥ 20 月',
    intentScore: 56,
    lastTouchAt: '2026-05-15',
    crmTags: ['iOS 竞品', '回收意向'],
    details: { competitor: 'Apple', avgDeviceAge: 24, tradeInValue: 3800 },
    suggestedScript: '冯总好，14 Pro Max 已用 24 个月，竞品换新专项补贴 ¥1500 + 旧机回收 ¥3800，新机立省 ¥5300。',
  },
  {
    id: 'L013',
    name: '蒋女士',
    phone: '188****0002',
    tier: 'D',
    avatarColor: 'bg-violet-100 text-violet-600',
    device: '三星 S23 Ultra',
    months: 22,
    isFlagship: true,
    matchRule: '竞品旗舰 + 购机 ≥ 20 月',
    intentScore: 49,
    lastTouchAt: '2026-05-12',
    crmTags: ['Android 竞品', '回收意向'],
    details: { competitor: 'Samsung', avgDeviceAge: 22, tradeInValue: 2900 },
    suggestedScript: '蒋姐好，S23U 用 22 个月了，换国产品牌旗舰 + 竞品专项补贴 ¥1200，到手价对比一目了然。',
  },
];

const TIER_ORDER: Tier[] = ['S', 'A', 'B', 'C', 'D'];

export default function Customers() {
  const [search, setSearch] = useState('');
  const [activeTiers, setActiveTiers] = useState<Set<Tier>>(new Set(TIER_ORDER));
  const [selectedLead, setSelectedLead] = useState<Lead | null>(null);
  const [touching, setTouching] = useState<string | null>(null);
  const [touchedId, setTouchedId] = useState<string | null>(null);
  const [creating, setCreating] = useState<string | null>(null);
  const [createdTaskId, setCreatedTaskId] = useState<string | null>(null);

  const stats = useMemo(() => {
    const result: Record<Tier, { count: number; leads: number; intent: number }> = {
      S: { count: 0, leads: 0, intent: 0 },
      A: { count: 0, leads: 0, intent: 0 },
      B: { count: 0, leads: 0, intent: 0 },
      C: { count: 0, leads: 0, intent: 0 },
      D: { count: 0, leads: 0, intent: 0 },
    };
    LEADS.forEach((l) => {
      result[l.tier].count += 1;
      result[l.tier].leads += 1;
      result[l.tier].intent += l.intentScore;
    });
    return result;
  }, []);

  const totalCount = LEADS.length;
  const totalIntent = LEADS.reduce((sum, l) => sum + l.intentScore, 0);
  const avgIntent = Math.round(totalIntent / totalCount);

  const filteredLeads = useMemo(() => {
    return LEADS.filter((l) => {
      if (!activeTiers.has(l.tier)) return false;
      if (search) {
        const s = search.toLowerCase();
        if (!l.name.toLowerCase().includes(s) && !l.phone.includes(s) && !l.device.toLowerCase().includes(s)) {
          return false;
        }
      }
      return true;
    });
  }, [search, activeTiers]);

  const toggleTier = (t: Tier) => {
    const next = new Set(activeTiers);
    if (next.has(t)) {
      if (next.size > 1) next.delete(t);
    } else {
      next.add(t);
    }
    setActiveTiers(next);
  };

  const handleTouch = (lead: Lead) => {
    setTouching(lead.id);
    setTimeout(() => {
      setTouching(null);
      setTouchedId(lead.id);
      setTimeout(() => setTouchedId(null), 2500);
    }, 800);
  };

  const handleCreateTask = (lead: Lead) => {
    setCreating(lead.id);
    setTimeout(() => {
      const taskId = `T-${Date.now().toString().slice(-6)}`;
      setCreatedTaskId(taskId);
      setCreating(null);
      setTimeout(() => {
        setCreatedTaskId(null);
        setSelectedLead(null);
      }, 2000);
    }, 800);
  };

  return (
    <div className="space-y-6">
      {/* 顶部标题 + 模型说明 */}
      <div className="bg-gradient-to-r from-slate-900 via-slate-800 to-slate-900 rounded-2xl p-5 text-white">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <Sparkles className="w-5 h-5 text-amber-400" />
              <h1 className="text-xl font-bold">客户资产库 V2.0</h1>
              <span className="px-2 py-0.5 text-xs bg-amber-400/20 text-amber-300 rounded border border-amber-400/30">
                品牌潜客分层模型
              </span>
            </div>
            <p className="text-sm text-slate-300">
              放弃公海/私海二元结构 · 改为品牌视角 S/A/B/C/D 五层分层 · 每层匹配专属触达策略
            </p>
          </div>
          <div className="flex items-center gap-6">
            <div className="text-right">
              <div className="text-2xl font-bold">{totalCount}</div>
              <div className="text-xs text-slate-400">潜客总数</div>
            </div>
            <div className="text-right">
              <div className="text-2xl font-bold text-amber-300">{avgIntent}</div>
              <div className="text-xs text-slate-400">平均意向分</div>
            </div>
            <div className="text-right">
              <div className="text-2xl font-bold text-emerald-300">23.4%</div>
              <div className="text-xs text-slate-400">预估综合转化率</div>
            </div>
          </div>
        </div>
      </div>

      {/* S/A/B/C/D 分层卡片 */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-3">
        {TIER_ORDER.map((t) => {
          const meta = TIER_META[t];
          const s = stats[t];
          const active = activeTiers.has(t);
          const Icon = meta.icon;
          const avgScore = s.count > 0 ? Math.round(s.intent / s.count) : 0;
          return (
            <button
              key={t}
              onClick={() => toggleTier(t)}
              className={`text-left bg-white rounded-xl border-2 p-4 transition-all ${
                active ? `border-transparent ring-2 ${meta.ring} shadow-md` : 'border-gray-200 opacity-60'
              }`}
            >
              <div className={`w-10 h-10 rounded-lg bg-gradient-to-br ${meta.gradient} flex items-center justify-center mb-3 shadow-sm`}>
                <Icon className="w-5 h-5 text-white" />
              </div>
              <div className="flex items-center gap-2 mb-1">
                <span className={`text-lg font-bold ${meta.text}`}>{t} 级</span>
                <span className="text-xs text-gray-500">{meta.name}</span>
              </div>
              <div className="text-xs text-gray-500 mb-3">{meta.subtitle}</div>
              <div className="flex items-end justify-between">
                <div>
                  <div className="text-2xl font-bold text-gray-900">{s.count}</div>
                  <div className="text-xs text-gray-400">客户数</div>
                </div>
                <div className="text-right">
                  <div className={`text-lg font-semibold ${meta.text}`}>{avgScore}</div>
                  <div className="text-xs text-gray-400">平均意向分</div>
                </div>
              </div>
              <div className="mt-2 pt-2 border-t border-gray-100 text-xs text-gray-500">
                预估转化 <span className={`font-semibold ${meta.text}`}>{meta.expectedConversion}%</span>
              </div>
            </button>
          );
        })}
      </div>

      {/* 分层规则说明 - 折叠式 */}
      <details className="bg-white rounded-xl border border-gray-200">
        <summary className="p-4 cursor-pointer flex items-center gap-2 font-medium text-gray-900 hover:bg-gray-50 rounded-xl">
          <BarChart3 className="w-4 h-4 text-gray-400" />
          分层规则 · 数据源 · 触达策略
          <span className="ml-auto text-xs text-gray-400">点击展开</span>
        </summary>
        <div className="p-4 pt-0 grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-5 gap-3">
          {TIER_ORDER.map((t) => {
            const meta = TIER_META[t];
            const Icon = meta.icon;
            return (
              <div key={t} className={`rounded-lg border p-3 ${meta.badge}`}>
                <div className="flex items-center gap-2 mb-2">
                  <Icon className="w-4 h-4" />
                  <span className="font-semibold">{t} 级 · {meta.name}</span>
                </div>
                <div className="space-y-2 text-xs">
                  <div>
                    <div className="opacity-60">定义</div>
                    <div className="font-medium">{meta.definition}</div>
                  </div>
                  <div>
                    <div className="opacity-60">数据源</div>
                    <div className="font-medium">{meta.dataSource}</div>
                  </div>
                  <div>
                    <div className="opacity-60">阈值</div>
                    <div className="font-mono text-[10px]">{meta.threshold}</div>
                  </div>
                  <div>
                    <div className="opacity-60">触达策略</div>
                    <div className="font-medium">{meta.touchStrategy}</div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </details>

      {/* 工具栏 */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex-1 min-w-[200px] relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索客户姓名 / 手机号 / 设备"
              className="w-full pl-10 pr-4 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div className="flex items-center gap-1 text-sm text-gray-500">
            <Filter className="w-4 h-4" />
            筛选分层：
            {TIER_ORDER.map((t) => (
              <button
                key={t}
                onClick={() => toggleTier(t)}
                className={`px-2 py-1 text-xs rounded ${
                  activeTiers.has(t) ? TIER_META[t].badge : 'bg-gray-100 text-gray-400'
                }`}
              >
                {t}
              </button>
            ))}
          </div>
          <button className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm">
            <Plus className="w-4 h-4" />
            手动新增
          </button>
        </div>
      </div>

      {/* 客户列表 - 按分层分组 */}
      <div className="space-y-4">
        {TIER_ORDER.map((t) => {
          if (!activeTiers.has(t)) return null;
          const meta = TIER_META[t];
          const Icon = meta.icon;
          const tierLeads = filteredLeads.filter((l) => l.tier === t);
          if (tierLeads.length === 0) return null;
          return (
            <div key={t} className="bg-white rounded-xl border border-gray-200 overflow-hidden">
              <div className={`p-4 bg-gradient-to-r ${meta.gradient} text-white flex items-center gap-3`}>
                <div className="w-8 h-8 rounded-lg bg-white/20 backdrop-blur flex items-center justify-center">
                  <Icon className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="font-semibold flex items-center gap-2">
                    {t} 级 · {meta.name}
                    <span className="px-2 py-0.5 text-xs bg-white/20 rounded">{tierLeads.length} 人</span>
                  </h3>
                  <p className="text-xs text-white/80">{meta.touchStrategy}</p>
                </div>
              </div>
              <div className="divide-y divide-gray-100">
                {tierLeads.map((lead) => {
                  const touched = touchedId === lead.id;
                  return (
                    <div
                      key={lead.id}
                      className="p-4 hover:bg-gray-50 flex items-center gap-4 cursor-pointer"
                      onClick={() => setSelectedLead(lead)}
                    >
                      <div className={`w-10 h-10 rounded-full ${lead.avatarColor} flex items-center justify-center`}>
                        <span className="font-medium">{lead.name[0]}</span>
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1 flex-wrap">
                          <span className="font-medium text-gray-900">{lead.name}</span>
                          <span className="text-sm text-gray-500">{lead.phone}</span>
                          <span className={`px-2 py-0.5 text-xs rounded border ${meta.badge}`}>
                            {t} 级
                          </span>
                          {lead.isFlagship && (
                            <span className="px-2 py-0.5 text-xs bg-amber-100 text-amber-700 rounded border border-amber-200">
                              <Crown className="w-3 h-3 inline -mt-0.5" /> 旗舰
                            </span>
                          )}
                        </div>
                        <div className="flex items-center gap-3 text-sm text-gray-500 flex-wrap">
                          <span className="flex items-center gap-1">
                            <Smartphone className="w-3 h-3" />
                            {lead.device}
                          </span>
                          {lead.months > 0 && (
                            <>
                              <span>·</span>
                              <span>{lead.months} 个月</span>
                            </>
                          )}
                          <span>·</span>
                          <span className="text-xs text-gray-400">{lead.matchRule}</span>
                          {lead.crmTags.slice(0, 2).map((tag) => (
                            <span key={tag} className="px-2 py-0.5 bg-gray-100 text-gray-600 text-xs rounded">
                              {tag}
                            </span>
                          ))}
                        </div>
                      </div>
                      <div className="flex items-center gap-3">
                        <div className="text-right">
                          <div className={`text-lg font-bold ${meta.text}`}>{lead.intentScore}</div>
                          <div className="text-xs text-gray-400">意向分</div>
                        </div>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleTouch(lead);
                          }}
                          className={`px-3 py-1.5 text-xs rounded-lg ${
                            touched
                              ? 'bg-emerald-100 text-emerald-700'
                              : 'bg-blue-600 text-white hover:bg-blue-700'
                          }`}
                        >
                          {touching === lead.id ? '发送中…' : touched ? '已触达' : '触达'}
                        </button>
                        <ChevronRight className="w-4 h-4 text-gray-400" />
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}

        {filteredLeads.length === 0 && (
          <div className="bg-white rounded-xl border border-gray-200 p-12 text-center text-gray-400">
            <Users className="w-12 h-12 mx-auto mb-3 opacity-50" />
            <p>当前筛选条件下无潜客</p>
          </div>
        )}
      </div>

      {/* 合规说明 */}
      <div className="bg-amber-50 border border-amber-200 rounded-xl p-4">
        <div className="flex items-start gap-3">
          <Shield className="w-5 h-5 text-amber-600 mt-0.5" />
          <div className="text-sm text-amber-900">
            <div className="font-medium mb-1">品牌分层合规说明</div>
            <ul className="space-y-0.5 text-amber-800 text-xs">
              <li>• S/A/B 级：使用品牌自有 CRM + 门店服务记录，触达前已签署《隐私协议》</li>
              <li>• C 级（周边潜客）：仅使用地图 POI + LBS 客群画像，不涉及个人身份信息</li>
              <li>• D 级（竞品用户）：来源于以旧换新回收数据 + 公开市场调研，已脱敏处理</li>
              <li>• 所有触达行为记录保留 180 天，支持品牌总部审计</li>
            </ul>
          </div>
        </div>
      </div>

      {/* 客户详情抽屉 */}
      {selectedLead && (
        <LeadDetailDrawer
          lead={selectedLead}
          onClose={() => setSelectedLead(null)}
          onTouch={handleTouch}
          onCreateTask={handleCreateTask}
          touching={touching === selectedLead.id}
          creating={creating === selectedLead.id}
          taskId={createdTaskId}
        />
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  客户详情抽屉                                                               */
/* -------------------------------------------------------------------------- */

function LeadDetailDrawer({
  lead,
  onClose,
  onTouch,
  onCreateTask,
  touching,
  creating,
  taskId,
}: {
  lead: Lead;
  onClose: () => void;
  onTouch: (l: Lead) => void;
  onCreateTask: (l: Lead) => void;
  touching: boolean;
  creating: boolean;
  taskId: string | null;
}) {
  const meta = TIER_META[lead.tier];
  const Icon = meta.icon;

  return (
    <div className="fixed inset-0 z-50 flex items-end md:items-center md:justify-end bg-black/40" onClick={onClose}>
      <div
        className="bg-white w-full md:max-w-xl md:h-full h-[85vh] overflow-y-auto rounded-t-2xl md:rounded-none"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 抽屉头 */}
        <div className={`p-5 bg-gradient-to-r ${meta.gradient} text-white`}>
          <div className="flex items-start justify-between mb-3">
            <div className="flex items-center gap-3">
              <div className={`w-12 h-12 rounded-full ${lead.avatarColor} flex items-center justify-center`}>
                <span className="text-xl font-bold">{lead.name[0]}</span>
              </div>
              <div>
                <h2 className="text-lg font-bold">{lead.name}</h2>
                <p className="text-sm text-white/80">{lead.phone}</p>
              </div>
            </div>
            <button onClick={onClose} className="p-1 hover:bg-white/20 rounded">
              <X className="w-5 h-5" />
            </button>
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <span className="px-2 py-1 text-xs bg-white/20 rounded flex items-center gap-1">
              <Icon className="w-3 h-3" /> {lead.tier} 级 · {meta.name}
            </span>
            <span className="px-2 py-1 text-xs bg-white/20 rounded">意向分 {lead.intentScore}</span>
            {lead.isFlagship && (
              <span className="px-2 py-1 text-xs bg-amber-400/30 text-amber-100 rounded flex items-center gap-1">
                <Crown className="w-3 h-3" /> 旗舰机
              </span>
            )}
          </div>
        </div>

        <div className="p-5 space-y-5">
          {/* 设备画像 */}
          <section>
            <h3 className="text-sm font-semibold text-gray-900 mb-3 flex items-center gap-2">
              <Smartphone className="w-4 h-4 text-gray-400" />
              设备画像
            </h3>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <Stat label="当前设备" value={lead.device} />
              {lead.months > 0 && <Stat label="使用时长" value={`${lead.months} 个月`} />}
              {lead.details.flagshipRatio !== undefined && (
                <Stat label="区域旗舰机占比" value={`${(lead.details.flagshipRatio * 100).toFixed(0)}%`} />
              )}
              {lead.details.avgDeviceAge !== undefined && (
                <Stat label="区域均龄" value={`${lead.details.avgDeviceAge} 个月`} />
              )}
              {lead.details.serviceCount !== undefined && (
                <Stat label="近 180 天到店" value={`${lead.details.serviceCount} 次`} />
              )}
              {lead.details.contractDays !== undefined && (
                <Stat label="合约剩余" value={`${lead.details.contractDays} 天`} />
              )}
              {lead.details.distance !== undefined && (
                <Stat label="距门店" value={`${lead.details.distance} km`} />
              )}
              {lead.details.competitor && <Stat label="竞品" value={lead.details.competitor} />}
              {lead.details.tradeInValue !== undefined && (
                <Stat label="旧机估值" value={`¥${lead.details.tradeInValue}`} />
              )}
            </div>
          </section>

          {/* 匹配规则 */}
          <section className="bg-gray-50 rounded-lg p-3">
            <div className="text-xs text-gray-500 mb-1">分层匹配规则</div>
            <div className="text-sm font-mono text-gray-900">{meta.threshold}</div>
            <div className="text-xs text-gray-600 mt-1">本客户命中：{lead.matchRule}</div>
          </section>

          {/* 触达建议 */}
          <section>
            <h3 className="text-sm font-semibold text-gray-900 mb-2 flex items-center gap-2">
              <Eye className="w-4 h-4 text-gray-400" />
              触达建议（{meta.touchStrategy}）
            </h3>
            <div className="bg-blue-50 border border-blue-100 rounded-lg p-3 mb-3">
              <div className="text-xs text-blue-600 mb-1 font-medium">推荐话术</div>
              <div className="text-sm text-gray-900">{lead.suggestedScript}</div>
            </div>
            <div className="flex flex-wrap gap-2">
              {meta.touchChannels.map((ch) => {
                const ChIcon = ch.icon;
                return (
                  <span
                    key={ch.name}
                    className={`px-2 py-1 text-xs bg-gray-100 rounded flex items-center gap-1 ${ch.color}`}
                  >
                    <ChIcon className="w-3 h-3" /> {ch.name}
                  </span>
                );
              })}
            </div>
          </section>

          {/* 国补预估 (S/D 级) */}
          {lead.subsidyEstimate && (
            <section className="bg-gradient-to-r from-green-50 to-emerald-50 border border-green-200 rounded-lg p-3">
              <div className="text-sm font-semibold text-green-900 mb-2 flex items-center gap-2">
                💰 以旧换新国补预估
              </div>
              <div className="space-y-1 text-sm">
                <Row label="旧机折价" value={`-¥${lead.subsidyEstimate.old}`} color="text-gray-600" />
                <Row label="国补" value={`-¥${lead.subsidyEstimate.gov}`} color="text-green-700" />
                <Row label="品牌补贴" value={`-¥${lead.subsidyEstimate.brand}`} color="text-green-700" />
                <div className="pt-2 mt-2 border-t border-green-200 flex justify-between font-bold">
                  <span>客户实付预估</span>
                  <span className="text-green-700">¥{lead.subsidyEstimate.final}</span>
                </div>
              </div>
            </section>
          )}

          {/* CRM 标签 */}
          <section>
            <h3 className="text-sm font-semibold text-gray-900 mb-2">客户标签</h3>
            <div className="flex flex-wrap gap-2">
              {lead.crmTags.map((t) => (
                <span key={t} className="px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded">
                  {t}
                </span>
              ))}
            </div>
            <div className="mt-2 text-xs text-gray-500">最后触达：{lead.lastTouchAt}</div>
          </section>

          {/* 任务结果提示 */}
          {taskId && (
            <div className="bg-emerald-50 border border-emerald-200 rounded-lg p-3 text-sm text-emerald-800">
              ✅ 扫街任务已创建：<span className="font-mono font-bold">{taskId}</span>，已自动派发到店员 App。
            </div>
          )}

          {/* 操作按钮 */}
          <div className="flex gap-2 pt-2">
            <button
              onClick={() => onTouch(lead)}
              disabled={touching}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-60"
            >
              <MessageSquare className="w-4 h-4" />
              {touching ? '发送中…' : '立即触达'}
            </button>
            <button
              onClick={() => onCreateTask(lead)}
              disabled={creating}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 disabled:opacity-60"
            >
              <TrendingUp className="w-4 h-4" />
              {creating ? '创建中…' : '生成扫街任务'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-gray-50 rounded-lg p-2">
      <div className="text-xs text-gray-500">{label}</div>
      <div className="text-sm font-medium text-gray-900 mt-0.5">{value}</div>
    </div>
  );
}

function Row({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div className="flex justify-between">
      <span className="text-gray-500">{label}</span>
      <span className={`font-medium ${color}`}>{value}</span>
    </div>
  );
}
