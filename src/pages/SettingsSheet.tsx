import { useAppStore } from '../store/appStore';
import { X, Sun, Moon, WifiOff, Bell, HelpCircle, Settings as SettingsIcon, Vibrate, MapPin } from 'lucide-react';

export default function SettingsSheet() {
  const open = useAppStore((s) => s.showSettings);
  const setOpen = useAppStore((s) => s.setShowSettings);
  const outdoorMode = useAppStore((s) => s.outdoorMode);
  const toggleOutdoor = useAppStore((s) => s.toggleOutdoor);
  const offline = useAppStore((s) => s.offline);
  const toggleOffline = useAppStore((s) => s.toggleOffline);
  const showToast = useAppStore((s) => s.showToast);

  if (!open) return null;

  return (
    <>
      <div className="sheet-mask" onClick={() => setOpen(false)} />
      <div className="sheet" onClick={(e) => e.stopPropagation()}>
        <div className="sheet-handle" />
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold">设置</h3>
          <button
            onClick={() => setOpen(false)}
            className="w-8 h-8 rounded-full flex items-center justify-center"
            style={{ background: 'var(--surface-2)' }}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="space-y-2">
          <ToggleRow
            icon={outdoorMode ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
            iconBg="rgba(245,158,11,0.10)"
            iconColor="#f59e0b"
            title="户外模式"
            desc="高对比度 + 大字号，阳光下清晰可见"
            value={outdoorMode}
            onChange={toggleOutdoor}
          />
          <ToggleRow
            icon={<WifiOff className="w-4 h-4" />}
            iconBg="rgba(59,130,246,0.10)"
            iconColor="#3b82f6"
            title="离线模式"
            desc="本地缓存可用，恢复网络后自动同步"
            value={offline}
            onChange={toggleOffline}
          />
          <ToggleRow
            icon={<Bell className="w-4 h-4" />}
            iconBg="rgba(139,92,246,0.10)"
            iconColor="#8b5cf6"
            title="智能推送"
            desc="地理围栏 · 换机预测 · 任务提醒"
            value={true}
            onChange={() => showToast('推送已开启', '🔔')}
          />
          <ToggleRow
            icon={<Vibrate className="w-4 h-4" />}
            iconBg="rgba(16,185,129,0.10)"
            iconColor="#10b981"
            title="摇一摇救援"
            desc="摇动手机触发 SOS 一键救援"
            value={true}
            onChange={() => showToast('摇一摇已开启', '📳')}
          />
          <ToggleRow
            icon={<MapPin className="w-4 h-4" />}
            iconBg="rgba(239,68,68,0.10)"
            iconColor="#ef4444"
            title="位置水印"
            desc="拍照打卡自动附带位置信息"
            value={true}
            onChange={() => showToast('位置水印已开启', '📍')}
          />
        </div>

        <div
          className="mt-4 p-3 rounded-2xl space-y-1"
          style={{ background: 'var(--surface-2)' }}
        >
          <button
            onClick={() => showToast('正在打开帮助中心', '❓')}
            className="w-full flex items-center gap-3 py-2.5"
          >
            <HelpCircle className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
            <span className="text-sm flex-1 text-left" style={{ color: 'var(--text-primary)' }}>
              帮助中心
            </span>
          </button>
          <button
            onClick={() => showToast('当前版本 V2.0.0', 'ℹ️')}
            className="w-full flex items-center gap-3 py-2.5"
          >
            <SettingsIcon className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
            <span className="text-sm flex-1 text-left" style={{ color: 'var(--text-primary)' }}>
              关于掌上商客
            </span>
            <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
              V2.0.0
            </span>
          </button>
        </div>
      </div>
    </>
  );
}

function ToggleRow({
  icon,
  iconBg,
  iconColor,
  title,
  desc,
  value,
  onChange,
}: {
  icon: React.ReactNode;
  iconBg: string;
  iconColor: string;
  title: string;
  desc: string;
  value: boolean;
  onChange: () => void;
}) {
  return (
    <button
      onClick={onChange}
      className="w-full card p-3 flex items-center gap-3 text-left"
    >
      <div
        className="w-9 h-9 rounded-full flex items-center justify-center flex-shrink-0"
        style={{ background: iconBg, color: iconColor }}
      >
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
          {title}
        </p>
        <p className="text-[11px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
          {desc}
        </p>
      </div>
      <div
        className="w-11 h-6 rounded-full relative transition-colors flex-shrink-0"
        style={{ background: value ? 'var(--primary)' : '#d4d4d4' }}
      >
        <span
          className="absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-all"
          style={{ left: value ? 22 : 2 }}
        />
      </div>
    </button>
  );
}
