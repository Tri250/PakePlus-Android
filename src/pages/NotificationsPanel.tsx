import { useAppStore } from '../store/appStore';
import { notifications } from '../data/mockData';
import { X, MapPin, TrendingUp, ListTodo, Bell, Check } from 'lucide-react';
import { useState } from 'react';

const iconMap = {
  geo: <MapPin className="w-4 h-4" style={{ color: '#3b82f6' }} />,
  predict: <TrendingUp className="w-4 h-4" style={{ color: '#8b5cf6' }} />,
  task: <ListTodo className="w-4 h-4" style={{ color: '#10b981' }} />,
  system: <Bell className="w-4 h-4" style={{ color: '#f59e0b' }} />,
};

const bgMap = {
  geo: 'rgba(59,130,246,0.10)',
  predict: 'rgba(139,92,246,0.10)',
  task: 'rgba(16,185,129,0.10)',
  system: 'rgba(245,158,11,0.10)',
};

export default function NotificationsPanel() {
  const open = useAppStore((s) => s.showNotifications);
  const setOpen = useAppStore((s) => s.setShowNotifications);
  const showToast = useAppStore((s) => s.showToast);
  const [readIds, setReadIds] = useState<Set<string>>(new Set());

  if (!open) return null;

  const allRead = () => {
    setReadIds(new Set(notifications.map((n) => n.id)));
    showToast('已全部标记为已读', '✓');
  };

  return (
    <>
      <div className="sheet-mask" onClick={() => setOpen(false)} />
      <div className="sheet" onClick={(e) => e.stopPropagation()}>
        <div className="sheet-handle" />
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
              消息通知
            </h3>
            <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
              {notifications.filter((n) => n.unread && !readIds.has(n.id)).length} 条未读 · 每日最多 5 条
            </p>
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={allRead}
              className="text-xs font-semibold flex items-center gap-1 px-2.5 h-8 rounded-full"
              style={{ background: 'var(--surface-2)', color: 'var(--text-secondary)' }}
            >
              <Check className="w-3 h-3" />
              全部已读
            </button>
            <button
              onClick={() => setOpen(false)}
              className="w-8 h-8 rounded-full flex items-center justify-center"
              style={{ background: 'var(--surface-2)' }}
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>
        <div className="space-y-2.5">
          {notifications.map((n) => {
            const isUnread = n.unread && !readIds.has(n.id);
            return (
              <div
                key={n.id}
                className="card p-3.5 flex gap-3 cursor-pointer"
                onClick={() => setReadIds((s) => new Set([...s, n.id]))}
                style={isUnread ? { background: 'rgba(59,130,246,0.04)' } : {}}
              >
                <div
                  className="w-9 h-9 rounded-full flex items-center justify-center flex-shrink-0"
                  style={{ background: bgMap[n.type] }}
                >
                  {iconMap[n.type]}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-semibold flex-1" style={{ color: 'var(--text-primary)' }}>
                      {n.title}
                    </p>
                    {isUnread && (
                      <span
                        className="w-2 h-2 rounded-full flex-shrink-0"
                        style={{ background: '#ef4444' }}
                      />
                    )}
                  </div>
                  <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
                    {n.body}
                  </p>
                  <p className="text-[10px] mt-1.5" style={{ color: 'var(--text-muted)' }}>
                    {n.time}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
        {/* 勿扰设置 */}
        <div
          className="mt-4 p-3 rounded-2xl flex items-center justify-between"
          style={{ background: 'var(--surface-2)' }}
        >
          <div>
            <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
              勿扰时段
            </p>
            <p className="text-[11px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
              22:00 - 08:00 静默接收
            </p>
          </div>
          <button
            className="w-11 h-6 rounded-full relative transition-colors"
            style={{ background: 'var(--primary)' }}
          >
            <span
              className="absolute top-0.5 w-5 h-5 rounded-full bg-white shadow"
              style={{ right: 2 }}
            />
          </button>
        </div>
      </div>
    </>
  );
}
