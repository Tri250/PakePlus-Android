import { useState, useEffect, useMemo } from 'react';
import {
  Radar, MapPin, Building2, Home, GraduationCap, Smartphone,
  TrendingUp, Clock, Filter, Loader2, Route, AlertCircle, RefreshCw,
  Crosshair, Lock, Unlock, Users, Target, Zap, ChevronRight, X,
  Briefcase, ShoppingBag, Flame, Shield, Award, Sparkles, Phone,
  MessageSquare, CheckCircle2, ArrowRight, FileText, ChevronDown
} from 'lucide-react';
import { useGeolocation, calculateDistance, formatDistance } from '../hooks/useGeolocation';

/* ============================== 类型定义 ============================== */
type AudienceTag = 'white-collar-flagship' | 'student-budget' | 'family-elderly' | 'all';

interface HeatmapPoint {
  id: string;
  name: string;
  type: 'member' | 'poi' | 'competitor';
  subType: 'office' | 'residential' | 'school' | 'mall' | 'competitor' | 'cluster';
  lat: number;
  lng: number;
  /** 客群评分 0-100 */
  score: number;
  /** 可触达线索数 */
  leads: number;
  /** 预估换机意向人数 */
  intentCount: number;
  /** 平均购机月数（品牌会员专属） */
  avgDeviceAge?: number;
  /** 旗舰机用户占比 */
  flagshipRatio?: number;
  /** 距离（动态计算） */
  distance?: number;
  /** 品牌 CRM 数据标签 */
  crmTags?: string[];
  /** 建议话术 */
  suggestedScripts?: string[];
}

interface CompetitorStore {
  id: string;
  brand: string;
  name: string;
  address: string;
  distance?: number;
  hotness: 'high' | 'medium' | 'low';
}

interface AudienceConfig {
  id: AudienceTag;
  label: string;
  description: string;
  icon: typeof Briefcase;
  color: string;
  filters: {
    poiTypes: string[];
    memberAges: number[];
    deviceFlagshipOnly: boolean;
  };
}

/* ============================== 模拟数据 ============================== */
// 4 层数据源之第 1 层：地图 POI
const mapPOIs: HeatmapPoint[] = [
  { id: 'p1', name: '中关村科技大厦A座', type: 'poi', subType: 'office', lat: 39.9841, lng: 116.3073, score: 92, leads: 156, intentCount: 23 },
  { id: 'p2', name: '创业大厦', type: 'poi', subType: 'office', lat: 39.9825, lng: 116.3089, score: 85, leads: 98, intentCount: 12 },
  { id: 'p3', name: '知春里小区', type: 'poi', subType: 'residential', lat: 39.9798, lng: 116.3102, score: 78, leads: 124, intentCount: 18 },
  { id: 'p4', name: '海淀黄庄购物中心', type: 'poi', subType: 'mall', lat: 39.9812, lng: 116.3056, score: 88, leads: 215, intentCount: 31 },
  { id: 'p5', name: '中关村第一小学', type: 'poi', subType: 'school', lat: 39.9835, lng: 116.3068, score: 82, leads: 67, intentCount: 8 },
  { id: 'p6', name: '清华大学', type: 'poi', subType: 'school', lat: 39.9920, lng: 116.3260, score: 90, leads: 312, intentCount: 45 },
];

// 4 层数据源之第 2 层：品牌 CRM 数据 + 第 3 层：换机周期模型
const memberClusters: HeatmapPoint[] = [
  {
    id: 'm1', name: '中关村·华为会员聚集区', type: 'member', subType: 'cluster', lat: 39.9845, lng: 116.3075,
    score: 96, leads: 38, intentCount: 28, avgDeviceAge: 26, flagshipRatio: 0.68,
    crmTags: ['Mate40 系列', 'P50 系列', '已购 > 20 月', '高净值'],
    suggestedScripts: ['王总您好，我是华为体验店顾问，您 Mate40 Pro 已使用 26 个月，正值换机黄金期', '我们最新 Mate70 系列搭载纯血鸿蒙，体验全面升级']
  },
  {
    id: 'm2', name: '黄庄·小米会员聚集区', type: 'member', subType: 'cluster', lat: 39.9810, lng: 116.3060,
    score: 88, leads: 25, intentCount: 16, avgDeviceAge: 22, flagshipRatio: 0.42,
    crmTags: ['小米11/12 系列', '生态用户', '价格敏感'],
    suggestedScripts: ['您小米 12 已用 22 个月，现在以旧换新能抵扣 1500 元', '小米 14 系列现货，徕卡影像升级']
  },
  {
    id: 'm3', name: '知春里·OPPO老用户区', type: 'member', subType: 'cluster', lat: 39.9800, lng: 116.3100,
    score: 75, leads: 18, intentCount: 9, avgDeviceAge: 32, flagshipRatio: 0.22,
    crmTags: ['OPPO Find X3', '超期服役', '维修记录 2 次'],
    suggestedScripts: ['您 OPPO Find X3 已使用 32 个月，电池效率可能下降，建议换新', '现在以旧换新最高补贴 800 元']
  },
];

