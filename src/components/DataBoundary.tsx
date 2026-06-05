import { Loader2, AlertCircle, RefreshCw } from 'lucide-react';
import { hapticClick } from '../hooks/useAndroidBack';

interface Props {
  loading: boolean;
  error: Error | null;
  onRetry?: () => void;
  children: React.ReactNode;
  loadingText?: string;
  errorText?: string;
}

/**
 * 数据状态包装 - 处理 loading/error/empty 状态
 */
export default function DataBoundary({
  loading,
  error,
  onRetry,
  children,
  loadingText = '加载中…',
  errorText = '加载失败',
}: Props) {
  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-12 animate-fadeIn">
        <Loader2 className="w-6 h-6 animate-spin" style={{ color: 'var(--primary)' }} />
        <p className="text-xs mt-2" style={{ color: 'var(--text-muted)' }}>
          {loadingText}
        </p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-12 animate-fadeIn">
        <div
          className="w-12 h-12 rounded-full flex items-center justify-center"
          style={{ background: 'rgba(239, 68, 68, 0.10)' }}
        >
          <AlertCircle className="w-6 h-6" style={{ color: '#ef4444' }} />
        </div>
        <p className="text-sm font-semibold mt-3" style={{ color: 'var(--text-primary)' }}>
          {errorText}
        </p>
        <p className="text-xs mt-1" style={{ color: 'var(--text-muted)' }}>
          {error.message || '网络异常'}
        </p>
        {onRetry && (
          <button
            onClick={() => {
              hapticClick();
              onRetry();
            }}
            className="btn-primary mt-4"
            style={{ padding: '8px 16px' }}
          >
            <RefreshCw className="w-3.5 h-3.5" />
            重试
          </button>
        )}
      </div>
    );
  }

  return <>{children}</>;
}
