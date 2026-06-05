import { useEffect, useMemo, useState } from 'react';
import {
  MapPin,
  Route,
  Users,
  Sparkles,
  Wand2,
  ScanLine,
  TrendingUp,
  Clock,
  Cloud,
  Sun,
  CloudRain,
  Megaphone,
  Trophy,
  CheckCircle2,
  ArrowRight,
  Zap,
  Target,
  FileImage,
  Smartphone,
  Hash,
  Activity,
  BarChart3,
  Eye,
  Play,
  ChevronRight,
  Shield,
  Brain,
} from 'lucide-react';
import { searchPlaces, type Place } from '../services/amapPlaceApi';
import { listRecentTapEvents, listTagPool, postNFCTapEvent, getActionLabel, type NFCAction } from '../services/nfcTapApi';

/* -------------------------------------------------------------------------- */
/*  地推作战系统 V2.0 — 数据驱动精准触达                                       */
/*  AI 智能路线 / 智能派单 / 实时话术 / AI 一键物料 / NFC 自动归因                */
/* -------------------------------------------------------------------------- */

interface RouteStop {
  order: number;
  place: Place;
  arriveTime: string;
  durationMin: number;
  heatScore: number; // 0-100
  trafficLevel: 'low' | 'mid' | 'high';
  reason: string;
  suggestedScript: string;
}

interface StaffMember {
  id: string;
  name: string;
  avatarColor: string;
  location: string;
  bestAtAudience: ('白领' | '学生' | '家庭' | '老人' | '商务')[];
  conversionRate: number;
  todayCompleted: number;
  status: 'idle' | 'busy' | 'offline';
}

interface MaterialTemplate {
  channel: '朋友圈海报' | '小红书图文' | '抖音口播脚本';
  title: string;
  cover: string;
  preview: string;
  tag: string;
}

const STAFFS: StaffMember[] = [
  { id: 'S001', name: '小李', avatarColor: 'bg-blue-100 text-blue-600', location: '国贸 0.5km', bestAtAudience: ['白领', '商务'], conversionRate: 0.31, todayCompleted: 12, status: 'idle' },
  { id: 'S002', name: '小王', avatarColor: 'bg-pink-100 text-pink-600', location: '三里屯 1.2km', bestAtAudience: ['白领', '学生'], conversionRate: 0.27, todayCompleted: 9, status: 'busy' },
  { id: 'S003', name: '小张', avatarColor: 'bg-emerald-100 text-emerald-600', location: '望京 0.8km', bestAtAudience: ['家庭', '老人'], conversionRate: 0.34, todayCompleted: 15, status: 'idle' },
  { id: 'S004', name: '小赵', avatarColor: 'bg-violet-100 text-violet-600', location: '中关村 2.1km', bestAtAudience: ['学生', '白领'], conversionRate: 0.22, todayCompleted: 6, status: 'idle' },
  { id: 'S005', name: '小陈', avatarColor: 'bg-amber-100 text-amber-600', location: '西单 1.5km', bestAtAudience: ['商务', '白领'], conversionRate: 0.29, todayCompleted: 11, status: 'offline' },
];

const MATERIAL_TEMPLATES: MaterialTemplate[] = [
  {
    channel: '朋友圈海报',
    title: 'Mate70 旗舰上市 · 老用户专属 ¥1500 补贴',
    cover: 'linear-gradient(135deg, #f43f5e 0%, #f59e0b 100%)',
    preview: '标题 + 价格 + 倒计时 + 二维码（4 要素黄金版式）',
    tag: 'AI 推荐',
  },
  {
    channel: '小红书图文',
    title: '「换机党」进来看！3 步搞定 Mate70 尝鲜价',
    cover: 'linear-gradient(135deg, #ec4899 0%, #8b5cf6 100%)',
    preview: '3 张配图 + 1 段种草文案 + 5 个话题标签',
    tag: '高互动',
  },
  {
    channel: '抖音口播脚本',
    title: '60 秒口播：Mate70 国补 + 以旧换新怎么薅？',
    cover: 'linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%)',
    preview: 'Hook 5s + 痛点 15s + 方案 30s + CTA 10s',
    tag: '爆款潜质',
  },
];

