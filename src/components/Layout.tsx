import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import {
  LayoutDashboard, Radar, Brain, Users, Megaphone, BarChart3,
  Settings, Menu, X, Bell, User, ChevronDown, ChevronRight,
  Zap, Database
} from 'lucide-react';

const navigation = [
  { 
    name: '仪表盘', 
    href: '/', 
    icon: LayoutDashboard 
  },
  { 
    name: '智能获客中枢', 
    icon: Radar,
    children: [
      { name: 'LBS雷达扫描', href: '/lbs', icon: Radar },
      { name: 'GEO优化引擎', href: '/geo-optimization', icon: Brain, badge: '2026' },
    ]
  },
  { 
    name: '客户资产库', 
    icon: Users,
    children: [
      { name: '客户管理', href: '/customers', icon: Users },
    ]
  },
  { 
    name: '地推作战系统', 
    icon: Zap,
    children: [
      { name: '营销作战', href: '/marketing', icon: Megaphone },
      { name: 'AI 作战 V2.0', href: '/ground-combat', icon: Zap, badge: '2026' },
    ]
  },
  { 
    name: '品牌数据中台', 
    icon: Database,
    children: [
      { name: '数据分析', href: '/analytics', icon: BarChart3 },
      { name: '数据中台 V2.0', href: '/brand-data', icon: Database, badge: '新' },
    ]
  },
  { 
    name: '系统设置', 
    href: '/settings',
    icon: Settings 
  },
];

export default function Layout({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [expandedMenus, setExpandedMenus] = useState<string[]>(['智能获客中枢']);

  const toggleMenu = (name: string) => {
    setExpandedMenus((prev) =>
      prev.includes(name) ? prev.filter((n) => n !== name) : [...prev, name]
    );
  };

  const isActive = (href: string) => location.pathname === href;
  const isChildActive = (children?: { href: string }[]) => 
    children?.some((child) => location.pathname === child.href);

  return (
    <div className="min-h-screen bg-gray-50">
      <aside className={`fixed inset-y-0 left-0 z-50 w-64 bg-white border-r border-gray-200 transform transition-transform duration-200 ${sidebarOpen ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="h-16 flex items-center justify-between px-4 border-b border-gray-200">
          <Link to="/" className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
              <Radar className="w-5 h-5 text-white" />
            </div>
            <span className="font-bold text-lg text-gray-900">掌上商客</span>
          </Link>
          <button onClick={() => setSidebarOpen(false)} className="p-1 rounded-md hover:bg-gray-100 lg:hidden">
            <X className="w-5 h-5 text-gray-500" />
          </button>
        </div>
        <nav className="flex-1 px-2 py-4 space-y-1 overflow-y-auto">
          {navigation.map((item) => {
            if (item.children) {
              const expanded = expandedMenus.includes(item.name);
              const active = isChildActive(item.children);
              return (
                <div key={item.name}>
                  <button
                    onClick={() => toggleMenu(item.name)}
                    className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                      active ? 'bg-blue-50 text-blue-600' : 'text-gray-700 hover:bg-gray-100'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <item.icon className="w-5 h-5" />
                      {item.name}
                    </div>
                    {expanded ? (
                      <ChevronDown className="w-4 h-4" />
                    ) : (
                      <ChevronRight className="w-4 h-4" />
                    )}
                  </button>
                  {expanded && (
                    <div className="ml-4 mt-1 space-y-1">
                      {item.children.map((child) => (
                        <Link
                          key={child.href}
                          to={child.href}
                          className={`flex items-center justify-between gap-3 px-3 py-2 rounded-lg text-sm transition-colors ${
                            isActive(child.href)
                              ? 'bg-blue-50 text-blue-600 font-medium'
                              : 'text-gray-600 hover:bg-gray-100'
                          }`}
                        >
                          <div className="flex items-center gap-3">
                            <child.icon className="w-4 h-4" />
                            {child.name}
                          </div>
                          {(child as any).badge && (
                            <span className="px-1.5 py-0.5 bg-gradient-to-r from-violet-500 to-pink-500 text-white text-[10px] font-bold rounded">
                              {(child as any).badge}
                            </span>
                          )}
                        </Link>
                      ))}
                    </div>
                  )}
                </div>
              );
            }
            return (
              <Link
                key={item.href}
                to={item.href!}
                className={`flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive(item.href!) ? 'bg-blue-50 text-blue-600' : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <item.icon className="w-5 h-5" />
                {item.name}
              </Link>
            );
          })}
        </nav>
      </aside>

      <div className={`transition-all duration-200 ${sidebarOpen ? 'lg:pl-64' : ''}`}>
        <header className="sticky top-0 z-40 h-16 bg-white border-b border-gray-200">
          <div className="flex items-center justify-between h-full px-4">
            <div className="flex items-center gap-4">
              <button onClick={() => setSidebarOpen(!sidebarOpen)} className="p-2 rounded-md hover:bg-gray-100">
                <Menu className="w-5 h-5 text-gray-500" />
              </button>
              <h1 className="text-lg font-semibold text-gray-900">
                {navigation.find((n) => n.href === location.pathname)?.name ||
                  navigation.find((n) => n.children?.some((c) => c.href === location.pathname))?.children?.find((c) => c.href === location.pathname)?.name ||
                  '掌上商客'}
              </h1>
            </div>
            <div className="flex items-center gap-4">
              <button className="relative p-2 rounded-md hover:bg-gray-100">
                <Bell className="w-5 h-5 text-gray-500" />
                <span className="absolute top-1 right-1 w-2 h-2 bg-red-500 rounded-full"></span>
              </button>
              <button className="p-2 rounded-md hover:bg-gray-100">
                <User className="w-5 h-5 text-gray-500" />
              </button>
            </div>
          </div>
        </header>
        <main className="p-6">{children}</main>
      </div>

      {sidebarOpen && <div className="fixed inset-0 z-40 bg-black/50 lg:hidden" onClick={() => setSidebarOpen(false)} />}
    </div>
  );
}
