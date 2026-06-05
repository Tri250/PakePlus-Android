/**
 * Error Boundary 错误边界组件
 * 捕获子组件的 JavaScript 错误，防止白屏崩溃
 */
import { Component, ReactNode } from 'react';
import { AlertTriangle, RefreshCw, Home, Bug } from 'lucide-react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  onError?: (error: Error, errorInfo: React.ErrorInfo) => void;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: React.ErrorInfo | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    };
  }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    this.setState({ errorInfo });
    this.props.onError?.(error, errorInfo);

    // 可以上报错误到监控系统
    console.error('[ErrorBoundary] Caught error:', error, errorInfo);
  }

  handleRetry = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    });
  };

  handleGoHome = () => {
    window.location.href = '/';
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="min-h-[400px] flex items-center justify-center p-8">
          <div className="max-w-md w-full text-center">
            <div className="w-16 h-16 mx-auto mb-4 bg-red-100 rounded-2xl flex items-center justify-center">
              <AlertTriangle className="w-8 h-8 text-red-600" />
            </div>
            <h2 className="text-lg font-semibold text-gray-900 mb-2">
              页面出错了
            </h2>
            <p className="text-sm text-gray-500 mb-4">
              很抱歉，页面遇到了一些问题。您可以尝试刷新页面或返回首页。
            </p>

            {/* 错误详情（开发环境） */}
            {this.state.error && (
              <details className="text-left mb-4 bg-gray-50 rounded-lg p-3 text-xs">
                <summary className="flex items-center gap-2 cursor-pointer text-gray-700 font-medium">
                  <Bug className="w-4 h-4" />
                  错误详情
                </summary>
                <pre className="mt-2 text-red-600 whitespace-pre-wrap overflow-auto">
                  {this.state.error.toString()}
                  {this.state.errorInfo?.componentStack}
                </pre>
              </details>
            )}

            <div className="flex gap-2 justify-center">
              <button
                onClick={this.handleRetry}
                className="px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 flex items-center gap-2"
              >
                <RefreshCw className="w-4 h-4" />
                重试
              </button>
              <button
                onClick={this.handleGoHome}
                className="px-4 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg hover:bg-gray-200 flex items-center gap-2"
              >
                <Home className="w-4 h-4" />
                返回首页
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

// 轻量级错误边界（用于小组件）
export function SimpleErrorFallback({ error, retry }: { error?: Error; retry?: () => void }) {
  return (
    <div className="p-4 bg-red-50 border border-red-200 rounded-lg text-center">
      <AlertTriangle className="w-6 h-6 text-red-600 mx-auto mb-2" />
      <p className="text-sm text-red-700">
        {error?.message || '加载失败'}
      </p>
      {retry && (
        <button
          onClick={retry}
          className="mt-2 text-xs text-red-600 underline hover:no-underline"
        >
          重试
        </button>
      )}
    </div>
  );
}
