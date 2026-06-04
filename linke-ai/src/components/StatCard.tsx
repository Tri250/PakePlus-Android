import { motion } from 'framer-motion';
import { ArrowUpRight, ArrowDownRight, Minus } from 'lucide-react';
import AnimatedNumber from './AnimatedNumber';
import { cn } from '@/lib/utils';

type Tone = 'ember' | 'cyber' | 'violet' | 'gold' | 'rose';

interface Props {
  label: string;
  value: number;
  format?: 'comma' | 'wan' | 'none';
  decimals?: number;
  suffix?: string;
  prefix?: string;
  delta?: number;          // 百分比变化
  deltaLabel?: string;     // "较上周"
  tone?: Tone;
  icon?: React.ComponentType<{ className?: string }>;
  /** 副标题/单位 */
  caption?: string;
  /** 微 sparkline 数据(可选) */
  spark?: number[];
  /** 装饰:左上角小标识 */
  badge?: string;
  delay?: number;
}

const TONE_STYLES: Record<Tone, { accent: string; glow: string; chip: string; line: string }> = {
  ember:  { accent: 'text-ember-300',  glow: 'shadow-glow',    chip: 'bg-ember-500/15 text-ember-200 border-ember-500/30',   line: 'bg-ember-500' },
  cyber:  { accent: 'text-cyber-200',  glow: 'shadow-cyber',   chip: 'bg-cyber-300/15 text-cyber-200 border-cyber-300/30',  line: 'bg-cyber-300' },
  violet: { accent: 'text-signal-violet', glow: 'shadow-[0_0_0_1px_rgba(167,139,250,0.4),0_8px_30px_-8px_rgba(167,139,250,0.45)]', chip: 'bg-signal-violet/15 text-signal-violet border-signal-violet/30', line: 'bg-signal-violet' },
  gold:   { accent: 'text-signal-gold', glow: 'shadow-[0_0_0_1px_rgba(250,204,21,0.4),0_8px_30px_-8px_rgba(250,204,21,0.45)]', chip: 'bg-signal-gold/15 text-signal-gold border-signal-gold/30', line: 'bg-signal-gold' },
  rose:   { accent: 'text-signal-rose', glow: 'shadow-[0_0_0_1px_rgba(251,113,133,0.4),0_8px_30px_-8px_rgba(251,113,133,0.45)]', chip: 'bg-signal-rose/15 text-signal-rose border-signal-rose/30', line: 'bg-signal-rose' },
};

/**
 * 紧凑 SVG sparkline(无依赖)
 */
const Spark = ({ data, tone }: { data: number[]; tone: Tone }) => {
  if (!data.length) return null;
  const W = 120;
  const H = 36;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const pts = data.map((v, i) => {
    const x = (i / (data.length - 1)) * W;
    const y = H - ((v - min) / range) * H;
    return `${x},${y}`;
  });
  const polyline = pts.join(' ');
  const color = tone === 'ember' ? '#FF6A2C' : tone === 'cyber' ? '#3CE0C6' : tone === 'violet' ? '#A78BFA' : tone === 'gold' ? '#FACC15' : '#FB7185';
  const id = `spark-grad-${tone}`;
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-9">
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity={0.5} />
          <stop offset="100%" stopColor={color} stopOpacity={0} />
        </linearGradient>
      </defs>
      <polygon points={`0,${H} ${polyline} ${W},${H}`} fill={`url(#${id})`} />
      <polyline
        points={polyline}
        fill="none"
        stroke={color}
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        style={{ filter: `drop-shadow(0 0 4px ${color}66)` }}
      />
    </svg>
  );
};

/**
 * 数据卡:渐变描边 + 数字滚动 + sparkline + 装饰元素
 * 借鉴:美团商家 / 抖音来客 / 微盟 顶部数据卡样式
 */
export default function StatCard({
  label,
  value,
  format = 'none',
  decimals = 0,
  prefix,
  suffix,
  delta,
  deltaLabel = '较上周',
  tone = 'ember',
  icon: Icon,
  caption,
  spark,
  badge,
  delay = 0,
}: Props) {
  const toneStyle = TONE_STYLES[tone];
  const positive = (delta ?? 0) > 0;
  const flat = delta === 0;

  return (
    <motion.div
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
      whileHover={{ y: -3 }}
      className={cn(
        'group relative overflow-hidden rounded-2xl border border-white/[0.07] bg-ink-900/60 backdrop-blur-sm p-5 transition-shadow',
        'hover:border-white/15',
        toneStyle.glow,
      )}
    >
      {/* 装饰:左上渐变光斑 */}
      <div
        className={cn(
          'absolute -top-12 -right-12 w-40 h-40 rounded-full blur-3xl opacity-30',
          tone === 'ember' && 'bg-ember-500/40',
          tone === 'cyber' && 'bg-cyber-300/40',
          tone === 'violet' && 'bg-signal-violet/40',
          tone === 'gold' && 'bg-signal-gold/40',
          tone === 'rose' && 'bg-signal-rose/40',
        )}
      />
      {/* 装饰:左下小色条 */}
      <div className={cn('absolute bottom-0 left-0 h-[2px] w-12', toneStyle.line, 'opacity-60')} />

      <div className="relative">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-mono uppercase tracking-[0.18em] text-ink-400">
              {label}
            </span>
            {badge && (
              <span className={cn('text-[9px] font-mono px-1.5 py-0.5 rounded border', toneStyle.chip)}>
                {badge}
              </span>
            )}
          </div>
          {Icon && (
            <div className={cn('w-7 h-7 rounded-lg grid place-items-center border', toneStyle.chip)}>
              <Icon className="w-3.5 h-3.5" />
            </div>
          )}
        </div>

        <div className="flex items-baseline gap-1.5">
          {prefix && <span className="text-2xl text-ink-400 font-mono">{prefix}</span>}
          <span className={cn('font-mono text-[34px] font-bold tracking-tight leading-none', toneStyle.accent)}>
            <AnimatedNumber value={value} format={format} decimals={decimals} />
          </span>
          {suffix && <span className="text-sm text-ink-400 font-mono">{suffix}</span>}
          {caption && <span className="ml-1 text-sm text-ink-400 font-mono">{caption}</span>}
        </div>

        <div className="mt-2 flex items-center justify-between">
          {delta !== undefined ? (
            <div
              className={cn(
                'inline-flex items-center gap-1 text-[11px] font-mono',
                positive && 'text-cyber-200',
                !positive && !flat && 'text-ember-300',
                flat && 'text-ink-400',
              )}
            >
              {positive && <ArrowUpRight className="w-3 h-3" />}
              {!positive && !flat && <ArrowDownRight className="w-3 h-3" />}
              {flat && <Minus className="w-3 h-3" />}
              {deltaLabel} {positive ? '+' : ''}
              {delta}%
            </div>
          ) : (
            <div />
          )}

          {spark && (
            <div className="w-24 opacity-80 group-hover:opacity-100 transition">
              <Spark data={spark} tone={tone} />
            </div>
          )}
        </div>
      </div>
    </motion.div>
  );
}
