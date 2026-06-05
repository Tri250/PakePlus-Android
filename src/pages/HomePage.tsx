import { Bell, MapPin, ChevronRight, Sun, Moon, Mic, Camera, Award, Phone, Settings as SettingsIcon } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { customers, roleConfig } from '../data/mockData';
import Avatar from '../components/Avatar';
import { useMemo } from 'react';

const greetingByHour = (h: number) => {
  if (h < 6) return '夜深了';
  if (h < 11) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
};

export default function HomePage() {
  const role = useAppStore((s) => s.role);
  const outdoorMode = useAppStore((s) => s.outdoorMode);
  const toggleOutdoor = useAppStore((s) => s.toggleOutdoor);
  const toggleOffline = useAppStore((s) => s.toggleOffline);
  const offline = useAppStore((s) => s.offline);
  const setActiveTab = useAppStore((s) => s.setActiveTab);
  const setShowNotifications = useAppStore((s) => s.setShowNotifications);
  const setShowAddCustomer = useAppStore((s) => s.setShowAddCustomer);
  const setShowSOS = useAppStore((s) => s.setShowSOS);
  const setShowRoleSwitcher = useAppStore((s) => s.setShowRoleSwitcher);
  const setShowRadar = useAppStore((s) => s.setShowRadar);
  const setShowSettings = useAppStore((s) => s.setShowSettings);
  const showToast = useAppStore((s) => s.showToast);
  const setSelectedCustomer = useAppStore((s) => s.setSelectedCustomer);

  const user = roleConfig[role];
  const hour = new Date().getHours();
  const greeting = greetingByHour(hour);

  // 高意向客户 (从mock数据)
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
            <Avatar name={user.avatar} color={user.avatarColor} size={36} />
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
          <MetricCard
            label="今日任务"
            value="5/8"
            sub="已完成"
            subColor="#10b981"
          />
          <MetricCard
            label="附近商机"
            value="12"
            sub="500m内"
            subColor="#3b82f6"
          />
          <MetricCard
            label="转化率"
            value="34.2%"
            sub="↑ 2.1%"
            subColor="#10b981"
          />
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
              {/* 装饰光晕 */}
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
              {/* 雷达扫描线 */}
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
                    width: 110,
                    height: 2,
                    background:
                      'linear-gradient(90deg, rgba(59,130,246,0.4) 0%, transparent 100%)',
                    transform: 'translateY(-1px)',
                  }}
                />
              </div>
              {/* 客户点 */}
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
                  3位高意向客户在附近
                </p>
                <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
                  最近280m · 点击查看详情
                </p>
              </div>
              <button
                className="btn-primary flex-shrink-0"
                onClick={(e) => {
                  e.stopPropagation();
                  setShowRadar(true);
                }}
              >
                查看雷达
                <ChevronRight className="w-4 h-4" strokeWidth={2.5} />
              </button>
            </div>
          </div>
        </div>

        {/* 最近客户动态 */}
        <div className="px-5 pb-6 animate-slideUp" style={{ animationDelay: '180ms' }}>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
              最近客户动态
            </h2>
            <button
              onClick={() => setActiveTab('customers')}
              className="text-xs font-medium flex items-center gap-0.5"
              style={{ color: 'var(--text-secondary)' }}
            >
              查看全部
              <ChevronRight className="w-3 h-3" />
            </button>
          </div>

          <div className="space-y-2.5">
            {nearbyHighIntent.map((c, i) => (
              <div
                key={c.id}
                className="card p-3.5 flex items-center gap-3 animate-slideInRight cursor-pointer"
                style={{ animationDelay: `${240 + i * 80}ms` }}
                onClick={() => setSelectedCustomer(c.id)}
              >
                <Avatar name={c.avatar} color={c.avatarColor} size={44} grade={c.grade} showGrade />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-[15px] font-semibold" style={{ color: 'var(--text-primary)' }}>
                      {c.name}
                    </span>
                  </div>
                  <p className="text-xs mt-0.5 truncate" style={{ color: 'var(--text-secondary)' }}>
                    {c.statusText} · {c.statusSub}
                  </p>
                </div>
                <button
                  className="w-9 h-9 rounded-full flex items-center justify-center"
                  style={{ background: 'var(--surface-2)' }}
                  onClick={(e) => {
                    e.stopPropagation();
                    showToast(`正在拨打 ${c.phone}`, '📞');
                  }}
                  aria-label="拨打电话"
                >
                  <Phone className="w-4 h-4" style={{ color: 'var(--primary)' }} />
                </button>
              </div>
            ))}
          </div>

          {/* P2 成就条 */}
          <div
            className="mt-4 card p-3.5 flex items-center gap-3"
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
