import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Sparkles,
  Search,
  Filter,
  X,
  ChevronRight,
  ArrowRight,
  Layers,
  MapPin,
  Building2,
  School,
  Home,
  Train,
  Trees,
  BarChart3,
  Flame,
  Compass,
} from 'lucide-react';
import RadiusSelector from '@/components/RadiusSelector';
import ConcentricMap from '@/components/ConcentricMap';
import SectionHeader from '@/components/SectionHeader';
import { toast } from '@/components/Toast';
import { useGlobal } from '@/store/useGlobal';
import { api } from '@/lib/api';
import type { POI, RadiusKm, RadiusStats } from '@/lib/types';
import { useNavigate } from 'react-router-dom';
import { cn } from '@/lib/utils';

const CAT_META: Record<POI['category'], { label: string; icon: typeof MapPin; tone: string }> = {
  office:    { label: '写字楼', icon: Building2, tone: 'text-ember-300 bg-ember-500/10 border-ember-500/30' },
  mall:      { label: '商场',   icon: Layers,    tone: 'text-signal-gold bg-signal-gold/10 border-signal-gold/30' },
  school:    { label: '学校',   icon: School,    tone: 'text-cyber-200 bg-cyber-300/10 border-cyber-300/30' },
  residence: { label: '住宅',   icon: Home,      tone: 'text-signal-violet bg-signal-violet/10 border-signal-violet/30' },
  subway:    { label: '地铁',   icon: Train,     tone: 'text-cyan-200 bg-cyan-400/10 border-cyan-400/30' },
  park:      { label: '公园',   icon: Trees,     tone: 'text-green-200 bg-green-400/10 border-green-400/30' },
};

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
        toast.success(`加载 ${r.pois.length} 个 POI · ${r.stats.length} 个圈层`, '基于真实北京建筑数据');
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
      <motion.header
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        className="px-8 pt-6 pb-4 flex items-end justify-between flex-wrap gap-3 border-b border-white/5"
      >
        <div>
          <div className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-widest text-cyber-200">
            <Compass className="w-3.5 h-3.5" />
            Map · 同心圆圈选工作台
            <span className="text-ink-500">·</span>
            <span className="inline-flex items-center gap-1 text-ember-300">
              <span className="w-1.5 h-1.5 rounded-full bg-ember-500 animate-pulse" />
              {pois.length} POI
            </span>
          </div>
          <h1 className="mt-1.5 text-2xl font-display font-bold">3 - 5 - 8 - 10 公里圈层 · 实时渲染</h1>
          <div className="mt-0.5 text-xs text-ink-400 font-mono flex items-center gap-1.5">
            <MapPin className="w-3 h-3 text-ember-500" />
            {currentStore?.name} · {currentStore?.address}
          </div>
        </div>
        <RadiusSelector value={radius} onChange={setRadius} size="md" />
      </motion.header>

      {/* 主体 */}
      <div className="flex-1 grid grid-cols-12 gap-4 px-8 py-5 min-h-0">
        {/* 左:筛选 + 圈层数据 */}
        <motion.aside
          initial={{ opacity: 0, x: -10 }}
          animate={{ opacity: 1, x: 0 }}
          className="col-span-3 panel p-4 space-y-4 overflow-y-auto"
        >
          <div>
            <SectionHeader
              index="01"
              icon={BarChart3}
              title="圈层数据"
              caption="3-5-8-10 km"
            />
            <div className="space-y-2 mt-2">
              {stats.map((s, i) => {
                const active = s.km === radius;
                return (
                  <motion.button
                    key={s.km}
                    initial={{ opacity: 0, x: -6 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: i * 0.05 }}
                    whileHover={{ x: 3 }}
                    onClick={() => setRadius(s.km)}
                    className={cn(
                      'w-full text-left rounded-xl border p-3 transition relative overflow-hidden',
                      active
                        ? 'border-ember-500/60 bg-gradient-to-br from-ember-500/[0.08] to-transparent shadow-glow'
                        : 'border-white/5 bg-ink-800/40 hover:border-white/15',
                    )}
                  >
                    {active && (
                      <div className="absolute -top-6 -right-6 w-16 h-16 rounded-full bg-ember-500/30 blur-xl" />
                    )}
                    <div className="relative flex items-center justify-between">
                      <span className="font-mono font-bold text-lg">
                        {s.km} <span className="text-xs text-ink-400">km</span>
                      </span>
                      <span className="text-[10px] font-mono text-cyber-200">高潜 {s.avgScore}</span>
                    </div>
                    <div className="relative mt-1.5 flex items-center justify-between text-[11px] font-mono text-ink-300">
                      <span>人口 {s.population.toLocaleString()}</span>
                      <span>竞品 {s.competitorCount}</span>
                    </div>
                    <div className="relative mt-1.5 h-1 rounded-full bg-white/5 overflow-hidden">
                      <motion.div
                        initial={{ width: 0 }}
                        animate={{ width: `${s.avgScore}%` }}
                        transition={{ duration: 1, ease: [0.16, 1, 0.3, 1] }}
                        className="h-full bg-gradient-to-r from-ember-500 to-cyber-300"
                      />
                    </div>
                  </motion.button>
                );
              })}
            </div>
          </div>

          <div>
            <SectionHeader
              index="02"
              icon={Filter}
              title="POI 筛选"
              caption="6 大类别"
            />
            <div className="flex flex-wrap gap-1.5 mt-2">
              <motion.button
                whileHover={{ y: -1 }}
                onClick={() => setFilter('all')}
                className={cn(
                  'inline-flex items-center gap-1 px-2.5 py-1 text-[11px] rounded-full font-mono border transition',
                  filter === 'all'
                    ? 'bg-white/10 text-white border-white/20 shadow-glow'
                    : 'border-white/10 text-ink-300 hover:border-white/20',
                )}
              >
                全部
                <span className="text-ink-500">{pois.length}</span>
              </motion.button>
              {(['office', 'mall', 'school', 'residence', 'subway', 'park'] as const).map((c) => {
                const Icon = CAT_META[c].icon;
                const count = pois.filter((p) => p.category === c).length;
                const active = filter === c;
                return (
                  <motion.button
                    key={c}
                    whileHover={{ y: -1 }}
                    onClick={() => setFilter(c)}
                    className={cn(
                      'inline-flex items-center gap-1 px-2.5 py-1 text-[11px] rounded-full font-mono border transition',
                      active
                        ? `${CAT_META[c].tone} shadow-glow`
                        : 'border-white/10 text-ink-300 hover:border-white/20',
                    )}
                  >
                    <Icon className="w-3 h-3" />
                    {CAT_META[c].label}
                    <span className="text-ink-500">{count}</span>
                  </motion.button>
                );
              })}
            </div>
          </div>

          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="rounded-xl border border-cyber-300/20 bg-cyber-300/[0.04] p-3 text-[11px] text-ink-200 leading-relaxed relative overflow-hidden"
          >
            <div className="absolute -top-4 -right-4 w-16 h-16 rounded-full bg-cyber-300/30 blur-xl" />
            <div className="relative flex items-center gap-1.5 text-cyber-200 font-mono mb-1">
              <Flame className="w-3 h-3" /> AI 标注
            </div>
            <p className="relative">
              {currentStat ? `${radius} km 内共 ${currentStat.hotSpots} 个高潜 POI,建议优先攻克评分 ≥ 70 的点位。` : '正在分析…'}
            </p>
          </motion.div>
        </motion.aside>

        {/* 中:地图 */}
        <motion.main
          initial={{ opacity: 0, scale: 0.98 }}
          animate={{ opacity: 1, scale: 1 }}
          className="col-span-6 min-h-0 relative"
        >
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
          <motion.button
            whileHover={{ scale: 1.03 }}
            onClick={() => setDrawerOpen((v) => !v)}
            className="absolute top-4 right-4 panel px-3 py-2 text-xs font-mono text-ink-200 hover:text-white flex items-center gap-1.5"
          >
            {drawerOpen ? '收起清单' : '展开清单'}
            <ChevronRight className={`w-3.5 h-3.5 transition-transform ${drawerOpen ? 'rotate-180' : ''}`} />
          </motion.button>
        </motion.main>

        {/* 右:高潜清单 */}
        <AnimatePresence>
          {drawerOpen && (
            <motion.aside
              key="drawer"
              initial={{ x: 40, opacity: 0 }}
              animate={{ x: 0, opacity: 1 }}
              exit={{ x: 40, opacity: 0 }}
              transition={{ duration: 0.25 }}
              className="col-span-3 panel p-4 flex flex-col min-h-0 relative overflow-hidden"
            >
              <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-ember-500/15 blur-3xl" />
              <div className="relative flex items-center justify-between mb-3">
                <div>
                  <SectionHeader
                    index="03"
                    icon={MapPin}
                    title={`${radius} km · 高潜客群`}
                    caption={`按高潜指数排序 · ${top.length} 个`}
                  />
                </div>
                <button onClick={() => setDrawerOpen(false)} className="text-ink-400 hover:text-white -mt-8">
                  <X className="w-4 h-4" />
                </button>
              </div>

              <div className="flex items-center gap-2 mb-3">
                <div className="flex-1 flex items-center gap-2 bg-ink-800/60 border border-white/5 rounded-lg px-3 py-1.5 focus-within:border-ember-500/40 transition">
                  <Search className="w-3.5 h-3.5 text-ink-400" />
                  <input
                    placeholder="搜索 POI / 商圈"
                    className="flex-1 bg-transparent text-xs focus:outline-none placeholder:text-ink-500"
                  />
                </div>
                <button className="p-1.5 rounded-lg border border-white/10 text-ink-300 hover:border-white/20 transition">
                  <Filter className="w-3.5 h-3.5" />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto space-y-2 pr-1">
                {top.map((p, i) => {
                  const Icon = CAT_META[p.category].icon;
                  return (
                    <motion.div
                      key={p.id}
                      initial={{ opacity: 0, y: 4 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: i * 0.04 }}
                      whileHover={{ x: 2 }}
                      onMouseEnter={() => setHovered(p.id)}
                      onMouseLeave={() => setHovered(null)}
                      className={cn(
                        'group rounded-xl border p-3 transition relative overflow-hidden',
                        hovered === p.id
                          ? 'border-ember-500/50 bg-ember-500/[0.04] shadow-glow'
                          : 'border-white/5 bg-ink-800/30 hover:border-ember-500/30',
                      )}
                    >
                      {hovered === p.id && (
                        <div className="absolute -top-4 -right-4 w-12 h-12 rounded-full bg-ember-500/30 blur-xl" />
                      )}
                      <div className="relative flex items-start justify-between">
                        <div className="min-w-0 flex items-start gap-2">
                          <span className={cn('w-7 h-7 rounded-lg grid place-items-center border shrink-0', CAT_META[p.category].tone)}>
                            <Icon className="w-3.5 h-3.5" />
                          </span>
                          <div className="min-w-0">
                            <div className="text-sm font-medium text-white truncate">{p.name}</div>
                            <div className="mt-0.5 text-[10px] font-mono text-ink-400">
                              {CAT_META[p.category].label} · 距门店 {p.radiusKm}km
                            </div>
                          </div>
                        </div>
                        <div className="text-right shrink-0">
                          <div className="text-[10px] font-mono text-ink-400">高潜</div>
                          <div className="font-mono text-lg font-bold text-ember-300 leading-none">
                            {p.hotScore}
                          </div>
                        </div>
                      </div>
                      <div className="relative mt-2 flex items-center justify-between">
                        <span
                          className={cn(
                            'pill text-[9px]',
                            p.hotScore >= 80
                              ? 'bg-ember-500/15 text-ember-200 border border-ember-500/30'
                              : p.hotScore >= 60
                              ? 'bg-cyber-300/10 text-cyber-200 border border-cyber-300/20'
                              : 'bg-white/5 text-ink-300 border border-white/10',
                          )}
                        >
                          {p.hotScore >= 80 ? 'S 级 · 必攻' : p.hotScore >= 60 ? 'A 级 · 推荐' : 'B 级 · 观察'}
                        </span>
                        <button
                          onClick={() => nav('/persona')}
                          className="text-[10px] font-mono text-cyber-200 hover:text-cyber-100 inline-flex items-center gap-0.5 group-hover:translate-x-0.5 transition"
                        >
                          生成话术
                          <ArrowRight className="w-3 h-3" />
                        </button>
                      </div>
                    </motion.div>
                  );
                })}
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
