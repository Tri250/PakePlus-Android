import { useState } from 'react';
import {
  Calculator,
  Sparkles,
  Copy,
  ThumbsUp,
  Send,
  Image as ImageIcon,
  Megaphone,
  MapPin,
  ScanLine,
  Plus,
  Eye,
  Heart,
  Share2,
  Download,
  Wand2,
  CheckCircle2,
  Building2,
  Briefcase,
  GraduationCap,
  Home as HomeIcon,
  ShoppingBag,
  TrendingUp,
  Clock,
  Users,
  Target,
  Zap,
  FileText,
  Hash,
  Type,
  Play,
} from 'lucide-react';

/* -------------------------------------------------------------------------- */
/*  营销作战台 V2.0                                                              */
/*  AI 活动海报 / 微信附近推 / AI 话术库 / NFC 素材 / 以旧换新估价器              */
/* -------------------------------------------------------------------------- */

type Tab = 'poster' | 'wechat-nearby' | 'script' | 'nfc-material' | 'trade-in';

interface PosterTemplate {
  id: string;
  title: string;
  channel: '朋友圈海报' | '小红书图文' | '抖音口播';
  audience: string;
  cover: string;
  preview: string[];
  tag: string;
}

const POSTER_TEMPLATES: PosterTemplate[] = [
  {
    id: 'P001',
    title: 'Mate70 上市首发 · 老用户 ¥1500 补贴',
    channel: '朋友圈海报',
    audience: '商务白领',
    cover: 'linear-gradient(135deg, #f43f5e 0%, #f59e0b 100%)',
    preview: ['Mate70 Pro 上市', '老用户专享 ¥1500', '扫码预约 · 国补 ¥500', '北京·国贸旗舰店'],
    tag: '高转化',
  },
  {
    id: 'P002',
    title: '暑期学生季 · 9 折 + 碎屏险',
    channel: '小红书图文',
    audience: '学生教师',
    cover: 'linear-gradient(135deg, #ec4899 0%, #8b5cf6 100%)',
    preview: ['学生证立减 9 折', '送一年碎屏险', '3 期免息分期', '门店：北京/上海/深圳'],
    tag: '学生爆款',
  },
  {
    id: 'P003',
    title: '家庭融合套餐 · 全家共享流量',
    channel: '朋友圈海报',
    audience: '家庭用户',
    cover: 'linear-gradient(135deg, #10b981 0%, #06b6d4 100%)',
    preview: ['一家 5 口共享', '流量 + 视频会员', '最高立省 ¥3000', '运营商官方合作'],
    tag: '家庭优选',
  },
  {
    id: 'P004',
    title: '60s 口播：国补 + 以旧换新怎么薅？',
    channel: '抖音口播',
    audience: '年轻客群',
    cover: 'linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%)',
    preview: ['Hook：换机亏了？', '3 步教你薅国补', 'Case：Mate70 立省 2800', 'CTA：到店领'],
    tag: '爆款潜质',
  },
];

interface ScriptItem {
  poiType: '写字楼' | '小区' | '学校' | '商场';
  scenario: string;
  script: string;
  highlight: string;
}

