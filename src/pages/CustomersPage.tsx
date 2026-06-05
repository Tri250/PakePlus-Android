import { Search, Phone, Star, MessageCircle, Filter, Mic } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { customers, gradeColors, type Grade } from '../data/mockData';
import PullToRefresh from '../components/PullToRefresh';
import { hapticClick, hapticSuccess } from '../hooks/useAndroidBack';
import { useMemo } from 'react';

export default function CustomersPage() {
  const search = useAppStore((s) => s.customerSearch);
  const setSearch = useAppStore((s) => s.setCustomerSearch);
  const filter = useAppStore((s) => s.customerFilter);
  const setFilter = useAppStore((s) => s.setCustomerFilter);
  const setSelectedCustomer = useAppStore((s) => s.setSelectedCustomer);
  const showToast = useAppStore((s) => s.showToast);

  const filtered = useMemo(() => {
    return customers
      .filter((c) => filter === 'all' || c.grade === filter)
      .filter((c) =>
        search.trim() === '' ||
        c.name.includes(search.trim()) ||
        c.phoneModel.toLowerCase().includes(search.trim().toLowerCase())
      )
      .sort((a, b) => b.intentScore - a.intentScore);
  }, [search, filter]);

  const onRefresh = async () => {
    await new Promise((r) => setTimeout(r, 700));
    hapticSuccess();
    showToast('客户列表已更新', '✓');
  };

  return (
    <div className="flex flex-col h-full">
      <PullToRefresh onRefresh={onRefresh}>
        {/* 页面标题 */}
        <div className="px-5 pt-1 pb-3 animate-fadeIn">
          <h1 className="text-[28px] font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            客户资产
          </h1>
          <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
            共 {customers.length} 位客户 · 高意向 {customers.filter((c) => c.intentScore >= 75).length} 位
          </p>
        </div>

        {/* 搜索框 */}
        <div className="px-5 mb-3 animate-slideUp" style={{ animationDelay: '60ms' }}>
          <div
            className="flex items-center gap-2 h-11 px-3.5 rounded-2xl"
            style={{ background: 'var(--surface-2)' }}
          >
            <Search className="w-4 h-4" style={{ color: 'var(--text-muted)' }} />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索客户姓名、手机型号..."
              className="flex-1 bg-transparent outline-none text-sm"
              style={{ color: 'var(--text-primary)' }}
            />
            <button
              onClick={() => showToast('请说出客户姓名或手机型号', '🎙️')}
              aria-label="语音搜索"
            >
              <Mic className="w-4 h-4" style={{ color: 'var(--text-muted)' }} />
            </button>
          </div>
        </div>

        {/* 等级筛选 chips */}
        <div
          className="px-5 mb-3 flex gap-2 overflow-x-auto animate-slideUp"
          style={{ animationDelay: '120ms', scrollbarWidth: 'none' }}
        >
          {(['all', 'S', 'A', 'B', 'C', 'D'] as const).map((g) => (
            <button
              key={g}
              onClick={() => setFilter(g)}
              className="px-3.5 h-8 rounded-full text-xs font-semibold whitespace-nowrap flex-shrink-0 transition-colors"
              style={{
                background: filter === g ? 'var(--primary)' : '#fff',
                color: filter === g ? '#fff' : 'var(--text-primary)',
                border: filter === g ? 'none' : '1px solid var(--border)',
              }}
            >
              {g === 'all' ? '全部' : `${g}级`}
              {g !== 'all' && (
                <span className="ml-1 opacity-70">
                  {customers.filter((c) => c.grade === g).length}
                </span>
              )}
            </button>
          ))}
        </div>

        {/* 列表 */}
        <div className="px-5 pb-6">
          {filtered.length === 0 ? (
            <EmptyState />
          ) : (
            <div className="space-y-2.5">
              {filtered.map((c, i) => (
                <CustomerCard
                  key={c.id}
                  customer={c}
                  delay={i * 40}
                  onClick={() => setSelectedCustomer(c.id)}
                  onCall={() => showToast(`正在拨打 ${c.phone}`, '📞')}
                />
              ))}
            </div>
          )}
        </div>
      </PullToRefresh>
    </div>
  );
}

