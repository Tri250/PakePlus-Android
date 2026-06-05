import { useAppStore } from '../store/appStore';
import { WifiOff, X } from 'lucide-react';

export default function OfflineBar() {
  const offline = useAppStore((s) => s.offline);
  const toggleOffline = useAppStore((s) => s.toggleOffline);

  if (!offline) return null;

  return (
    <div className="offline-bar">
      <WifiOff className="w-3.5 h-3.5" strokeWidth={2.5} />
      <span>离线模式 · 3项操作待同步</span>
      <button
        onClick={toggleOffline}
        className="ml-2 p-0.5 rounded hover:bg-white/20"
        aria-label="关闭离线提示"
      >
        <X className="w-3 h-3" />
      </button>
    </div>
  );
}
