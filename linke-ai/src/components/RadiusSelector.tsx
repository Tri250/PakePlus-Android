import { motion } from 'framer-motion';
import { cn } from '@/lib/utils';
import type { RadiusKm } from '@/lib/types';

interface Props {
  value: RadiusKm;
  onChange: (v: RadiusKm) => void;
  size?: 'sm' | 'md' | 'lg';
  align?: 'left' | 'center';
  showLabel?: boolean;
}

const RADIUS_LIST: { km: RadiusKm; color: string; tag: string }[] = [
  { km: 3,  color: 'from-ember-500/30 to-ember-500/0',  tag: '3KM·核心' },
  { km: 5,  color: 'from-ember-400/25 to-ember-400/0',  tag: '5KM·周边' },
  { km: 8,  color: 'from-cyber-300/25 to-cyber-300/0',  tag: '8KM·延伸' },
  { km: 10, color: 'from-cyber-300/20 to-cyber-300/0',  tag: '10KM·全域' },
];

const sizeMap = {
  sm: { wrap: 'gap-1.5', pill: 'px-3 py-1.5 text-xs' },
  md: { wrap: 'gap-2',    pill: 'px-4 py-2 text-sm' },
  lg: { wrap: 'gap-2.5',  pill: 'px-5 py-2.5 text-base' },
};

export default function RadiusSelector({ value, onChange, size = 'md', align = 'left', showLabel = true }: Props) {
  const sz = sizeMap[size];
  return (
    <div className={cn('flex items-center', align === 'center' && 'justify-center', sz.wrap)}>
      {showLabel && (
        <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400 mr-2">
          Radius
        </div>
      )}
      {RADIUS_LIST.map(({ km, color, tag }) => {
        const active = km === value;
        return (
          <button
            key={km}
            onClick={() => onChange(km)}
            className={cn(
              'relative group rounded-full font-mono font-semibold transition-all',
              sz.pill,
              active
                ? 'text-ink-950 shadow-glow bg-ember-500'
                : 'text-ink-300 border border-white/10 bg-white/[0.03] hover:border-ember-500/40 hover:text-white',
            )}
          >
            {active && (
              <motion.span
                layoutId="radius-glow"
                className={cn('absolute inset-0 rounded-full bg-gradient-to-r', color)}
                transition={{ type: 'spring', stiffness: 380, damping: 30 }}
              />
            )}
            <span className="relative z-10 flex items-center gap-1.5">
              <span className="font-bold tracking-tight">{km}</span>
              <span className="opacity-70 text-[10px]">km</span>
            </span>
            {active && (
              <motion.span
                layoutId="radius-pill"
                className="absolute -bottom-5 left-1/2 -translate-x-1/2 text-[10px] text-cyber-200 font-mono whitespace-nowrap"
                transition={{ type: 'spring', stiffness: 380, damping: 30 }}
              >
                {tag}
              </motion.span>
            )}
          </button>
        );
      })}
    </div>
  );
}
