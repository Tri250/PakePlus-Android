import { useAppStore } from '../store/appStore';
import { leadStatusMap, type Lead, type Customer } from '../data/mockData';
import {
  Sliders, Home, Phone, Navigation, Mic, ChevronLeft,
  UserPlus, MessageSquare, Star, MapPin, Database, RefreshCw, HardDrive,
} from 'lucide-react';
import { useState, useMemo } from 'react';
import LiveIndicator from '../components/LiveIndicator';
import DataBoundary from '../components/DataBoundary';
import POISourcePanel from '../components/POISourcePanel';
import MapTaskPanel from '../components/MapTaskPanel';
import { useCustomers, useLeads } from '../hooks/useRealTimeData';
import { usePOI } from '../hooks/usePOI';
import type { CustomerLead, POICategory } from '../services/poiCollector';

/** 深圳南山科技园中心点（项目默认采集点） */
const DEFAULT_CENTER = { lat: 22.5400, lng: 113.9436 };

const CATEGORY_LABEL: Record<POICategory, { label: string; color: string; icon: string }> = {
  office: { label: '写字楼', color: '#3b82f6', icon: '🏢' },
  residential: { label: '住宅', color: '#10b981', icon: '🏘️' },
  school: { label: '学校', color: '#f59e0b', icon: '🎓' },
  mall: { label: '商场', color: '#ec4899', icon: '🛍️' },
  hospital: { label: '医院', color: '#ef4444', icon: '🏥' },
  hotel: { label: '酒店', color: '#8b5cf6', icon: '🏨' },
  operator: { label: '运营商', color: '#06b6d4', icon: '📡' },
  digital_shop: { label: '数码店', color: '#f97316', icon: '📱' },
  restaurant: { label: '餐饮', color: '#84cc16', icon: '🍴' },
  transport: { label: '交通', color: '#6366f1', icon: '🚇' },
};

const PROVIDER_LABEL: Record<string, { name: string; color: string }> = {
  amap: { name: '高德', color: '#009650' },
  baidu: { name: '百度', color: '#3b82f6' },
  tencent: { name: '腾讯', color: '#00b4b4' },
  synthetic: { name: '合成', color: '#f59e0b' },
};

