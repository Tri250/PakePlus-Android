import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Radar,
  Map as MapIcon,
  Sparkles,
  Workflow,
  Users2,
  LineChart,
  Settings,
  LogOut,
  ChevronDown,
} from 'lucide-react';
import { useGlobal } from '@/store/useGlobal';
import { useEffect } from 'react';
import { cn } from '@/lib/utils';

const NAV = [
  { to: '/cockpit', label: '获客驾驶舱', icon: Radar },
  { to: '/map', label: '地图工作台', icon: MapIcon },
  { to: '/persona', label: 'AI 客户画像', icon: Sparkles },
  { to: '/campaign', label: '智能营销中心', icon: Workflow },
  { to: '/leads', label: '线索池', icon: Users2 },
  { to: '/dashboard', label: '数据看板', icon: LineChart },
];

export default function Layout() {
  const nav = useNavigate();
  const loc = useLocation();
  const { user, stores, currentStoreId, setCurrentStore, logout } = useGlobal();

  useEffect(() => {
    if (loc.pathname === '/') nav('/cockpit', { replace: true });
  }, [loc.pathname, nav]);

  const currentStore = stores.find((s) => s.id === currentStoreId);

  return (
    <div className="min-h-screen flex">
      {/* 左侧导航 */}
      <aside className="w-64 shrink-0 border-r border-white/5 bg-ink-950/80 backdrop-blur-xl flex flex-col">
        <div className="px-5 pt-6 pb-5">
          <div className="flex items-center gap-2.5">
            <div className="relative">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-ember-500 to-ember-700 flex items-center justify-center shadow-glow">
                <Radar className="w-5 h-5 text-ink-950" strokeWidth={2.5} />
              </div>
              <span className="absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full bg-cyber-300 ring-2 ring-ink-950 animate-ping-slow" />
            </div>
            <div>
              <div className="text-base font-display font-bold text-white tracking-wide">邻客 AI</div>
              <div className="text-[10px] font-mono uppercase tracking-[0.2em] text-ink-400">
                Linke · 3-5-8-10
              </div>
            </div>
          </div>
        </div>

        <nav className="px-3 flex-1 space-y-0.5">
          {NAV.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) => cn('nav-item', isActive && 'nav-item-active')}
            >
              <Icon className="w-4 h-4" />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="px-3 pb-3">
          <NavLink
            to="/settings"
            className={({ isActive }) => cn('nav-item', isActive && 'nav-item-active')}
          >
            <Settings className="w-4 h-4" />
            <span>门店与成员</span>
          </NavLink>
        </div>

        {/* 门店切换 */}
        <div className="px-3 pb-3">
          <div className="panel p-3 space-y-2">
            <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400">
              当前门店
            </div>
            <div className="relative">
              <select
                value={currentStoreId || ''}
                onChange={(e) => setCurrentStore(e.target.value)}
                className="w-full appearance-none bg-ink-800 border border-white/5 rounded-lg px-3 py-2 pr-8 text-sm text-white focus:outline-none focus:border-ember-500/60"
              >
                {stores.map((s) => (
                  <option key={s.id} value={s.id} className="bg-ink-900">
                    {s.name}
                  </option>
                ))}
              </select>
              <ChevronDown className="w-4 h-4 absolute right-2 top-1/2 -translate-y-1/2 text-ink-400 pointer-events-none" />
            </div>
            {currentStore && (
              <div className="text-[11px] font-mono text-ink-400 leading-relaxed">
                {currentStore.category}<br />
                {currentStore.address}
              </div>
            )}
          </div>
        </div>

        {/* 用户 */}
        <div className="border-t border-white/5 p-3 flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-gradient-to-br from-cyber-300 to-cyber-500 flex items-center justify-center text-ink-950 font-bold text-sm">
            {user?.name?.slice(0, 1) || '店'}
          </div>
          <div className="flex-1 min-w-0">
            <div className="text-sm font-medium text-white truncate">{user?.name}</div>
            <div className="text-[11px] font-mono text-ink-400 truncate">{user?.phone}</div>
          </div>
          <button
            onClick={() => {
              logout();
              nav('/login');
            }}
            className="p-2 rounded-lg text-ink-400 hover:text-ember-300 hover:bg-white/5"
            title="退出"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </aside>

      {/* 主内容 */}
      <main className="flex-1 min-w-0 relative">
        {/* 顶部背景层 */}
        <div className="pointer-events-none absolute inset-0 bg-grid-faint bg-grid-32 opacity-30" />
        <div className="pointer-events-none absolute -top-32 -right-32 w-[480px] h-[480px] rounded-full bg-ember-500/10 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 left-1/3 w-[420px] h-[420px] rounded-full bg-cyber-300/10 blur-3xl" />

        <motion.div
          key={loc.pathname}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut' }}
          className="relative z-10"
        >
          <Outlet />
        </motion.div>
      </main>
    </div>
  );
}