const WeatherIcon = ({ type }: { type: 'sun' | 'cloud' | 'rain' }) => {
  if (type === 'sun') return <Sun className="w-4 h-4 text-amber-500" />;
  if (type === 'rain') return <CloudRain className="w-4 h-4 text-blue-500" />;
  return <Cloud className="w-4 h-4 text-gray-500" />;
};

export default function GroundCombat() {
  const [tab, setTab] = useState<'route' | 'dispatch' | 'script' | 'material' | 'nfc'>('route');

  return (
    <div className="space-y-6">
      {/* 顶部说明 */}
      <div className="bg-gradient-to-r from-violet-600 via-fuchsia-600 to-pink-600 rounded-2xl p-5 text-white">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <Zap className="w-5 h-5 text-amber-300" />
              <h1 className="text-xl font-bold">地推作战系统 V2.0</h1>
              <span className="px-2 py-0.5 text-xs bg-white/20 rounded border border-white/30">2026 数据驱动版</span>
            </div>
            <p className="text-sm text-white/90">
              从「扫楼盲推」升级为「数据驱动精准触达」· AI 智能路线 / 智能派单 / 实时话术 / AI 一键物料 / NFC 自动归因
            </p>
          </div>
          <div className="flex items-center gap-6 text-sm">
            <Stat label="今日触达" value="184" suffix="人" />
            <Stat label="扫码转化" value="47" suffix="单" />
            <Stat label="归因率" value="92" suffix="%" />
            <Stat label="到店率" value="18.6" suffix="%" />
          </div>
        </div>
      </div>

      {/* Tab 导航 */}
      <div className="bg-white rounded-xl border border-gray-200 p-1 flex gap-1 overflow-x-auto">
        {[
          { key: 'route', label: 'AI 智能路线', icon: Route },
          { key: 'dispatch', label: '智能派单', icon: Users },
          { key: 'script', label: 'AI 实时话术', icon: Wand2 },
          { key: 'material', label: 'AI 一键物料', icon: FileImage },
          { key: 'nfc', label: 'NFC 碰一碰', icon: ScanLine },
        ].map((t) => {
          const Icon = t.icon;
          const active = tab === t.key;
          return (
            <button
              key={t.key}
              onClick={() => setTab(t.key as any)}
              className={`flex-1 min-w-[140px] flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-medium rounded-lg transition-colors ${
                active ? 'bg-violet-600 text-white shadow-sm' : 'text-gray-600 hover:bg-gray-50'
              }`}
            >
              <Icon className="w-4 h-4" />
              {t.label}
            </button>
          );
        })}
      </div>

      {tab === 'route' && <RoutePlanner />}
      {tab === 'dispatch' && <DispatchPanel />}
      {tab === 'script' && <ScriptStudio />}
      {tab === 'material' && <MaterialFactory />}
      {tab === 'nfc' && <NFCDashboard />}
    </div>
  );
}

