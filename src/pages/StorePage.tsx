import { useAppStore } from '../store/appStore';
import { type Alert as AlertData } from '../data/mockData';
import {
  ChevronRight, AlertTriangle, AlertCircle, Info, Users, MapPin,
  TrendingUp, BarChart3, CheckSquare, Clock, Package, BookOpen,
  Award, Grid3X3,
} from 'lucide-react';
import LiveIndicator from '../components/LiveIndicator';
import DataBoundary from '../components/DataBoundary';
import { useAlerts, useTeamMembers } from '../hooks/useRealTimeData';
import { useMemo } from 'react';

export default function StorePage() {
  const setShowAllFeatures = useAppStore((s) => s.setShowAllFeatures);
  const setActiveTab = useAppStore((s) => s.setActiveTab);
  const setSelectedTask = useAppStore((s) => s.setSelectedTask);
  const setActiveFeature = useAppStore((s) => s.setActiveFeature);
  const showToast = useAppStore((s) => s.showToast);

  const teamQ = useTeamMembers();
  const alertsQ = useAlerts();
  const teamMembers = teamQ.data || [];
  const alerts = alertsQ.data || [];

  const stats = useMemo(() => {
    const totalDone = teamMembers.reduce((s, m) => s + m.todayDone, 0);
    const totalTarget = teamMembers.reduce((s, m) => s + m.todayTotal, 0);
    const onlineCount = teamMembers.filter((m) => m.status === 'online').length;
    return { totalDone, totalTarget, onlineCount };
  }, [teamMembers]);

  return (
    <div className="flex flex-col h-full">
      <div className="scroll-area">
        {/* 页面标题 */}
        <div className="px-5 pt-1 pb-3 flex items-center justify-between animate-fadeIn">
          <div>
            <h1 className="text-[28px] font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
              门店管理
            </h1>
            <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
              深圳市南山区科技园旗舰店 · {teamMembers.length} 位成员
            </p>
          </div>
          <LiveIndicator fetchedAt={teamQ.fetchedAt} source={teamQ.source} />
        </div>

        {/* 门店今日数据 */}
        <div className="px-5 mb-3 animate-slideUp" style={{ animationDelay: '60ms' }}>
          <div
            className="card p-4"
            style={{
              background: 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)',
              color: '#fff',
            }}
          >
            <div className="flex items-center justify-between mb-3">
              <div>
                <p className="text-xs opacity-90">门店今日业绩</p>
                <p className="text-3xl font-bold mt-0.5">
                  {stats.totalDone}
                  <span className="text-base font-normal opacity-70 mx-0.5">/</span>
                  <span className="text-base font-normal opacity-70">{stats.totalTarget}</span>
                </p>
              </div>
              <div className="text-right">
                <p className="text-xs opacity-90">完成率</p>
                <p className="text-2xl font-bold mt-0.5">
                  {stats.totalTarget ? Math.round((stats.totalDone / stats.totalTarget) * 100) : 0}%
                </p>
              </div>
            </div>
            <div className="grid grid-cols-3 gap-2">
              <Stat label="在线" value={`${stats.onlineCount}/${teamMembers.length}`} />
              <Stat label="本周积分" value={teamMembers.reduce((s, m) => s + m.weeklyPoints, 0).toString()} />
              <Stat label="客诉" value="0" />
            </div>
          </div>
        </div>

        {/* 异常预警 */}
        {alerts.length > 0 && (
          <div className="px-5 mb-3 animate-slideUp" style={{ animationDelay: '120ms' }}>
            <div className="flex items-center justify-between mb-2.5">
              <h2 className="text-base font-bold flex items-center gap-1.5" style={{ color: 'var(--text-primary)' }}>
                <AlertTriangle className="w-4 h-4" style={{ color: '#ef4444' }} />
                异常预警
                <span
                  className="chip"
                  style={{ background: '#fee2e2', color: '#dc2626' }}
                >
                  {alerts.length}
                </span>
              </h2>
              <button
                onClick={() => showToast('查看全部预警', '⚠️')}
                className="text-xs font-medium flex items-center"
                style={{ color: 'var(--text-secondary)' }}
              >
                全部
                <ChevronRight className="w-3 h-3" />
              </button>
            </div>
            <div className="space-y-2">
              <DataBoundary
                loading={alertsQ.loading && alerts.length === 0}
                error={alertsQ.error}
                onRetry={() => alertsQ.refresh()}
                loadingText="正在拉取最新预警…"
              >
                {alerts.map((a, i) => (
                  <AlertCard key={a.id} alert={a} delay={i * 60} />
                ))}
              </DataBoundary>
            </div>
          </div>
        )}

        {/* 团队任务进度 */}
        <div className="px-5 mb-3 animate-slideUp" style={{ animationDelay: '180ms' }}>
          <div className="flex items-center justify-between mb-2.5">
            <h2 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
              团队任务
            </h2>
            <button
              onClick={() => setActiveTab('tasks')}
              className="text-xs font-medium flex items-center"
              style={{ color: 'var(--text-secondary)' }}
            >
              查看全部
              <ChevronRight className="w-3 h-3" />
            </button>
          </div>
          <div className="space-y-2">
            {teamMembers.slice(0, 4).map((m, i) => {
              const pct = Math.round((m.todayDone / m.todayTotal) * 100);
              return (
                <div
                  key={m.id}
                  className="card p-3 flex items-center gap-3 animate-slideInRight"
                  style={{ animationDelay: `${i * 60}ms` }}
                  onClick={() => setSelectedTask('t1')}
                >
                  <div className="relative">
                    <div
                      className="w-10 h-10 rounded-full flex items-center justify-center text-white font-semibold"
                      style={{ background: m.color, fontSize: 16 }}
                    >
                      {m.avatar}
                    </div>
                    <span
                      className="absolute bottom-0 right-0 w-2.5 h-2.5 rounded-full border-2"
                      style={{
                        background:
                          m.status === 'online' ? '#10b981' :
                          m.status === 'busy' ? '#f59e0b' : '#94a3b8',
                        borderColor: '#fff',
                      }}
                    />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5">
                      <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                        {m.name}
                      </p>
                      <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
                        {m.role}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 mt-1">
                      <div className="progress flex-1">
                        <div
                          className="progress-bar"
                          style={{
                            width: `${pct}%`,
                            background: pct === 100 ? '#10b981' : 'var(--primary)',
                          }}
                        />
                      </div>
                      <span
                        className="text-[10px] font-semibold flex-shrink-0"
                        style={{ color: 'var(--text-secondary)' }}
                      >
                        {m.todayDone}/{m.todayTotal}
                      </span>
                    </div>
                  </div>
                  <span
                    className="chip flex items-center gap-0.5"
                    style={{ background: 'rgba(245,158,11,0.10)', color: '#f59e0b' }}
                  >
                    <Award className="w-3 h-3" />
                    {m.weeklyPoints}
                  </span>
                </div>
              );
            })}
          </div>
        </div>

        {/* 门店管理快捷 */}
        <div className="px-5 mb-3 animate-slideUp" style={{ animationDelay: '240ms' }}>
          <h2 className="text-base font-bold mb-2.5" style={{ color: 'var(--text-primary)' }}>
            门店管理
          </h2>
          <div className="grid grid-cols-4 gap-2.5">
            {[
              { icon: CheckSquare, name: '巡店', color: '#10b981' },
              { icon: Clock, name: '排班', color: '#3b82f6' },
              { icon: Package, name: '物料', color: '#f59e0b' },
              { icon: BookOpen, name: '培训', color: '#8b5cf6' },
            ].map((f, i) => (
              <button
                key={f.name}
                className="flex flex-col items-center gap-1.5 p-3 rounded-2xl animate-pop"
                style={{
                  background: 'var(--surface-2)',
                  animationDelay: `${i * 50}ms`,
                }}
                onClick={() => showToast(`${f.name}功能已触发`, '✨')}
              >
                <f.icon className="w-5 h-5" style={{ color: f.color }} />
                <span className="text-[11px] font-medium" style={{ color: 'var(--text-secondary)' }}>
                  {f.name}
                </span>
              </button>
            ))}
          </div>
        </div>

        {/* 客户线索池 */}
        <div className="px-5 mb-3 animate-slideUp" style={{ animationDelay: '300ms' }}>
          <div className="flex items-center justify-between mb-2.5">
            <h2 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
              客户线索池
            </h2>
            <span
              className="chip"
              style={{ background: 'rgba(59,130,246,0.10)', color: '#3b82f6' }}
            >
              32 条待跟进
            </span>
          </div>
          <div
            className="card p-3.5"
            style={{
              background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)',
              border: '1px solid #bfdbfe',
            }}
          >
            <div className="grid grid-cols-3 gap-3">
              {[
                { label: '新线索', value: 18, color: '#3b82f6' },
                { label: '沟通中', value: 9, color: '#f59e0b' },
                { label: '高意向', value: 5, color: '#10b981' },
              ].map((s) => (
                <div key={s.label} className="text-center">
                  <p className="text-2xl font-bold" style={{ color: s.color }}>
                    {s.value}
                  </p>
                  <p className="text-[11px] mt-0.5" style={{ color: '#1e40af' }}>
                    {s.label}
                  </p>
                </div>
              ))}
            </div>
            <button
              className="w-full h-9 rounded-xl text-xs font-semibold mt-3"
              style={{ background: '#fff', color: '#1e40af' }}
              onClick={() => {
                setActiveTab('customers');
                showToast('打开线索池', '🎯');
              }}
            >
              查看全部线索
            </button>
          </div>
        </div>

        {/* 全部功能入口 */}
        <div className="px-5 mb-6 animate-slideUp" style={{ animationDelay: '360ms' }}>
          <button
            onClick={() => setShowAllFeatures(true)}
            className="w-full card p-3.5 flex items-center gap-3 text-left"
            style={{ background: 'var(--surface-2)' }}
          >
            <div
              className="w-9 h-9 rounded-full flex items-center justify-center flex-shrink-0"
              style={{ background: 'rgba(139,92,246,0.10)' }}
            >
              <Grid3X3 className="w-4 h-4" style={{ color: '#8b5cf6' }} />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                全部功能
              </p>
              <p className="text-[11px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
                20 个功能模块 · 5 大分类
              </p>
            </div>
            <ChevronRight className="w-4 h-4" style={{ color: 'var(--text-muted)' }} />
          </button>
        </div>
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div
      className="text-center p-2 rounded-xl"
      style={{ background: 'rgba(255,255,255,0.15)' }}
    >
      <p className="text-lg font-bold">{value}</p>
      <p className="text-[10px] opacity-80 mt-0.5">{label}</p>
    </div>
  );
}

