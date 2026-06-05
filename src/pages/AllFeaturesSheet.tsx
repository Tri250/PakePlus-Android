import { useAppStore } from '../store/appStore';
import { allFeatures } from '../data/mockData';
import { X, Search } from 'lucide-react';
import { useState, useMemo } from 'react';

export default function AllFeaturesSheet() {
  const open = useAppStore((s) => s.showAllFeatures);
  const setOpen = useAppStore((s) => s.setShowAllFeatures);
  const setActiveFeature = useAppStore((s) => s.setActiveFeature);
  const showToast = useAppStore((s) => s.showToast);
  const [search, setSearch] = useState('');

  const grouped = useMemo(() => {
    const filtered = allFeatures.filter(
      (f) =>
        search.trim() === '' ||
        f.name.includes(search.trim()) ||
        f.desc.includes(search.trim())
    );
    return filtered.reduce<Record<string, typeof allFeatures>>((acc, f) => {
      if (!acc[f.category]) acc[f.category] = [];
      acc[f.category].push(f);
      return acc;
    }, {});
  }, [search]);

  if (!open) return null;

  return (
    <>
      <div className="sheet-mask" onClick={() => setOpen(false)} />
      <div
        className="absolute inset-0 z-[101] bg-white animate-slideUp flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between p-4 flex-shrink-0">
          <div>
            <h3 className="text-xl font-bold">全部功能</h3>
            <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
              {allFeatures.length} 个功能模块 · 5 大分类
            </p>
          </div>
          <button
            onClick={() => setOpen(false)}
            className="w-9 h-9 rounded-full flex items-center justify-center"
            style={{ background: 'var(--surface-2)' }}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* 搜索 */}
        <div className="px-4 mb-3 flex-shrink-0">
          <div
            className="flex items-center gap-2 h-10 px-3.5 rounded-full"
            style={{ background: 'var(--surface-2)' }}
          >
            <Search className="w-4 h-4" style={{ color: 'var(--text-muted)' }} />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索功能..."
              className="flex-1 bg-transparent outline-none text-sm"
              style={{ color: 'var(--text-primary)' }}
            />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto scroll-area">
          {Object.entries(grouped).map(([category, list]) => (
            <div key={category} className="px-4 mb-4">
              <h4
                className="text-[11px] font-bold tracking-wider uppercase mb-2 px-1"
                style={{ color: 'var(--text-muted)' }}
              >
                {category}
              </h4>
              <div className="grid grid-cols-2 gap-2.5">
                {list.map((f, i) => (
                  <button
                    key={f.id}
                    onClick={() => {
                      setOpen(false);
                      setActiveFeature(f.id);
                      showToast(`打开 ${f.name}`, f.icon);
                    }}
                    className="card p-3 text-left animate-fadeScale"
                    style={{ animationDelay: `${i * 30}ms` }}
                  >
                    <div
                      className="w-9 h-9 rounded-xl flex items-center justify-center text-xl mb-2"
                      style={{ background: `${f.color}15` }}
                    >
                      {f.icon}
                    </div>
                    <div className="flex items-center gap-1.5">
                      <p
                        className="text-[13px] font-semibold flex-1 truncate"
                        style={{ color: 'var(--text-primary)' }}
                      >
                        {f.name}
                      </p>
                      {f.badge && (
                        <span
                          className="chip"
                          style={{ background: '#fee2e2', color: '#dc2626' }}
                        >
                          {f.badge}
                        </span>
                      )}
                    </div>
                    <p
                      className="text-[10px] mt-0.5 truncate"
                      style={{ color: 'var(--text-muted)' }}
                    >
                      {f.desc}
                    </p>
                  </button>
                ))}
              </div>
            </div>
          ))}

          {Object.keys(grouped).length === 0 && (
            <div className="text-center py-12">
              <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
                未找到匹配的功能
              </p>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
