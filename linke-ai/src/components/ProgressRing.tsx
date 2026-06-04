import { motion } from 'framer-motion';
import { cn } from '@/lib/utils';

interface Props {
  value: number;       // 0-100
  size?: number;
  stroke?: number;
  label?: string;      // 中间显示文字
  caption?: string;    // 下方小字
  tone?: 'ember' | 'cyber' | 'violet' | 'gold' | 'rose';
  showPercent?: boolean;
}

const TONE_COLOR: Record<NonNullable<Props['tone']>, { stroke: string; from: string; to: string }> = {
  ember:  { stroke: '#FF6A2C', from: '#FF6A2C', to: '#FFB380' },
  cyber:  { stroke: '#3CE0C6', from: '#3CE0C6', to: '#7FE9D6' },
  violet: { stroke: '#A78BFA', from: '#A78BFA', to: '#D8B4FE' },
  gold:   { stroke: '#FACC15', from: '#FACC15', to: '#FDE68A' },
  rose:   { stroke: '#FB7185', from: '#FB7185', to: '#FDA4AF' },
};

/**
 * 环形进度:数字 + 环形,展示占比 / 完成度
 * 借鉴:抖音来客 / 美团 转化率组件
 */
export default function ProgressRing({
  value,
  size = 96,
  stroke = 8,
  label,
  caption,
  tone = 'ember',
  showPercent = true,
}: Props) {
  const c = TONE_COLOR[tone];
  const r = (size - stroke) / 2;
  const C = 2 * Math.PI * r;
  const offset = C - (Math.min(100, Math.max(0, value)) / 100) * C;
  const id = `ring-${tone}-${size}`;

  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <defs>
          <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor={c.from} />
            <stop offset="100%" stopColor={c.to} />
          </linearGradient>
        </defs>
        <circle cx={size / 2} cy={size / 2} r={r} stroke="rgba(255,255,255,0.06)" strokeWidth={stroke} fill="none" />
        <motion.circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          stroke={`url(#${id})`}
          strokeWidth={stroke}
          fill="none"
          strokeLinecap="round"
          initial={{ strokeDashoffset: C }}
          animate={{ strokeDashoffset: offset }}
          transition={{ duration: 1.2, ease: [0.16, 1, 0.3, 1] }}
          style={{ strokeDasharray: C, filter: `drop-shadow(0 0 6px ${c.stroke}88)` }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        {label ? (
          <span className="font-mono text-base font-bold text-white leading-none">{label}</span>
        ) : showPercent ? (
          <span className="font-mono text-base font-bold text-white leading-none">
            {Math.round(value)}<span className="text-[10px] text-ink-400 ml-0.5">%</span>
          </span>
        ) : null}
        {caption && <span className="text-[9px] font-mono text-ink-400 mt-0.5">{caption}</span>}
      </div>
    </div>
  );
}
