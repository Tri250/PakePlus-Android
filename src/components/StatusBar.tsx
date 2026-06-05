import { useAppStore } from '../store/appStore';
import { Signal, BatteryFull, Wifi } from 'lucide-react';
import { useEffect, useState } from 'react';

export default function StatusBar() {
  const outdoorMode = useAppStore((s) => s.outdoorMode);
  const [time, setTime] = useState(() => formatTime(new Date()));

  useEffect(() => {
    const t = setInterval(() => setTime(formatTime(new Date())), 30_000);
    return () => clearInterval(t);
  }, []);

  return (
    <div className={`status-bar ${outdoorMode ? 'outdoor' : ''}`}>
      <span>{time}</span>
      <div className="flex items-center gap-1.5">
        <Signal className="w-3.5 h-3.5" strokeWidth={2.5} />
        <Wifi className="w-3.5 h-3.5" strokeWidth={2.5} />
        <BatteryFull className="w-5 h-5" strokeWidth={2.5} />
      </div>
    </div>
  );
}

function formatTime(d: Date) {
  const h = d.getHours().toString().padStart(2, '0');
  const m = d.getMinutes().toString().padStart(2, '0');
  return `${h}:${m}`;
}
