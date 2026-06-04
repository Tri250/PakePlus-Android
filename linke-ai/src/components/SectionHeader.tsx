import { motion } from 'framer-motion';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface Props {
  index?: string;          // "01" "02"
  icon?: React.ComponentType<{ className?: string }>;
  title: string;
  caption?: string;
  /** 右侧操作区 */
  actions?: ReactNode;
  className?: string;
  /** 颜色调性 */
  tone?: 'ember' | 'cyber' | 'violet' | 'rose' | 'gold';
}

const TONE: Record<NonNullable<Props['tone']>, string> = {
  ember: 'from-ember-500',
  cyber: 'from-cyber-300',
  violet: 'from-signal-violet',
  rose: 'from-signal-rose',
  gold: 'from-signal-gold',
};

/**
 * 区域标题:左侧渐变条 + 编号 + 标题 + 副标题 + 右侧操作
 * 借鉴:微盟 / 飞书 多列表格上方的分组标题
 */
export default function SectionHeader({
  index,
  icon: Icon,
  title,
  caption,
  actions,
  className,
  tone = 'ember',
}: Props) {
  return (
    <div className={cn('flex items-center gap-3 mb-3', className)}>
      {index && (
        <span
          className={cn(
            'font-mono text-[11px] font-bold tracking-wider text-white/90 px-1.5 py-0.5 rounded-md bg-gradient-to-br',
            TONE[tone],
            'to-transparent',
          )}
        >
          {index}
        </span>
      )}
      {Icon && <Icon className="w-4 h-4 text-ink-300" />}
      <div>
        <div className="text-sm font-semibold text-white tracking-wide">{title}</div>
        {caption && <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400 mt-0.5">{caption}</div>}
      </div>
      <div className="ml-auto flex items-center gap-2">{actions}</div>
    </div>
  );
}