const SCRIPT_LIBRARY: ScriptItem[] = [
  {
    poiType: '写字楼',
    scenario: '早高峰 8:00-10:00',
    script: '您好，我是隔壁 1.2km 华为体验店的顾问，您赶时间的话加个微信，我把门店地址和 Mate70 预约链接发您，中午休息随时可以到店 30 分钟选机咨询。',
    highlight: '强调 "30 分钟即送咖啡券"',
  },
  {
    poiType: '写字楼',
    scenario: '午休 12:00-14:00',
    script: '您好，午休时间我们门店就在 800 米外，免费贴膜 + 1v1 选机咨询，30 分钟搞定，不耽误您下午工作。',
    highlight: '强调 "免费 + 快速"',
  },
  {
    poiType: '小区',
    scenario: '傍晚 17:00-20:00',
    script: '您好大姐，咱们小区附近门店今晚 8 点前到店有礼，凭小区门禁卡再送小米体重秤一台，顺便给老人/孩子选个手机？',
    highlight: '强调 "小区专享 + 全家"',
  },
  {
    poiType: '学校',
    scenario: '开学季 9 月',
    script: '同学你好，凭学生证 9 折 + 一年碎屏险，iPhone/华为/小米都有，要不要加微信预约本周到店？',
    highlight: '强调 "学生证优惠 + 预约"',
  },
  {
    poiType: '商场',
    scenario: '周末 14:00-18:00',
    script: '您好，今天我们店有 Mate70 体验活动，扫码领 ¥30 配件券，进店免费试用最新旗舰，留下微信后续活动优先通知。',
    highlight: '强调 "礼品 + 试用"',
  },
];

interface NFCMaterial {
  id: string;
  name: string;
  tag_id: string;
  scene: string;
  thumbnail: string;
  scans: number;
  conversions: number;
  status: 'online' | 'offline';
}

const NFC_MATERIALS: NFCMaterial[] = [
  { id: 'N001', name: '国贸地铁站领券卡', tag_id: 'NFC-BJ-001', scene: '地铁出口', thumbnail: 'linear-gradient(135deg, #f43f5e, #f59e0b)', scans: 1842, conversions: 268, status: 'online' },
  { id: 'N002', name: '三里屯太古里体验', tag_id: 'NFC-BJ-002', scene: '商场门口', thumbnail: 'linear-gradient(135deg, #8b5cf6, #ec4899)', scans: 1236, conversions: 192, status: 'online' },
  { id: 'N003', name: '望京 SOHO 入会卡', tag_id: 'NFC-BJ-003', scene: '写字楼大堂', thumbnail: 'linear-gradient(135deg, #10b981, #06b6d4)', scans: 968, conversions: 142, status: 'online' },
  { id: 'N004', name: '西单大悦城领券', tag_id: 'NFC-BJ-004', scene: '商场中庭', thumbnail: 'linear-gradient(135deg, #f59e0b, #ef4444)', scans: 658, conversions: 87, status: 'online' },
  { id: 'N005', name: '中关村鼎好体验', tag_id: 'NFC-BJ-005', scene: '电子市场', thumbnail: 'linear-gradient(135deg, #3b82f6, #8b5cf6)', scans: 432, conversions: 58, status: 'offline' },
];

