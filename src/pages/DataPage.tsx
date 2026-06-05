import { useAppStore } from '../store/appStore';
import { TrendingUp, TrendingDown, Award, Eye } from 'lucide-react';
import LiveIndicator from '../components/LiveIndicator';
import DataBoundary from '../components/DataBoundary';
import { useTrend, useCompetitors, useMetrics } from '../hooks/useRealTimeData';

export default function DataPage() {
  const role = useAppStore((s) => s.role);
  const setActiveTab = useAppStore((s) => s.setActiveTab);
  const setShowRoleSwitcher = useAppStore((s) => s.setShowRoleSwitcher);
  const showToast = useAppStore((s) => s.showToast);

  // HQ 角色显示更全面的数据
  const isHQ = role === 'hq';

  // 实时数据
  const trendQ = useTrend();
  const competitorsQ = useCompetitors();
  const metricsQ = useMetrics();
  const weeklyTrend = trendQ.data || [];
  const competitors = competitorsQ.data || [];
  const metrics = metricsQ.data;

  return (
    <div className="flex flex-col h-full">
      <div className="scroll-area">
        {/* 页面标题 */}
        <div className="px-5 pt-1 pb-3 flex items-center justify-between animate-fadeIn">
          <h1 className="text-[28px] font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            {isHQ ? '总部驾驶舱' : '数据中台'}
          </h1>
          <div className="flex items-center gap-2">
            <span
              className="chip"
              style={{ background: 'var(--surface-2)', color: 'var(--text-secondary)' }}
            >
              本月
            </span>
            <LiveIndicator fetchedAt={metricsQ.fetchedAt} source={metricsQ.source} />
          </div>
        </div>

        {/* 关键指标 2x2 */}
        <div className="px-5 mb-3 grid grid-cols-2 gap-2.5 animate-slideUp" style={{ animationDelay: '60ms' }}>
          <MetricCard
            label="本月获客"
            value={metrics ? `${Math.round(metrics.nearbyLeads * 12.6 + 100)}` : '--'}
            sub={`${metrics?.trendUp ? '↑' : '↓'} ${Math.abs(metrics?.conversionDelta || 0).toFixed(1)}%`}
            subIcon={<TrendingUp className="w-3 h-3" />}
            subColor={metrics?.trendUp ? '#10b981' : '#ef4444'}
          />
          <MetricCard
            label="转化率"
            value={metrics ? `${metrics.conversionRate.toFixed(1)}%` : '--'}
            sub={`${metrics?.trendUp ? '↑' : '↓'} ${Math.abs(metrics?.conversionDelta || 0).toFixed(1)}%`}
            subIcon={<TrendingUp className="w-3 h-3" />}
            subColor={metrics?.trendUp ? '#10b981' : '#ef4444'}
          />
          <MetricCard
            label="在跟客户"
            value={metrics ? `${Math.round(metrics.nearbyLeads * 6.8 + 20)}` : '--'}
            sub={metrics?.trendUp ? '↑ 5' : '↓ 3'}
            subIcon={metrics?.trendUp ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
            subColor={metrics?.trendUp ? '#10b981' : '#ef4444'}
          />
          <MetricCard
            label="平均跟进"
            value="4.2天"
            sub="↓ 0.5天"
            subIcon={<TrendingDown className="w-3 h-3" />}
            subColor="#10b981"
          />
        </div>

        {/* 周趋势图 */}
        <div className="px-5 mb-3 animate-slideUp" style={{ animationDelay: '120ms' }}>
          <div className="card p-4">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-[15px] font-semibold" style={{ color: 'var(--text-primary)' }}>
                近7天获客趋势
              </h2>
              <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
                总计 {weeklyTrend.reduce((s, d) => s + d.value, 0)} 人
              </span>
            </div>
            <DataBoundary
              loading={trendQ.loading && weeklyTrend.length === 0}
              error={trendQ.error}
              onRetry={() => trendQ.refresh()}
              loadingText="正在拉取最新趋势…"
            >
              <div className="flex items-end justify-between h-32 gap-2">
                {weeklyTrend.map((d, i) => {
                  const max = Math.max(...weeklyTrend.map((w) => w.value));
                  const h = max ? (d.value / max) * 100 : 0;
                  const isLast3 = i >= weeklyTrend.length - 3;
                  return (
                    <div key={d.day} className="flex-1 flex flex-col items-center gap-1.5">
                      <span
                        className="text-[10px] font-bold"
                        style={{ color: 'var(--text-muted)' }}
                      >
                        {d.value}
                      </span>
                      <div
                        className="w-full rounded-t-md transition-all duration-700"
                        style={{
                          height: `${h}%`,
                          background: isLast3
                            ? 'linear-gradient(180deg, #3b82f6 0%, #60a5fa 100%)'
                            : 'linear-gradient(180deg, #dbeafe 0%, #bfdbfe 100%)',
                          minHeight: 4,
                          animation: `slideUp 0.6s ${i * 60}ms cubic-bezier(0.16, 1, 0.3, 1) both`,
                        }}
                      />
                      <span
                        className="text-[10px] font-medium"
                        style={{ color: 'var(--text-muted)' }}
                      >
                        {d.day}
                      </span>
                    </div>
                  );
                })}
              </div>
            </DataBoundary>
          </div>
        </div>

        {/* 竞品动态 */}
        <div className="px-5 mb-3 animate-slideUp" style={{ animationDelay: '180ms' }}>
          <div className="card p-4">
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-[15px] font-semibold" style={{ color: 'var(--text-primary)' }}>
                竞品动态
              </h2>
              <button
                onClick={() => showToast('正在监控 12 个竞品品牌', '👀')}
                className="text-[11px] font-semibold flex items-center gap-0.5"
                style={{ color: 'var(--text-secondary)' }}
              >
                <Eye className="w-3 h-3" />
                {competitors.length} 个监控中
              </button>
            </div>
            <div className="space-y-2.5">
              <DataBoundary
                loading={competitorsQ.loading && competitors.length === 0}
                error={competitorsQ.error}
                onRetry={() => competitorsQ.refresh()}
                loadingText="正在拉取竞品动态…"
              >
                {competitors.map((c, i) => (
                  <div
                    key={c.id}
                    className="flex items-center justify-between p-2.5 rounded-xl animate-slideInRight"
                    style={{
                      background: 'var(--surface-2)',
                      animationDelay: `${240 + i * 80}ms`,
                    }}
                  >
                  <p className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                    {c.name}
                  </p>
                  {c.badge ? (
                    <span
                      className="chip"
                      style={{
                        background: '#fee2e2',
                        color: '#dc2626',
                      }}
                    >
                      {c.badge}
                    </span>
                  ) : (
                    <span
                      className="chip flex items-center gap-0.5"
                      style={{
                        background: c.isUp ? 'rgba(239,68,68,0.10)' : 'rgba(16,185,129,0.10)',
                        color: c.isUp ? '#dc2626' : '#10b981',
                      }}
                    >
                      {c.isUp ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                      {Math.abs(c.delta)}%
                    </span>
                  )}
                  </div>
                ))}
              </DataBoundary>
            </div>
          </div>
        </div>

        {/* 排行榜 (P2 激励) */}
        <div className="px-5 mb-6 animate-slideUp" style={{ animationDelay: '240ms' }}>
          <div
            className="card p-4"
            style={{
              background: 'linear-gradient(135deg, #fef3c7 0%, #fde68a 100%)',
            }}
          >
            <div className="flex items-center gap-2 mb-3">
              <Award className="w-5 h-5" style={{ color: '#b45309' }} />
              <h2 className="text-[15px] font-semibold" style={{ color: '#92400e' }}>
                本周团队排行
              </h2>
            </div>
            {[
              { rank: 1, name: '陈志强', area: '华强北', score: 92 },
              { rank: 2, name: '王美玲', area: '福田CBD', score: 88 },
              { rank: 3, name: '李大伟', area: '南山', score: 75 },
            ].map((r) => (
              <div
                key={r.rank}
                className="flex items-center gap-3 py-2"
                style={{
                  borderBottom:
                    r.rank !== 3 ? '1px solid rgba(180, 83, 9, 0.10)' : 'none',
                }}
              >
                <div
                  className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0"
                  style={{
                    background:
                      r.rank === 1
                        ? 'linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%)'
                        : r.rank === 2
                        ? 'linear-gradient(135deg, #d1d5db 0%, #9ca3af 100%)'
                        : 'linear-gradient(135deg, #fed7aa 0%, #fb923c 100%)',
                    color: '#fff',
                  }}
                >
                  {r.rank}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold truncate" style={{ color: '#78350f' }}>
                    {r.name}
                  </p>
                  <p className="text-[11px]" style={{ color: '#92400e' }}>
                    {r.area}
                  </p>
                </div>
                <span className="text-sm font-bold" style={{ color: '#b45309' }}>
                  {r.score} 分
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function MetricCard({
  label,
  value,
  sub,
  subIcon,
  subColor,
}: {
  label: string;
  value: string;
  sub: string;
  subIcon?: React.ReactNode;
  subColor: string;
}) {
  return (
    <div className="metric-card">
      <p className="text-[11px] font-medium" style={{ color: 'var(--text-muted)' }}>
        {label}
      </p>
      <p className="text-[24px] font-bold mt-1 tracking-tight" style={{ color: 'var(--text-primary)' }}>
        {value}
      </p>
      <p
        className="text-[11px] font-semibold mt-0.5 flex items-center gap-0.5"
        style={{ color: subColor }}
      >
        {subIcon}
        {sub}
      </p>
    </div>
  );
}
