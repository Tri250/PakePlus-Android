import { motion } from 'framer-motion';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface Props {
  className?: string;
  rounded?: string;
}

/**
 * 骨架占位:用于加载态
 */
export const Skeleton = ({ className = '', rounded = 'rounded-md' }: Props) => (
  <div
    className={cn(
      'relative overflow-hidden bg-white/[0.04]',
      rounded,
      'before:absolute before:inset-0 before:bg-gradient-to-r before:from-transparent before:via-white/[0.06] before:to-transparent before:translate-x-[-100%] before:animate-shimmer',
      className,
    )}
  />
);

export const StatCardSkeleton = () => (
  <div className="rounded-2xl border border-white/[0.07] bg-ink-900/60 p-5 space-y-3">
    <Skeleton className="h-3 w-1/3" />
    <Skeleton className="h-8 w-2/3" />
    <Skeleton className="h-2 w-1/2" />
  </div>
);

export const PanelSkeleton = ({ rows = 4 }: { rows?: number }) => (
  <div className="rounded-2xl border border-white/[0.07] bg-ink-900/60 p-5 space-y-3">
    <Skeleton className="h-4 w-1/4" />
    {Array.from({ length: rows }).map((_, i) => (
      <Skeleton key={i} className="h-3" rounded="rounded" />
    ))}
  </div>
);

/**
 * 空状态:带装饰插画 + 主标题 + 副标题 + 操作
 */
export function EmptyState({
  icon,
  title,
  caption,
  action,
  tone = 'ember',
}: {
  icon?: ReactNode;
  title: string;
  caption?: string;
  action?: ReactNode;
  tone?: 'ember' | 'cyber' | 'violet' | 'gold';
}) {
  const toneClass = {
    ember: 'from-ember-500/30 to-ember-500/0',
    cyber: 'from-cyber-300/30 to-cyber-300/0',
    violet: 'from-signal-violet/30 to-signal-violet/0',
    gold: 'from-signal-gold/30 to-signal-gold/0',
  }[tone];
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.96 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.4 }}
      className="relative overflow-hidden text-center py-12 px-6 rounded-2xl border border-dashed border-white/10 bg-ink-900/30"
    >
      <div className={cn('absolute -top-12 left-1/2 -translate-x-1/2 w-56 h-56 rounded-full blur-3xl opacity-50 bg-gradient-to-b', toneClass)} />
      <div className="relative">
        {icon && (
          <div className="mx-auto w-16 h-16 rounded-2xl bg-white/[0.04] border border-white/10 grid place-items-center mb-3">
            {icon}
          </div>
        )}
        <div className="text-base font-display font-semibold text-white">{title}</div>
        {caption && <div className="text-xs text-ink-400 mt-1.5 max-w-xs mx-auto leading-relaxed">{caption}</div>}
        {action && <div className="mt-4">{action}</div>}
      </div>
    </motion.div>
  );
}
