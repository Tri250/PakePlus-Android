import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Sparkles, Search, Filter, X, ChevronRight, ArrowRight, Layers } from 'lucide-react';
import RadiusSelector from '@/components/RadiusSelector';
import ConcentricMap from '@/components/ConcentricMap';
import { useGlobal } from '@/store/useGlobal';
import { api } from '@/lib/api';
import type { POI, RadiusKm, RadiusStats } from '@/lib/types';
import { useNavigate } from 'react-router-dom';

export default function MapWorkspace() {
  const { radius, setRadius, currentStoreId, stores } = useGlobal();
  const nav = useNavigate();
  const [stats, setStats] = useState<RadiusStats[]>([]);
  const [pois, setPois] = useState<POI[]>([]);
  const [center, setCenter] = useState<{ lng: number; lat: number } | null>(null);
  const [filter, setFilter] = useState<POI['category'] | 'all'>('all');
  const [hovered, setHovered] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(true);

  const currentStore = stores.find((s) => s.id === currentStoreId);

  useEffect(() => {
    if (!currentStoreId) return;
    api
      .get<{ stats: RadiusStats[]; pois: POI[] }>(`/stores/${currentStoreId}/radius?km=3,5,8,10`)
      .then((r) => {
        setStats(r.stats);
        setPois(r.pois);
      });
  }, [currentStoreId]);

  useEffect(() => {
    if (currentStore) setCenter({ lng: currentStore.lng, lat: currentStore.lat });
  }, [currentStore]);

  const filtered = pois.filter((p) => (filter === 'all' ? true : p.category === filter) && p.radiusKm <= radius);
  const currentStat = stats.find((s) => s.km === radius);
  const top = [...filtered].sort((a, b) => b.hotScore - a.hotScore).slice(0, 12);

  return (
    <div className="h-screen flex flex-col">
      {/* 顶部 */}
      <header className="px-8 pt-6 pb-4 flex items-end justify-between flex-wrap gap-3 border-b border-white/5">
        <div>
          <div className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-widest text-cyber-200">
            <Layers className="w-3.5 h-3.5" />
            Map · 同心圆圈选工作台
          </div>
          <h1 className="mt-1.5 text-2xl font-display font-bold">3 - 5 - 8 - 10 公里圈层 · 实时渲染</h1>
          <div className="mt-0.5 text-xs text-ink-400 font-mono">
            {currentStore?.name} · {currentStore?.address}
          </div>
        </div>
        <RadiusSelector value={radius} onChange={setRadius} size="md" />
      </header>

      {/* 主体 */}
      <div className="flex-1 grid grid-cols-12 gap-4 px-8 py-5 min-h-0">
        {/* 左:筛选 + 圈层数据 */}
        <aside className="col-span-3 panel p-4 space-y-4 overflow-y-auto">
          <div>
            <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400 mb-2">圈层数据</div>
            {stats.map((s) => (
              <div
                key={s.km}
                onClick={() => setRadius(s.km)}
                className={`cursor-pointer rounded-xl border p-3 mb-2 transition ${
                  s.km === radius
                    ? 'border-ember-500/60 bg-ember-500/[0.05]'
                    : 'border-white/5 bg-ink-800/40 hover:border-white/10'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="font-mono font-bold text-lg">
                    {s.km} <span className="text-xs text-ink-400">km</span>
                  </span>
                  <span className="text-[10px] font-mono text-cyber-200">高潜 {s.avgScore}</span>
                </div>
                <div className="mt-1.5 flex items-center justify-between text-[11px] font-mono text-ink-300">
                  <span>人口 {s.population.toLocaleString()}</span>
                  <span>竞品 {s.competitorCount}</span>
                </div>
                <div className="mt-1.5 h-1 rounded-full bg-white/5 overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-ember-500 to-cyber-300"
                    style={{ width: `${s.avgScore}%` }}
                  />
                </div>
              </div>
            ))}
          </div>

          <div>
            <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400 mb-2">POI 筛选</div>
            <div className="flex flex-wrap gap-1.5">
              {(['all', 'office', 'mall', 'school', 'residence', 'subway', 'park'] as const).map((c) => (
                <button
                  key={c}
                  onClick={() => setFilter(c)}
                  className={`px-2.5 py-1 text-[11px] rounded-full font-mono transition ${
                    filter === c
                      ? 'bg-cyber-300 text-ink-950 shadow-cyber'
                      : 'border border-white/10 text-ink-300 hover:border-white/20'
                  }`}
                >
                  {c === 'all' ? '全部' : ({ office: '写字楼', mall: '商场', school: '学校', residence: '住宅', subway: '地铁', park: '公园' }[c] as string)}
                </button>
              ))}
            </div>
          </div>

          <div className="rounded-xl border border-cyber-300/20 bg-cyber-300/[0.04] p-3 text-[11px] text-ink-200 leading-relaxed">
            <div className="flex items-center gap-1.5 text-cyber-200 font-mono mb-1">
              <Sparkles className="w-3 h-3" /> AI 标注
            </div            >
            <span className="block">
              {currentStat ? `${radius} km 内共 ${currentStat.hotSpots} 个高潜 POI,建议优先攻克评分 ≥ 70 的点位。` : '正在分析…'}
            </span>
          </div>
        </aside>

        {/* 中:地图 */}
        <main className="col-span-6 min-h-0 relative">
          {center ? (
            <ConcentricMap
              center={center}
              pois={filtered}
              stats={stats}
              activeRadius={radius}
              hoveredPoiId={hovered}
              onHoverPoi={setHovered}
              className="h-full"
            />
          ) : (
            <div className="h-full grid place-items-center text-ink-400">加载中…</div>
          )}

          {/* 右下角抽屉开关 */}
          <button
            onClick={() => setDrawerOpen((v) => !v)}
            className="absolute top-4 right-4 panel px-3 py-2 text-xs font-mono text-ink-200 hover:text-white flex items-center gap-1.5"
          >
            {drawerOpen ? '收起清单' : '展开清单'}
            <ChevronRight className={`w-3.5 h-3.5 transition-transform ${drawerOpen ? 'rotate-180' : ''}`} />
          </button>
        </main>

        {/* 右:高潜清单 */}
        <AnimatePresence>
          {drawerOpen && (
            <motion.aside
              key="drawer"
              initial={{ x: 40, opacity: 0 }}
              animate={{ x: 0, opacity: 1 }}
              exit={{ x: 40, opacity: 0 }}
              transition={{ duration: 0.25 }}
              className="col-span-3 panel p-4 flex flex-col min-h-0"
            >
              <div className="flex items-center justify-between mb-3">
                <div>
                  <div className="text-sm font-semibold text-white">{radius} km · 高潜客群</div>
                  <div className="text-[10px] font-mono text-ink-400">按高潜指数排序 · {top.length} 个</div>
                </div>
                <button onClick={() => setDrawerOpen(false)} className="text-ink-400 hover:text-white">
                  <X className="w-4 h-4" />
                </button>
              </div>

              <div className="flex items-center gap-2 mb-3">
                <div className="flex-1 flex items-center gap-2 bg-ink-800/60 border border-white/5 rounded-lg px-3 py-1.5">
                  <Search className="w-3.5 h-3.5 text-ink-400" />
                  <input
                    placeholder="搜索 POI / 商圈"
                    className="flex-1 bg-transparent text-xs focus:outline-none placeholder:text-ink-500"
                  />
                </div>
                <button className="p-1.5 rounded-lg border border-white/10 text-ink-300">
                  <Filter className="w-3.5 h-3.5" />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto space-y-2 pr-1">
                {top.map((p) => (
                  <div
                    key={p.id}
                    onMouseEnter={() => setHovered(p.id)}
                    onMouseLeave={() => setHovered(null)}
                    className="rounded-xl border border-white/5 bg-ink-800/30 p-3 hover:border-ember-500/30 transition group"
                  >
                    <div className="flex items-start justify-between">
                      <div className="min-w-0">
                        <div className="text-sm font-medium text-white truncate">{p.name}</div>
                        <div className="mt-0.5 text-[10px] font-mono text-ink-400">
                          {({ office: '写字楼', mall: '商场', school: '学校', residence: '住宅', subway: '地铁', park: '公园' }[p.category])} · 距门店 {p.radiusKm}km
                        </div>
                      </div>
                      <div className="text-right shrink-0">
                        <div className="text-[10px] font-mono text-ink-400">高潜</div>
                        <div className="font-mono text-lg font-bold text-ember-300 leading-none">
                          {p.hotScore}
                        </div>
                      </div>
                    </div>
                    <div className="mt-2 flex items-center justify-between">
                      <span
                        className={`pill ${
                          p.hotScore >= 80
                            ? 'bg-ember-500/15 text-ember-200 border border-ember-500/30'
                            : p.hotScore >= 60
                            ? 'bg-cyber-300/10 text-cyber-200 border border-cyber-300/20'
                            : 'bg-white/5 text-ink-300 border border-white/10'
                        }`}
                      >
                        {p.hotScore >= 80 ? 'S 级 · 必攻' : p.hotScore >= 60 ? 'A 级 · 推荐' : 'B 级 · 观察'}
                      </span>
                      <button
                        onClick={() => nav('/persona')}
                        className="text-[10px] font-mono text-cyber-200 hover:text-cyber-100 inline-flex items-center gap-0.5"
                      >
                        生成话术
                        <ArrowRight className="w-3 h-3" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>

              <div className="mt-3 pt-3 border-t border-white/5 flex items-center justify-between">
                <span className="text-[10px] font-mono text-ink-400">AI 已为这批点位生成 3 套话术</span>
                <button
                  onClick={() => nav('/persona')}
                  className="btn-primary !px-3 !py-1.5 !text-xs"
                >
                  <Sparkles className="w-3.5 h-3.5" />
                  立即生成
                </button>
              </div>
            </motion.aside>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
