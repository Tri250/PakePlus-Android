import { useAppStore } from '../store/appStore';
import { X, Camera, Mic, User, Phone, MapPin } from 'lucide-react';
import { useState } from 'react';

export default function AddCustomerSheet() {
  const open = useAppStore((s) => s.showAddCustomer);
  const setOpen = useAppStore((s) => s.setShowAddCustomer);
  const showToast = useAppStore((s) => s.showToast);

  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');

  if (!open) return null;

  const submit = () => {
    if (!name.trim()) {
      showToast('请输入客户姓名', '⚠️');
      return;
    }
    showToast(`客户 ${name} 已成功添加`, '✓');
    setName('');
    setPhone('');
    setOpen(false);
  };

  return (
    <>
      <div className="sheet-mask" onClick={() => setOpen(false)} />
      <div className="sheet" onClick={(e) => e.stopPropagation()}>
        <div className="sheet-handle" />
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold">添加客户</h3>
          <button
            onClick={() => setOpen(false)}
            className="w-8 h-8 rounded-full flex items-center justify-center"
            style={{ background: 'var(--surface-2)' }}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* 拍照识别 / 语音录入 */}
        <div className="grid grid-cols-2 gap-2.5 mb-4">
          <button
            className="card p-4 flex flex-col items-center gap-2"
            style={{ background: 'rgba(59,130,246,0.06)' }}
            onClick={() => showToast('相机已就绪，请拍摄名片', '📸')}
          >
            <div
              className="w-10 h-10 rounded-full flex items-center justify-center"
              style={{ background: '#3b82f6', color: '#fff' }}
            >
              <Camera className="w-5 h-5" />
            </div>
            <p className="text-sm font-semibold" style={{ color: 'var(--primary)' }}>
              拍照识别名片
            </p>
            <p className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
              自动填充信息
            </p>
          </button>
          <button
            className="card p-4 flex flex-col items-center gap-2"
            style={{ background: 'rgba(245,158,11,0.06)' }}
            onClick={() => showToast('请说出客户信息', '🎙️')}
          >
            <div
              className="w-10 h-10 rounded-full flex items-center justify-center"
              style={{ background: '#f59e0b', color: '#fff' }}
            >
              <Mic className="w-5 h-5" />
            </div>
            <p className="text-sm font-semibold" style={{ color: '#d97706' }}>
              语音录入
            </p>
            <p className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
              语音转文字
            </p>
          </button>
        </div>

        <div className="space-y-3">
          <Field
            icon={<User className="w-4 h-4" />}
            label="客户姓名"
            value={name}
            onChange={setName}
            placeholder="请输入姓名"
          />
          <Field
            icon={<Phone className="w-4 h-4" />}
            label="手机号"
            value={phone}
            onChange={setPhone}
            placeholder="请输入手机号"
            type="tel"
          />
          <button
            className="w-full card p-3.5 flex items-center gap-2.5"
            style={{ background: 'var(--surface-2)' }}
            onClick={() => showToast('已自动获取当前位置', '📍')}
          >
            <MapPin className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
            <span className="text-sm font-medium flex-1 text-left" style={{ color: 'var(--text-primary)' }}>
              当前位置：深圳市南山区科技园
            </span>
            <span
              className="chip"
              style={{ background: 'rgba(16,185,129,0.10)', color: '#10b981' }}
            >
              已定位
            </span>
          </button>
        </div>

        <div className="flex gap-2.5 mt-5">
          <button
            className="flex-1 h-12 rounded-2xl font-semibold"
            style={{ background: 'var(--surface-2)', color: 'var(--text-primary)' }}
            onClick={() => setOpen(false)}
          >
            取消
          </button>
          <button
            className="flex-1 h-12 rounded-2xl font-semibold"
            style={{ background: 'var(--primary)', color: '#fff' }}
            onClick={submit}
          >
            保存客户
          </button>
        </div>
      </div>
    </>
  );
}

function Field({
  icon,
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder: string;
  type?: string;
}) {
  return (
    <div>
      <p className="text-xs font-medium mb-1.5" style={{ color: 'var(--text-secondary)' }}>
        {label}
      </p>
      <div
        className="flex items-center gap-2.5 h-12 px-3.5 rounded-2xl"
        style={{ background: 'var(--surface-2)' }}
      >
        <span style={{ color: 'var(--text-muted)' }}>{icon}</span>
        <input
          type={type}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          className="flex-1 bg-transparent outline-none text-sm"
          style={{ color: 'var(--text-primary)' }}
        />
      </div>
    </div>
  );
}
