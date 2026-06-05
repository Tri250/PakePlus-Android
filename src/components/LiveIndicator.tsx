import { useEffect, useState } from 'react';
import { Radio } from 'lucide-react';

interface Props {
  fetchedAt: number;
  source?: 'api' | 'crawler' | 'cache' | 'mock' | 'synthetic' | null;
  className?: string;
}

/**
 * 实时数据指示器 - 显示数据源/新鲜度
 * 5s 内：绿色脉冲 (实时)
 * 60s 内：蓝色
 * 超过：灰色 (过期)
 */
export default function LiveIndicator({ fetchedAt, source, className = '' }: Props) {
  const [, force] = useState(0);
  useEffect(() => {
    const t = setInterval(() => force((x) => x + 1), 1000);
    return () => clearInterval(t);
  }, []);

  const age = Date.now() - fetchedAt;
  const isLive = age < 5000;
  const isFresh = age < 60_000;

  const color = !fetchedAt
    ? '#94a3b8'
    : isLive
    ? '#10b981'
    : isFresh
    ? '#3b82f6'
    : '#94a3b8';

  const label = !fetchedAt
    ? '加载中'
    : isLive
    ? '实时'
    : isFresh
    ? `${Math.floor(age / 1000)}秒前`
    : `${Math.floor(age / 60_000)}分钟前`;

  return (
    <div
      className={`inline-flex items-center gap-1.5 ${className}`}
      style={{ color }}
      title={source ? `数据源: ${source}` : undefined}
    >
      <div
        className={isLive ? 'animate-pulse' : ''}
        style={{
          width: 6,
          height: 6,
          borderRadius: '50%',
          background: color,
          boxShadow: isLive ? `0 0 0 4px ${color}30` : 'none',
        }}
      />
      <span className="text-[10px] font-semibold tracking-wide uppercase">
        {label}
      </span>
    </div>
  );
}