function AlertCard({ alert, delay }: { alert: AlertData; delay: number }) {
  const showToast = useAppStore((s) => s.showToast);
  const config = {
    urgent: { color: '#ef4444', bg: 'rgba(239,68,68,0.06)', icon: AlertCircle, label: '紧急' },
    warning: { color: '#f59e0b', bg: 'rgba(245,158,11,0.06)', icon: AlertTriangle, label: '警告' },
    info: { color: '#3b82f6', bg: 'rgba(59,130,246,0.06)', icon: Info, label: '提醒' },
  }[alert.type];

  const Icon = config.icon;

  return (
    <div
      className="card p-3 flex items-start gap-2.5 animate-slideInRight"
      style={{ animationDelay: `${delay}ms` }}
    >
      <div
        className="w-7 h-7 rounded-full flex items-center justify-center flex-shrink-0"
        style={{ background: config.bg }}
      >
        <Icon className="w-3.5 h-3.5" style={{ color: config.color }} />
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5">
          <p className="text-sm font-semibold flex-1" style={{ color: 'var(--text-primary)' }}>
            {alert.title}
          </p>
          <span
            className="chip"
            style={{ background: config.bg, color: config.color }}
          >
            {config.label}
          </span>
        </div>
        <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
          {alert.desc}
        </p>
        <div className="flex items-center justify-between mt-1.5">
          <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
            {alert.time}
          </span>
          <button
            className="text-[11px] font-semibold"
            style={{ color: config.color }}
            onClick={(e) => {
              e.stopPropagation();
              showToast(`已触发: ${alert.action}`, '✓');
            }}
          >
            {alert.action} →
          </button>
        </div>
      </div>
    </div>
  );
}
