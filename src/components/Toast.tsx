/**
 * Toast 通知组件 - 基于 react-hot-toast
 * 支持 success / error / loading / warning 四种类型
 */
import toast, { Toaster } from 'react-hot-toast';
import { CheckCircle2, XCircle, AlertCircle, Loader2, X } from 'lucide-react';

export { toast };

// 自定义 Toast 样式
export const toastOptions = {
  duration: 4000,
  position: 'top-right' as const,
  style: {
    background: '#fff',
    color: '#1f2937',
    padding: '12px 16px',
    borderRadius: '12px',
    boxShadow: '0 10px 40px rgba(0,0,0,0.12)',
    border: '1px solid #e5e7eb',
    maxWidth: '400px',
  },
};

// 成功 Toast
export function toastSuccess(message: string) {
  return toast.custom((t) => (
    <div
      className={`flex items-start gap-3 p-4 bg-white rounded-xl border border-emerald-200 shadow-lg ${
        t.visible ? 'animate-enter' : 'animate-leave'
      }`}
    >
      <CheckCircle2 className="w-5 h-5 text-emerald-600 flex-shrink-0 mt-0.5" />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-gray-900">{message}</p>
      </div>
      <button onClick={() => toast.dismiss(t.id)} className="p-1 hover:bg-gray-100 rounded">
        <X className="w-4 h-4 text-gray-400" />
      </button>
    </div>
  ));
}

// 错误 Toast
export function toastError(message: string) {
  return toast.custom((t) => (
    <div
      className={`flex items-start gap-3 p-4 bg-white rounded-xl border border-red-200 shadow-lg ${
        t.visible ? 'animate-enter' : 'animate-leave'
      }`}
    >
      <XCircle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-gray-900">{message}</p>
      </div>
      <button onClick={() => toast.dismiss(t.id)} className="p-1 hover:bg-gray-100 rounded">
        <X className="w-4 h-4 text-gray-400" />
      </button>
    </div>
  ));
}

// 警告 Toast
export function toastWarning(message: string) {
  return toast.custom((t) => (
    <div
      className={`flex items-start gap-3 p-4 bg-white rounded-xl border border-amber-200 shadow-lg ${
        t.visible ? 'animate-enter' : 'animate-leave'
      }`}
    >
      <AlertCircle className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-gray-900">{message}</p>
      </div>
      <button onClick={() => toast.dismiss(t.id)} className="p-1 hover:bg-gray-100 rounded">
        <X className="w-4 h-4 text-gray-400" />
      </button>
    </div>
  ));
}

// Loading Toast
export function toastLoading(message: string) {
  return toast.custom((t) => (
    <div
      className={`flex items-start gap-3 p-4 bg-white rounded-xl border border-blue-200 shadow-lg ${
        t.visible ? 'animate-enter' : 'animate-leave'
      }`}
    >
      <Loader2 className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5 animate-spin" />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-gray-900">{message}</p>
      </div>
    </div>
  ));
}

// Toast 容器组件
export function ToastContainer() {
  return (
    <Toaster
      position="top-right"
      toastOptions={{
        duration: 4000,
      }}
      containerStyle={{
        top: 80,
        right: 20,
      }}
    />
  );
}