export default function RadarPage() {
  const setSelectedCustomer = useAppStore((s) => s.setSelectedCustomer);
  const setShowRadar = useAppStore((s) => s.setShowRadar);
  const showToast = useAppStore((s) => s.showToast);
  const [filterOpen, setFilterOpen] = useState(false);
  const [showSourcePanel, setShowSourcePanel] = useState(false);
  const [showTaskPanel, setShowTaskPanel] = useState(false);
  const [mode, setMode] = useState<'leads' | 'customers' | 'poi'>('leads');
  const [selectedDot, setSelectedDot] = useState<string | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<POICategory | 'all'>('all');

  // 实时数据
  const customersQ = useCustomers();
  const leadsQ = useLeads();
  const customers = customersQ.data || [];
  const leads = leadsQ.data || [];

  // POI 多源实时采集
  const poi = usePOI({
    center: DEFAULT_CENTER,
    rings: [200, 500, 1000, 3000, 5000],
    interval: 60_000,
  });

  // 客户线索
  const sortedLeads = useMemo(() => [...leads].sort((a, b) => b.intent - a.intent), [leads]);
  const topThree = sortedLeads.slice(0, 3);
  const customersSorted = [...customers].sort((a, b) => a.distance - b.distance);

  // POI 过滤
  const filteredPOI = useMemo(() => {
    if (selectedCategory === 'all') return poi.leads;
    return poi.leads.filter((l) => l.category === selectedCategory);
  }, [poi.leads, selectedCategory]);

  const dataPoints = mode === 'leads'
    ? leads.map((l, i) => ({
        id: l.id, label: l.name[0], color: leadStatusMap[l.status].color,
        intent: l.intent, pos: { x: 20 + i * 15, y: 30 + (i % 2) * 25 }, sub: `${l.intent}分`,
      }))
    : mode === 'customers'
    ? customers.map((c, i) => ({
        id: c.id, label: c.avatar, color: c.avatarColor,
        intent: c.intentScore, pos: c.position, sub: `${c.distance}m`,
      }))
    : filteredPOI.slice(0, 8).map((l, i) => {
        const angle = (i / Math.max(filteredPOI.length, 1)) * 2 * Math.PI;
        return {
          id: l.id,
          label: l.name.slice(0, 2),
          color: CATEGORY_LABEL[l.category].color,
          intent: l.intentScore,
          pos: { x: 50 + Math.cos(angle) * 32, y: 50 + Math.sin(angle) * 32 },
          sub: `${l.intentScore}分`,
        };
      });

  if (showSourcePanel) {
    return <POISourcePanel onClose={() => setShowSourcePanel(false)} />;
  }

  if (showTaskPanel) {
    return <MapTaskPanel onClose={() => setShowTaskPanel(false)} />;
  }

  return (
    <div className="absolute inset-0 z-40 bg-white flex flex-col animate-slideInRight">
      <div className="scroll-area">
        {/* 页面标题 + 返回 */}
        <div className="px-5 pt-1 pb-3 animate-fadeIn">
          <div className="flex items-center justify-between">
            <button
              onClick={() => setShowRadar(false)}
              className="w-9 h-9 rounded-full flex items-center justify-center -ml-2"
              style={{ background: 'var(--surface-2)' }}
              aria-label="返回"
            >
              <ChevronLeft className="w-5 h-5" />
            </button>
            <h1 className="text-[20px] font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
              客户线索
            </h1>
            <div className="flex items-center gap-2">
              <LiveIndicator
                fetchedAt={mode === 'poi' ? poi.fetchedAt : mode === 'leads' ? leadsQ.fetchedAt : customersQ.fetchedAt}
                source={mode === 'poi' ? poi.source : mode === 'leads' ? leadsQ.source : customersQ.source}
              />
              <button
                onClick={() => {
                  if (mode === 'poi') poi.refresh(true);
                  showToast('正在拉取最新数据…', '🔄');
                }}
                className="w-9 h-9 rounded-full flex items-center justify-center"
                style={{ background: 'var(--surface-2)' }}
                aria-label="刷新"
              >
                <RefreshCw className="w-4 h-4" />
              </button>
              <button
                onClick={() => setShowSourcePanel(true)}
                className="w-9 h-9 rounded-full flex items-center justify-center"
                style={{ background: 'var(--surface-2)' }}
                aria-label="数据源"
              >
                <Database className="w-4 h-4" style={{ color: 'var(--primary)' }} />
              </button>
              <button
                onClick={() => setShowTaskPanel(true)}
                className="w-9 h-9 rounded-full flex items-center justify-center"
                style={{ background: 'var(--surface-2)' }}
                aria-label="任务管理"
              >
                <HardDrive className="w-4 h-4" style={{ color: '#f59e0b' }} />
              </button>
            </div>
          </div>
          {/* 模式切换 */}
          <div className="flex gap-1 mt-3 p-1 rounded-full" style={{ background: 'var(--surface-2)' }}>
            {(['poi', 'leads', 'customers'] as const).map((m) => (
              <button
                key={m}
                onClick={() => setMode(m)}
                className="flex-1 h-7 rounded-full text-xs font-semibold transition-colors"
                style={{
                  background: mode === m ? '#fff' : 'transparent',
                  color: mode === m ? 'var(--primary)' : 'var(--text-secondary)',
                  boxShadow: mode === m ? '0 2px 4px rgba(0,0,0,0.06)' : 'none',
                }}
              >
                {m === 'poi' ? '🗺️ POI 线索' : m === 'leads' ? '🎯 线索' : '👥 客户'}
              </button>
            ))}
          </div>
          {/* POI 类别过滤 chip */}
          {mode === 'poi' && (
            <div className="flex gap-1.5 mt-2 overflow-x-auto scroll-area" style={{ scrollbarWidth: 'none' }}>
              <CategoryChip
                active={selectedCategory === 'all'}
                onClick={() => setSelectedCategory('all')}
                icon="✨"
                label="全部"
              />
              {(Object.keys(CATEGORY_LABEL) as POICategory[]).map((cat) => (
                <CategoryChip
                  key={cat}
                  active={selectedCategory === cat}
                  onClick={() => setSelectedCategory(cat)}
                  icon={CATEGORY_LABEL[cat].icon}
                  label={CATEGORY_LABEL[cat].label}
                  color={CATEGORY_LABEL[cat].color}
                />
              ))}
            </div>
          )}
        </div>

        {filterOpen && <FilterPanel onClose={() => setFilterOpen(false)} />}

        {/* 雷达大图 */}
        <div className="px-5 mb-4 animate-slideUp" style={{ animationDelay: '60ms' }}>
          <div
            className="relative w-full rounded-3xl overflow-hidden"
            style={{
              aspectRatio: '1 / 1',
              background:
                'linear-gradient(180deg, #dbeafe 0%, #bfdbfe 50%, #93c5fd 100%)',
              boxShadow: '0 8px 24px rgba(59,130,246,0.20)',
            }}
          >
            {/* 同心圆 */}
            {[1, 2, 3, 4].map((i) => (
              <div
                key={i}
                className="absolute top-1/2 left-1/2 rounded-full"
                style={{
                  width: `${i * 22}%`,
                  height: `${i * 22}%`,
                  transform: 'translate(-50%, -50%)',
                  border: '1px solid rgba(59,130,246,0.18)',
                  background:
                    i === 4
                      ? 'radial-gradient(circle, rgba(59,130,246,0.06) 0%, transparent 70%)'
                      : 'transparent',
                }}
              />
            ))}
            {/* 十字线 */}
            <div
              className="absolute top-1/2 left-1/2"
              style={{
                width: '100%',
                height: 1,
                background: 'rgba(59,130,246,0.15)',
                transform: 'translateY(-50%)',
              }}
            />
            <div
              className="absolute top-1/2 left-1/2"
              style={{
                width: 1,
                height: '100%',
                background: 'rgba(59,130,246,0.15)',
                transform: 'translateX(-50%)',
              }}
            />
            {/* 扫描线 */}
            <div
              className="absolute top-1/2 left-1/2 animate-radarScan"
              style={{
                width: 0,
                height: 0,
                transformOrigin: '0 0',
              }}
            >
              <div
                style={{
                  width: '40%',
                  height: 2,
                  background:
                    'linear-gradient(90deg, rgba(59,130,246,0.6) 0%, transparent 100%)',
                  transform: 'translateY(-1px)',
                }}
              />
            </div>
            {/* 中心点 */}
            <div
              className="radar-dot center"
              style={{ left: '50%', top: '50%' }}
            >
              <Navigation className="w-5 h-5" fill="#fff" />
            </div>
            {/* 客户/线索点 */}
            {dataPoints.map((p, i) => (
              <button
                key={p.id}
                className="radar-dot animate-pop"
                style={{
                  left: `${p.pos.x}%`,
                  top: `${p.pos.y}%`,
                  background: p.color,
                  animationDelay: `${200 + i * 80}ms`,
                  width: selectedDot === p.id ? 44 : 36,
                  height: selectedDot === p.id ? 44 : 36,
                  fontSize: selectedDot === p.id ? 15 : 13,
                }}
                onClick={() => setSelectedDot(selectedDot === p.id ? null : p.id)}
              >
                {p.label}
              </button>
            ))}
            {/* 提示条 */}
            <div
              className="absolute left-1/2 -translate-x-1/2 flex items-center gap-1.5 px-3 py-1.5 rounded-full"
              style={{
                bottom: 12,
                background: 'rgba(255,255,255,0.85)',
                backdropFilter: 'blur(8px)',
                boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
              }}
            >
              <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
              <span className="text-[11px] font-medium" style={{ color: 'var(--text-secondary)' }}>
                实时扫描中 · {dataPoints.length} 个{mode === 'leads' ? '线索' : '客户'}
              </span>
            </div>
          </div>
        </div>

        {/* 列表 */}
        <div className="px-5 pb-6 animate-slideUp" style={{ animationDelay: '120ms' }}>
          {mode === 'poi' ? (
            <POILeadList
              poi={poi}
              filtered={filteredPOI}
              onLeadClick={(l) => showToast(`${l.name}\n${l.suggestedScript}`, '🎯')}
            />
          ) : (
            <>
              <h2 className="text-base font-bold mb-3" style={{ color: 'var(--text-primary)' }}>
                高意向 {topThree.length} 位{mode === 'leads' ? '线索' : '客户'}
              </h2>
              <DataBoundary
                loading={(mode === 'leads' ? leadsQ.loading : customersQ.loading) && (mode === 'leads' ? leads.length === 0 : customers.length === 0)}
                error={mode === 'leads' ? leadsQ.error : customersQ.error}
                onRetry={() => (mode === 'leads' ? leadsQ.refresh() : customersQ.refresh())}
                loadingText="正在拉取最新线索…"
              >
                <div className="space-y-2.5">
                  {topThree.map((item, i) => {
                    if (mode === 'leads') {
                      const l = item as Lead;
                      return (
                        <LeadCard
                          key={l.id}
                          lead={l}
                          delay={i * 80}
                          onCall={() => showToast(`正在跟进 ${l.name}`, '📞')}
                          onConvert={() => showToast(`${l.name} 已转为正式客户`, '✓')}
                        />
                      );
                    } else {
                      const c = item as unknown as Customer;
                      return (
                        <CustomerRow
                          key={c.id}
                          customer={c}
                          delay={i * 80}
                          onClick={() => setSelectedCustomer(c.id)}
                          onCall={() => showToast(`正在拨打 ${c.phone}`, '📞')}
                        />
                      );
                    }
                  })}
                </div>
              </DataBoundary>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function CategoryChip({
  active,
  onClick,
  icon,
  label,
  color,
}: {
  active: boolean;
  onClick: () => void;
  icon: string;
  label: string;
  color?: string;
}) {
  return (
    <button
      onClick={onClick}
      className="h-7 px-3 rounded-full text-[11px] font-semibold flex items-center gap-1 whitespace-nowrap flex-shrink-0"
      style={{
        background: active ? (color || 'var(--primary)') : 'var(--surface-2)',
        color: active ? '#fff' : 'var(--text-primary)',
      }}
    >
      <span>{icon}</span>
      <span>{label}</span>
    </button>
  );
}

function POILeadList({
  poi,
  filtered,
  onLeadClick,
}: {
  poi: ReturnType<typeof usePOI>;
  filtered: CustomerLead[];
  onLeadClick: (l: CustomerLead) => void;
}) {
  return (
    <>
      {/* 距离环 + Provider 状态条 */}
      <div className="card p-3 mb-3">
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
            多源距离环扫描
          </h3>
          <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
            耗时 {poi.data?.durationMs || 0}ms
          </span>
        </div>
        <div className="grid grid-cols-5 gap-1.5">
          {poi.rings.map((ring) => {
            return (
              <div
                key={ring.meters}
                className="text-center p-1.5 rounded-lg"
                style={{ background: 'var(--surface-2)' }}
              >
                <p className="text-[10px] font-bold" style={{ color: 'var(--text-primary)' }}>
                  {ring.label}
                </p>
                <p className="text-base font-bold mt-0.5" style={{
                  color: ring.pois.length > 0 ? 'var(--primary)' : 'var(--text-muted)',
                }}>
                  {ring.pois.length}
                </p>
                <p className="text-[8px] mt-0.5 truncate" style={{ color: PROVIDER_LABEL[ring.provider]?.color }}>
                  {PROVIDER_LABEL[ring.provider]?.name}
                </p>
              </div>
            );
          })}
        </div>
        {/* 类别分布 */}
        {poi.data && Object.keys(poi.data.stats.byCategory).length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1">
            {Object.entries(poi.data.stats.byCategory).map(([cat, count]) => {
              const meta = CATEGORY_LABEL[cat as POICategory];
              if (!meta) return null;
              return (
                <span
                  key={cat}
                  className="text-[10px] chip flex items-center gap-0.5"
                  style={{ background: `${meta.color}15`, color: meta.color }}
                >
                  {meta.icon} {meta.label} {count}
                </span>
              );
            })}
          </div>
        )}
      </div>

      <DataBoundary
        loading={poi.loading && poi.leads.length === 0}
        error={poi.error ? new Error(poi.error) : null}
        onRetry={() => poi.refresh(true)}
        loadingText="正在拉取 POI 数据…"
      >
        {filtered.length === 0 ? (
          <div className="text-center py-8">
            <p className="text-xs" style={{ color: 'var(--text-muted)' }}>当前类别无 POI 数据</p>
          </div>
        ) : (
          <div className="space-y-2.5">
            {filtered.map((l, i) => (
              <POILeadCard key={l.id} lead={l} delay={i * 60} onClick={() => onLeadClick(l)} />
            ))}
          </div>
        )}
      </DataBoundary>
    </>
  );
}

function POILeadCard({
  lead,
  delay,
  onClick,
}: {
  lead: CustomerLead;
  delay: number;
  onClick: () => void;
}) {
  const cat = CATEGORY_LABEL[lead.category];
  const prov = PROVIDER_LABEL[lead.provider] || { name: lead.provider, color: '#94a3b8' };
  const isCompetitor = lead.kind === 'competitor';

  return (
    <div
      className="card p-3 animate-slideInRight cursor-pointer"
      style={{ animationDelay: `${delay}ms` }}
      onClick={onClick}
    >
      <div className="flex items-start gap-2.5">
        <div
          className="w-10 h-10 rounded-xl flex items-center justify-center text-lg flex-shrink-0"
          style={{ background: `${cat.color}15` }}
        >
          {cat.icon}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-[14px] font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lead.name}
            </span>
            <span
              className="chip text-[9px]"
              style={{ background: `${cat.color}15`, color: cat.color }}
            >
              {cat.label}
            </span>
            {isCompetitor && (
              <span
                className="chip text-[9px]"
                style={{ background: 'rgba(239,68,68,0.10)', color: '#dc2626' }}
              >
                竞品
              </span>
            )}
          </div>
          <div className="flex items-center gap-1.5 mt-1 text-[10px]" style={{ color: 'var(--text-muted)' }}>
            <span>📍 {lead.distance}m</span>
            <span>·</span>
            <span>环 {lead.ringMeters}m</span>
            <span>·</span>
            <span style={{ color: prov.color }}>● {prov.name}</span>
          </div>
          {/* 评分条 */}
          <div className="mt-1.5 flex items-center gap-2">
            <div className="flex items-center gap-0.5">
              <span className="text-[10px] font-bold" style={{ color: 'var(--text-muted)' }}>意向</span>
              <div className="w-12 h-1 rounded-full overflow-hidden" style={{ background: 'var(--surface-2)' }}>
                <div className="h-full" style={{ width: `${lead.intentScore}%`, background: '#3b82f6' }} />
              </div>
              <span className="text-[10px] font-bold" style={{ color: '#3b82f6' }}>{lead.intentScore}</span>
            </div>
            <div className="flex items-center gap-0.5">
              <span className="text-[10px] font-bold" style={{ color: 'var(--text-muted)' }}>热度</span>
              <div className="w-12 h-1 rounded-full overflow-hidden" style={{ background: 'var(--surface-2)' }}>
                <div className="h-full" style={{ width: `${lead.heatScore}%`, background: '#f59e0b' }} />
              </div>
              <span className="text-[10px] font-bold" style={{ color: '#f59e0b' }}>{lead.heatScore}</span>
            </div>
            {lead.subsidyQuote.total > 0 && (
              <span
                className="chip text-[9px]"
                style={{ background: 'rgba(239,68,68,0.10)', color: '#dc2626' }}
              >
                国补 ¥{lead.subsidyQuote.total}
              </span>
            )}
          </div>
          {/* 推荐机型 */}
          {lead.recommendedModels.length > 0 && (
            <div className="mt-1.5 text-[10px]" style={{ color: 'var(--text-secondary)' }}>
              📱 {lead.recommendedModels.slice(0, 2).join(' / ')}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function LeadCard({
  lead,
  delay,
  onCall,
  onConvert,
}: {
  lead: Lead;
  delay: number;
  onCall: () => void;
  onConvert: () => void;
}) {
  const s = leadStatusMap[lead.status];
  return (
    <div
      className="card p-3.5 animate-slideInRight"
      style={{ animationDelay: `${delay}ms` }}
    >
      <div className="flex items-center gap-3">
        <div
          className="w-11 h-11 rounded-full flex items-center justify-center text-white font-semibold flex-shrink-0"
          style={{ background: s.color, fontSize: 17 }}
        >
          {lead.name[0]}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <span className="text-[15px] font-semibold" style={{ color: 'var(--text-primary)' }}>
              {lead.name}
            </span>
            <span
              className="chip"
              style={{ background: s.bg, color: s.color }}
            >
              {s.label}
            </span>
          </div>
          <p className="text-xs mt-0.5 truncate" style={{ color: 'var(--text-secondary)' }}>
            {lead.source} · 意向度 {lead.intent}
          </p>
        </div>
        <div className="text-right">
          <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
            {lead.capturedAt}
          </p>
        </div>
      </div>
      <div className="flex items-center gap-2 mt-3">
        <button
          className="flex-1 h-9 rounded-lg flex items-center justify-center gap-1 text-xs font-semibold"
          style={{ background: 'rgba(16,185,129,0.10)', color: '#10b981' }}
          onClick={onConvert}
        >
          <UserPlus className="w-3.5 h-3.5" />
          转为客户
        </button>
        <button
          className="flex-1 h-9 rounded-lg flex items-center justify-center gap-1 text-xs font-semibold"
          style={{ background: 'rgba(59,130,246,0.10)', color: '#3b82f6' }}
          onClick={onCall}
        >
          <MessageSquare className="w-3.5 h-3.5" />
          跟进沟通
        </button>
        <button
          className="flex-1 h-9 rounded-lg flex items-center justify-center gap-1 text-xs font-semibold"
          style={{ background: 'rgba(245,158,11,0.10)', color: '#f59e0b' }}
          onClick={onCall}
        >
          <Star className="w-3.5 h-3.5" />
          标记
        </button>
      </div>
    </div>
  );
}

function CustomerRow({
  customer: c,
  delay,
  onClick,
  onCall,
}: {
  customer: Customer;
  delay: number;
  onClick: () => void;
  onCall: () => void;
}) {
  return (
    <div
      className="card p-3.5 animate-slideInRight"
      style={{ animationDelay: `${delay}ms` }}
      onClick={onClick}
    >
      <div className="flex items-center gap-3">
        <div
          className="w-11 h-11 rounded-full flex items-center justify-center text-white font-semibold flex-shrink-0"
          style={{ background: c.avatarColor, fontSize: 17 }}
        >
          {c.avatar}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <span className="text-[15px] font-semibold" style={{ color: 'var(--text-primary)' }}>
              {c.name}
            </span>
            <span
              className="chip"
              style={{ background: 'rgba(245,158,11,0.10)', color: '#b45309' }}
            >
              {c.grade}级
            </span>
          </div>
          <p className="text-xs mt-0.5 truncate" style={{ color: 'var(--text-secondary)' }}>
            {c.phoneModel} · {c.statusText}
          </p>
        </div>
        <div className="text-right">
          <p className="text-[15px] font-bold" style={{ color: 'var(--text-primary)' }}>
            {c.distance}
            <span className="text-xs font-normal ml-0.5" style={{ color: 'var(--text-muted)' }}>
              m
            </span>
          </p>
          <button
            onClick={(e) => {
              e.stopPropagation();
              onCall();
            }}
            className="mt-1 w-8 h-8 rounded-full flex items-center justify-center"
            style={{ background: 'rgba(16,185,129,0.10)' }}
          >
            <Phone className="w-3.5 h-3.5" style={{ color: '#10b981' }} />
          </button>
        </div>
      </div>
    </div>
  );
}

function FilterPanel({ onClose }: { onClose: () => void }) {
  const [grade, setGrade] = useState<'all' | 'S' | 'A' | 'B'>('all');
  const [range, setRange] = useState(1000);
  const [intent, setIntent] = useState(70);

  return (
    <div
      className="mx-5 mb-3 p-4 rounded-2xl animate-fadeScale"
      style={{ background: 'var(--surface-2)' }}
    >
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold">筛选条件</h3>
        <button
          onClick={onClose}
          className="text-xs"
          style={{ color: 'var(--text-secondary)' }}
        >
          收起
        </button>
      </div>
      <div>
        <p className="text-xs font-medium mb-2" style={{ color: 'var(--text-secondary)' }}>
          客户等级
        </p>
        <div className="flex gap-2 mb-3">
          {(['all', 'S', 'A', 'B'] as const).map((g) => (
            <button
              key={g}
              onClick={() => setGrade(g)}
              className="px-3 h-8 rounded-full text-xs font-semibold transition-colors"
              style={{
                background: grade === g ? 'var(--primary)' : '#fff',
                color: grade === g ? '#fff' : 'var(--text-primary)',
                border: grade === g ? 'none' : '1px solid var(--border)',
              }}
            >
              {g === 'all' ? '全部' : `${g}级`}
            </button>
          ))}
        </div>
      </div>
      <div className="mb-3">
        <div className="flex items-center justify-between mb-1">
          <p className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
            搜索半径
          </p>
          <p className="text-xs font-bold" style={{ color: 'var(--primary)' }}>
            {range >= 1000 ? `${range / 1000}km` : `${range}m`}
          </p>
        </div>
        <input
          type="range"
          min="200"
          max="5000"
          step="100"
          value={range}
          onChange={(e) => setRange(Number(e.target.value))}
          className="w-full"
          style={{ accentColor: 'var(--primary)' }}
        />
      </div>
      <div>
        <div className="flex items-center justify-between mb-1">
          <p className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
            最低意向度
          </p>
          <p className="text-xs font-bold" style={{ color: 'var(--primary)' }}>
            {intent}+
          </p>
        </div>
        <input
          type="range"
          min="0"
          max="100"
          step="5"
          value={intent}
          onChange={(e) => setIntent(Number(e.target.value))}
          className="w-full"
          style={{ accentColor: 'var(--primary)' }}
        />
      </div>
    </div>
  );
}
