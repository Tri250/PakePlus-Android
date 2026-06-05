import { useAppStore } from '../store/appStore';
import { leadStatusMap, type Lead, type Customer } from '../data/mockData';
import {
  Sliders, Home, Phone, Navigation, Filter, Mic, ChevronLeft,
  UserPlus, MessageSquare, Star, MapPin,
} from 'lucide-react';
import { useState } from 'react';
import LiveIndicator from '../components/LiveIndicator';
import DataBoundary from '../components/DataBoundary';
import { useCustomers, useLeads } from '../hooks/useRealTimeData';

export default function RadarPage() {
  const setSelectedCustomer = useAppStore((s) => s.setSelectedCustomer);
  const setShowRadar = useAppStore((s) => s.setShowRadar);
  const showToast = useAppStore((s) => s.showToast);
  const [filterOpen, setFilterOpen] = useState(false);
  const [mode, setMode] = useState<'leads' | 'customers'>('leads');
  const [selectedDot, setSelectedDot] = useState<string | null>(null);

  // 实时数据
  const customersQ = useCustomers();
  const leadsQ = useLeads();
  const customers = customersQ.data || [];
  const leads = leadsQ.data || [];

  // 客户线索
  const sortedLeads = [...leads].sort((a, b) => b.intent - a.intent);
  const topThree = sortedLeads.slice(0, 3);
  const customersSorted = [...customers].sort((a, b) => a.distance - b.distance);

  const dataPoints = mode === 'leads' ? leads.map((l, i) => ({
    id: l.id,
    label: l.name[0],
    color: leadStatusMap[l.status].color,
    intent: l.intent,
    pos: { x: 20 + i * 15, y: 30 + (i % 2) * 25 },
    sub: `${l.intent}分`,
  })) : customers.map((c, i) => ({
    id: c.id,
    label: c.avatar,
    color: c.avatarColor,
    intent: c.intentScore,
    pos: c.position,
    sub: `${c.distance}m`,
  }));

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
                fetchedAt={mode === 'leads' ? leadsQ.fetchedAt : customersQ.fetchedAt}
                source={mode === 'leads' ? leadsQ.source : customersQ.source}
              />
              <button
                onClick={() => setFilterOpen(!filterOpen)}
                className="flex items-center gap-1.5 px-3.5 h-9 rounded-full text-sm font-medium"
                style={{ background: 'var(--surface-2)', color: 'var(--text-primary)' }}
              >
                <Sliders className="w-3.5 h-3.5" />
                筛选
              </button>
            </div>
          </div>
          {/* 模式切换 */}
          <div className="flex gap-1 mt-3 p-1 rounded-full" style={{ background: 'var(--surface-2)' }}>
            {(['leads', 'customers'] as const).map((m) => (
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
                {m === 'leads' ? '🎯 线索' : '👥 客户'}
              </button>
            ))}
          </div>
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
