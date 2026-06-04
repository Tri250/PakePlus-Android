import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import {
  Sparkles, MapPin, TrendingUp, Users2, ArrowUpRight, Calendar, Sun, CloudRain, Zap, Send,
  Activity, Target, Flame, Layers, ChevronDown, ChevronUp, Globe, Crosshair, Satellite,
} from 'lucide-react';
import RadiusSelector from '@/components/RadiusSelector';
import StatCard from '@/components/StatCard';
import SectionHeader from '@/components/SectionHeader';
import ProgressRing from '@/components/ProgressRing';
import { toast } from '@/components/Toast';
import { useGlobal } from '@/store/useGlobal';
import { api } from '@/lib/api';
import type { Overview, Persona, RadiusKm, Store } from '@/lib/types';
import { useNavigate } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { cn } from '@/lib/utils';

export default function Cockpit() {
  const { radius, setRadius, currentStoreId, setCurrentStore, stores, realtimePosition, setRealtimePosition, setLocating } = useGlobal();
  const nav = useNavigate();
  const [overview, setOverview] = useState<Overview | null>(null);
  const [suggestions, setSuggestions] = useState<{ title: string; body: string; cta: string }[]>([]);
  const [persona, setPersona] = useState<Persona | null>(null);
  const [seeding, setSeeding] = useState(false);
  const [radiusStats, setRadiusStats] = useState<{ km: RadiusKm; reachableCustomers: number; hotSpots: number }[]>([]);
  const [time, setTime] = useState(new Date());
  const [showStoreList, setShowStoreList] = useState(false);
  // 实时定位反查信息
  const [reverseInfo, setReverseInfo] = useState<{
    province: string; city: string; district: string; address: string; nearestPoi: string | null; distance: number;
  } | null>(null);
  const [candidates, setCandidates] = useState<{ id: string; name: string; distance: number }[]>([]);

  const currentStore = stores.find((s) => s.id === currentStoreId);

  useEffect(() => {
    const t = setInterval(() => setTime(new Date()), 30_000);
    return () => clearInterval(t);
  }, []);

  const load = async () => {
    if (!currentStoreId) return;
    const [o, sg, p, rs] = await Promise.all([
      api.get<{ overview: Overview }>(`/dashboard/overview?storeId=${currentStoreId}&range=7d`),
      api.get<{ suggestions: { title: string; body: string; cta: string }[] }>(`/ai/suggestion?radiusKm=${radius}`),
      api.post<{ persona: Persona }>('/ai/persona', { storeId: currentStoreId, radiusKm: radius }).catch(() => ({ persona: null as unknown as Persona })),
      api.get<{ stats: { km: RadiusKm; reachableCustomers: number; hotSpots: number }[] }>(`/stores/${currentStoreId}/radius?km=3,5,8,10`),
    ]);
    setOverview(o.overview);
    setSuggestions(sg.suggestions);
    setPersona(p.persona);
    setRadiusStats(rs.stats);
  };

  useEffect(() => { load(); }, [currentStoreId, radius]);

  // 切换门店(定位)
  const onLocate = async (s: Store) => {
    setShowStoreList(false);
    setCurrentStore(s.id);
    try {
      await load();
      toast.success('已定位新城市', `${s.name} · 加载了周边真实 POI 数据`);
    } catch (e) {
      toast.error('定位失败', '请重试');
    }
  };

  // 实时定位:调用浏览器 Geolocation API → 反查地址 → 自动切换最近门店
  const onRealtimeLocate = () => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      toast.error('浏览器不支持定位', '请使用 Chrome / Edge / Safari 访问');
      return;
    }
    setLocating(true);
    toast.info('🛰️ 正在获取 GPS 定位…', '请允许浏览器获取位置');
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const lng = pos.coords.longitude;
        const lat = pos.coords.latitude;
        const accuracy = pos.coords.accuracy;
        try {
          const r = await api.post<{
            address: { province: string; city: string; district: string; detail: string; nearestPoi: { name: string } | null };
            nearestStore: { id: string; name: string; address: string; distance: number };
            candidates: { id: string; name: string; distance: number }[];
          }>('/geo/reverse', { lng, lat, accuracy, source: 'browser' });
          setRealtimePosition({
            lng, lat, accuracy, source: 'browser',
            province: r.address.province, city: r.address.city, district: r.address.district,
            address: r.address.detail, nearestPoi: r.address.nearestPoi?.name,
            capturedAt: Date.now(),
          });
          setReverseInfo({
            province: r.address.province,
            city: r.address.city,
            district: r.address.district,
            address: r.address.detail,
            nearestPoi: r.address.nearestPoi?.name || null,
            distance: r.nearestStore?.distance || 0,
          });
          setCandidates(r.candidates.map((c) => ({ id: c.id, name: c.name, distance: c.distance })));
          // 自动切换到最近门店
          if (r.nearestStore?.id && r.nearestStore.id !== currentStoreId) {
            setCurrentStore(r.nearestStore.id);
            toast.success(
              '🛰️ 已根据实时定位切换门店',
              `${r.address.city} · ${r.address.district} · ${r.nearestStore.name} (${r.nearestStore.distance}km)`,
              4000,
            );
            await load();
          } else {
            toast.success('🛰️ 实时定位成功', `${r.address.city} · ${r.address.district} · ${r.address.detail}`);
          }
        } catch (err) {
          toast.error('反查地址失败', String(err));
        } finally {
          setLocating(false);
        }
      },
      (err) => {
        setLocating(false);
        const msg = err.code === 1 ? '用户拒绝授权' : err.code === 2 ? '位置不可用' : '定位超时';
        toast.error('定位失败', msg);
      },
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 60_000 },
    );
  };

  // 一键拓客:基于实时位置(浏览器 GPS) → 真实爬虫采集
  const oneClickAcquire = async () => {
    if (!currentStoreId) return;
    setSeeding(true);
    // 1. 显示爬虫启动
    toast.info(
      '🕷️ 正在启动爬虫…',
      `目标半径: ${radius}km · 渠道: 百度地图 / 高德地图 / 腾讯位置`,
    );
    try {
      const r = await api.post<{
        created: number;
        samples: { name: string; phone: string; meta: { source: string; fromPoi: string; fromDistrict: string; hotScore: number } }[];
        realPosition?: { lng: number; lat: number } | null;
      }>('/leads/seed', {
        storeId: currentStoreId,
        radiusKm: radius,
        count: 24,
        realLng: realtimePosition?.lng,
        realLat: realtimePosition?.lat,
      });
      // 2. 抓样例 TOP 3,展示来源
      const top = r.samples.slice(0, 3);
      const sources = Array.from(new Set(top.map((s) => s.meta.source))).join(' / ');
      const avgHot = Math.round(top.reduce((s, x) => s + x.meta.hotScore, 0) / top.length);
      const district = top[0]?.meta.fromDistrict || '周边';
      toast.success(
        `🕷️ 爬虫采集完成 · +${r.created} 客户`,
        `来源: ${sources} · 平均评分 ${avgHot} · 主城区: ${district}`,
        5000,
      );
      // 3. 等待 1.2s 让动画过渡
      setTimeout(() => {
        nav('/touch');
      }, 1200);
    } catch (e) {
      toast.error('采集失败', '请重试');
    } finally {
      setSeeding(false);
    }
  };

  const greeting = (() => {
    const h = time.getHours();
    if (h < 11) return '早上好';
    if (h < 14) return '中午好';
    if (h < 18) return '下午好';
    return '晚上好';
  })();

  const reachable = radiusStats.find((s) => s.km === radius)?.reachableCustomers || 0;
  const hotSpots = radiusStats.find((s) => s.km === radius)?.hotSpots || 0;

  // 生成 sparkline 假数据
  const sparkReach = overview?.trend?.map((t) => t.reach) || [120, 180, 240, 320, 280, 360, 410];

  return (
    <div className="p-8 max-w-[1400px] mx-auto">
      {/* 顶部 */}
      <header className="flex items-end justify-between flex-wrap gap-4 mb-6">
        <div className="min-w-0 flex-1">
          <motion.div
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-widest text-cyber-200"
          >
            <span className="relative flex w-2 h-2">
              <span className="absolute inline-flex h-full w-full rounded-full bg-cyber-300 opacity-75 animate-ping" />
              <span className="relative inline-flex rounded-full h-2 w-2 bg-cyber-300" />
            </span>
            Cockpit · 实时
            <span className="text-ink-500">·</span>
            <span className="text-ink-400">{time.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}</span>
            {realtimePosition && (
              <>
                <span className="text-ink-500">·</span>
                <span className="inline-flex items-center gap-1 text-ember-300">
                  <span className="w-1.5 h-1.5 rounded-full bg-ember-500 animate-pulse" />
                  GPS 已锁定
                </span>
              </>
            )}
          </motion.div>
          <h1 className="mt-2 text-3xl font-display font-extrabold tracking-tight">
            {greeting},{realtimePosition?.city?.replace(/市$/, '') || currentStore?.name?.split(' · ').pop() || '店长'}
          </h1>
          <div className="mt-1 flex items-center gap-2 text-sm text-ink-400 relative flex-wrap">
            <MapPin className="w-3.5 h-3.5 text-ember-500" />
            <span className="font-mono">
              {realtimePosition
                ? `${realtimePosition.province} · ${realtimePosition.city} · ${realtimePosition.district} · ${realtimePosition.address || ''}`
                : currentStore?.address}
            </span>
            <span className="text-ink-500">·</span>
            <span className="font-mono">{currentStore?.category}</span>
            {realtimePosition && (
              <span className="font-mono text-[10px] text-ink-500 ml-1">
                · 🛰️ {realtimePosition.lng.toFixed(4)}, {realtimePosition.lat.toFixed(4)}
                {realtimePosition.accuracy ? ` · ±${Math.round(realtimePosition.accuracy)}m` : ''}
              </span>
            )}
            {/* 🛰️ 实时定位按钮 */}
            <button
              onClick={onRealtimeLocate}
              className={cn(
                'ml-1 inline-flex items-center gap-1 px-2.5 py-0.5 text-[10px] font-mono rounded-full border transition',
                realtimePosition
                  ? 'border-ember-500/50 bg-ember-500/10 text-ember-200 shadow-glow'
                  : 'border-cyber-300/30 text-cyber-200 hover:bg-cyber-300/10',
              )}
            >
              <Satellite className="w-3 h-3" />
              {locating ? '定位中…' : realtimePosition ? '重新 GPS 定位' : '🛰️ 实时定位'}
            </button>
            {/* 切换定位下拉(原 dropdown) */}
            <div className="relative">
              <button
                onClick={() => setShowStoreList((v) => !v)}
                className={cn(
                  'inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-mono rounded-full border transition',
                  showStoreList
                    ? 'border-ember-500/50 bg-ember-500/10 text-ember-200 shadow-glow'
                    : 'border-white/10 text-ink-300 hover:border-ember-500/30',
                )}
              >
                <Crosshair className="w-3 h-3" />
                切换定位
                {showStoreList ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
              </button>
              <AnimatePresence>
                {showStoreList && (
                  <motion.div
                    initial={{ opacity: 0, y: -8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    className="absolute left-0 top-full mt-2 w-80 max-h-96 overflow-y-auto panel p-2 z-30 shadow-glow"
                  >
                    <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400 px-2 py-1.5 flex items-center gap-1.5">
                      <Globe className="w-3 h-3" /> 全国 {stores.length} 家门店
                    </div>
                    {stores.map((s) => (
                      <button
                        key={s.id}
                        onClick={() => onLocate(s)}
                        className={cn(
                          'w-full text-left px-3 py-2 rounded-lg text-xs flex items-center gap-2 transition',
                          s.id === currentStoreId
                            ? 'bg-ember-500/10 text-ember-200'
                            : 'text-ink-200 hover:bg-white/5',
                        )}
                      >
                        <MapPin className="w-3 h-3 text-ember-500 shrink-0" />
                        <div className="min-w-0 flex-1">
                          <div className="truncate font-medium">{s.name}</div>
                          <div className="text-[10px] text-ink-500 font-mono">{s.address}</div>
                        </div>
                      </button>
                    ))}
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>

          {/* 实时定位反查信息条 */}
          {realtimePosition && (
            <motion.div
              initial={{ opacity: 0, y: -4 }}
              animate={{ opacity: 1, y: 0 }}
              className="mt-2 flex items-center gap-2 text-[11px] font-mono flex-wrap"
            >
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-ember-500/10 border border-ember-500/30 text-ember-200">
                🛰️ 实时定位锁定
              </span>
              {realtimePosition.nearestPoi && (
                <span className="text-ink-300">最近 POI: <span className="text-white">{realtimePosition.nearestPoi}</span></span>
              )}
              {candidates[0] && (
                <span className="text-ink-300">
                  最近门店: <span className="text-ember-200">{candidates[0].name}</span>
                  <span className="text-ink-500 ml-1">({candidates[0].distance} km)</span>
                </span>
              )}
              {candidates.slice(1, 4).length > 0 && (
                <span className="text-ink-500">
                  备选: {candidates.slice(1, 4).map((c) => c.name).join(' / ')}
                </span>
              )}
            </motion.div>
          )}
        </div>

        <div className="panel px-3 py-2.5 flex items-center gap-4">
          <RadiusSelector value={radius} onChange={(k) => setRadius(k)} size="sm" showLabel={false} />
        </div>
      </header>

      {/* 顶部数据卡 — StatCard 组件 */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-6">
        <StatCard
          label={`${radius}km 可触达`}
          value={reachable}
          format="comma"
          suffix="人"
          icon={Users2}
          tone="ember"
          delta={12.4}
          spark={sparkReach}
          caption="REAL-TIME"
          delay={0}
        />
        <StatCard
          label={`${radius}km 高潜 POI`}
          value={hotSpots}
          suffix="个"
          icon={MapPin}
          tone="cyber"
          delta={8.7}
          caption="LIVE"
          delay={0.06}
        />
        <StatCard
          label="本周曝光"
          value={overview?.reach ?? 0}
          format="wan"
          icon={TrendingUp}
          tone="violet"
          delta={5.2}
          delay={0.12}
        />
        <StatCard
          label="加微数"
          value={overview?.addedWechat ?? 0}
          format="comma"
          icon={Flame}
          tone="gold"
          delta={18.3}
          spark={overview?.trend?.map((t) => t.added) || [8, 12, 16, 22, 18, 26, 32]}
          delay={0.18}
        />
      </div>

      {/* 一键拓客 / 一键群发 — 大型 CTA 面板 */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-6">
        <motion.button
          onClick={oneClickAcquire}
          disabled={seeding}
          whileHover={{ y: -3 }}
          className="group relative overflow-hidden panel p-5 text-left hover:border-ember-500/40 transition flex items-center gap-4"
        >
          <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-ember-500/30 blur-3xl opacity-50 group-hover:opacity-80 transition" />
          <div className="relative w-14 h-14 rounded-2xl bg-gradient-to-br from-ember-500 to-ember-700 text-ink-950 grid place-items-center shrink-0 group-hover:scale-110 transition shadow-glow">
            {seeding ? (
              <span className="w-6 h-6 border-2 border-ink-950 border-t-transparent rounded-full animate-spin" />
            ) : (
              <Zap className="w-7 h-7" />
            )}
          </div>
          <div className="flex-1 min-w-0 relative">
            <div className="flex items-center gap-2">
              <div className="text-base font-display font-bold">一键拓客 · {radius} km</div>
              <span className="chip bg-ember-500/15 text-ember-200 border-ember-500/30 text-[9px]">HOT</span>
            </div>
            <div className="text-xs text-ink-400 mt-0.5">
              从 {hotSpots} 个高潜 POI 批量生成 24 位可触达客户 · 自动入池
            </div>
          </div>
          <span className="text-[10px] font-mono text-ember-300 shrink-0 relative">
            {seeding ? '拓客中…' : '立即执行 →'}
          </span>
        </motion.button>

        <motion.button
          onClick={() => nav('/touch')}
          whileHover={{ y: -3 }}
          className="group relative overflow-hidden panel p-5 text-left hover:border-cyber-300/40 transition flex items-center gap-4"
        >
          <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-cyber-300/30 blur-3xl opacity-50 group-hover:opacity-80 transition" />
          <div className="relative w-14 h-14 rounded-2xl bg-gradient-to-br from-cyber-300 to-cyber-500 text-ink-950 grid place-items-center shrink-0 group-hover:scale-110 transition shadow-cyber">
            <Send className="w-7 h-7" />
          </div>
          <div className="flex-1 min-w-0 relative">
            <div className="flex items-center gap-2">
              <div className="text-base font-display font-bold">一键群发 · 多渠道</div>
              <span className="chip bg-cyber-300/15 text-cyber-200 border-cyber-300/30 text-[9px]">AI</span>
            </div>
            <div className="text-xs text-ink-400 mt-0.5">
              短信 / 企微 / 抖音 / 卡券 / AI 外呼 · AI 自动选文案 · 触达结果实时回写
            </div>
          </div>
          <span className="text-[10px] font-mono text-cyber-200 shrink-0 relative">进入中心 →</span>
        </motion.button>
      </div>

      {/* 主体两列 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 今日 AI 建议 */}
        <div className="lg:col-span-2 space-y-4">
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className="panel p-6 relative overflow-hidden"
          >
            <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-ember-500/15 blur-3xl" />
            <SectionHeader
              index="01"
              icon={Sparkles}
              title="AI 今日建议"
              caption={`基于 ${currentStore?.name} 周边数据`}
              actions={
                <div className="flex items-center gap-2 text-[10px] font-mono text-ink-400">
                  <span className="inline-flex items-center gap-1 px-2 py-1 rounded-md bg-white/5">
                    <Sun className="w-3 h-3 text-ember-300" /> 多云 22°
                  </span>
                  <span className="inline-flex items-center gap-1 px-2 py-1 rounded-md bg-white/5">
                    <CloudRain className="w-3 h-3 text-cyber-300" /> 明日小雨
                  </span>
                </div>
              }
            />
            <div className="space-y-2.5">
              {suggestions.length === 0 ? (
                <div className="text-center py-8 text-xs text-ink-400">正在生成建议…</div>
              ) : (
                suggestions.map((s, i) => (
                  <motion.div
                    key={i}
                    initial={{ opacity: 0, x: -8 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.15 + i * 0.08 }}
                    whileHover={{ x: 4 }}
                    className="group rounded-xl border border-white/5 bg-ink-800/40 p-4 flex items-start gap-4 hover:border-ember-500/30 transition cursor-pointer"
                  >
                    <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-ember-500/20 to-ember-500/0 text-ember-300 flex items-center justify-center font-mono text-xs shrink-0 border border-ember-500/20">
                      0{i + 1}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-semibold text-white">{s.title}</div>
                      <div className="text-xs text-ink-300 mt-1 leading-relaxed">{s.body}</div>
                    </div>
                    <button
                      onClick={() => nav('/campaign')}
                      className="shrink-0 text-xs font-medium text-ember-300 hover:text-ember-200 inline-flex items-center gap-1 group-hover:translate-x-0.5 transition"
                    >
                      {s.cta}
                      <ArrowUpRight className="w-3.5 h-3.5" />
                    </button>
                  </motion.div>
                ))
              )}
            </div>
          </motion.div>

          {/* 圈层漏斗 — 含 ProgressRing */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="panel p-6"
          >
            <SectionHeader
              index="02"
              icon={Layers}
              title="3 / 5 / 8 / 10 km · 圈层漏斗"
              caption="从曝光到加微到成交的转化漏斗"
              actions={
                <button
                  onClick={() => nav('/dashboard')}
                  className="text-[11px] font-mono text-cyber-200 hover:text-cyber-100 inline-flex items-center gap-1"
                >
                  完整看板 <ArrowUpRight className="w-3 h-3" />
                </button>
              }
            />
            <div className="grid grid-cols-4 gap-3">
              {([3, 5, 8, 10] as RadiusKm[]).map((km, idx) => {
                const stat = radiusStats.find((s) => s.km === km);
                const reach = km === 3 ? 4800 : km === 5 ? 9600 : km === 8 ? 18400 : 32400;
                const added = stat?.reachableCustomers ? Math.round(stat.reachableCustomers * 0.08) : Math.round(reach * 0.08);
                const visited = Math.round(added * 0.32);
                const won = Math.round(visited * 0.18);
                const conv = (won / reach) * 100;
                return (
                  <motion.div
                    key={km}
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.25 + idx * 0.05 }}
                    className={cn(
                      'relative rounded-xl border p-3 transition',
                      km === radius ? 'border-ember-500/50 bg-ember-500/[0.06] shadow-glow' : 'border-white/5 bg-ink-800/30',
                    )}
                  >
                    {km === radius && (
                      <div className="absolute -top-1 left-1/2 -translate-x-1/2 px-1.5 py-0.5 rounded-b-md bg-ember-500 text-ink-950 text-[8px] font-bold tracking-wider">
                        CURRENT
                      </div>
                    )}
                    <div className="flex items-center justify-between mb-2">
                      <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400">
                        {km} km
                      </div>
                      <div className="font-mono text-[10px] text-cyber-200">
                        {conv.toFixed(1)}%
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <ProgressRing
                        value={conv * 4}
                        size={56}
                        stroke={5}
                        tone={km === radius ? 'ember' : 'cyber'}
                      />
                      <div className="flex-1 space-y-1.5">
                        {[
                          { v: reach, label: '曝光', color: 'bg-ember-500/30' },
                          { v: added, label: '加微', color: 'bg-ember-500/60' },
                          { v: visited, label: '到店', color: 'bg-cyber-300/60' },
                          { v: won, label: '成交', color: 'bg-cyber-300' },
                        ].map((s) => (
                          <div key={s.label} className="flex items-center gap-1.5">
                            <div className={`h-1.5 rounded-full ${s.color}`} style={{ width: `${Math.max(8, (s.v / reach) * 100)}%` }} />
                            <span className="text-[9px] font-mono text-ink-300 w-7 text-right">{s.v}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  </motion.div>
                );
              })}
            </div>
          </motion.div>
        </div>

        {/* 右侧:AI 画像速览 + 日程 */}
        <div className="space-y-4">
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.15 }}
            className="panel p-5 relative overflow-hidden"
          >
            <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-cyber-300/20 blur-3xl" />
            <SectionHeader
              index="03"
              icon={Target}
              title={`${radius} km · 画像速览`}
              caption="AI PERSONA"
              actions={
                <button
                  onClick={() => nav('/persona')}
                  className="text-[11px] font-mono text-cyber-200 hover:text-cyber-100"
                >
                  详情 →
                </button>
              }
            />
            {persona ? (
              <div className="relative">
                <p className="text-xs text-ink-300 leading-relaxed mb-3">{persona.summary}</p>
                <div className="flex flex-wrap gap-1.5 mb-4">
                  {persona.keywords.map((k) => (
                    <motion.span
                      key={k}
                      whileHover={{ y: -1 }}
                      className="pill bg-ember-500/10 text-ember-200 border border-ember-500/20 text-[10px]"
                    >
                      #{k}
                    </motion.span>
                  ))}
                </div>
                <div className="grid grid-cols-2 gap-2">
                  {persona.radar.slice(0, 4).map((r) => (
                    <div key={r.dim} className="rounded-lg border border-white/5 p-2.5 hover:border-cyber-300/30 transition">
                      <div className="text-[10px] font-mono text-ink-400">{r.dim}</div>
                      <div className="mt-1 flex items-baseline gap-1.5">
                        <span className="font-mono text-lg font-bold text-white">{r.value}</span>
                        <span className="text-[10px] text-cyber-200">/ 100</span>
                      </div>
                      <div className="mt-1 h-1 rounded-full bg-white/5 overflow-hidden">
                        <motion.div
                          initial={{ width: 0 }}
                          animate={{ width: `${r.value}%` }}
                          transition={{ duration: 1, ease: [0.16, 1, 0.3, 1] }}
                          className="h-full bg-gradient-to-r from-ember-500 to-cyber-300"
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="text-xs text-ink-400 py-4">正在分析 {radius} km 圈层…</div>
            )}
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="panel p-5"
          >
            <SectionHeader
              index="04"
              icon={Calendar}
              title="今日日程"
              caption="TODAY"
            />
            <ul className="space-y-2.5 text-xs">
              {[
                { time: '11:30', t: '推送"午间手冲 9.9 元"朋友圈广告', tag: '营销', tone: 'ember' },
                { time: '14:00', t: `BD 小赵 跟进 ${radius} 公里王女士`, tag: '跟进', tone: 'cyber' },
                { time: '17:30', t: '生成 5km 圈层卡券并上架美团', tag: '内容', tone: 'ember' },
                { time: '21:00', t: '复盘今日 8km 加微成本', tag: '数据', tone: 'cyber' },
              ].map((s, i) => (
                <motion.li
                  key={i}
                  initial={{ opacity: 0, x: -6 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.3 + i * 0.05 }}
                  className="flex items-start gap-3 group"
                >
                  <span className="font-mono text-[10px] text-ink-400 pt-0.5 w-10 shrink-0">{s.time}</span>
                  <span className="flex-1 text-ink-200 group-hover:text-white transition">{s.t}</span>
                  <span
                    className={cn(
                      'pill text-[9px]',
                      s.tone === 'ember'
                        ? 'bg-ember-500/10 text-ember-200 border border-ember-500/20'
                        : 'bg-cyber-300/10 text-cyber-200 border border-cyber-300/20',
                    )}
                  >
                    {s.tag}
                  </span>
                </motion.li>
              ))}
            </ul>
          </motion.div>

          {/* 实时活动流 */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.25 }}
            className="panel p-5"
          >
            <SectionHeader
              index="05"
              icon={Activity}
              title="实时活动"
              caption={`${currentStore?.name} 实时触达流`}
            />
            <div className="space-y-2 text-xs">
              {[
                { t: '12s', e: '王女士 已加微 · 来源 望京 SOHO', c: 'cyber' },
                { t: '48s', e: '李先生 已送达 · 短信渠道', c: 'ember' },
                { t: '2m', e: '张总 标记高潜 · AI 评分 92', c: 'gold' },
                { t: '5m', e: '刘姐 已到店 · 抖音同城', c: 'cyber' },
              ].map((row, i) => (
                <motion.div
                  key={i}
                  initial={{ opacity: 0, x: -4 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.35 + i * 0.06 }}
                  className="flex items-center gap-2"
                >
                  <span className="font-mono text-[9px] text-ink-500 w-7">{row.t}</span>
                  <span className={cn('w-1.5 h-1.5 rounded-full',
                    row.c === 'ember' ? 'bg-ember-500' :
                    row.c === 'cyber' ? 'bg-cyber-300' : 'bg-signal-gold',
                  )} />
                  <span className="text-ink-300 flex-1 truncate">{row.e}</span>
                </motion.div>
              ))}
            </div>
          </motion.div>
        </div>
      </div>
    </div>
  );
}