export default function Marketing() {
  const [tab, setTab] = useState<Tab>('poster');

  return (
    <div className="space-y-6">
      {/* 顶部 */}
      <div className="bg-gradient-to-r from-amber-500 via-orange-500 to-pink-500 rounded-2xl p-5 text-white">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <Megaphone className="w-5 h-5" />
              <h1 className="text-xl font-bold">营销作战台 V2.0</h1>
              <span className="px-2 py-0.5 text-xs bg-white/20 rounded border border-white/30">5 大模块</span>
            </div>
            <p className="text-sm text-white/90">
              AI 活动海报 / 微信附近推 / AI 话术库 / NFC 素材管理 / 以旧换新估价器
            </p>
          </div>
          <div className="flex items-center gap-6 text-sm">
            <div>
              <div className="text-xl font-bold">4</div>
              <div className="text-xs text-white/70">在投海报</div>
            </div>
            <div>
              <div className="text-xl font-bold">5,136</div>
              <div className="text-xs text-white/70">今日触达</div>
            </div>
            <div>
              <div className="text-xl font-bold">747</div>
              <div className="text-xs text-white/70">转化人数</div>
            </div>
            <div>
              <div className="text-xl font-bold">14.5%</div>
              <div className="text-xs text-white/70">综合转化</div>
            </div>
          </div>
        </div>
      </div>

      {/* Tab 导航 */}
      <div className="bg-white rounded-xl border border-gray-200 p-1 flex gap-1 overflow-x-auto">
        {[
          { key: 'poster', label: 'AI 活动海报', icon: ImageIcon },
          { key: 'wechat-nearby', label: '微信附近推', icon: MapPin },
          { key: 'script', label: 'AI 话术库', icon: Wand2 },
          { key: 'nfc-material', label: 'NFC 素材', icon: ScanLine },
          { key: 'trade-in', label: '以旧换新估价', icon: Calculator },
        ].map((t) => {
          const Icon = t.icon;
          const active = tab === t.key;
          return (
            <button
              key={t.key}
              onClick={() => setTab(t.key as Tab)}
              className={`flex-1 min-w-[120px] flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-medium rounded-lg transition-colors ${
                active ? 'bg-amber-500 text-white shadow-sm' : 'text-gray-600 hover:bg-gray-50'
              }`}
            >
              <Icon className="w-4 h-4" />
              {t.label}
            </button>
          );
        })}
      </div>

      {tab === 'poster' && <PosterStudio />}
      {tab === 'wechat-nearby' && <WechatNearby />}
      {tab === 'script' && <ScriptLibrary />}
      {tab === 'nfc-material' && <NFCMaterialPanel />}
      {tab === 'trade-in' && <TradeInCalculator />}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  1. AI 活动海报生成                                                          */
/* -------------------------------------------------------------------------- */

function PosterStudio() {
  const [generating, setGenerating] = useState<string | null>(null);
  const [generated, setGenerated] = useState<Set<string>>(new Set());

  const handleGenerate = (id: string) => {
    setGenerating(id);
    setTimeout(() => {
      setGenerating(null);
      setGenerated((prev) => new Set(prev).add(id));
    }, 1200);
  };

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-200 p-4 flex flex-wrap items-center gap-3">
        <div className="flex-1 min-w-[200px]">
          <div className="text-sm font-medium text-gray-900">活动主题</div>
          <input
            defaultValue="Mate70 上市首发 · 老用户专属补贴"
            className="w-full mt-1 px-3 py-1.5 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-500"
          />
        </div>
        <div>
          <div className="text-sm font-medium text-gray-900">目标客群</div>
          <select className="mt-1 px-3 py-1.5 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-500">
            <option>商务白领</option>
            <option>学生教师</option>
            <option>家庭用户</option>
            <option>年轻客群</option>
          </select>
        </div>
        <button className="px-4 py-2 bg-gradient-to-r from-amber-500 to-pink-500 text-white rounded-lg flex items-center gap-2">
          <Wand2 className="w-4 h-4" />
          AI 智能生成
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {POSTER_TEMPLATES.map((p) => {
          const isGen = generating === p.id;
          const done = generated.has(p.id);
          return (
            <div key={p.id} className="bg-white rounded-xl border border-gray-200 overflow-hidden">
              <div className="h-40 relative" style={{ background: p.cover }}>
                <div className="absolute top-2 left-2 px-2 py-0.5 text-[10px] bg-white/30 text-white rounded backdrop-blur">
                  {p.channel}
                </div>
                <div className="absolute top-2 right-2 px-2 py-0.5 text-[10px] bg-white text-gray-700 rounded font-medium">
                  {p.tag}
                </div>
                <div className="absolute bottom-2 left-2 right-2 text-white">
                  <div className="text-xs opacity-80">{p.audience}</div>
                  <div className="text-sm font-bold leading-tight">{p.title}</div>
                </div>
              </div>
              <div className="p-3">
                <div className="space-y-1 mb-3 text-xs text-gray-600">
                  {p.preview.map((line, i) => (
                    <div key={i} className="flex items-start gap-1">
                      <span className="text-amber-500 mt-0.5">▸</span>
                      <span>{line}</span>
                    </div>
                  ))}
                </div>
                <div className="flex gap-1">
                  <button
                    onClick={() => handleGenerate(p.id)}
                    disabled={isGen}
                    className="flex-1 px-2 py-1.5 text-xs bg-gradient-to-r from-amber-500 to-pink-500 text-white rounded disabled:opacity-50"
                  >
                    {isGen ? '生成中…' : done ? '已生成 ✓' : 'AI 一键生成'}
                  </button>
                  {done && (
                    <>
                      <button className="px-2 py-1.5 text-xs bg-gray-100 text-gray-700 rounded">
                        <Eye className="w-3 h-3" />
                      </button>
                      <button className="px-2 py-1.5 text-xs bg-gray-100 text-gray-700 rounded">
                        <Download className="w-3 h-3" />
                      </button>
                    </>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  2. 微信附近推                                                                */
/* -------------------------------------------------------------------------- */

function WechatNearby() {
  const [radius, setRadius] = useState(3);
  const [audience, setAudience] = useState<'all' | 'white-collar' | 'family' | 'student'>('all');
  const [launching, setLaunching] = useState(false);
  const [launched, setLaunched] = useState(false);

  const handleLaunch = () => {
    setLaunching(true);
    setTimeout(() => {
      setLaunching(false);
      setLaunched(true);
    }, 1500);
  };

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 配置面板 */}
        <div className="bg-white rounded-xl border border-gray-200 p-4 space-y-4">
          <h3 className="font-semibold text-gray-900 flex items-center gap-2">
            <MapPin className="w-4 h-4 text-emerald-600" />
            投放配置
          </h3>

          <div>
            <label className="text-xs text-gray-500 block mb-1">投放半径</label>
            <div className="grid grid-cols-4 gap-1">
              {[1, 3, 5, 10].map((r) => (
                <button
                  key={r}
                  onClick={() => setRadius(r)}
                  className={`px-2 py-1.5 text-sm rounded-lg border ${
                    radius === r
                      ? 'bg-emerald-600 text-white border-emerald-600'
                      : 'bg-white text-gray-700 border-gray-200'
                  }`}
                >
                  {r}km
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="text-xs text-gray-500 block mb-1">目标人群</label>
            <div className="space-y-1">
              {[
                { value: 'all', label: '全部人群', icon: Users, count: '24.6万' },
                { value: 'white-collar', label: '写字楼白领', icon: Briefcase, count: '8.2万' },
                { value: 'family', label: '家庭用户', icon: HomeIcon, count: '6.4万' },
                { value: 'student', label: '高校学生', icon: GraduationCap, count: '3.8万' },
              ].map((a) => {
                const Icon = a.icon;
                const active = audience === a.value;
                return (
                  <button
                    key={a.value}
                    onClick={() => setAudience(a.value as any)}
                    className={`w-full p-2 text-left rounded-lg border flex items-center gap-2 ${
                      active ? 'bg-emerald-50 border-emerald-300' : 'border-gray-200 hover:border-emerald-200'
                    }`}
                  >
                    <Icon className="w-4 h-4 text-emerald-600" />
                    <span className="text-sm text-gray-700 flex-1">{a.label}</span>
                    <span className="text-xs text-gray-500">{a.count}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <label className="text-xs text-gray-500 block mb-1">预算</label>
            <div className="text-2xl font-bold text-gray-900">¥5,000</div>
            <div className="text-xs text-gray-500">预计触达 1.2 万人 · CPM ¥42</div>
          </div>

          <button
            onClick={handleLaunch}
            disabled={launching}
            className="w-full px-4 py-2.5 bg-gradient-to-r from-emerald-500 to-green-500 text-white rounded-lg font-medium disabled:opacity-50 flex items-center justify-center gap-2"
          >
            {launching ? '投放中…' : launched ? '✓ 已投放朋友圈' : '一键投放朋友圈广告'}
          </button>
          {launched && (
            <div className="text-xs text-emerald-700 bg-emerald-50 rounded p-2">
              ✅ 已通过腾讯广告 API 提交 · 预计 30 分钟内审核完成
            </div>
          )}
        </div>

        {/* 地图预览 + 统计 */}
        <div className="lg:col-span-2 space-y-4">
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <div className="flex items-center gap-2 mb-3">
              <MapPin className="w-4 h-4 text-emerald-600" />
              <h3 className="font-semibold text-gray-900">投放范围预览</h3>
              <span className="text-xs text-gray-500 ml-auto">门店：国贸旗舰店</span>
            </div>
            <div className="relative h-48 bg-gradient-to-br from-emerald-50 to-teal-50 rounded-lg flex items-center justify-center overflow-hidden">
              <div
                className="absolute rounded-full border-2 border-emerald-400 bg-emerald-200/30"
                style={{ width: `${radius * 40}px`, height: `${radius * 40}px` }}
              />
              <div className="absolute w-4 h-4 bg-emerald-600 rounded-full ring-4 ring-emerald-300" />
              <div className="absolute bottom-2 right-2 text-xs text-emerald-700 bg-white/80 rounded px-2 py-1">
                覆盖人口 {(radius * 8.2).toFixed(1)} 万
              </div>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <StatCard label="预计曝光" value="8.6万" icon={Eye} color="blue" />
            <StatCard label="预计点击" value="5,400" icon={TrendingUp} color="violet" />
            <StatCard label="预计到店" value="186" icon={Target} color="emerald" />
          </div>
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value, icon: Icon, color }: any) {
  const colorMap: Record<string, string> = {
    blue: 'from-blue-500 to-cyan-500',
    violet: 'from-violet-500 to-purple-500',
    emerald: 'from-emerald-500 to-teal-500',
  };
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-3">
      <div className="flex items-center gap-2">
        <div className={`w-8 h-8 rounded-lg bg-gradient-to-br ${colorMap[color]} flex items-center justify-center`}>
          <Icon className="w-4 h-4 text-white" />
        </div>
        <div>
          <div className="text-lg font-bold text-gray-900">{value}</div>
          <div className="text-xs text-gray-500">{label}</div>
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  3. AI 话术库（按 POI 类型个性化）                                            */
/* -------------------------------------------------------------------------- */

function ScriptLibrary() {
  const [filterType, setFilterType] = useState<'全部' | ScriptItem['poiType']>('全部');
  const [copied, setCopied] = useState<string | null>(null);

  const filtered = SCRIPT_LIBRARY.filter((s) => filterType === '全部' || s.poiType === filterType);

  const handleCopy = (script: string, key: string) => {
    navigator.clipboard?.writeText(script);
    setCopied(key);
    setTimeout(() => setCopied(null), 1500);
  };

  const poiIcon = (t: ScriptItem['poiType']) => {
    const map = {
      写字楼: <Building2 className="w-4 h-4" />,
      小区: <HomeIcon className="w-4 h-4" />,
      学校: <GraduationCap className="w-4 h-4" />,
      商场: <ShoppingBag className="w-4 h-4" />,
    };
    return map[t];
  };

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-200 p-4 flex flex-wrap items-center gap-2">
        <Wand2 className="w-4 h-4 text-violet-600" />
        <span className="text-sm font-medium text-gray-900">POI 类型：</span>
        {(['全部', '写字楼', '小区', '学校', '商场'] as const).map((t) => (
          <button
            key={t}
            onClick={() => setFilterType(t as any)}
            className={`px-3 py-1 text-xs rounded-lg border ${
              filterType === t
                ? 'bg-violet-600 text-white border-violet-600'
                : 'bg-white text-gray-700 border-gray-200'
            }`}
          >
            {t}
          </button>
        ))}
        <span className="ml-auto text-xs text-gray-500">共 {filtered.length} 个话术</span>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        {filtered.map((s, i) => {
          const key = `${s.poiType}-${i}`;
          return (
            <div key={key} className="bg-white rounded-xl border border-gray-200 p-4">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <div className="w-8 h-8 rounded-lg bg-violet-100 text-violet-700 flex items-center justify-center">
                    {poiIcon(s.poiType)}
                  </div>
                  <div>
                    <div className="text-sm font-semibold text-gray-900">{s.poiType}</div>
                    <div className="text-xs text-gray-500 flex items-center gap-1">
                      <Clock className="w-3 h-3" /> {s.scenario}
                    </div>
                  </div>
                </div>
              </div>
              <div className="bg-violet-50 rounded-lg p-3 mb-2">
                <div className="text-sm text-gray-900 leading-relaxed">{s.script}</div>
              </div>
              <div className="flex items-center justify-between">
                <div className="text-xs text-amber-700 bg-amber-50 rounded px-2 py-1">
                  💡 {s.highlight}
                </div>
                <div className="flex gap-1">
                  <button
                    onClick={() => handleCopy(s.script, key)}
                    className="px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded flex items-center gap-1"
                  >
                    {copied === key ? <><CheckCircle2 className="w-3 h-3 text-emerald-500" />已复制</> : <><Copy className="w-3 h-3" />复制</>}
                  </button>
                  <button className="px-2 py-1 text-xs bg-violet-100 text-violet-700 rounded flex items-center gap-1">
                    <ThumbsUp className="w-3 h-3" /> 好评
                  </button>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  4. NFC 碰一碰素材管理                                                       */
/* -------------------------------------------------------------------------- */

function NFCMaterialPanel() {
  const [selected, setSelected] = useState<NFCMaterial | null>(null);

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-200 p-4 flex flex-wrap items-center gap-3">
        <div className="flex-1 min-w-[200px]">
          <h3 className="text-sm font-semibold text-gray-900">NFC 素材管理</h3>
          <p className="text-xs text-gray-500">为每个 NFC 标签配置投放素材 · 实时追踪扫码转化</p>
        </div>
        <button className="px-3 py-1.5 bg-amber-500 text-white text-sm rounded-lg flex items-center gap-1">
          <Plus className="w-4 h-4" /> 新建素材
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
        {NFC_MATERIALS.map((m) => {
          const cvr = m.scans > 0 ? ((m.conversions / m.scans) * 100).toFixed(1) : '0';
          return (
            <div
              key={m.id}
              className="bg-white rounded-xl border border-gray-200 overflow-hidden cursor-pointer hover:shadow-md transition-shadow"
              onClick={() => setSelected(m)}
            >
              <div className="h-24 relative" style={{ background: m.thumbnail }}>
                <div className="absolute top-2 left-2 px-2 py-0.5 text-[10px] bg-white/30 text-white rounded backdrop-blur flex items-center gap-1">
                  <ScanLine className="w-3 h-3" /> {m.tag_id}
                </div>
                <div className="absolute top-2 right-2">
                  <span className={`px-2 py-0.5 text-[10px] rounded ${
                    m.status === 'online' ? 'bg-emerald-500 text-white' : 'bg-gray-500 text-white'
                  }`}>
                    {m.status === 'online' ? '在线' : '已下架'}
                  </span>
                </div>
                <div className="absolute bottom-2 left-2 right-2 text-white text-sm font-bold">
                  {m.name}
                </div>
              </div>
              <div className="p-3">
                <div className="text-xs text-gray-500 mb-2">📍 {m.scene}</div>
                <div className="grid grid-cols-3 gap-2 text-xs">
                  <div>
                    <div className="text-gray-500">扫码</div>
                    <div className="font-bold text-gray-900">{m.scans.toLocaleString()}</div>
                  </div>
                  <div>
                    <div className="text-gray-500">转化</div>
                    <div className="font-bold text-emerald-600">{m.conversions}</div>
                  </div>
                  <div>
                    <div className="text-gray-500">CVR</div>
                    <div className="font-bold text-violet-600">{cvr}%</div>
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {selected && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setSelected(null)}>
          <div className="bg-white rounded-2xl max-w-md w-full mx-4 overflow-hidden" onClick={(e) => e.stopPropagation()}>
            <div className="h-32" style={{ background: selected.thumbnail }} />
            <div className="p-5">
              <h3 className="font-bold text-gray-900 mb-1">{selected.name}</h3>
              <p className="text-sm text-gray-500 mb-3">📍 {selected.scene} · {selected.tag_id}</p>
              <div className="grid grid-cols-3 gap-2 text-center text-sm">
                <div className="bg-gray-50 rounded p-2">
                  <div className="text-lg font-bold">{selected.scans.toLocaleString()}</div>
                  <div className="text-xs text-gray-500">扫码数</div>
                </div>
                <div className="bg-emerald-50 rounded p-2">
                  <div className="text-lg font-bold text-emerald-700">{selected.conversions}</div>
                  <div className="text-xs text-gray-500">转化数</div>
                </div>
                <div className="bg-violet-50 rounded p-2">
                  <div className="text-lg font-bold text-violet-700">
                    {((selected.conversions / selected.scans) * 100).toFixed(1)}%
                  </div>
                  <div className="text-xs text-gray-500">CVR</div>
                </div>
              </div>
              <button
                onClick={() => setSelected(null)}
                className="w-full mt-4 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  5. 以旧换新估价器（升级版）                                                  */
/* -------------------------------------------------------------------------- */

function TradeInCalculator() {
  const [oldDevice, setOldDevice] = useState('华为 Mate40 Pro');
  const [oldPrice, setOldPrice] = useState(2400);
  const [condition, setCondition] = useState<'excellent' | 'good' | 'fair'>('good');
  const [newDevice, setNewDevice] = useState('华为 Mate70 Pro');
  const [newPrice, setNewPrice] = useState(6999);
  const [govSubsidy, setGovSubsidy] = useState(500);
  const [brandSubsidy, setBrandSubsidy] = useState(1000);
  const [repairCount] = useState(0);

  const conditionFactor = { excellent: 1.0, good: 0.85, fair: 0.7 }[condition];
  const actualOldPrice = Math.round(oldPrice * conditionFactor);
  const actualPay = Math.max(0, newPrice - actualOldPrice - govSubsidy - brandSubsidy);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div className="bg-white rounded-xl border border-gray-200 p-5 space-y-4">
        <div className="flex items-center gap-2 pb-2 border-b border-gray-200">
          <Calculator className="w-5 h-5 text-blue-600" />
          <h2 className="font-semibold text-gray-900">以旧换新估价器</h2>
        </div>

        <div className="space-y-3">
          <div>
            <label className="text-xs text-gray-500 block mb-1">旧设备型号</label>
            <input
              value={oldDevice}
              onChange={(e) => setOldDevice(e.target.value)}
              className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-gray-500 block mb-1">原购入价 (元)</label>
              <input
                type="number"
                value={oldPrice}
                onChange={(e) => setOldPrice(Number(e.target.value))}
                className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm"
              />
            </div>
            <div>
              <label className="text-xs text-gray-500 block mb-1">机况</label>
              <div className="grid grid-cols-3 gap-1">
                {(['excellent', 'good', 'fair'] as const).map((c) => (
                  <button
                    key={c}
                    onClick={() => setCondition(c)}
                    className={`px-2 py-1.5 text-xs rounded-lg border ${
                      condition === c
                        ? 'bg-blue-600 text-white border-blue-600'
                        : 'bg-white text-gray-700 border-gray-200'
                    }`}
                  >
                    {c === 'excellent' ? '优' : c === 'good' ? '良' : '一般'}
                  </button>
                ))}
              </div>
            </div>
          </div>
          <div className="text-xs text-gray-500 bg-gray-50 rounded p-2">
            🔧 维修次数：{repairCount} 次（影响估价）
          </div>
        </div>

        <div className="border-t border-gray-200 pt-4 space-y-3">
          <div>
            <label className="text-xs text-gray-500 block mb-1">新设备型号</label>
            <input
              value={newDevice}
              onChange={(e) => setNewDevice(e.target.value)}
              className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm"
            />
          </div>
          <div className="grid grid-cols-3 gap-2">
            <div>
              <label className="text-xs text-gray-500 block mb-1">售价 (元)</label>
              <input
                type="number"
                value={newPrice}
                onChange={(e) => setNewPrice(Number(e.target.value))}
                className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm"
              />
            </div>
            <div>
              <label className="text-xs text-gray-500 block mb-1">国补 (元)</label>
              <input
                type="number"
                value={govSubsidy}
                onChange={(e) => setGovSubsidy(Number(e.target.value))}
                className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm"
              />
            </div>
            <div>
              <label className="text-xs text-gray-500 block mb-1">品牌补贴 (元)</label>
              <input
                type="number"
                value={brandSubsidy}
                onChange={(e) => setBrandSubsidy(Number(e.target.value))}
                className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm"
              />
            </div>
          </div>
        </div>
      </div>

      <div className="bg-gradient-to-br from-blue-50 to-cyan-50 rounded-xl border border-blue-200 p-5 space-y-3">
        <div className="flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-blue-600" />
          <h2 className="font-semibold text-gray-900">实付金额预估</h2>
        </div>
        <div className="space-y-2 text-sm">
          <Row label="新机售价" value={`¥${newPrice.toLocaleString()}`} color="text-gray-700" />
          <Row label={`旧机抵扣（机况 ${condition === 'excellent' ? '优' : condition === 'good' ? '良' : '一般'} × ${(conditionFactor * 100).toFixed(0)}%）`} value={`-¥${actualOldPrice.toLocaleString()}`} color="text-emerald-600" />
          <Row label="政府国补" value={`-¥${govSubsidy.toLocaleString()}`} color="text-emerald-600" />
          <Row label="品牌专项补贴" value={`-¥${brandSubsidy.toLocaleString()}`} color="text-emerald-600" />
        </div>
        <div className="pt-3 mt-2 border-t border-blue-200">
          <div className="flex items-center justify-between">
            <span className="text-base font-semibold text-gray-900">客户实付预估</span>
            <span className="text-3xl font-bold text-blue-600">¥{actualPay.toLocaleString()}</span>
          </div>
          <div className="text-xs text-gray-500 mt-1">
            综合优惠 ¥{(actualOldPrice + govSubsidy + brandSubsidy).toLocaleString()} · 优惠幅度 {(((actualOldPrice + govSubsidy + brandSubsidy) / newPrice) * 100).toFixed(0)}%
          </div>
        </div>

        <div className="bg-white rounded-lg p-3 border border-blue-200">
          <div className="flex items-center gap-2 mb-2">
            <Wand2 className="w-4 h-4 text-violet-600" />
            <span className="text-sm font-semibold text-gray-900">AI 推荐话术</span>
          </div>
          <div className="bg-violet-50 rounded p-2 text-xs text-gray-700 leading-relaxed">
            您好，{oldDevice} 现在回收 ¥{actualOldPrice.toLocaleString()}，叠加国补 ¥{govSubsidy} + 品牌补贴 ¥{brandSubsidy}，换 {newDevice} 只需 ¥{actualPay.toLocaleString()}，比单独购买省 ¥{(actualOldPrice + govSubsidy + brandSubsidy).toLocaleString()}！
          </div>
          <div className="flex gap-1 mt-2">
            <button className="px-2 py-1 text-xs bg-violet-100 text-violet-700 rounded flex items-center gap-1">
              <Copy className="w-3 h-3" /> 复制
            </button>
            <button className="px-2 py-1 text-xs bg-blue-100 text-blue-700 rounded flex items-center gap-1">
              <Send className="w-3 h-3" /> 发送给客户
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function Row({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-gray-600">{label}</span>
      <span className={`font-medium ${color}`}>{value}</span>
    </div>
  );
}
