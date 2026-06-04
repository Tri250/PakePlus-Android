import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Sparkles, MapPin, TrendingUp, Users2, ArrowUpRight, Calendar, Sun, CloudRain, Zap, Send, Activity, Target, Flame, Layers } from 'lucide-react';
import RadiusSelector from '@/components/RadiusSelector';
import StatCard from '@/components/StatCard';
import SectionHeader from '@/components/SectionHeader';
import ProgressRing from '@/components/ProgressRing';
import { toast } from '@/components/Toast';
import { useGlobal } from '@/store/useGlobal';
import { api } from '@/lib/api';
import type { Overview, Persona, RadiusKm } from '@/lib/types';
import { useNavigate } from 'react-router-dom';

export default function Cockpit() {
  const { radius, setRadius, currentStoreId, stores } = useGlobal();
  const nav = useNavigate();
  const [overview, setOverview] = useState<Overview | null>(null);
  const [suggestions, setSuggestions] = useState<{ title: string; body: string; cta: string }[]>([]);
  const [persona, setPersona] = useState<Persona | null>(null);
  const [seeding, setSeeding] = useState(false);
  const [radiusStats, setRadiusStats] = useState<{ km: RadiusKm; reachableCustomers: number; hotSpots: number }[]>([]);
  const [time, setTime] = useState(new Date());

  const currentStore = stores.find((s) => s.id === currentStoreId);

  useEffect(() => {
    const t = setInterval(() => setTime(new Date()), 30_000);
    return () => clearInterval(t);
  }, []);

  useEffect(() => {
    if (!currentStoreId) return;
    api.get<{ overview: Overview }>(`/dashboard/overview?storeId=${currentStoreId}&range=7d`).then((r) => setOverview(r.overview));
    api.get<{ suggestions: { title: string; body: string; cta: string }[] }>(`/ai/suggestion?radiusKm=${radius}`).then((r) => setSuggestions(r.suggestions));
    api
      .post<{ persona: Persona }>('/ai/persona', { storeId: currentStoreId, radiusKm: radius })
      .then((r) => setPersona(r.persona))
      .catch(() => setPersona(null));
    api
      .get<{ stats: { km: RadiusKm; reachableCustomers: number; hotSpots: number }[] }>(`/stores/${currentStoreId}/radius?km=3,5,8,10`)
      .then((r) => setRadiusStats(r.stats));
  }, [currentStoreId, radius]);

  // 一键拓客:从真实 POI 批量造 24 个可触达客户
  const oneClickAcquire = async () => {
    if (!currentStoreId) return;
    setSeeding(true);
    try {
      const r = await api.post<{ created: number }>('/leads/seed', {
        storeId: currentStoreId,
        radiusKm: radius,
        count: 24,
      });
      const o = await api.get<{ overview: Overview }>(`/dashboard/overview?storeId=${currentStoreId}&range=7d`);
      setOverview(o.overview);
      if (r.created > 0) {
        toast.success(
          `拓客成功 · ${radius} km 圈层 +${r.created} 客户`,
          `已基于真实 POI 生成,跳转至触达中心可一键群发`,
        );
        setTimeout(() => nav('/touch'), 600);
      } else {
        toast.info('该圈层暂无可拓展客户', '请尝试更大半径或更换门店');
      }
    } catch (e) {
      toast.error('拓客失败', '请稍后重试或联系客服');
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
        <div>
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
          </motion.div>
          <h1 className="mt-2 text-3xl font-display font-extrabold tracking-tight">
            {greeting},{currentStore?.name?.split(' · ').pop() || '店长'}
          </h1>
          <div className="mt-1 flex items-center gap-2 text-sm text-ink-400">
            <MapPin className="w-3.5 h-3.5 text-ember-500" />
            <span className="font-mono">{currentStore?.address}</span>
            <span className="text-ink-500">·</span>
            <span className="font-mono">{currentStore?.category}</span>
          </div>
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
              caption="基于天气 / 节假日 / 历史数据"
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
                const reach = km === 3 ? 4800 : km === 5 ? 9600 : km === 8 ? 18400 : 32400;
                const added = Math.round(reach * 0.08);
                const visited = Math.round(added * 0.32);
                const won = Math.round(visited * 0.18);
                const conv = (won / reach) * 100;
                return (
                  <motion.div
                    key={km}
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.25 + idx * 0.05 }}
                    className={`relative rounded-xl border p-3 transition ${
                      km === radius ? 'border-ember-500/50 bg-ember-500/[0.06] shadow-glow' : 'border-white/5 bg-ink-800/30'
                    }`}
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
                { time: '14:00', t: 'BD 小赵 跟进 3 公里王女士', tag: '跟进', tone: 'cyber' },
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
                    className={`pill text-[9px] ${
                      s.tone === 'ember'
                        ? 'bg-ember-500/10 text-ember-200 border border-ember-500/20'
                        : 'bg-cyber-300/10 text-cyber-200 border border-cyber-300/20'
                    }`}
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
              caption="LIVE FEED"
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
                  <span className={`w-1.5 h-1.5 rounded-full ${
                    row.c === 'ember' ? 'bg-ember-500' :
                    row.c === 'cyber' ? 'bg-cyber-300' : 'bg-signal-gold'
                  }`} />
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
