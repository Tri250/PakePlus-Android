import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Sparkles, MapPin, TrendingUp, Users2, ArrowUpRight, Calendar, Sun, CloudRain } from 'lucide-react';
import RadiusSelector from '@/components/RadiusSelector';
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

  const currentStore = stores.find((s) => s.id === currentStoreId);

  useEffect(() => {
    if (!currentStoreId) return;
    api.get<{ overview: Overview }>(`/dashboard/overview?storeId=${currentStoreId}&range=7d`).then((r) => setOverview(r.overview));
    api.get<{ suggestions: { title: string; body: string; cta: string }[] }>(`/ai/suggestion?radiusKm=${radius}`).then((r) => setSuggestions(r.suggestions));
    api
      .post<{ persona: Persona }>('/ai/persona', { storeId: currentStoreId, radiusKm: radius })
      .then((r) => setPersona(r.persona))
      .catch(() => setPersona(null));
  }, [currentStoreId, radius]);

  const radiusPopulation: Record<RadiusKm, string> = {
    3: '12.4 万',
    5: '31.2 万',
    8: '68.9 万',
    10: '124.5 万',
  };

  return (
    <div className="p-8 max-w-[1400px] mx-auto">
      {/* 顶部 */}
      <header className="flex items-end justify-between flex-wrap gap-4 mb-6">
        <div>
          <div className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-widest text-cyber-200">
            <Sparkles className="w-3.5 h-3.5" />
            Cockpit · 今日建议已就绪
          </div>
          <h1 className="mt-2 text-3xl font-display font-extrabold tracking-tight">
            欢迎回来,{currentStore?.name?.split(' · ').pop() || '店长'}
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

      {/* 顶部数据卡 */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-6">
        {[
          { label: '圈层覆盖人口', value: radiusPopulation[radius], icon: Users2, tone: 'cyber' },
          { label: '本周曝光', value: overview ? overview.reach.toLocaleString() : '—', icon: TrendingUp, tone: 'ember' },
          { label: '加微数', value: overview ? overview.addedWechat.toString() : '—', icon: Users2, tone: 'cyber' },
          { label: '到店数', value: overview ? overview.visited.toString() : '—', icon: MapPin, tone: 'ember' },
        ].map((c, i) => (
          <motion.div
            key={c.label}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.05 }}
            className="panel p-5"
          >
            <div className="flex items-center justify-between mb-3">
              <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400">
                {c.label}
              </div>
              <c.icon
                className={`w-4 h-4 ${c.tone === 'ember' ? 'text-ember-500' : 'text-cyber-300'}`}
              />
            </div>
            <div className="metric-num">{c.value}</div>
            <div className="mt-1 text-[11px] font-mono text-cyber-200 inline-flex items-center gap-1">
              <ArrowUpRight className="w-3 h-3" /> 较上周 +{8 + i * 2}.{i * 3}%
            </div>
          </motion.div>
        ))}
      </div>

      {/* 主体两列 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 今日 AI 建议 */}
        <div className="lg:col-span-2 space-y-4">
          <div className="panel p-6 relative overflow-hidden">
            <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-ember-500/15 blur-2xl" />
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-cyber-300 to-cyber-500 flex items-center justify-center">
                  <Sparkles className="w-4 h-4 text-ink-950" />
                </div>
                <div>
                  <div className="text-sm font-semibold text-white">AI 今日建议</div>
                  <div className="text-[10px] font-mono text-ink-400">基于天气 / 节假日 / 历史数据</div>
                </div>
              </div>
              <div className="flex items-center gap-1.5 text-[10px] font-mono text-ink-400">
                <Sun className="w-3 h-3 text-ember-300" />
                多云 22°
                <span className="text-ink-500">·</span>
                <CloudRain className="w-3 h-3 text-cyber-300" />
                明日小雨
              </div>
            </div>

            <div className="space-y-3">
              {suggestions.map((s, i) => (
                <motion.div
                  key={i}
                  initial={{ opacity: 0, x: -8 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: i * 0.08 }}
                  className="group rounded-xl border border-white/5 bg-ink-800/40 p-4 flex items-start gap-4 hover:border-ember-500/30 transition"
                >
                  <div className="w-8 h-8 rounded-lg bg-ember-500/10 text-ember-300 flex items-center justify-center font-mono text-xs shrink-0">
                    0{i + 1}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-semibold text-white">{s.title}</div>
                    <div className="text-xs text-ink-300 mt-1 leading-relaxed">{s.body}</div>
                  </div>
                  <button
                    onClick={() => nav('/campaign')}
                    className="shrink-0 text-xs font-medium text-ember-300 hover:text-ember-200 inline-flex items-center gap-1"
                  >
                    {s.cta}
                    <ArrowUpRight className="w-3.5 h-3.5" />
                  </button>
                </motion.div>
              ))}
            </div>
          </div>

          {/* 圈层对比图 */}
          <div className="panel p-6">
            <div className="flex items-center justify-between mb-4">
              <div>
                <div className="text-sm font-semibold text-white">3 / 5 / 8 / 10 km · 圈层漏斗</div>
                <div className="text-[10px] font-mono text-ink-400">从曝光到加微到成交的转化漏斗</div>
              </div>
              <button
                onClick={() => nav('/dashboard')}
                className="text-[11px] font-mono text-cyber-200 hover:text-cyber-100"
              >
                查看完整看板 →
              </button>
            </div>
            <div className="grid grid-cols-4 gap-3">
              {([3, 5, 8, 10] as RadiusKm[]).map((km) => {
                const reach = km === 3 ? 4800 : km === 5 ? 9600 : km === 8 ? 18400 : 32400;
                const added = Math.round(reach * 0.08);
                const visited = Math.round(added * 0.32);
                const won = Math.round(visited * 0.18);
                return (
                  <div
                    key={km}
                    className={`relative rounded-xl border p-3 ${km === radius ? 'border-ember-500/50 bg-ember-500/[0.04]' : 'border-white/5 bg-ink-800/30'}`}
                  >
                    <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400">
                      {km} km
                    </div>
                    <div className="mt-2 space-y-1.5">
                      {[
                        { v: reach, label: '曝光', color: 'bg-ember-500/30' },
                        { v: added, label: '加微', color: 'bg-ember-500/60' },
                        { v: visited, label: '到店', color: 'bg-cyber-300/60' },
                        { v: won, label: '成交', color: 'bg-cyber-300' },
                      ].map((s) => (
                        <div key={s.label} className="flex items-center gap-1.5">
                          <div className={`h-1.5 rounded-full ${s.color}`} style={{ width: `${Math.max(8, s.v / reach * 100)}%` }} />
                          <span className="text-[10px] font-mono text-ink-300 w-10 text-right">{s.v}</span>
                        </div>
                      ))}
                    </div>
                    <div className="mt-2 text-[10px] font-mono text-ink-500">曝光 / 加微 / 到店 / 成交</div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* 右侧:AI 画像速览 + 日程 */}
        <div className="space-y-4">
          <div className="panel p-5">
            <div className="flex items-center justify-between mb-3">
              <div className="text-sm font-semibold text-white">
                {radius} km · 画像速览
              </div>
              <button
                onClick={() => nav('/persona')}
                className="text-[11px] font-mono text-cyber-200 hover:text-cyber-100"
              >
                详情 →
              </button>
            </div>
            {persona ? (
              <>
                <p className="text-xs text-ink-300 leading-relaxed mb-3">{persona.summary}</p>
                <div className="flex flex-wrap gap-1.5">
                  {persona.keywords.map((k) => (
                    <span key={k} className="pill bg-ember-500/10 text-ember-200 border border-ember-500/20">
                      {k}
                    </span>
                  ))}
                </div>
                <div className="mt-4 grid grid-cols-2 gap-2">
                  {persona.radar.slice(0, 4).map((r) => (
                    <div key={r.dim} className="rounded-lg border border-white/5 p-2.5">
                      <div className="text-[10px] font-mono text-ink-400">{r.dim}</div>
                      <div className="mt-1 flex items-baseline gap-1.5">
                        <span className="font-mono text-lg font-bold text-white">{r.value}</span>
                        <span className="text-[10px] text-cyber-200">/ 100</span>
                      </div>
                      <div className="mt-1 h-1 rounded-full bg-white/5 overflow-hidden">
                        <div
                          className="h-full bg-gradient-to-r from-ember-500 to-cyber-300"
                          style={{ width: `${r.value}%` }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </>
            ) : (
              <div className="text-xs text-ink-400">正在分析 {radius} km 圈层…</div>
            )}
          </div>

          <div className="panel p-5">
            <div className="flex items-center justify-between mb-3">
              <div className="text-sm font-semibold text-white">今日日程</div>
              <Calendar className="w-4 h-4 text-cyber-300" />
            </div>
            <ul className="space-y-2.5 text-xs">
              {[
                { time: '11:30', t: '推送"午间手冲 9.9 元"朋友圈广告', tag: '营销', tone: 'ember' },
                { time: '14:00', t: 'BD 小赵 跟进 3 公里王女士', tag: '跟进', tone: 'cyber' },
                { time: '17:30', t: '生成 5km 圈层卡券并上架美团', tag: '内容', tone: 'ember' },
                { time: '21:00', t: '复盘今日 8km 加微成本', tag: '数据', tone: 'cyber' },
              ].map((s, i) => (
                <li key={i} className="flex items-start gap-3">
                  <span className="font-mono text-[10px] text-ink-400 pt-0.5 w-10 shrink-0">{s.time}</span>
                  <span className="flex-1 text-ink-200">{s.t}</span>
                  <span
                    className={`pill ${
                      s.tone === 'ember'
                        ? 'bg-ember-500/10 text-ember-200 border border-ember-500/20'
                        : 'bg-cyber-300/10 text-cyber-200 border border-cyber-300/20'
                    }`}
                  >
                    {s.tag}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
