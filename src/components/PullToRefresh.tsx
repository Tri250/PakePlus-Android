import { useEffect, useRef, useState, ReactNode } from 'react';
import { RefreshCw } from 'lucide-react';

interface Props {
  onRefresh: () => Promise<void> | void;
  children: ReactNode;
  threshold?: number; // 触发刷新的下拉距离
  className?: string;
}

/**
 * Android 原生下拉刷新 (Pull-to-Refresh)
 */
export default function PullToRefresh({ onRefresh, children, threshold = 60, className = '' }: Props) {
  const [pulling, setPulling] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [pullDist, setPullDist] = useState(0);
  const startY = useRef(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const isAtTop = useRef(true);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    const onTouchStart = (e: TouchEvent) => {
      if (refreshing) return;
      if (el.scrollTop <= 0) {
        isAtTop.current = true;
        startY.current = e.touches[0].clientY;
      } else {
        isAtTop.current = false;
      }
    };

    const onTouchMove = (e: TouchEvent) => {
      if (refreshing || !isAtTop.current) return;
      const dy = e.touches[0].clientY - startY.current;
      if (dy > 0 && el.scrollTop <= 0) {
        // 阻尼
        const dist = Math.min(dy * 0.4, 120);
        setPullDist(dist);
        setPulling(dist > 10);
      }
    };

    const onTouchEnd = async () => {
      if (refreshing) return;
      if (pullDist >= threshold) {
        setRefreshing(true);
        setPullDist(threshold);
        try {
          await Promise.resolve(onRefresh());
        } finally {
          setTimeout(() => {
            setRefreshing(false);
            setPulling(false);
            setPullDist(0);
          }, 500);
        }
      } else {
        setPulling(false);
        setPullDist(0);
      }
    };

    el.addEventListener('touchstart', onTouchStart, { passive: true });
    el.addEventListener('touchmove', onTouchMove, { passive: true });
    el.addEventListener('touchend', onTouchEnd, { passive: true });

    return () => {
      el.removeEventListener('touchstart', onTouchStart);
      el.removeEventListener('touchmove', onTouchMove);
      el.removeEventListener('touchend', onTouchEnd);
    };
  }, [onRefresh, pullDist, refreshing, threshold]);

  return (
    <div ref={containerRef} className={`scroll-area ${className}`}>
      {/* 指示器 */}
      <div
        className="ptr-indicator"
        style={{
          top: pulling || refreshing ? Math.max(8, pullDist - 40) : -40,
          opacity: pulling || refreshing ? 1 : 0,
          transform: `translateX(-50%) rotate(${refreshing ? '0deg' : `${pullDist * 4}deg`})`,
        }}
      >
        <RefreshCw
          className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`}
          style={{
            transform: refreshing ? 'none' : `rotate(${pullDist * 2}deg)`,
            transition: refreshing ? 'none' : 'transform 0.1s',
          }}
        />
      </div>
      {children}
    </div>
  );
}
