import { useAppStore } from '../store/appStore';
import { roleConfig } from '../data/mockData';
import { X, Check, User, Briefcase, Building2 } from 'lucide-react';
import type { Role } from '../data/mockData';

const roles: { key: Role; icon: typeof User; tag: string; desc: string }[] = [
  { key: 'rep', icon: User, tag: '地推专员', desc: '每日任务 · 附近商机 · 客户动态' },
  { key: 'manager', icon: Briefcase, tag: '门店店长', desc: '门店数据 · 团队进度 · 异常预警' },
  { key: 'hq', icon: Building2, tag: '总部运营', desc: '全国指标 · 同比环比 · 区域排名' },
];

export default function RoleSwitcher() {
  const open = useAppStore((s) => s.showRoleSwitcher);
  const setOpen = useAppStore((s) => s.setShowRoleSwitcher);
  const role = useAppStore((s) => s.role);
  const setRole = useAppStore((s) => s.setRole);

  if (!open) return null;

  return (
    <>
      <div className="sheet-mask" onClick={() => setOpen(false)} />
      <div className="sheet" onClick={(e) => e.stopPropagation()}>
        <div className="sheet-handle" />
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold">切换角色</h3>
          <button
            onClick={() => setOpen(false)}
            className="w-8 h-8 rounded-full flex items-center justify-center"
            style={{ background: 'var(--surface-2)' }}
          >
            <X className="w-4 h-4" />
          </button>
        </div>
        <p className="text-xs mb-4" style={{ color: 'var(--text-muted)' }}>
          不同角色看到的首页内容不同，根据实际工作场景切换
        </p>
        <div className="space-y-2.5">
          {roles.map((r) => {
            const Icon = r.icon;
            const user = roleConfig[r.key];
            const isActive = role === r.key;
            return (
              <button
                key={r.key}
                onClick={() => setRole(r.key)}
                className="w-full card p-3.5 flex items-center gap-3 text-left transition-all"
                style={
                  isActive
                    ? {
                        background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)',
                        border: '1px solid #93c5fd',
                      }
                    : {}
                }
              >
                <div
                  className="w-12 h-12 rounded-2xl flex items-center justify-center flex-shrink-0"
                  style={{
                    background: `linear-gradient(135deg, ${user.avatarColor} 0%, ${user.avatarColor}cc 100%)`,
                    color: '#fff',
                  }}
                >
                  <Icon className="w-5 h-5" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                    {user.name} · {r.tag}
                  </p>
                  <p className="text-[11px] mt-0.5" style={{ color: 'var(--text-muted)' }}>
                    {r.desc}
                  </p>
                </div>
                {isActive && (
                  <div
                    className="w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0"
                    style={{ background: 'var(--primary)' }}
                  >
                    <Check className="w-3.5 h-3.5 text-white" strokeWidth={3} />
                  </div>
                )}
              </button>
            );
          })}
        </div>
      </div>
    </>
  );
}
