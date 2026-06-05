import { Radar, Users, MapPin, BarChart3 } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import type { TabKey } from '../store/appStore';

const tabs: { key: TabKey; label: string; icon: typeof Radar }[] = [
  { key: 'home', label: '获客', icon: Radar },
  { key: 'customers', label: '客户', icon: Users },
  { key: 'tasks', label: '地推', icon: MapPin },
  { key: 'data', label: '数据', icon: BarChart3 },
];

export default function BottomTabBar() {
  const active = useAppStore((s) => s.activeTab);
  const setActive = useAppStore((s) => s.setActiveTab);
  const outdoor = useAppStore((s) => s.outdoorMode);

  return (
    <div className={`tab-bar ${outdoor ? 'outdoor' : ''}`}>
      <div className="tab-pill">
        {tabs.map(({ key, label, icon: Icon }) => {
          const isActive = active === key;
          return (
            <button
              key={key}
              onClick={() => setActive(key)}
              className="relative flex-1 flex flex-col items-center justify-center gap-0.5 h-full rounded-2xl transition-colors"
              style={{
                color: isActive ? '#fff' : 'var(--text-muted)',
              }}
            >
              {isActive && (
                <span
                  className="absolute inset-0 rounded-2xl animate-fadeScale"
                  style={{
                    background:
                      'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
                    boxShadow: '0 6px 16px rgba(59, 130, 246, 0.35)',
                    zIndex: -1,
                  }}
                />
              )}
              <Icon
                className="w-[18px] h-[18px]"
                strokeWidth={isActive ? 2.5 : 2}
              />
              <span
                className="text-[10px] font-semibold tracking-wide"
                style={{
                  textTransform: 'uppercase',
                  letterSpacing: '0.04em',
                }}
              >
                {label}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
