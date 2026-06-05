import { useAppStore } from '../store/appStore';
import { AlertTriangle, X, Phone, MapPin, Mic } from 'lucide-react';
import { useState } from 'react';

export default function SOSPanel() {
  const open = useAppStore((s) => s.showSOS);
  const setOpen = useAppStore((s) => s.setShowSOS);
  const showToast = useAppStore((s) => s.showToast);
  const [countdown, setCountdown] = useState<number | null>(null);

  if (!open) return null;

  const triggerSOS = () => {
    setCountdown(3);
    let n = 3;
    const t = setInterval(() => {
      n -= 1;
      if (n <= 0) {
        clearInterval(t);
        setCountdown(null);
        setOpen(false);
        showToast('SOS 已发送，店长将在 30s 内回复', '🚨');
      } else {
        setCountdown(n);
      }
    }, 1000);
  };

  return (
    <>
      <div className="sheet-mask" onClick={() => setOpen(false)} />
      <div className="sheet" onClick={(e) => e.stopPropagation()}>
        <div className="sheet-handle" />
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <div
              className="w-10 h-10 rounded-full flex items-center justify-center animate-pulse"
              style={{ background: '#fee2e2' }}
            >
              <AlertTriangle className="w-5 h-5" style={{ color: '#dc2626' }} />
            </div>
            <div>
              <h3 className="text-lg font-bold" style={{ color: '#dc2626' }}>
                一键救援
              </h3>
              <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                紧急情况时使用，自动附带位置
              </p>
            </div>
          </div>
          <button
            onClick={() => setOpen(false)}
            className="w-8 h-8 rounded-full flex items-center justify-center"
            style={{ background: 'var(--surface-2)' }}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {countdown !== null ? (
          <div className="py-8 flex flex-col items-center">
            <div
              className="w-20 h-20 rounded-full flex items-center justify-center text-4xl font-bold animate-shake"
              style={{ background: '#dc2626', color: '#fff' }}
            >
              {countdown}
            </div>
            <p className="text-sm font-semibold mt-4" style={{ color: '#dc2626' }}>
              正在呼叫紧急联系人...
            </p>
            <p className="text-xs mt-1" style={{ color: 'var(--text-muted)' }}>
              点击任意位置取消
            </p>
          </div>
        ) : (
          <>
            <div
              className="card p-3 mb-3 flex items-center gap-2.5"
              style={{ background: 'var(--surface-2)' }}
            >
              <MapPin className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
              <div className="flex-1 min-w-0">
                <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                  当前位置
                </p>
                <p className="text-sm font-medium truncate" style={{ color: 'var(--text-primary)' }}>
                  深圳市南山区科技园南路 18 号
                </p>
              </div>
            </div>

            <div className="space-y-2">
              {[
                { name: '李美华 (店长)', phone: '138****0011', color: '#dc2626' },
                { name: '王经理 (区域)', phone: '139****0099', color: '#f59e0b' },
                { name: '客服中心', phone: '400-888-0000', color: '#3b82f6' },
              ].map((c) => (
                <button
                  key={c.name}
                  className="w-full card p-3 flex items-center gap-3 text-left"
                  onClick={() => showToast(`正在拨打 ${c.name}`, '📞')}
                >
                  <div
                    className="w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0"
                    style={{ background: `${c.color}15`, color: c.color }}
                  >
                    <Phone className="w-4 h-4" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                      {c.name}
                    </p>
                    <p className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                      {c.phone}
                    </p>
                  </div>
                  <span
                    className="chip"
                    style={{ background: `${c.color}15`, color: c.color }}
                  >
                    拨打
                  </span>
                </button>
              ))}
            </div>

            <button
              onClick={triggerSOS}
              className="w-full h-14 rounded-2xl font-bold mt-4 animate-pulse"
              style={{
                background: 'linear-gradient(135deg, #dc2626 0%, #b91c1c 100%)',
                color: '#fff',
                boxShadow: '0 8px 20px rgba(220, 38, 38, 0.40)',
              }}
            >
              🚨 立即呼叫店长（3秒后自动拨打）
            </button>

            <p className="text-[11px] text-center mt-3" style={{ color: 'var(--text-muted)' }}>
              也可摇一摇手机或长按音量键触发
            </p>
          </>
        )}
      </div>
    </>
  );
}
