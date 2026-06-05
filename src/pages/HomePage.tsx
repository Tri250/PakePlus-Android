import { Bell, MapPin, ChevronRight, Sun, Moon, Mic, Camera, Award, Phone, Settings as SettingsIcon, Users, Flame, Sparkles } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { customers, activities, competitorEvents, type CompetitorEvent } from '../data/mockData';
import Avatar from '../components/Avatar';
import { useMemo, useState } from 'react';

const greetingByHour = (h: number) => {
  if (h < 6) return '夜深了';
  if (h < 11) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
};

const eventTypeIcon: Record<CompetitorEvent['type'], string> = {
  promotion: '🏷️',
  opening: '🎉',
  event: '📅',
  price: '💰',
};

const impactColor: Record<CompetitorEvent['impact'], string> = {
  high: '#ef4444',
  mid: '#f59e0b',
  low: '#94a3b8',
};

const impactLabel: Record<CompetitorEvent['impact'], string> = {
  high: '高',
  mid: '中',
  low: '低',
};

export default function HomePage() {
  const role = useAppStore((s) => s.role);
  const outdoorMode = useAppStore((s) => s.outdoorMode);
  const toggleOutdoor = useAppStore((s) => s.toggleOutdoor);
  const setActiveTab = useAppStore((s) => s.setActiveTab);
  const setShowNotifications = useAppStore((s) => s.setShowNotifications);
  const setShowAddCustomer = useAppStore((s) => s.setShowAddCustomer);
  const setShowSOS = useAppStore((s) => s.setShowSOS);
  const setShowRoleSwitcher = useAppStore((s) => s.setShowRoleSwitcher);
  const setShowRadar = useAppStore((s) => s.setShowRadar);
  const setShowSettings = useAppStore((s) => s.setShowSettings);
  const showToast = useAppStore((s) => s.showToast);
  const setSelectedCustomer = useAppStore((s) => s.setSelectedCustomer);
  const setShowAllFeatures = useAppStore((s) => s.setShowAllFeatures);
  const feedTab = useAppStore((s) => s.homeFeedTab);
  const setFeedTab = useAppStore((s) => s.setHomeFeedTab);

  const user = useMemo(() => {
    const map = {
      rep: { name: '王磊', title: '地推专员', avatar: '王', color: '#3b82f6' },
      manager: { name: '李美华', title: '门店店长', avatar: '李', color: '#8b5cf6' },
      hq: { name: '张志远', title: '总部运营', avatar: '张', color: '#10b981' },
    } as const;
    return map[role];
  }, [role]);
  const hour = new Date().getHours();
  const greeting = greetingByHour(hour);

  const nearbyHighIntent = useMemo(
    () => customers.filter((c) => c.intentScore >= 75).slice(0, 2),
    []
  );

  return (
    <div className="flex flex-col h-full">
      <div className="scroll-area">
        {/* 顶部 Header */}
        <div className="px-5 pt-1 pb-3 flex items-center justify-between animate-fadeIn">
          <button
            onClick={() => setShowRoleSwitcher(true)}
            className="flex items-center gap-2"
          >
            <Avatar name={user.avatar} color={user.color} size={36} />
            <div className="text-left">
              <div className="flex items-center gap-1.5">
                <h1 className="text-[22px] font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
                  {greeting}，{user.name}
                </h1>
              </div>
              <div className="flex items-center gap-1 mt-0.5">
                <span
                  className="chip"
                  style={{
                    background: 'rgba(59,130,246,0.10)',
                    color: '#3b82f6',
                  }}
                >
                  <MapPin className="w-2.5 h-2.5 mr-0.5" strokeWidth={3} />
                  {user.title}
                </span>
              </div>
            </div>
          </button>
          <div className="flex items-center gap-1.5">
            <button
              onClick={toggleOutdoor}
              className="w-9 h-9 rounded-full flex items-center justify-center"
              style={{ background: 'var(--surface-2)' }}
              aria-label="切换户外模式"
            >
              {outdoorMode ? (
                <Moon className="w-4 h-4" style={{ color: '#fbbf24' }} />
              ) : (
                <Sun className="w-4 h-4" style={{ color: '#f59e0b' }} />
              )}
            </button>
            <button
              onClick={() => setShowSettings(true)}
              className="w-9 h-9 rounded-full flex items-center justify-center"
              style={{ background: 'var(--surface-2)' }}
              aria-label="设置"
            >
              <SettingsIcon className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
            </button>
            <button
              onClick={() => setShowNotifications(true)}
              className="w-9 h-9 rounded-full flex items-center justify-center relative"
              style={{ background: 'var(--surface-2)' }}
              aria-label="通知"
            >
              <Bell className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} strokeWidth={2.2} />
              <span className="absolute top-1.5 right-2 w-1.5 h-1.5 rounded-full bg-red-500"></span>
            </button>
          </div>
        </div>

        {/* 指标卡 - 3列 */}
        <div className="px-5 grid grid-cols-3 gap-2.5 mb-3 animate-slideUp" style={{ animationDelay: '60ms' }}>
          <MetricCard label="今日任务" value="5/8" sub="已完成" subColor="#10b981" />
          <MetricCard label="附近商机" value="12" sub="500m内" subColor="#3b82f6" />
          <MetricCard label="转化率" value="34.2%" sub="↑ 2.1%" subColor="#10b981" />
        </div>

        {/* 雷达预览卡 */}
        <div className="px-5 mb-3 animate-slideUp" style={{ animationDelay: '120ms' }}>
          <div
            className="card overflow-hidden cursor-pointer"
            onClick={() => setShowRadar(true)}
            style={{ padding: 0 }}
          >
            <div
              className="relative h-32"
              style={{
                background:
                  'linear-gradient(180deg, #e0e7ff 0%, #dbeafe 100%)',
              }}
            >
              <div className="absolute inset-0 flex items-center justify-center">
                <div
                  className="rounded-full"
                  style={{
                    width: 200,
                    height: 200,
                    background:
                      'radial-gradient(circle, rgba(59,130,246,0.10) 0%, transparent 70%)',
                  }}
                />
              </div>
              <div
                className="absolute top-1/2 left-1/2 animate-radarScan"
                style={{ width: 0, height: 0, transformOrigin: '0 0' }}
              >
                <div
                  style={{
                    width: 110,
                    height: 2,
                    background:
                      'linear-gradient(90deg, rgba(59,130,246,0.4) 0%, transparent 100%)',
                    transform: 'translateY(-1px)',
                  }}
                />
              </div>
              {[
                { x: 25, y: 35, c: '#10b981' },
                { x: 50, y: 50, c: '#3b82f6', big: true },
                { x: 70, y: 30, c: '#f59e0b' },
                { x: 35, y: 70, c: '#10b981' },
                { x: 80, y: 65, c: '#ef4444' },
              ].map((p, i) => (
                <div
                  key={i}
                  className="absolute rounded-full animate-pop"
                  style={{
                    left: `${p.x}%`,
                    top: `${p.y}%`,
                    transform: 'translate(-50%, -50%)',
                    width: p.big ? 12 : 8,
                    height: p.big ? 12 : 8,
                    background: p.c,
                    boxShadow: `0 0 0 4px ${p.c}30, 0 2px 6px ${p.c}50`,
                    animationDelay: `${i * 100}ms`,
                  }}
                />
              ))}
            </div>
            <div className="p-4 flex items-center justify-between">
              <div className="flex-1 min-w-0">
                <p className="text-[15px] font-semibold" style={{ color: 'var(--text-primary)' }}>
                  3位高意向线索在附近
                </p>
                <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
                  最近280m · 客户线索地图
                </p>
              </div>
              <button
                className="btn-primary flex-shrink-0"
                onClick={(e) => {
                  e.stopPropagation();
                  setShowRadar(true);
                }}
              >
                查看线索
                <ChevronRight className="w-4 h-4" strokeWidth={2.5} />
              </button>
            </div>
          </div>
        </div>

        {/* 最近客户动态 / 附近竞品动态 - Tab 切换合并区域 */}
        <div className="px-5 mb-3 animate-slideUp" style={{ animationDelay: '180ms' }}>
          <div className="card overflow-hidden" style={{ padding: 0 }}>
            {/* Tab header */}
            <div
              className="flex items-center justify-between px-3 pt-3"
              style={{ borderBottom: '1px solid var(--border)' }}
            >
              <div className="flex gap-1">
                <FeedTabButton
                  active={feedTab === 'customer'}
                  onClick={() => setFeedTab('customer')}
                  icon={<Users className="w-3.5 h-3.5" />}
                  label="客户动态"
                />
                <FeedTabButton
                  active={feedTab === 'competitor'}
                  onClick={() => setFeedTab('competitor')}
                  icon={<Flame className="w-3.5 h-3.5" />}
                  label="竞品动态"
                />
              </div>
              <button
                onClick={() => showToast('查看完整动态', '📰')}
                className="text-xs font-medium flex items-center pr-1"
                style={{ color: 'var(--text-secondary)' }}
              >
                全部
                <ChevronRight className="w-3 h-3" />
              </button>
            </div>
            {/* Content */}
            <div className="p-3 space-y-1.5 max-h-72 overflow-y-auto scroll-area">
              {feedTab === 'customer' ? (
                <>
                  {nearbyHighIntent.map((c, i) => (
                    <div
                      key={c.id}
                      className="flex items-center gap-3 p-2 rounded-xl cursor-pointer hover:bg-gray-50"
                      style={{ animation: `slideInRight 0.3s ${i * 60}ms both` }}
                      onClick={() => setSelectedCustomer(c.id)}
                    >
                      <Avatar name={c.avatar} color={c.avatarColor} size={40} grade={c.grade} showGrade />
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-1.5">
                          <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                            {c.name}
                          </span>
                        </div>
                        <p className="text-[11px] mt-0.5 truncate" style={{ color: 'var(--text-secondary)' }}>
                          {c.statusText} · {c.statusSub}
                        </p>
                      </div>
                      <button
                        className="w-8 h-8 rounded-full flex items-center justify-center"
                        style={{ background: 'var(--surface-2)' }}
                        onClick={(e) => {
                          e.stopPropagation();
                          showToast(`正在拨打 ${c.phone}`, '📞');
                        }}
                        aria-label="拨打电话"
                      >
                        <Phone className="w-3.5 h-3.5" style={{ color: 'var(--primary)' }} />
                      </button>
                    </div>
                  ))}
                  {activities.slice(0, 2).map((a, i) => (
                    <div
                      key={a.id}
                      className="flex items-center gap-2.5 p-2 rounded-xl"
                      style={{ animation: `slideInRight 0.3s ${(i + 2) * 60}ms both` }}
                    >
                      <div
                        className="w-7 h-7 rounded-full flex items-center justify-center flex-shrink-0"
                        style={{
                          background:
                            a.type === 'reward'
                              ? 'rgba(245,158,11,0.10)'
                              : a.type === 'signal'
                              ? 'rgba(59,130,246,0.10)'
                              : 'rgba(16,185,129,0.10)',
                        }}
                      >
                        <span className="text-sm">
                          {a.type === 'reward' ? '🏆' : a.type === 'signal' ? '📡' : a.type === 'task_done' ? '✓' : '📞'}
                        </span>
                      </div>
                      <p className="text-xs flex-1 truncate" style={{ color: 'var(--text-secondary)' }}>
                        {a.text}
                      </p>
                      <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
                        {a.time}
                      </span>
                    </div>
                  ))}
                </>
              ) : (
                <>
                  {competitorEvents.map((e, i) => (
                    <div
                      key={e.id}
                      className="flex items-center gap-2.5 p-2 rounded-xl cursor-pointer hover:bg-gray-50"
                      style={{ animation: `slideInRight 0.3s ${i * 60}ms both` }}
                      onClick={() => showToast(`查看 ${e.competitor} 详情`, '🔥')}
                    >
                      <div
                        className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
                        style={{
                          background: `${impactColor[e.impact]}15`,
                          fontSize: 18,
                        }}
                      >
                        {eventTypeIcon[e.type]}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-1.5">
                          <span className="text-[13px] font-semibold truncate" style={{ color: 'var(--text-primary)' }}>
                            {e.competitor}
                          </span>
                          <span
                            className="chip"
                            style={{
                              background: `${impactColor[e.impact]}15`,
                              color: impactColor[e.impact],
                            }}
                          >
                            影响 {impactLabel[e.impact]}
                          </span>
                        </div>
                        <p className="text-[11px] mt-0.5 truncate" style={{ color: 'var(--text-secondary)' }}>
                          {e.title} · {e.distance}
                        </p>
                      </div>
                      <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
                        {e.time}
                      </span>
                    </div>
                  ))}
                </>
              )}
            </div>
            {/* 底部快捷入口 */}
            <div
              className="grid grid-cols-2 gap-0"
              style={{ borderTop: '1px solid var(--border)' }}
            >
              <button
                onClick={() => setActiveTab('customers')}
                className="flex items-center justify-center gap-1.5 py-2.5 text-xs font-semibold"
                style={{
                  color: 'var(--text-secondary)',
                  borderRight: '1px solid var(--border)',
                }}
              >
                <Users className="w-3.5 h-3.5" />
                查看客户
              </button>
              <button
                onClick={() => setShowAllFeatures(true)}
                className="flex items-center justify-center gap-1.5 py-2.5 text-xs font-semibold"
                style={{ color: 'var(--text-secondary)' }}
              >
                <Sparkles className="w-3.5 h-3.5" />
                更多功能
              </button>
            </div>
          </div>
        </div>

        {/* P2 成就条 */}
        <div className="px-5 mb-6 animate-slideUp" style={{ animationDelay: '240ms' }}>
          <div
            className="card p-3.5 flex items-center gap-3"
            style={{
              background: 'linear-gradient(135deg, #fef3c7 0%, #fde68a 100%)',
            }}
          >
            <div
              className="w-10 h-10 rounded-full flex items-center justify-center"
              style={{ background: 'rgba(255,255,255,0.7)' }}
            >
              <Award className="w-5 h-5" style={{ color: '#b45309' }} />
            </div>
            <div className="flex-1">
              <p className="text-sm font-semibold" style={{ color: '#92400e' }}>
                本周步行之王：累计扫街32公里
              </p>
              <div className="mt-1.5 progress" style={{ background: 'rgba(255,255,255,0.5)' }}>
                <div
                  className="progress-bar"
                  style={{
                    width: '64%',
                    background: 'linear-gradient(90deg, #f59e0b 0%, #d97706 100%)',
                  }}
                />
              </div>
            </div>
            <span className="text-xs font-bold" style={{ color: '#92400e' }}>
              32/50
            </span>
          </div>
        </div>
      </div>

      {/* FAB 浮动操作按钮组 */}
      <div className="fab-rail">
        <div className="flex flex-col items-end gap-2 animate-fadeScale">
          <button
            className="w-12 h-12 rounded-full flex items-center justify-center"
            style={{
              background: '#fff',
              boxShadow: '0 4px 14px rgba(0,0,0,0.10)',
            }}
            onClick={() => showToast('语音搜索已启动，请说出客户名称', '🎙️')}
            aria-label="语音搜索"
          >
            <Mic className="w-5 h-5" style={{ color: 'var(--text-secondary)' }} />
          </button>
          <button
            className="w-12 h-12 rounded-full flex items-center justify-center"
            style={{
              background: '#fff',
              boxShadow: '0 4px 14px rgba(0,0,0,0.10)',
            }}
            onClick={() => setShowAddCustomer(true)}
            aria-label="拍照添加客户"
          >
            <Camera className="w-5 h-5" style={{ color: 'var(--text-secondary)' }} />
          </button>
        </div>
        <button
          className="fab animate-pop"
          onClick={() => setShowSOS(true)}
          aria-label="一键救援"
        >
          <Phone className="w-6 h-6" />
        </button>
      </div>
    </div>
  );
}

function MetricCard({ label, value, sub, subColor }: { label: string; value: string; sub: string; subColor: string }) {
  return (
    <div className="metric-card">
      <p className="text-[11px] font-medium" style={{ color: 'var(--text-muted)' }}>
        {label}
      </p>
      <p className="text-[22px] font-bold mt-1 tracking-tight" style={{ color: 'var(--text-primary)' }}>
        {value}
      </p>
      <p className="text-[11px] font-semibold mt-0.5" style={{ color: subColor }}>
        {sub}
      </p>
    </div>
  );
}

function FeedTabButton({
  active,
  onClick,
  icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
}) {
  return (
    <button
      onClick={onClick}
      className="relative flex items-center gap-1.5 px-3 py-2.5 text-sm font-semibold transition-colors"
      style={{ color: active ? 'var(--primary)' : 'var(--text-secondary)' }}
    >
      {icon}
      {label}
      {active && (
        <span
          className="absolute bottom-0 left-2 right-2 h-0.5 rounded-full"
          style={{ background: 'var(--primary)' }}
        />
      )}
    </button>
  );
}
