import { useMemo } from 'react';
import { motion } from 'framer-motion';
import type { POI, RadiusKm, RadiusStats } from '@/lib/types';
import { cn } from '@/lib/utils';

interface Props {
  center: { lng: number; lat: number };
  pois: POI[];
  stats: RadiusStats[];
  activeRadius: RadiusKm;
  hoveredPoiId?: string | null;
  onHoverPoi?: (id: string | null) => void;
  showAllRings?: boolean;
  className?: string;
}

const COLOR_RING: Record<RadiusKm, { stroke: string; fill: string; pulse: string }> = {
  3:  { stroke: '#FF6A2C', fill: 'rgba(255,106,44,0.18)',  pulse: 'rgba(255,106,44,0.55)' },
  5:  { stroke: '#FF8F4D', fill: 'rgba(255,143,77,0.13)',  pulse: 'rgba(255,143,77,0.45)' },
  8:  { stroke: '#3CE0C6', fill: 'rgba(60,224,198,0.10)',  pulse: 'rgba(60,224,198,0.40)' },
  10: { stroke: '#7FE9D6', fill: 'rgba(127,233,214,0.07)', pulse: 'rgba(127,233,214,0.32)' },
};

const CATEGORY_LABEL: Record<POI['category'], string> = {
  office: '写字楼',
  mall: '商场',
  school: '学校',
  residence: '住宅',
  subway: '地铁',
  park: '公园',
  community: '社区',
  street: '街道',
  cbd: '商圈',
  industrial: '产业园',
};

const CATEGORY_COLOR: Record<POI['category'], string> = {
  office: '#FF6A2C',
  mall: '#FACC15',
  school: '#3CE0C6',
  residence: '#A78BFA',
  subway: '#22D3EE',
  park: '#4ADE80',
  community: '#F472B6',
  street: '#FB923C',
  cbd: '#FF6A2C',
  industrial: '#60A5FA',
};

// 简易墨卡托投影:把经纬度转成 viewBox 坐标
const project = (
  lng: number,
  lat: number,
  center: { lng: number; lat: number },
  scale: number,
  w: number,
  h: number,
) => {
  const x = w / 2 + (lng - center.lng) * scale;
  const y = h / 2 - (lat - center.lat) * scale;
  return { x, y };
};

