import { useAppStore } from '../store/appStore';
import { customers, gradeColors } from '../data/mockData';
import {
  ChevronLeft, Phone, MessageCircle, Navigation, Star, Mic, Camera,
  MapPin, TrendingUp, Calendar, Tag,
} from 'lucide-react';

export default function CustomerDetail() {
  const id = useAppStore((s) => s.selectedCustomerId);
  const setSelected = useAppStore((s) => s.setSelectedCustomer);
  const favorited = useAppStore((s) => s.favoritedCustomers);
  const toggleFav = useAppStore((s) => s.toggleFavorite);
  const showToast = useAppStore((s) => s.showToast);

  const customer = customers.find((c) => c.id === id);
  if (!customer) return null;
  const isFav = favorited.includes(customer.id);

  return (
    <div className="absolute inset-0 z-50 bg-white animate-slideInRight flex flex-col">
      {/* 顶部导航 */}
      <div className="flex items-center justify-between p-4 flex-shrink-0">
        <button
          onClick={() => setSelected(null)}
          className="w-9 h-9 rounded-full flex items-center justify-center"
          style={{ background: 'var(--surface-2)' }}
          aria-label="返回"
        >
          <ChevronLeft className="w-5 h-5" />
        </button>
        <h1 className="text-base font-semibold">客户详情</h1>
        <button
          onClick={() => toggleFav(customer.id)}
          className="w-9 h-9 rounded-full flex items-center justify-center"
          style={{ background: 'var(--surface-2)' }}
          aria-label="收藏"
        >
          <Star
            className="w-4 h-4"
            fill={isFav ? '#fbbf24' : 'none'}
            stroke={isFav ? '#fbbf24' : 'currentColor'}
          />
        </button>
      </div>

      <div className="scroll-area flex-1">
        {/* 头部 */}
        <div className="px-5 pb-5 animate-fadeIn">
          <div className="flex items-center gap-4">
            <div
              className="w-20 h-20 rounded-3xl flex items-center justify-center text-white font-bold flex-shrink-0"
              style={{
                background: `linear-gradient(135deg, ${customer.avatarColor} 0%, ${customer.avatarColor}dd 100%)`,
                fontSize: 32,
                boxShadow: `0 8px 20px ${customer.avatarColor}40`,
              }}
            >
              {customer.avatar}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <h2 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                  {customer.name}
                </h2>
                <span
                  className="chip"
                  style={{
                    background: gradeColors[customer.grade].bg,
                    color: gradeColors[customer.grade].text,
                  }}
                >
                  {customer.grade}级
                </span>
              </div>
              <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>
                {customer.phoneModel} · {customer.phone}
              </p>
              <div className="flex items-center gap-1.5 mt-2">
                <MapPin className="w-3 h-3" style={{ color: 'var(--text-muted)' }} />
                <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
                  距离 {customer.distance}m · 上次联系 {customer.lastContact}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* 意向度 */}
        <div className="px-5 mb-4 animate-slideUp" style={{ animationDelay: '60ms' }}>
          <div
            className="card p-4"
            style={{
              background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)',
              border: '1px solid #bfdbfe',
            }}
          >
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-1.5">
                <TrendingUp className="w-4 h-4" style={{ color: '#3b82f6' }} />
                <span className="text-sm font-semibold" style={{ color: '#1e40af' }}>
                  换机意向度
                </span>
              </div>
              <span className="text-2xl font-bold" style={{ color: '#3b82f6' }}>
                {customer.intentScore}
              </span>
            </div>
            <div className="progress" style={{ background: 'rgba(255,255,255,0.6)' }}>
              <div
                className="progress-bar"
                style={{
                  width: `${customer.intentScore}%`,
                  background: 'linear-gradient(90deg, #3b82f6 0%, #60a5fa 100%)',
                }}
              />
            </div>
            <p className="text-xs mt-2" style={{ color: '#1e40af' }}>
              {customer.statusText} · {customer.statusSub}
            </p>
          </div>
        </div>

        {/* 快捷操作 */}
        <div className="px-5 mb-4 animate-slideUp" style={{ animationDelay: '120ms' }}>
          <div className="grid grid-cols-4 gap-2">
            {[
              { icon: Phone, label: '拨号', color: '#10b981' },
              { icon: MessageCircle, label: '消息', color: '#3b82f6' },
              { icon: Navigation, label: '导航', color: '#8b5cf6' },
              { icon: Mic, label: '语音', color: '#f59e0b' },
            ].map((a) => (
              <button
                key={a.label}
                className="flex flex-col items-center gap-1.5 p-3 rounded-2xl"
                style={{ background: 'var(--surface-2)' }}
                onClick={() => showToast(`${a.label}功能已触发`, '✨')}
              >
                <a.icon className="w-5 h-5" style={{ color: a.color }} />
                <span className="text-[11px] font-medium" style={{ color: 'var(--text-secondary)' }}>
                  {a.label}
                </span>
              </button>
            ))}
          </div>
        </div>

        {/* 标签 */}
        <div className="px-5 mb-4 animate-slideUp" style={{ animationDelay: '180ms' }}>
          <h3 className="text-sm font-semibold mb-2 flex items-center gap-1.5">
            <Tag className="w-3.5 h-3.5" />
            客户标签
          </h3>
          <div className="flex flex-wrap gap-1.5">
            {customer.tags.map((t) => (
              <span
                key={t}
                className="chip"
                style={{
                  background: 'rgba(59,130,246,0.10)',
                  color: '#3b82f6',
                }}
              >
                {t}
              </span>
            ))}
          </div>
        </div>

        {/* 服务事件时间轴 */}
        <div className="px-5 mb-4 animate-slideUp" style={{ animationDelay: '240ms' }}>
          <h3 className="text-sm font-semibold mb-3 flex items-center gap-1.5">
            <Calendar className="w-3.5 h-3.5" />
            服务事件时间轴
          </h3>
          <div
            className="card p-4"
            style={{ background: 'var(--surface-2)' }}
          >
            {[
              { time: '今天 10:30', text: '电话沟通换机方案，客户表示感兴趣', type: 'call' },
              { time: '昨天 16:42', text: '到店体验新机，预约下周回访', type: 'visit' },
              { time: '3天前', text: '扫码加入会员，标记为潜客', type: 'system' },
              { time: '1周前', text: '首次接触，添加企微', type: 'system' },
            ].map((ev, i) => (
              <div
                key={i}
                className="flex gap-3 pb-3 last:pb-0"
                style={{
                  borderLeft: i < 3 ? '2px solid var(--border)' : 'none',
                  marginLeft: 6,
                  paddingLeft: 14,
                  position: 'relative',
                }}
              >
                <div
                  className="absolute rounded-full"
                  style={{
                    width: 10,
                    height: 10,
                    left: -6,
                    top: 4,
                    background:
                      ev.type === 'call'
                        ? '#10b981'
                        : ev.type === 'visit'
                        ? '#3b82f6'
                        : '#94a3b8',
                    border: '2px solid #fff',
                  }}
                />
                <div className="flex-1">
                  <p className="text-[11px] font-medium" style={{ color: 'var(--text-muted)' }}>
                    {ev.time}
                  </p>
                  <p className="text-sm mt-0.5" style={{ color: 'var(--text-primary)' }}>
                    {ev.text}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* AI 建议 */}
        <div className="px-5 mb-6 animate-slideUp" style={{ animationDelay: '300ms' }}>
          <div
            className="card p-4"
            style={{
              background: 'linear-gradient(135deg, #f3e8ff 0%, #e9d5ff 100%)',
              border: '1px solid #d8b4fe',
            }}
          >
            <p className="text-xs font-semibold mb-1.5" style={{ color: '#6b21a8' }}>
              🤖 AI 话术推荐
            </p>
            <p className="text-sm" style={{ color: '#581c87' }}>
              "王总您好，上次您关注的 {customer.phoneModel} 现在有以旧换新补贴，
              最高可抵 ¥2000，本周到店还有专属礼品。"
            </p>
            <div className="flex gap-2 mt-3">
              <button
                className="flex-1 h-8 rounded-lg text-xs font-semibold"
                style={{ background: '#fff', color: '#6b21a8' }}
                onClick={() => showToast('话术已复制到剪贴板', '📋')}
              >
                复制话术
              </button>
              <button
                className="flex-1 h-8 rounded-lg text-xs font-semibold"
                style={{ background: '#7c3aed', color: '#fff' }}
                onClick={() => showToast('话术已通过企微发送', '💬')}
              >
                一键发送
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* 底部固定操作栏 */}
      <div
        className="flex-shrink-0 p-3 flex items-center gap-2"
        style={{
          background: 'rgba(255,255,255,0.95)',
          backdropFilter: 'blur(20px)',
          borderTop: '1px solid var(--border)',
        }}
      >
        <button
          className="w-12 h-12 rounded-2xl flex items-center justify-center"
          style={{ background: 'var(--surface-2)' }}
          onClick={() => showToast('相机已就绪，可拍摄名片自动识别', '📸')}
        >
          <Camera className="w-5 h-5" style={{ color: 'var(--text-secondary)' }} />
        </button>
        <button
          className="flex-1 h-12 rounded-2xl flex items-center justify-center gap-2 font-semibold"
          style={{ background: 'rgba(16,185,129,0.10)', color: '#10b981' }}
          onClick={() => showToast(`正在拨打 ${customer.phone}...`, '📞')}
        >
          <Phone className="w-4 h-4" />
          拨打
        </button>
        <button
          className="flex-1 h-12 rounded-2xl flex items-center justify-center gap-2 font-semibold"
          style={{ background: 'var(--primary)', color: '#fff' }}
          onClick={() => showToast('正在发起即时消息', '💬')}
        >
          <MessageCircle className="w-4 h-4" />
          跟进记录
        </button>
      </div>
    </div>
  );
}
