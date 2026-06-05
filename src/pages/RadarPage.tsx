import { Sliders, Home, Phone, Navigation, Filter, Mic, ChevronLeft, X } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { customers, gradeColors } from '../data/mockData';
import { useState } from 'react';

export default function RadarPage() {
  const setSelectedCustomer = useAppStore((s) => s.setSelectedCustomer);
  const setShowRadar = useAppStore((s) => s.setShowRadar);
  const showToast = useAppStore((s) => s.showToast);
  const [filterOpen, setFilterOpen] = useState(false);
  const [selectedDot, setSelectedDot] = useState<string | null>(null);

  // 排序后的客户 (按距离)
  const sortedCustomers = [...customers].sort((a, b) => a.distance - b.distance);
  const topThree = sortedCustomers.slice(0, 3);

  return (
    <div className="absolute inset-0 z-40 bg-white flex flex-col animate-slideInRight">
      <div className="scroll-area">
        {/* 页面标题 + 返回 */}
        <div className="px-5 pt-1 pb-3 flex items-center justify-between animate-fadeIn">
          <button
            onClick={() => setShowRadar(false)}
            className="w-9 h-9 rounded-full flex items-center justify-center -ml-2"
            style={{ background: 'var(--surface-2)' }}
            aria-label="返回"
          >
            <ChevronLeft className="w-5 h-5" />
          </button>
          <h1 className="text-[20px] font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            获客雷达
          </h1>
          <button
            onClick={() => setFilterOpen(!filterOpen)}
            className="flex items-center gap-1.5 px-3.5 h-9 rounded-full text-sm font-medium"
            style={{ background: 'var(--surface-2)', color: 'var(--text-primary)' }}
          >
            <Sliders className="w-3.5 h-3.5" />
            筛选
          </button>
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
            {/* 客户点 */}
            {customers.map((c, i) => (
              <button
                key={c.id}
                className="radar-dot animate-pop"
                style={{
                  left: `${c.position.x}%`,
                  top: `${c.position.y}%`,
                  background: c.avatarColor,
                  animationDelay: `${200 + i * 80}ms`,
                  width: selectedDot === c.id ? 44 : 36,
                  height: selectedDot === c.id ? 44 : 36,
                  fontSize: selectedDot === c.id ? 15 : 13,
                }}
                onClick={() => setSelectedDot(selectedDot === c.id ? null : c.id)}
              >
                {c.avatar}
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
                实时扫描中 · {customers.length} 位客户
              </span>
            </div>
          </div>
        </div>

        {/* 附近高意向客户 */}
        <div className="px-5 pb-6 animate-slideUp" style={{ animationDelay: '120ms' }}>
          <h2 className="text-base font-bold mb-3" style={{ color: 'var(--text-primary)' }}>
            附近 {topThree.length} 位高意向客户
          </h2>
          <div className="space-y-2.5">
            {topThree.map((c, i) => (
              <div
                key={c.id}
                className="card p-3.5 animate-slideInRight"
                style={{ animationDelay: `${180 + i * 80}ms` }}
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
                        style={{
                          background: gradeColors[c.grade].bg,
                          color: gradeColors[c.grade].text,
                        }}
                      >
                        {c.grade}级
                      </span>
                    </div>
                    <p className="text-xs mt-0.5 truncate" style={{ color: 'var(--text-secondary)' }}>
                      {c.phoneModel}用户 · {c.statusText}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-[15px] font-bold" style={{ color: 'var(--text-primary)' }}>
                      {c.distance}
                      <span className="text-xs font-normal ml-0.5" style={{ color: 'var(--text-muted)' }}>
                        m
                      </span>
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2 mt-3">
                  <button
                    className="flex-1 h-9 rounded-lg flex items-center justify-center gap-1 text-xs font-semibold"
                    style={{ background: 'rgba(59,130,246,0.10)', color: '#3b82f6' }}
                    onClick={() => {
                      showToast(`正在导航到 ${c.name} 位置...`, '🧭');
                    }}
                  >
                    <Navigation className="w-3.5 h-3.5" />
                    导航
                  </button>
                  <button
                    className="flex-1 h-9 rounded-lg flex items-center justify-center gap-1 text-xs font-semibold"
                    style={{ background: 'rgba(16,185,129,0.10)', color: '#10b981' }}
                    onClick={() => {
                      showToast(`正在拨打 ${c.phone}`, '📞');
                    }}
                  >
                    <Phone className="w-3.5 h-3.5" />
                    拨号
                  </button>
                  <button
                    className="flex-1 h-9 rounded-lg flex items-center justify-center gap-1 text-xs font-semibold"
                    style={{ background: 'var(--surface-2)', color: 'var(--text-primary)' }}
                    onClick={() => setSelectedCustomer(c.id)}
                  >
                    <Home className="w-3.5 h-3.5" />
                    详情
                  </button>
                </div>
              </div>
            ))}
          </div>
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