function CustomerCard({
  customer: c,
  delay,
  onClick,
  onCall,
}: {
  customer: typeof customers[0];
  delay: number;
  onClick: () => void;
  onCall: () => void;
}) {
  return (
    <div
      className="card p-3.5 animate-slideInRight cursor-pointer"
      style={{ animationDelay: `${delay}ms` }}
      onClick={onClick}
    >
      <div className="flex items-center gap-3">
        <div
          className="w-12 h-12 rounded-full flex items-center justify-center text-white font-semibold flex-shrink-0"
          style={{ background: c.avatarColor, fontSize: 18 }}
        >
          {c.avatar}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <span className="text-[15px] font-semibold" style={{ color: 'var(--text-primary)' }}>
              {c.name}
            </span>
            <span
              className="chip"
              style={{
                background: gradeColors[c.grade as Grade].bg,
                color: gradeColors[c.grade as Grade].text,
              }}
            >
              {c.grade}级
            </span>
            {c.intentScore >= 85 && (
              <span
                className="chip"
                style={{
                  background: '#fee2e2',
                  color: '#dc2626',
                }}
              >
                🔥 高意向
              </span>
            )}
          </div>
          <p className="text-xs mt-0.5 truncate" style={{ color: 'var(--text-secondary)' }}>
            {c.phoneModel} · {c.statusText}
          </p>
          <div className="flex items-center gap-1.5 mt-1.5">
            <span
              className="chip"
              style={{
                background: 'var(--surface-2)',
                color: 'var(--text-secondary)',
              }}
            >
              📍 {c.distance}m
            </span>
            <span
              className="chip"
              style={{
                background: 'var(--surface-2)',
                color: 'var(--text-secondary)',
              }}
            >
              意向 {c.intentScore}
            </span>
            {c.tags.slice(0, 1).map((t) => (
              <span
                key={t}
                className="chip"
                style={{
                  background: 'rgba(139,92,246,0.10)',
                  color: '#7c3aed',
                }}
              >
                {t}
              </span>
            ))}
          </div>
        </div>
        <div className="flex flex-col gap-1.5">
          <button
            onClick={(e) => {
              e.stopPropagation();
              onCall();
            }}
            className="w-9 h-9 rounded-full flex items-center justify-center"
            style={{ background: 'rgba(16,185,129,0.10)' }}
            aria-label="拨打电话"
          >
            <Phone className="w-4 h-4" style={{ color: '#10b981' }} />
          </button>
          <button
            onClick={(e) => {
              e.stopPropagation();
            }}
            className="w-9 h-9 rounded-full flex items-center justify-center"
            style={{ background: 'rgba(59,130,246,0.10)' }}
            aria-label="发起会话"
          >
            <MessageCircle className="w-4 h-4" style={{ color: '#3b82f6' }} />
          </button>
        </div>
      </div>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center py-16 animate-fadeIn">
      <img
        src="https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=Minimalist%20flat%20illustration%20empty%20box%20with%20magnifying%20glass%2C%20soft%20blue%20gray%20palette%2C%20rounded%20corners%2C%20no%20text%2C%20isolated%20on%20white%20background%2C%20modern%20ui%20style&image_size=square"
        alt="空状态"
        className="w-32 h-32 object-contain opacity-80"
      />
      <p className="text-sm font-semibold mt-4" style={{ color: 'var(--text-primary)' }}>
        暂无符合条件的客户
      </p>
      <p className="text-xs mt-1" style={{ color: 'var(--text-muted)' }}>
        试试调整筛选条件或搜索关键词
      </p>
      <button
        className="btn-primary mt-4"
        onClick={() => {
          const store = useAppStore.getState();
          store.setCustomerFilter('all');
          store.setCustomerSearch('');
        }}
      >
        重置筛选
      </button>
    </div>
  );
}