export default function ConcentricMap({
  center,
  pois,
  stats,
  activeRadius,
  hoveredPoiId,
  onHoverPoi,
  showAllRings = true,
  className,
}: Props) {
  const W = 1200;
  const H = 720;
  const scale = 36;

  // 视口裁剪
  const visiblePois = useMemo(
    () => pois.filter((p) => p.radiusKm <= activeRadius || showAllRings),
    [pois, activeRadius, showAllRings],
  );

  // 背景城市网格
  const grid = useMemo(() => {
    const rand = (i: number) => ((i * 9301 + 49297) % 233280) / 233280;
    const arr = [];
    for (let i = 0; i < 90; i++) {
      arr.push({
        x: rand(i * 7) * W,
        y: rand(i * 13 + 1) * H,
        o: 0.05 + rand(i * 17) * 0.15,
      });
    }
    return arr;
  }, []);

  // 流动光点(随机在同心圆内圈)
  const flowDots = useMemo(() => {
    const rand = (i: number) => ((i * 9301 + 49297) % 233280) / 233280;
    const arr: { angle: number; rKm: RadiusKm; speed: number; delay: number; size: number }[] = [];
    for (let i = 0; i < 18; i++) {
      arr.push({
        angle: rand(i * 3) * Math.PI * 2,
        rKm: ([3, 5, 8, 10] as RadiusKm[])[Math.floor(rand(i * 11) * 4)],
        speed: 10 + rand(i * 19) * 12,
        delay: rand(i * 23) * 4,
        size: 1 + rand(i * 5) * 1.5,
      });
    }
    return arr;
  }, []);

  // 主动脉线(从中心辐射出去)
  const rays = useMemo(() => {
    return Array.from({ length: 8 }, (_, i) => (i * 2 * Math.PI) / 8 + Math.PI / 8);
  }, []);

  return (
    <div className={cn('relative w-full h-full overflow-hidden rounded-2xl bg-ink-950 border border-white/5', className)}>
      {/* 背景底色 */}
      <div className="absolute inset-0 bg-[radial-gradient(900px_500px_at_50%_45%,rgba(255,106,44,0.10),transparent_60%)]" />
      <div className="absolute inset-0 bg-grid-faint bg-grid-32 opacity-30" />

      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="w-full h-full"
        preserveAspectRatio="xMidYMid meet"
      >
        <defs>
          <radialGradient id="halo" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="rgba(255,106,44,0.55)" />
            <stop offset="60%" stopColor="rgba(255,106,44,0.10)" />
            <stop offset="100%" stopColor="rgba(255,106,44,0)" />
          </radialGradient>
          <radialGradient id="centerPulse" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="rgba(255,106,44,0.0)" />
            <stop offset="80%" stopColor="rgba(255,106,44,0.0)" />
            <stop offset="100%" stopColor="rgba(255,106,44,0.25)" />
          </radialGradient>
          <linearGradient id="rayGrad" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="rgba(255,106,44,0.0)" />
            <stop offset="50%" stopColor="rgba(255,106,44,0.25)" />
            <stop offset="100%" stopColor="rgba(255,106,44,0.0)" />
          </linearGradient>
          <pattern id="diag" patternUnits="userSpaceOnUse" width="6" height="6" patternTransform="rotate(45)">
            <line x1="0" y1="0" x2="0" y2="6" stroke="rgba(255,255,255,0.05)" strokeWidth="1" />
          </pattern>
        </defs>

        {/* 背景散点 */}
        {grid.map((g, i) => (
          <circle key={i} cx={g.x} cy={g.y} r="1.2" fill="rgba(255,255,255,0.4)" opacity={g.o} />
        ))}

        {/* 辐射光线(中心 → 边缘) */}
        {rays.map((angle, i) => (
          <motion.line
            key={`ray-${i}`}
            x1={W / 2}
            y1={H / 2}
            x2={W / 2 + Math.cos(angle) * 10 * scale}
            y2={H / 2 + Math.sin(angle) * 10 * scale}
            stroke="url(#rayGrad)"
            strokeWidth="0.6"
            initial={{ opacity: 0.05 }}
            animate={{ opacity: [0.05, 0.35, 0.05] }}
            transition={{ duration: 4 + i * 0.2, repeat: Infinity, delay: i * 0.2, ease: 'easeInOut' }}
          />
        ))}

        {/* 同心圆 */}
        {([3, 5, 8, 10] as RadiusKm[]).map((km) => {
          const r = km * scale;
          const c = COLOR_RING[km];
          const isActive = activeRadius === km;
          return (
            <g key={km}>
              <circle
                cx={W / 2}
                cy={H / 2}
                r={r}
                fill={c.fill}
                stroke={c.stroke}
                strokeWidth={isActive ? 2 : 1}
                strokeDasharray={isActive ? '0' : '4 6'}
                opacity={isActive ? 1 : 0.55}
              />
              <circle cx={W / 2} cy={H / 2} r={r} fill="url(#diag)" opacity={isActive ? 0.6 : 0.3} />

              {/* 选中时双层脉冲 */}
              {isActive && (
                <>
                  <motion.circle
                    cx={W / 2}
                    cy={H / 2}
                    r={r}
                    fill="none"
                    stroke={c.stroke}
                    strokeWidth={2}
                    initial={{ scale: 1, opacity: 0.6 }}
                    animate={{ scale: 1.18, opacity: 0 }}
                    transition={{ duration: 2.2, repeat: Infinity, ease: 'easeOut' }}
                    style={{ transformOrigin: `${W / 2}px ${H / 2}px` }}
                  />
                  <motion.circle
                    cx={W / 2}
                    cy={H / 2}
                    r={r}
                    fill="none"
                    stroke={c.stroke}
                    strokeWidth={1.5}
                    initial={{ scale: 1, opacity: 0.4 }}
                    animate={{ scale: 1.32, opacity: 0 }}
                    transition={{ duration: 2.2, repeat: Infinity, ease: 'easeOut', delay: 0.7 }}
                    style={{ transformOrigin: `${W / 2}px ${H / 2}px` }}
                  />
                </>
              )}

              {/* 半径标尺 */}
              <text
                x={W / 2 + r * Math.cos(-Math.PI / 4)}
                y={H / 2 + r * Math.sin(-Math.PI / 4)}
                fill={c.stroke}
                fontSize="14"
                fontFamily="JetBrains Mono, monospace"
                fontWeight={600}
                opacity={0.9}
                textAnchor="middle"
                dy="0.35em"
              >
                {km} km
              </text>
            </g>
          );
        })}

        {/* 中心 halo */}
        <circle cx={W / 2} cy={H / 2} r="200" fill="url(#halo)" />

        {/* 中心波纹(三层) */}
        {[0, 0.6, 1.2].map((delay, i) => (
          <motion.circle
            key={`pulse-${i}`}
            cx={W / 2}
            cy={H / 2}
            r="40"
            fill="none"
            stroke="#FF6A2C"
            strokeWidth={1.5}
            initial={{ scale: 0.4, opacity: 0.8 }}
            animate={{ scale: 4, opacity: 0 }}
            transition={{ duration: 3, repeat: Infinity, delay, ease: 'easeOut' }}
            style={{ transformOrigin: `${W / 2}px ${H / 2}px` }}
          />
        ))}

        {/* 中心门店 */}
        <g>
          <motion.circle
            cx={W / 2}
            cy={H / 2}
            r="6"
            fill="#FF6A2C"
            animate={{ r: [6, 10, 6] }}
            transition={{ duration: 2.2, repeat: Infinity }}
          />
          <circle cx={W / 2} cy={H / 2} r="22" fill="none" stroke="#FF6A2C" strokeWidth="1" opacity="0.4" />
          <circle cx={W / 2} cy={H / 2} r="36" fill="none" stroke="#FF6A2C" strokeWidth="1" strokeDasharray="2 4" opacity="0.5" />
          <text
            x={W / 2}
            y={H / 2 - 48}
            fill="#fff"
            fontSize="14"
            fontFamily="Manrope, sans-serif"
            fontWeight={700}
            textAnchor="middle"
          >
            我的门店
          </text>
        </g>

        {/* 流动光点 - 沿圆周慢跑 */}
        {flowDots.map((d, i) => {
          const r = d.rKm * scale;
          const x0 = W / 2 + Math.cos(d.angle) * r;
          const y0 = H / 2 + Math.sin(d.angle) * r;
          return (
            <motion.circle
              key={`flow-${i}`}
              r={d.size}
              fill={COLOR_RING[d.rKm].stroke}
              initial={{ cx: x0, cy: y0, opacity: 0 }}
              animate={{
                cx: [x0, x0],
                cy: [y0, y0],
                opacity: [0, 0.9, 0.9, 0],
              }}
              transition={{
                duration: d.speed,
                repeat: Infinity,
                delay: d.delay,
                ease: 'linear',
              }}
            />
          );
        })}

        {/* 半径标线(米字格) */}
        {[0, 45, 90, 135, 180, 225, 270, 315].map((deg) => {
          const rad = (deg * Math.PI) / 180;
          return (
            <line
              key={deg}
              x1={W / 2}
              y1={H / 2}
              x2={W / 2 + Math.cos(rad) * 10 * scale}
              y2={H / 2 + Math.sin(rad) * 10 * scale}
              stroke="rgba(255,255,255,0.04)"
              strokeWidth="1"
            />
          );
        })}

        {/* POI */}
        {visiblePois.map((p) => {
          const { x, y } = project(p.lng, p.lat, center, scale, W, H);
          if (x < 0 || x > W || y < 0 || y > H) return null;
          const inActive = p.radiusKm <= activeRadius;
          const isHover = hoveredPoiId === p.id;
          const r = p.hotScore >= 80 ? 6 : p.hotScore >= 60 ? 5 : 4;
          const c = CATEGORY_COLOR[p.category];
          return (
            <g
              key={p.id}
              onMouseEnter={() => onHoverPoi?.(p.id)}
              onMouseLeave={() => onHoverPoi?.(null)}
              style={{ cursor: 'pointer' }}
            >
              {/* 呼吸光环(在 active 圈内) */}
              {inActive && (
                <motion.circle
                  cx={x}
                  cy={y}
                  r={r}
                  fill="transparent"
                  stroke={c}
                  strokeWidth={1}
                  initial={{ scale: 1, opacity: 0.4 }}
                  animate={{ scale: [1, 2.4, 1], opacity: [0.5, 0, 0.5] }}
                  transition={{ duration: 2.4 + (p.hotScore % 5) * 0.2, repeat: Infinity, delay: (p.hotScore % 7) * 0.3 }}
                  style={{ transformOrigin: `${x}px ${y}px` }}
                />
              )}
              {/* hover 光环 */}
              {inActive && (
                <motion.circle
                  cx={x}
                  cy={y}
                  r={r}
                  fill={c}
                  fillOpacity={0.35}
                  initial={{ scale: 0, opacity: 0 }}
                  animate={{ scale: isHover ? 2.4 : 1.6, opacity: isHover ? 0.55 : 0.3 }}
                  transition={{ duration: 0.3 }}
                />
              )}
              {/* 中心点 */}
              <circle
                cx={x}
                cy={y}
                r={r}
                fill={inActive ? c : '#54545F'}
                stroke={isHover ? '#FF6A2C' : inActive ? '#0B0B0F' : 'transparent'}
                strokeWidth={isHover ? 2 : 1}
              />
              {/* hover 标签 */}
              {isHover && (
                <g>
                  <rect
                    x={x + 10}
                    y={y - 28}
                    width="170"
                    height="60"
                    rx="8"
                    fill="rgba(11,11,15,0.95)"
                    stroke="rgba(255,106,44,0.5)"
                  />
                  <text x={x + 18} y={y - 10} fill="#fff" fontSize="12" fontFamily="Manrope, sans-serif" fontWeight={600}>
                    {p.name}
                  </text>
                  <text x={x + 18} y={y + 6} fill="#7A7A85" fontSize="10" fontFamily="JetBrains Mono, monospace">
                    {CATEGORY_LABEL[p.category]} · 高潜 {p.hotScore} · {p.radiusKm}km
                  </text>
                  <text x={x + 18} y={y + 22} fill={c} fontSize="10" fontFamily="JetBrains Mono, monospace">
                    ● {p.hotScore >= 80 ? 'S 级 · 必攻' : p.hotScore >= 60 ? 'A 级 · 推荐' : 'B 级 · 观察'}
                  </text>
                </g>
              )}
            </g>
          );
        })}
      </svg>

      {/* 角落刻度 - 左上 */}
      <motion.div
        initial={{ opacity: 0, x: -8 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ delay: 0.3 }}
        className="absolute top-4 left-4 flex flex-col gap-1 font-mono text-[10px] text-ink-400"
      >
        <div className="flex items-center gap-1.5">
          <span className="relative flex w-2 h-2">
            <span className="absolute inline-flex h-full w-full rounded-full bg-ember-500 opacity-75 animate-ping" />
            <span className="relative inline-flex rounded-full h-2 w-2 bg-ember-500" />
          </span>
          <span>实时商圈热力</span>
        </div>
        <div>SAMPLES = {visiblePois.length}</div>
      </motion.div>

      {/* 角落图例 - 右下 */}
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
        className="absolute bottom-4 right-4 panel p-3 space-y-1.5 font-mono text-[10px]"
      >
        {(['office', 'mall', 'school', 'residence', 'subway', 'park'] as POI['category'][]).map((cat) => (
          <div key={cat} className="flex items-center gap-2 text-ink-300">
            <span className="w-2 h-2 rounded-full" style={{ background: CATEGORY_COLOR[cat], boxShadow: `0 0 6px ${CATEGORY_COLOR[cat]}` }} />
            <span className="w-12 text-right">{CATEGORY_LABEL[cat]}</span>
            <span className="text-ink-500">·</span>
            <span className="text-ink-400">
              {visiblePois.filter((p) => p.category === cat).length}
            </span>
          </div>
        ))}
      </motion.div>

      {/* 中心 KPI - 左下 */}
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.4 }}
        className="absolute bottom-4 left-4 panel px-3 py-2 font-mono text-[10px] flex items-center gap-3"
      >
        <div>
          <div className="text-ink-400">当前半径</div>
          <div className="text-base font-bold text-ember-300">{activeRadius} <span className="text-xs text-ink-400">km</span></div>
        </div>
        <div className="w-px h-6 bg-white/10" />
        <div>
          <div className="text-ink-400">高潜 POI</div>
          <div className="text-base font-bold text-cyber-200">{stats.find((s) => s.km === activeRadius)?.hotSpots ?? 0}</div>
        </div>
        <div className="w-px h-6 bg-white/10" />
        <div>
          <div className="text-ink-400">人口</div>
          <div className="text-base font-bold text-white">{(stats.find((s) => s.km === activeRadius)?.population ?? 0).toLocaleString()}</div>
        </div>
      </motion.div>
    </div>
  );
}
