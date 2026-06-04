import { useEffect, useRef, useState } from 'react';
import { useInView, useMotionValue, animate } from 'framer-motion';

interface Props {
  value: number;
  duration?: number;
  decimals?: number;
  prefix?: string;
  suffix?: string;
  /** 千分位格式化 */
  format?: 'comma' | 'wan' | 'none';
  className?: string;
}

const formatNumber = (n: number, fmt: Props['format'], decimals: number) => {
  if (fmt === 'wan' && n >= 10000) {
    return `${(n / 10000).toFixed(decimals)}万`;
  }
  if (fmt === 'comma') {
    return n.toLocaleString('zh-CN', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    });
  }
  return n.toFixed(decimals);
};

/**
 * AnimatedNumber · 进入视口时,数字从 0 滚动到目标值
 * 借鉴:美团商家 / 抖音来客 / 微盟 数字滚动效果
 */
export default function AnimatedNumber({
  value,
  duration = 1.2,
  decimals = 0,
  prefix = '',
  suffix = '',
  format = 'none',
  className = '',
}: Props) {
  const ref = useRef<HTMLSpanElement>(null);
  const inView = useInView(ref, { once: true, amount: 0.3 });
  const motion = useMotionValue(0);
  const [display, setDisplay] = useState('0');

  useEffect(() => {
    if (!inView) return;
    const controls = animate(motion, value, {
      duration,
      ease: [0.16, 1, 0.3, 1],
      onUpdate: (v) => setDisplay(formatNumber(v, format, decimals)),
    });
    return () => controls.stop();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inView, value]);

  return (
    <span ref={ref} className={className}>
      {prefix}
      {display}
      {suffix}
    </span>
  );
}