// 第 1 层：竞品门店
const competitors: CompetitorStore[] = [
  { id: 'c1', brand: '华为', name: '华为体验店·西单大悦城', address: '西城区西单北大街', hotness: 'high' },
  { id: 'c2', brand: '小米', name: '小米之家·朝阳大悦城', address: '朝阳区青年路', hotness: 'high' },
  { id: 'c3', brand: 'OPPO', name: 'OPPO 体验店·三里屯', address: '朝阳区三里屯', hotness: 'medium' },
  { id: 'c4', brand: 'vivo', name: 'vivo 体验店·国贸', address: '朝阳区国贸', hotness: 'medium' },
];

/* ============================== 主组件 ============================== */
export default function LBSRadar() {
  const [radius, setRadius] = useState(5000);
  const [scanning, setScanning] = useState(false);
  const [scanned, setScanned] = useState(false);
  const [selectedAudience, setSelectedAudience] = useState<AudienceTag>('white-collar-flagship');
  const [selectedPoint, setSelectedPoint] = useState<HeatmapPoint | null>(null);
  const [showCompetitor, setShowCompetitor] = useState(true);
  const [showMembers, setShowMembers] = useState(true);
  const [showPOIs, setShowPOIs] = useState(true);
  const [creatingTask, setCreatingTask] = useState(false);
  const [createdTask, setCreatedTask] = useState<string | null>(null);

  const { 
    loading: locationLoading, 
    error: locationError, 
    location, 
    accuracy, 
    requestLocation, 
    hasPermission 
  } = useGeolocation();

  const audiences: AudienceConfig[] = [
    {
      id: 'white-collar-flagship',
      label: '白领·旗舰机用户',
      description: '高净值商务人群，换机周期长，预算充足',
      icon: Briefcase,
      color: 'purple',
      filters: { poiTypes: ['office'], memberAges: [20, 30], deviceFlagshipOnly: true }
    },
    {
      id: 'student-budget',
      label: '学生·预算敏感',
      description: '高校学生群体，性价比优先',
      icon: GraduationCap,
      color: 'blue',
      filters: { poiTypes: ['school'], memberAges: [18, 24], deviceFlagshipOnly: false }
    },
    {
      id: 'family-elderly',
      label: '家庭·老人机需求',
      description: '家庭住宅区，老人换机、子女代购',
      icon: Home,
      color: 'green',
      filters: { poiTypes: ['residential'], memberAges: [24, 36], deviceFlagshipOnly: false }
    },
    {
      id: 'all',
      label: '全客群扫描',
      description: '无差别扫描所有线索',
      icon: Target,
      color: 'gray',
      filters: { poiTypes: [], memberAges: [], deviceFlagshipOnly: false }
    },
  ];

  const currentAudience = audiences.find(a => a.id === selectedAudience)!;

  // 距离计算 + 筛选
  const filteredPoints = useMemo(() => {
    if (!location) return { points: [], memberPoints: [], competitorsWithDist: [] };
    
    const calcDist = (p: HeatmapPoint) => calculateDistance(location.lat, location.lng, p.lat, p.lng);
    
    // 客群过滤
    const matchesAudience = (p: HeatmapPoint) => {
      if (currentAudience.id === 'all') return true;
      if (p.type === 'poi') {
        return currentAudience.filters.poiTypes.includes(p.subType) || currentAudience.filters.poiTypes.length === 0;
      }
      if (p.type === 'member') {
        if (currentAudience.filters.deviceFlagshipOnly && (p.flagshipRatio ?? 0) < 0.5) return false;
        return true;
      }
      return true;
    };

    const points = mapPOIs
      .map(p => ({ ...p, distance: calcDist(p) }))
      .filter(p => p.distance! <= radius && matchesAudience(p));
    
    const memberPoints = memberClusters
      .map(p => ({ ...p, distance: calcDist(p) }))
      .filter(p => p.distance! <= radius && matchesAudience(p));

    const competitorsWithDist = competitors
      .map(c => ({ ...c, distance: calcDist({
        id: c.id, name: c.name, type: 'competitor', subType: 'competitor',
        lat: 39.9900 + Math.random() * 0.01, lng: 116.3100 + Math.random() * 0.01,
        score: 0, leads: 0, intentCount: 0
      }) }))
      .filter(c => c.distance! <= radius);

    return { points, memberPoints, competitorsWithDist };
  }, [location, radius, currentAudience]);

  const handleScan = () => {
    if (!location) {
      requestLocation();
      return;
    }
    setScanning(true);
    setScanned(false);
    setSelectedPoint(null);
    setTimeout(() => {
      setScanning(false);
      setScanned(true);
    }, 2500);
  };

  // 一键生成扫街任务
  const handleCreateTask = () => {
    if (!selectedPoint) return;
    setCreatingTask(true);
    setTimeout(() => {
      setCreatingTask(false);
      setCreatedTask(`#${Math.floor(Math.random() * 9000 + 1000)}`);
    }, 1500);
  };

  /* ============================== 渲染 ============================== */
  return (
    <div className="space-y-6">
      {/* 定位权限提示 */}
      {!location && (
        <div className={`rounded-xl border p-4 ${locationError ? 'bg-red-50 border-red-200' : 'bg-blue-50 border-blue-200'}`}>
          <div className="flex items-start gap-3">
            {locationError ? (
              <AlertCircle className="w-6 h-6 text-red-500 flex-shrink-0 mt-0.5" />
            ) : (
              <Crosshair className="w-6 h-6 text-blue-500 flex-shrink-0 mt-0.5 animate-pulse" />
            )}
            <div className="flex-1">
              <h3 className={`font-medium ${locationError ? 'text-red-800' : 'text-blue-800'}`}>
                {locationError ? '定位失败' : '需要获取您的位置'}
              </h3>
              <p className={`text-sm mt-1 ${locationError ? 'text-red-600' : 'text-blue-600'}`}>
                {locationError || 'V2.0 重构版 LBS 雷达融合 4 层数据源，需要精准定位'}
              </p>
              <div className="mt-3 flex items-center gap-3">
                <button
                  onClick={requestLocation}
                  disabled={locationLoading}
                  className={`flex items-center gap-2 px-4 py-2 rounded-lg text-white font-medium ${
                    locationError ? 'bg-red-600 hover:bg-red-700' : 'bg-blue-600 hover:bg-blue-700'
                  } disabled:opacity-50 transition-colors`}
                >
                  {locationLoading ? (
                    <>
                      <Loader2 className="w-5 h-5 animate-spin" />
                      正在定位...
                    </>
                  ) : (
                    <>
                      <Crosshair className="w-5 h-5" />
                      {locationError ? '重新获取' : '授权定位'}
                    </>
                  )}
                </button>
                {hasPermission === false && (
                  <span className="flex items-center gap-1.5 text-sm text-red-600">
                    <Lock className="w-4 h-4" />
                    权限被拒绝
                  </span>
                )}
                {hasPermission === true && (
                  <span className="flex items-center gap-1.5 text-sm text-green-600">
                    <Unlock className="w-4 h-4" />
                    权限已开启
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 头部卡片 - 4 层数据源融合标识 */}
      <div className="bg-gradient-to-r from-blue-600 via-indigo-600 to-purple-600 rounded-2xl p-6 text-white">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 bg-white/20 rounded-2xl flex items-center justify-center backdrop-blur-sm">
              <Radar className="w-8 h-8" />
            </div>
            <div>
              <div className="flex items-center gap-2 mb-1">
                <h2 className="text-2xl font-bold">LBS 雷达 V2.0</h2>
                <span className="px-2 py-0.5 bg-yellow-400 text-yellow-900 text-xs font-bold rounded">重构版</span>
              </div>
              <p className="text-blue-100 text-sm">4 层数据融合 · 输出可执行销售线索</p>
            </div>
          </div>
          <div className="hidden md:flex items-center gap-2 text-sm">
            <div className="px-3 py-1.5 bg-white/10 rounded-lg backdrop-blur-sm">📍 地图 POI</div>
            <span className="text-white/50">+</span>
            <div className="px-3 py-1.5 bg-white/10 rounded-lg backdrop-blur-sm">👥 品牌 CRM</div>
            <span className="text-white/50">+</span>
            <div className="px-3 py-1.5 bg-white/10 rounded-lg backdrop-blur-sm">🔄 换机模型</div>
            <span className="text-white/50">+</span>
            <div className="px-3 py-1.5 bg-white/10 rounded-lg backdrop-blur-sm">💰 国补计算</div>
          </div>
        </div>

        {/* 当前定位信息 */}
        <div className="mt-5 flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 px-3 py-2 bg-white/10 rounded-lg backdrop-blur-sm">
            <MapPin className="w-4 h-4" />
            <span className="text-sm">
              {location ? `门店位置 (${location.lat.toFixed(4)}, ${location.lng.toFixed(4)})` : '正在获取位置...'}
            </span>
            {accuracy && <span className="text-xs text-blue-200">±{Math.round(accuracy)}m</span>}
          </div>
          <button 
            onClick={requestLocation}
            disabled={locationLoading}
            className="p-2 bg-white/10 rounded-lg hover:bg-white/20 disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${locationLoading ? 'animate-spin' : ''}`} />
          </button>
          <button
            onClick={handleScan}
            disabled={scanning || !location}
            className="px-4 py-2 bg-white text-blue-600 rounded-lg font-medium hover:bg-blue-50 disabled:opacity-50 flex items-center gap-2"
          >
            {scanning ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                扫描 4 层数据中...
              </>
            ) : (
              <>
                <Radar className="w-4 h-4" />
                {location ? '启动雷达扫描' : '请先定位'}
              </>
            )}
          </button>
        </div>
      </div>

      {/* 客群标签选择器 */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <div className="flex items-center gap-2 mb-4">
          <Filter className="w-5 h-5 text-gray-500" />
          <h3 className="font-semibold text-gray-900">选择目标客群标签</h3>
          <span className="text-xs text-gray-500 ml-2">不同标签对应不同筛选规则</span>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {audiences.map((a) => {
            const isActive = selectedAudience === a.id;
            return (
              <button
                key={a.id}
                onClick={() => setSelectedAudience(a.id)}
                className={`p-4 rounded-xl border-2 text-left transition-all ${
                  isActive
                    ? `border-${a.color}-500 bg-${a.color}-50 shadow-md`
                    : 'border-gray-200 hover:border-gray-300 bg-white'
                }`}
              >
                <div className="flex items-center gap-2 mb-2">
                  <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                    isActive ? `bg-${a.color}-500 text-white` : `bg-${a.color}-100 text-${a.color}-600`
                  }`}>
                    <a.icon className="w-5 h-5" />
                  </div>
                  {isActive && <CheckCircle2 className="w-5 h-5 text-green-500" />}
                </div>
                <p className={`font-medium text-sm ${isActive ? `text-${a.color}-900` : 'text-gray-900'}`}>
                  {a.label}
                </p>
                <p className="text-xs text-gray-500 mt-1">{a.description}</p>
              </button>
            );
          })}
        </div>
      </div>

      {/* 筛选 + 显示控制 */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2">
            <span className="text-sm text-gray-500">半径：</span>
            {[3000, 5000, 8000, 10000].map((r) => (
              <button
                key={r}
                onClick={() => setRadius(r)}
                className={`px-3 py-1.5 text-sm rounded-lg transition-colors ${
                  radius === r ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {r / 1000}km
              </button>
            ))}
          </div>
          <div className="ml-auto flex items-center gap-2">
            <span className="text-sm text-gray-500">显示：</span>
            <button
              onClick={() => setShowMembers(!showMembers)}
              className={`px-3 py-1.5 text-sm rounded-lg flex items-center gap-1.5 ${
                showMembers ? 'bg-red-100 text-red-700' : 'bg-gray-100 text-gray-500'
              }`}
            >
              <Flame className="w-3.5 h-3.5" />
              品牌会员
            </button>
            <button
              onClick={() => setShowPOIs(!showPOIs)}
              className={`px-3 py-1.5 text-sm rounded-lg flex items-center gap-1.5 ${
                showPOIs ? 'bg-blue-100 text-blue-700' : 'bg-gray-100 text-gray-500'
              }`}
            >
              <Building2 className="w-3.5 h-3.5" />
              POI
            </button>
            <button
              onClick={() => setShowCompetitor(!showCompetitor)}
              className={`px-3 py-1.5 text-sm rounded-lg flex items-center gap-1.5 ${
                showCompetitor ? 'bg-yellow-100 text-yellow-700' : 'bg-gray-100 text-gray-500'
              }`}
            >
              <ShoppingBag className="w-3.5 h-3.5" />
              竞品
            </button>
          </div>
        </div>
      </div>

      {/* 地图 + 热力区 */}
      <div className="grid grid-cols-3 gap-6">
        <div className="col-span-2 bg-white rounded-xl border border-gray-200 overflow-hidden">
          <div className="h-[500px] bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50 relative">
            {/* 扫描动画 */}
            {scanning && (
              <div className="absolute inset-0 flex items-center justify-center z-30">
                <div className="text-center">
                  <div className="relative w-32 h-32 mx-auto mb-3">
                    <div className="absolute inset-0 rounded-full border-4 border-blue-400 animate-ping opacity-75" />
                    <div className="absolute inset-2 rounded-full border-4 border-purple-400 animate-ping opacity-50" style={{ animationDelay: '0.5s' }} />
                    <div className="absolute inset-4 rounded-full border-4 border-pink-400 animate-ping opacity-30" style={{ animationDelay: '1s' }} />
                  </div>
                  <p className="text-sm font-medium text-gray-700">融合 4 层数据源...</p>
                  <p className="text-xs text-gray-500 mt-1">地图 POI · 品牌 CRM · 换机模型 · 国补计算</p>
                </div>
              </div>
            )}

            {/* POI 热力点（蓝色） */}
            {scanned && showPOIs && filteredPoints.points.map((p, i) => (
              <div
                key={p.id}
                className="absolute cursor-pointer transform hover:scale-110 transition-transform"
                style={{ left: `${15 + i * 12}%`, top: `${25 + (i % 3) * 18}%` }}
                onClick={() => setSelectedPoint(p)}
              >
                <div className="w-10 h-10 rounded-full bg-blue-500 flex items-center justify-center shadow-lg text-white text-xs font-bold ring-4 ring-blue-200">
                  {p.leads}
                </div>
                <div className="absolute -bottom-6 left-1/2 -translate-x-1/2 whitespace-nowrap text-[10px] bg-blue-600 text-white px-1.5 py-0.5 rounded">
                  POI · {p.subType}
                </div>
              </div>
            ))}

            {/* 品牌会员热力点（红色 - 即将换机） */}
            {scanned && showMembers && filteredPoints.memberPoints.map((m, i) => (
              <div
                key={m.id}
                className="absolute cursor-pointer transform hover:scale-110 transition-transform z-10"
                style={{ left: `${30 + i * 20}%`, top: `${35 + (i % 2) * 25}%` }}
                onClick={() => setSelectedPoint(m)}
              >
                <div className="relative">
                  <div className="absolute inset-0 rounded-full bg-red-500 animate-ping opacity-40" />
                  <div className="relative w-14 h-14 rounded-full bg-gradient-to-br from-red-500 to-orange-500 flex items-center justify-center shadow-xl text-white">
                    <div className="text-center">
                      <div className="text-sm font-bold">{m.intentCount}</div>
                      <div className="text-[8px] opacity-90">高意向</div>
                    </div>
                  </div>
                </div>
                <div className="absolute -bottom-7 left-1/2 -translate-x-1/2 whitespace-nowrap text-[10px] bg-red-600 text-white px-2 py-0.5 rounded font-medium">
                  🔥 即将换机 · {m.avgDeviceAge}月
                </div>
              </div>
            ))}

            {/* 竞品门店（黄色） */}
            {scanned && showCompetitor && filteredPoints.competitorsWithDist.map((c, i) => (
              <div
                key={c.id}
                className="absolute cursor-pointer"
                style={{ left: `${60 + (i % 2) * 25}%`, top: `${20 + i * 20}%` }}
              >
                <div className="w-8 h-8 rounded-lg bg-yellow-400 flex items-center justify-center shadow-lg text-xs font-bold border-2 border-yellow-600">
                  ⚔
                </div>
                <div className="absolute -bottom-6 left-1/2 -translate-x-1/2 whitespace-nowrap text-[10px] bg-yellow-600 text-white px-1.5 py-0.5 rounded">
                  {c.brand}
                </div>
              </div>
            ))}

            {/* 门店位置（中心） */}
            <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-20">
              <div className="relative">
                <div className="absolute inset-0 rounded-full bg-blue-600 animate-ping opacity-30 w-12 h-12" />
                <div className="relative w-6 h-6 rounded-full bg-blue-600 border-4 border-white shadow-xl flex items-center justify-center">
                  <div className="w-2 h-2 bg-white rounded-full" />
                </div>
                <div className="absolute -top-8 left-1/2 -translate-x-1/2 whitespace-nowrap text-[10px] bg-blue-600 text-white px-2 py-0.5 rounded font-medium">
                  📍 我的门店
                </div>
              </div>
            </div>

            {/* 未定位提示 */}
            {!location && !locationLoading && (
              <div className="absolute inset-0 bg-gray-100/80 backdrop-blur-sm flex items-center justify-center z-40">
                <div className="text-center">
                  <Crosshair className="w-16 h-16 text-gray-400 mx-auto mb-3" />
                  <p className="text-gray-700 font-medium">请先获取位置</p>
                  <p className="text-gray-500 text-sm mt-1">雷达需要门店精准定位</p>
                </div>
              </div>
            )}

            {/* 图例 */}
            <div className="absolute bottom-3 left-3 bg-white/90 backdrop-blur-sm rounded-lg p-2 text-[10px] space-y-1">
              <div className="flex items-center gap-1.5">
                <span className="w-3 h-3 rounded-full bg-red-500" />
                <span>🔥 即将换机的品牌会员</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-3 h-3 rounded-full bg-blue-500" />
                <span>📍 周边 POI 客群</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-3 h-3 rounded bg-yellow-400" />
                <span>⚔ 竞品门店</span>
              </div>
            </div>
          </div>
        </div>

        {/* 右侧：详情面板或扫描提示 */}
        <div className="space-y-4">
          {selectedPoint ? (
            <PointDetailPanel
              point={selectedPoint}
              onClose={() => setSelectedPoint(null)}
              onCreateTask={handleCreateTask}
              creating={creatingTask}
              taskId={createdTask}
            />
          ) : (
            <div className="bg-white rounded-xl border border-gray-200 p-5">
              <div className="text-center py-8">
                <Target className="w-12 h-12 text-gray-300 mx-auto mb-3" />
                <h3 className="font-medium text-gray-700">点击热力点查看详情</h3>
                <p className="text-xs text-gray-500 mt-2">
                  每个热力点都包含<br />可触达线索、换机意向、建议话术
                </p>
              </div>
            </div>
          )}

          {/* 数据层标识 */}
          <div className="bg-white rounded-xl border border-gray-200 p-5">
            <h3 className="text-sm font-semibold text-gray-900 mb-3">4 层数据源</h3>
            <div className="space-y-2">
              {[
                { name: '地图 POI', count: mapPOIs.length, color: 'blue', icon: '📍' },
                { name: '品牌 CRM', count: memberClusters.reduce((a, m) => a + m.leads, 0), color: 'red', icon: '👥' },
                { name: '换机模型', count: memberClusters.reduce((a, m) => a + m.intentCount, 0), color: 'orange', icon: '🔄' },
                { name: '国补计算', count: '4 政策', color: 'green', icon: '💰' },
              ].map((s) => (
                <div key={s.name} className="flex items-center gap-2 text-sm">
                  <span className="text-base">{s.icon}</span>
                  <span className="flex-1 text-gray-700">{s.name}</span>
                  <span className={`font-medium text-${s.color}-600`}>{s.count}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* 扫描结果统计 */}
      {scanned && (
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-semibold text-gray-900">本轮扫描结果</h3>
            <span className="text-sm text-gray-500">客群：{currentAudience.label} · 半径 {formatDistance(radius)}</span>
          </div>
          <div className="grid grid-cols-4 gap-4">
            <StatCard icon={Flame} label="品牌会员热力点" value={filteredPoints.memberPoints.length} unit="个" color="red" />
            <StatCard icon={Building2} label="POI 客群区" value={filteredPoints.points.length} unit="个" color="blue" />
            <StatCard icon={Users} label="可触达线索" value={filteredPoints.points.reduce((a, p) => a + p.leads, 0) + filteredPoints.memberPoints.reduce((a, m) => a + m.leads, 0)} unit="人" color="purple" />
            <StatCard icon={Target} label="预估高意向" value={filteredPoints.memberPoints.reduce((a, m) => a + m.intentCount, 0)} unit="人" color="orange" />
          </div>
        </div>
      )}

      {/* 合规说明 */}
      <div className="bg-amber-50 border border-amber-200 rounded-xl p-4">
        <div className="flex items-start gap-3">
          <Shield className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" />
          <div>
            <h4 className="font-medium text-amber-900">合规红线</h4>
            <ul className="text-xs text-amber-800 mt-1.5 space-y-0.5">
              <li>• 不爬取、不购买任何非公开个人数据</li>
              <li>• 地图 POI 数据仅用于商圈分析，不可导出个人手机号</li>
              <li>• 品牌 CRM 数据的使用需取得客户授权（在购买时签署《隐私协议》）</li>
              <li>• 所有操作日志保留 180 天，支持品牌总部审计</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ============================== 子组件 ============================== */
function StatCard({ icon: Icon, label, value, unit, color }: {
  icon: typeof Flame, label: string, value: number | string, unit: string, color: string
}) {
  return (
    <div className={`p-4 rounded-xl border border-${color}-200 bg-${color}-50`}>
      <div className="flex items-center gap-2 mb-2">
        <Icon className={`w-4 h-4 text-${color}-600`} />
        <span className="text-xs text-gray-600">{label}</span>
      </div>
      <div className="flex items-baseline gap-1">
        <span className={`text-2xl font-bold text-${color}-700`}>{value}</span>
        <span className="text-xs text-gray-500">{unit}</span>
      </div>
    </div>
  );
}

function PointDetailPanel({ point, onClose, onCreateTask, creating, taskId }: {
  point: HeatmapPoint,
  onClose: () => void,
  onCreateTask: () => void,
  creating: boolean,
  taskId: string | null
}) {
  const isMember = point.type === 'member';
  
  return (
    <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
      {/* 头部 */}
      <div className={`p-4 ${
        isMember 
          ? 'bg-gradient-to-r from-red-500 to-orange-500' 
          : 'bg-gradient-to-r from-blue-500 to-indigo-500'
      } text-white`}>
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-1.5 mb-1">
              {isMember ? <Flame className="w-4 h-4" /> : <Building2 className="w-4 h-4" />}
              <span className="text-xs">
                {isMember ? '品牌会员聚集区' : 'POI 客群区'}
              </span>
            </div>
            <h3 className="font-semibold">{point.name}</h3>
            {point.distance && (
              <p className="text-xs opacity-90 mt-0.5">
                距门店 {formatDistance(point.distance)}
              </p>
            )}
          </div>
          <button onClick={onClose} className="p-1 hover:bg-white/20 rounded">
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* 数据指标 */}
      <div className="p-4 space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <div className="p-3 bg-purple-50 rounded-lg">
            <div className="text-xs text-gray-500">可触达线索</div>
            <div className="text-xl font-bold text-purple-600">{point.leads} <span className="text-xs font-normal text-gray-500">人</span></div>
          </div>
          <div className="p-3 bg-orange-50 rounded-lg">
            <div className="text-xs text-gray-500">换机意向</div>
            <div className="text-xl font-bold text-orange-600">{point.intentCount} <span className="text-xs font-normal text-gray-500">人</span></div>
          </div>
        </div>

        {/* 会员专属字段 */}
        {isMember && (
          <div className="space-y-2 pt-2 border-t border-gray-100">
            {point.avgDeviceAge && (
              <div className="flex items-center justify-between text-sm">
                <span className="text-gray-500 flex items-center gap-1">
                  <Clock className="w-3.5 h-3.5" />
                  平均购机月数
                </span>
                <span className={`font-bold ${
                  point.avgDeviceAge > 30 ? 'text-red-600' : 
                  point.avgDeviceAge > 20 ? 'text-orange-600' : 'text-gray-900'
                }`}>
                  {point.avgDeviceAge} 月
                  {point.avgDeviceAge > 30 && <span className="ml-1 text-xs">⚠ 超期服役</span>}
                  {point.avgDeviceAge > 20 && point.avgDeviceAge <= 30 && <span className="ml-1 text-xs">🔥 即将换机</span>}
                </span>
              </div>
            )}
            {point.flagshipRatio !== undefined && (
              <div className="flex items-center justify-between text-sm">
                <span className="text-gray-500 flex items-center gap-1">
                  <Award className="w-3.5 h-3.5" />
                  旗舰机用户占比
                </span>
                <span className="font-bold text-purple-600">{(point.flagshipRatio * 100).toFixed(0)}%</span>
              </div>
            )}
            {point.crmTags && (
              <div className="pt-2">
                <div className="text-xs text-gray-500 mb-1.5">CRM 标签</div>
                <div className="flex flex-wrap gap-1">
                  {point.crmTags.map(tag => (
                    <span key={tag} className="px-2 py-0.5 bg-red-100 text-red-700 text-xs rounded">
                      {tag}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* 建议话术 */}
        {point.suggestedScripts && point.suggestedScripts.length > 0 && (
          <div className="pt-2 border-t border-gray-100">
            <div className="flex items-center gap-1.5 text-xs text-gray-500 mb-2">
              <Sparkles className="w-3.5 h-3.5" />
              AI 建议话术
            </div>
            <div className="space-y-1.5">
              {point.suggestedScripts.map((s, i) => (
                <div key={i} className="p-2 bg-blue-50 rounded text-xs text-gray-700 leading-relaxed">
                  {s}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 国补预估（仅会员显示） */}
        {isMember && (
          <div className="pt-2 border-t border-gray-100">
            <div className="flex items-center gap-1.5 text-xs text-gray-500 mb-2">
              💰 国补预估
            </div>
            <div className="space-y-1 text-xs">
              <div className="flex justify-between"><span className="text-gray-500">机型原价</span><span>¥6,999</span></div>
              <div className="flex justify-between"><span className="text-gray-500">旧机抵扣</span><span className="text-green-600">-¥2,000</span></div>
              <div className="flex justify-between"><span className="text-gray-500">国补</span><span className="text-green-600">-¥500</span></div>
              <div className="flex justify-between"><span className="text-gray-500">品牌补贴</span><span className="text-green-600">-¥300</span></div>
              <div className="flex justify-between pt-1.5 border-t border-gray-200 font-bold">
                <span>实付预估</span>
                <span className="text-blue-600">¥4,199</span>
              </div>
            </div>
          </div>
        )}

        {/* 操作按钮 */}
        <div className="pt-3 space-y-2">
          {taskId ? (
            <div className="p-3 bg-green-50 border border-green-200 rounded-lg">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-5 h-5 text-green-600" />
                <div>
                  <p className="text-sm font-medium text-green-900">任务已创建</p>
                  <p className="text-xs text-green-700">任务编号：{taskId}，已分配给店员</p>
                </div>
              </div>
            </div>
          ) : (
            <button
              onClick={onCreateTask}
              disabled={creating}
              className="w-full py-2.5 bg-gradient-to-r from-blue-600 to-purple-600 text-white rounded-lg font-medium hover:from-blue-700 hover:to-purple-700 disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {creating ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  生成中...
                </>
              ) : (
                <>
                  <Route className="w-4 h-4" />
                  一键生成扫街任务
                </>
              )}
            </button>
          )}
          <div className="grid grid-cols-2 gap-2">
            <button className="py-2 bg-gray-100 text-gray-700 rounded-lg text-sm flex items-center justify-center gap-1.5">
              <Phone className="w-3.5 h-3.5" />
              群发短信
            </button>
            <button className="py-2 bg-gray-100 text-gray-700 rounded-lg text-sm flex items-center justify-center gap-1.5">
              <MessageSquare className="w-3.5 h-3.5" />
              企微触达
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