function Stat({ label, value, suffix }: { label: string; value: string; suffix?: string }) {
  return (
    <div>
      <div className="text-xl font-bold">
        {value}
        {suffix && <span className="text-sm ml-0.5">{suffix}</span>}
      </div>
      <div className="text-xs text-white/70">{label}</div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  1. AI 智能路线规划                                                          */
/* -------------------------------------------------------------------------- */

function RoutePlanner() {
  const [audience, setAudience] = useState<'白领' | '学生' | '家庭' | '商务'>('白领');
  const [duration, setDuration] = useState(4); // 小时
  const [generating, setGenerating] = useState(false);
  const [stops, setStops] = useState<RouteStop[]>([]);

  const handleGenerate = async () => {
    setGenerating(true);
    const places = await searchPlaces({
      keyword: audience + '聚集地',
      location: '116.473168,39.993015',
      radius: 3000,
    });
    await new Promise((r) => setTimeout(r, 600));
    const generated: RouteStop[] = places.slice(0, 4).map((p, i) => {
      const arriveHour = 9 + i * Math.floor(duration / 4);
      const heatScore = 60 + (i === 0 ? 30 : i === 1 ? 20 : i === 2 ? 10 : 5);
      const trafficLevel: 'low' | 'mid' | 'high' = i === 0 ? 'mid' : i === 2 ? 'high' : 'low';
      return {
        order: i + 1,
        place: p,
        arriveTime: `${arriveHour.toString().padStart(2, '0')}:00`,
        durationMin: 45 + i * 15,
        heatScore,
        trafficLevel,
        reason: i === 0 ? '客群密度最高' : i === 1 ? '停留时长较长' : i === 2 ? '竞品分布密集' : '交通便利',
        suggestedScript: `您好，我们是 ${audience} 专属服务团队，进店可享 ${audience === '白领' ? '商务套餐免费体验' : audience === '学生' ? '学生证额外 9 折' : audience === '家庭' ? '家庭融合套餐' : 'CEO 1v1 服务'}。`,
      };
    });
    setStops(generated);
    setGenerating(false);
  };

  useEffect(() => {
    if (stops.length === 0) {
      handleGenerate();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex flex-wrap items-end gap-4">
          <div>
            <label className="text-xs text-gray-500 block mb-1">目标客群</label>
            <div className="flex gap-1">
              {(['白领', '学生', '家庭', '商务'] as const).map((a) => (
                <button
                  key={a}
                  onClick={() => setAudience(a)}
                  className={`px-3 py-1.5 text-sm rounded-lg border ${
                    audience === a
                      ? 'bg-violet-600 text-white border-violet-600'
                      : 'bg-white text-gray-700 border-gray-200 hover:border-violet-300'
                  }`}
                >
                  {a}
                </button>
              ))}
            </div>
          </div>
          <div>
            <label className="text-xs text-gray-500 block mb-1">扫街时长</label>
            <div className="flex gap-1">
              {[2, 4, 6, 8].map((h) => (
                <button
                  key={h}
                  onClick={() => setDuration(h)}
                  className={`px-3 py-1.5 text-sm rounded-lg border ${
                    duration === h
                      ? 'bg-violet-600 text-white border-violet-600'
                      : 'bg-white text-gray-700 border-gray-200 hover:border-violet-300'
                  }`}
                >
                  {h}h
                </button>
              ))}
            </div>
          </div>
          <button
            onClick={handleGenerate}
            disabled={generating}
            className="ml-auto flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-violet-600 to-pink-600 text-white rounded-lg hover:opacity-90 disabled:opacity-50"
          >
            <Sparkles className="w-4 h-4" />
            {generating ? 'AI 规划中…' : 'AI 重新规划'}
          </button>
        </div>
        <div className="mt-3 text-xs text-gray-500 flex items-center gap-2">
          <Brain className="w-3 h-3" />
          AI 已综合考虑实时交通、热力分布、POI 营业时间，自动规划最优路线
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {stops.map((stop) => (
          <div key={stop.order} className="bg-white rounded-xl border border-gray-200 p-4">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-full bg-gradient-to-br from-violet-500 to-pink-500 text-white flex items-center justify-center font-bold text-sm">
                  {stop.order}
                </div>
                <div>
                  <div className="font-semibold text-gray-900 text-sm">{stop.place.name}</div>
                  <div className="text-xs text-gray-500 flex items-center gap-1">
                    <Clock className="w-3 h-3" /> {stop.arriveTime} · 停留 {stop.durationMin} 分钟
                  </div>
                </div>
              </div>
              <span className={`px-2 py-0.5 text-xs rounded ${
                stop.heatScore >= 80 ? 'bg-red-100 text-red-700' : stop.heatScore >= 60 ? 'bg-amber-100 text-amber-700' : 'bg-gray-100 text-gray-600'
              }`}>
                热力 {stop.heatScore}
              </span>
            </div>
            <div className="space-y-2 text-sm">
              <div className="flex items-center gap-2 text-xs">
                <span className="text-gray-500">距门店</span>
                <span className="font-medium">{stop.place.distance?.toFixed(1)} km</span>
                <span className="ml-auto text-gray-500">实时交通</span>
                <span className={`px-1.5 py-0.5 rounded text-[10px] ${
                  stop.trafficLevel === 'high' ? 'bg-red-100 text-red-600' :
                  stop.trafficLevel === 'mid' ? 'bg-amber-100 text-amber-600' :
                  'bg-emerald-100 text-emerald-600'
                }`}>
                  {stop.trafficLevel === 'high' ? '拥堵' : stop.trafficLevel === 'mid' ? '缓行' : '畅通'}
                </span>
              </div>
              <div className="bg-violet-50 rounded-lg p-2 text-xs text-violet-800">
                💡 推荐理由：{stop.reason}
              </div>
              <div className="bg-blue-50 rounded-lg p-2 text-xs text-blue-900">
                <div className="text-blue-600 font-medium mb-0.5">AI 话术</div>
                <div className="text-gray-900">{stop.suggestedScript}</div>
              </div>
            </div>
          </div>
        ))}
        {stops.length === 0 && !generating && (
          <div className="col-span-3 bg-white rounded-xl border border-gray-200 p-12 text-center text-gray-400">
            <Route className="w-12 h-12 mx-auto mb-3 opacity-50" />
            点击「AI 重新规划」生成最优路线
          </div>
        )}
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  2. 智能派单                                                                 */
/* -------------------------------------------------------------------------- */

function DispatchPanel() {
  const [grabMode, setGrabMode] = useState(false);
  const [dispatching, setDispatching] = useState<string | null>(null);

  const handleAutoDispatch = (staff: StaffMember) => {
    setDispatching(staff.id);
    setTimeout(() => {
      setDispatching(null);
    }, 1500);
  };

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-200 p-4 flex flex-wrap items-center gap-3">
        <div className="flex-1 min-w-[200px]">
          <div className="text-sm text-gray-900 font-medium">智能派单引擎</div>
          <div className="text-xs text-gray-500">
            综合考虑店员当前位置、历史转化率、擅长客群，自动匹配最优派单
          </div>
        </div>
        <button
          onClick={() => setGrabMode(!grabMode)}
          className={`px-3 py-1.5 text-sm rounded-lg border ${
            grabMode ? 'bg-amber-50 text-amber-700 border-amber-300' : 'bg-white text-gray-700 border-gray-200'
          }`}
        >
          {grabMode ? '🔥 抢单模式 已开启' : '切换到抢单模式'}
        </button>
        <button className="px-4 py-2 bg-gradient-to-r from-violet-600 to-pink-600 text-white rounded-lg text-sm flex items-center gap-2">
          <Zap className="w-4 h-4" /> 一键自动派单
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
        {STAFFS.map((s) => {
          const statusBadge = s.status === 'idle'
            ? 'bg-emerald-100 text-emerald-700'
            : s.status === 'busy'
            ? 'bg-amber-100 text-amber-700'
            : 'bg-gray-100 text-gray-500';
          const statusLabel = s.status === 'idle' ? '空闲' : s.status === 'busy' ? '执行中' : '离线';
          return (
            <div key={s.id} className="bg-white rounded-xl border border-gray-200 p-4">
              <div className="flex items-center gap-3 mb-3">
                <div className={`w-12 h-12 rounded-full ${s.avatarColor} flex items-center justify-center font-bold`}>
                  {s.name[0]}
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-gray-900">{s.name}</span>
                    <span className={`px-1.5 py-0.5 text-[10px] rounded ${statusBadge}`}>{statusLabel}</span>
                  </div>
                  <div className="text-xs text-gray-500 flex items-center gap-1">
                    <MapPin className="w-3 h-3" />{s.location}
                  </div>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2 text-xs mb-3">
                <div className="bg-gray-50 rounded p-2">
                  <div className="text-gray-500">转化率</div>
                  <div className="font-bold text-emerald-600">{(s.conversionRate * 100).toFixed(0)}%</div>
                </div>
                <div className="bg-gray-50 rounded p-2">
                  <div className="text-gray-500">今日完成</div>
                  <div className="font-bold text-violet-600">{s.todayCompleted} 单</div>
                </div>
              </div>
              <div className="flex flex-wrap gap-1 mb-3">
                {s.bestAtAudience.map((a) => (
                  <span key={a} className="px-2 py-0.5 text-[10px] bg-violet-50 text-violet-700 rounded">
                    擅长 {a}
                  </span>
                ))}
              </div>
              <button
                onClick={() => handleAutoDispatch(s)}
                disabled={s.status !== 'idle' || dispatching === s.id}
                className="w-full px-3 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-1"
              >
                {dispatching === s.id ? (
                  <>
                    <CheckCircle2 className="w-4 h-4" /> 已派单
                  </>
                ) : (
                  <>
                    <ArrowRight className="w-4 h-4" /> 派单给 {s.name}
                  </>
                )}
              </button>
            </div>
          );
        })}
      </div>

      <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 flex items-start gap-3">
        <Brain className="w-5 h-5 text-blue-600 mt-0.5" />
        <div className="text-sm text-blue-900">
          <div className="font-medium mb-1">AI 派单建议</div>
          <div className="text-xs text-blue-800">
            国贸 CBD 写字楼 9-11 点建议派 <b>小李</b>（商务专长 + 当前位置最近）；
            望京 SOHO 17-19 点建议派 <b>小张</b>（家庭客群转化率 34%）。
          </div>
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  3. AI 实时话术                                                              */
/* -------------------------------------------------------------------------- */

function ScriptStudio() {
  const [poiType, setPoiType] = useState<'写字楼' | '小区' | '学校' | '商场'>('写字楼');
  const [promotion, setPromotion] = useState('Mate70 上市 · 国补 ¥500');
  const [timeSlot, setTimeSlot] = useState<'上午' | '中午' | '下午' | '晚上'>('上午');
  const [weather] = useState<'sun' | 'cloud' | 'rain'>('sun');
  const [generating, setGenerating] = useState(false);
  const [script, setScript] = useState('');

  const generate = () => {
    setGenerating(true);
    setTimeout(() => {
      const timeHint = timeSlot === '上午' ? '清新的早晨' : timeSlot === '中午' ? '忙碌的午间' : timeSlot === '下午' ? '下午茶时光' : '下班路上';
      const weatherHint = weather === 'sun' ? '阳光正好' : weather === 'rain' ? '雨天出行不便' : '阴天凉爽';
      const audienceHint = poiType === '写字楼' ? '白领商务人群' : poiType === '小区' ? '家庭主妇/退休老人' : poiType === '学校' ? '学生和老师' : '购物休闲人群';
      const scriptText = `【${poiType} · ${timeSlot}场景】\n您好，${timeHint}${weatherHint}，${audienceHint}专属福利：${promotion}。\n我们门店就在附近 1.2 公里，进店免费体验 + 1v1 选机咨询，加微信还能领 ¥30 配件券。\n方便加个微信吗？我把门店地址和活动详情发您。`;
      setScript(scriptText);
      setGenerating(false);
    }, 800);
  };

  useEffect(() => {
    if (!script) generate();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="bg-white rounded-xl border border-gray-200 p-4 space-y-4">
          <h3 className="font-semibold text-gray-900 flex items-center gap-2">
            <Wand2 className="w-4 h-4 text-violet-600" />
            实时话术生成器
          </h3>
          <div>
            <label className="text-xs text-gray-500 block mb-1">POI 类型</label>
            <div className="grid grid-cols-4 gap-1">
              {(['写字楼', '小区', '学校', '商场'] as const).map((t) => (
                <button
                  key={t}
                  onClick={() => setPoiType(t)}
                  className={`px-2 py-1.5 text-sm rounded-lg border ${
                    poiType === t
                      ? 'bg-violet-600 text-white border-violet-600'
                      : 'bg-white text-gray-700 border-gray-200'
                  }`}
                >
                  {t}
                </button>
              ))}
            </div>
          </div>
          <div>
            <label className="text-xs text-gray-500 block mb-1">当前促销活动</label>
            <input
              value={promotion}
              onChange={(e) => setPromotion(e.target.value)}
              className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
            />
          </div>
          <div>
            <label className="text-xs text-gray-500 block mb-1">时段</label>
            <div className="grid grid-cols-4 gap-1">
              {(['上午', '中午', '下午', '晚上'] as const).map((t) => (
                <button
                  key={t}
                  onClick={() => setTimeSlot(t)}
                  className={`px-2 py-1.5 text-sm rounded-lg border ${
                    timeSlot === t
                      ? 'bg-violet-600 text-white border-violet-600'
                      : 'bg-white text-gray-700 border-gray-200'
                  }`}
                >
                  {t}
                </button>
              ))}
            </div>
          </div>
          <div className="flex items-center gap-2 text-sm bg-gray-50 rounded-lg p-3">
            <WeatherIcon type={weather} />
            <span className="text-gray-700">实时天气：晴 24°C（已自动并入话术）</span>
          </div>
          <button
            onClick={generate}
            disabled={generating}
            className="w-full px-4 py-2.5 bg-gradient-to-r from-violet-600 to-pink-600 text-white rounded-lg flex items-center justify-center gap-2 disabled:opacity-50"
          >
            <Sparkles className="w-4 h-4" />
            {generating ? '生成中…' : 'AI 重新生成话术'}
          </button>
        </div>

        <div className="bg-gradient-to-br from-violet-50 to-pink-50 rounded-xl border border-violet-200 p-4">
          <div className="flex items-center justify-between mb-3">
            <h3 className="font-semibold text-gray-900 flex items-center gap-2">
              <Megaphone className="w-4 h-4 text-violet-600" />
              生成结果
            </h3>
            {script && (
              <button
                onClick={() => navigator.clipboard?.writeText(script)}
                className="text-xs px-2 py-1 bg-white text-violet-700 rounded border border-violet-200"
              >
                复制
              </button>
            )}
          </div>
          {script ? (
            <div className="bg-white rounded-lg p-4 text-sm text-gray-900 leading-relaxed whitespace-pre-line shadow-sm">
              {script}
            </div>
          ) : (
            <div className="bg-white/50 rounded-lg p-8 text-center text-gray-400">
              <Wand2 className="w-10 h-10 mx-auto mb-2 opacity-50" />
              点击「AI 重新生成话术」
            </div>
          )}
          <div className="mt-3 flex items-center gap-2 text-xs text-gray-600">
            <Shield className="w-3 h-3" />
            已通过合规过滤 · 不含诱导话术
          </div>
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  4. AI 一键物料                                                              */
/* -------------------------------------------------------------------------- */

function MaterialFactory() {
  const [generating, setGenerating] = useState<string | null>(null);
  const [generated, setGenerated] = useState<Set<string>>(new Set());

  const handleGenerate = (channel: string) => {
    setGenerating(channel);
    setTimeout(() => {
      setGenerating(null);
      setGenerated((prev) => new Set(prev).add(channel));
    }, 1200);
  };

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex items-center gap-2 mb-1">
          <Wand2 className="w-4 h-4 text-violet-600" />
          <h3 className="font-semibold text-gray-900">AI 一键物料工厂</h3>
        </div>
        <p className="text-xs text-gray-500">
          输入门店活动和目标客群，自动生成朋友圈海报 / 小红书图文 / 抖音口播脚本（适配不同渠道风格）
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {MATERIAL_TEMPLATES.map((m) => {
          const isGen = generating === m.channel;
          const done = generated.has(m.channel);
          return (
            <div key={m.channel} className="bg-white rounded-xl border border-gray-200 overflow-hidden">
              <div className="h-32 relative" style={{ background: m.cover }}>
                <div className="absolute top-2 left-2 px-2 py-0.5 text-[10px] bg-white/30 text-white rounded backdrop-blur">
                  {m.channel}
                </div>
                <div className="absolute top-2 right-2 px-2 py-0.5 text-[10px] bg-white text-gray-700 rounded font-medium">
                  {m.tag}
                </div>
                <div className="absolute bottom-2 left-2 right-2 text-white text-sm font-bold leading-tight">
                  {m.title}
                </div>
              </div>
              <div className="p-4">
                <div className="text-xs text-gray-500 mb-3">{m.preview}</div>
                <button
                  onClick={() => handleGenerate(m.channel)}
                  disabled={isGen}
                  className="w-full px-3 py-2 bg-gradient-to-r from-violet-600 to-pink-600 text-white text-sm rounded-lg flex items-center justify-center gap-2 disabled:opacity-50"
                >
                  {isGen ? (
                    <>
                      <Sparkles className="w-4 h-4 animate-pulse" /> AI 生成中…
                    </>
                  ) : done ? (
                    <>
                      <CheckCircle2 className="w-4 h-4" /> 已生成
                    </>
                  ) : (
                    <>
                      <Sparkles className="w-4 h-4" /> AI 一键生成
                    </>
                  )}
                </button>
                {done && (
                  <div className="mt-2 grid grid-cols-2 gap-1">
                    <button className="text-xs py-1.5 bg-gray-100 text-gray-700 rounded">预览</button>
                    <button className="text-xs py-1.5 bg-blue-600 text-white rounded">下载</button>
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  5. NFC 碰一碰 Dashboard                                                     */
/* -------------------------------------------------------------------------- */

function NFCDashboard() {
  const tags = useMemo(() => listTagPool(), []);
  const [events, setEvents] = useState(listRecentTapEvents(8));
  const [simulating, setSimulating] = useState<string | null>(null);

  const simulate = async (tagId: string) => {
    setSimulating(tagId);
    const action: NFCAction = 'coupon_claim';
    await postNFCTapEvent({
      tag_id: tagId,
      action,
      user_openid: `OPENID-${Math.random().toString(36).slice(2, 8)}`,
      timestamp: new Date().toISOString(),
      lat: 39.993015,
      lng: 116.473168,
    });
    setTimeout(() => {
      setEvents(listRecentTapEvents(8));
      setSimulating(null);
    }, 300);
  };

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex items-start gap-2 mb-3">
          <ScanLine className="w-5 h-5 text-violet-600 mt-0.5" />
          <div>
            <h3 className="font-semibold text-gray-900">NFC 碰一碰 · 现场演示</h3>
            <p className="text-xs text-gray-500">
              点击下方 NFC 标签卡片模拟用户碰一碰，自动触发 <code className="text-violet-600">POST /api/nfc/tap_event</code>，系统将自动归因渠道
            </p>
          </div>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-2">
          {tags.map((t) => (
            <button
              key={t.tag_id}
              onClick={() => simulate(t.tag_id)}
              disabled={simulating === t.tag_id}
              className="p-3 text-left border-2 border-dashed border-violet-300 bg-violet-50 rounded-lg hover:bg-violet-100 transition-colors disabled:opacity-50"
            >
              <div className="flex items-center gap-1 text-xs text-violet-600 font-mono mb-1">
                <Hash className="w-3 h-3" />
                {t.tag_id}
              </div>
              <div className="text-sm text-gray-900 font-medium">{t.location}</div>
              <div className="text-xs text-gray-500 mt-0.5">负责人：{t.staff}</div>
              {simulating === t.tag_id && (
                <div className="mt-2 text-[10px] text-violet-700 flex items-center gap-1">
                  <Activity className="w-3 h-3 animate-pulse" /> 模拟碰触中…
                </div>
              )}
            </button>
          ))}
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200">
        <div className="p-4 border-b border-gray-200 flex items-center gap-2">
          <Activity className="w-4 h-4 text-violet-600" />
          <h3 className="font-semibold text-gray-900">实时事件流</h3>
          <span className="text-xs text-gray-500 ml-auto">最近 {events.length} 条</span>
        </div>
        <div className="divide-y divide-gray-100">
          {events.length === 0 && (
            <div className="p-12 text-center text-gray-400">
              <ScanLine className="w-10 h-10 mx-auto mb-2 opacity-50" />
              <p className="text-sm">点击上方 NFC 标签开始模拟</p>
            </div>
          )}
          {events.map((e) => (
            <div key={e.event_id} className="p-3 flex items-center gap-3">
              <div className="w-8 h-8 rounded-full bg-violet-100 text-violet-600 flex items-center justify-center">
                <ScanLine className="w-4 h-4" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium text-gray-900">
                  {getActionLabel(e.action as NFCAction)} · {e.tag_id}
                </div>
                <div className="text-xs text-gray-500 font-mono truncate">
                  openid={e.user_openid} · {new Date(e.timestamp).toLocaleTimeString('zh-CN')}
                </div>
              </div>
              <span className="text-xs px-2 py-0.5 bg-emerald-100 text-emerald-700 rounded">
                {e.event_id}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* 归因链路示意 */}
      <div className="bg-gradient-to-r from-blue-50 to-violet-50 border border-blue-200 rounded-xl p-4">
        <div className="text-sm font-semibold text-gray-900 mb-2 flex items-center gap-2">
          <Target className="w-4 h-4 text-blue-600" /> 自动归因链路示意
        </div>
        <div className="flex flex-wrap items-center gap-2 text-xs">
          {['地推扫码', 'NFC 碰触', '领取优惠券', '查看活动页', '预约到店', '完成核销'].map((step, i) => (
            <span key={i} className="flex items-center gap-2">
              <span className="px-2 py-1 bg-white rounded border border-blue-200 text-blue-900">{step}</span>
              {i < 5 && <ChevronRight className="w-3 h-3 text-gray-400" />}
            </span>
          ))}
        </div>
        <div className="mt-2 text-xs text-gray-600">
          完转化率 18.6% · 平均归因时长 2.4 秒
        </div>
      </div>
    </div>
  );
}
