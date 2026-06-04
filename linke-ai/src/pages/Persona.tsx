import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import {
  Radar,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  ResponsiveContainer,
} from 'recharts';
import { Sparkles, Copy, Send, RefreshCw, Check, Zap, Target, Flame, Layers, Wand2, Lightbulb } from 'lucide-react';
import RadiusSelector from '@/components/RadiusSelector';
import SectionHeader from '@/components/SectionHeader';
import { toast } from '@/components/Toast';
import { useGlobal } from '@/store/useGlobal';
import { api } from '@/lib/api';
import type { Copy as CopyT, Persona, Channel, Offer, TimeSlot } from '@/lib/types';
import { useNavigate } from 'react-router-dom';
import { cn } from '@/lib/utils';

const CHANNELS: { value: Channel; label: string; tone: string; icon: typeof Sparkles }[] = [
  { value: 'wechat', label: '企微 / 朋友圈', tone: 'bg-cyber-300/15 text-cyber-200 border-cyber-300/40 shadow-[0_0_0_1px_rgba(60,224,198,0.4),0_8px_30px_-8px_rgba(60,224,198,0.45)]', icon: Sparkles },
  { value: 'sms', label: '短信', tone: 'bg-ember-500/15 text-ember-200 border-ember-500/40 shadow-[0_0_0_1px_rgba(255,106,44,0.4),0_8px_30px_-8px_rgba(255,106,44,0.45)]', icon: Send },
  { value: 'douyin', label: '抖音同城', tone: 'bg-signal-violet/15 text-signal-violet border-signal-violet/40 shadow-[0_0_0_1px_rgba(167,139,250,0.4),0_8px_30px_-8px_rgba(167,139,250,0.45)]', icon: Flame },
  { value: 'phone', label: 'AI 外呼', tone: 'bg-signal-rose/15 text-signal-rose border-signal-rose/40 shadow-[0_0_0_1px_rgba(251,113,133,0.4),0_8px_30px_-8px_rgba(251,113,133,0.45)]', icon: Wand2 },
  { value: 'card', label: '美团 / 卡券', tone: 'bg-signal-gold/15 text-signal-gold border-signal-gold/40 shadow-[0_0_0_1px_rgba(250,204,21,0.4),0_8px_30px_-8px_rgba(250,204,21,0.45)]', icon: Layers },
];

const OFFER_OPTIONS: { value: Offer; label: string }[] = [
  { value: 'discount', label: '9.9 元起' },
  { value: 'gift', label: '到店即送' },
  { value: 'coupon', label: '30 元代金券' },
  { value: 'trial', label: '0 元体验' },
  { value: 'member', label: '会员 5 折' },
];

const TIME_OPTIONS: { value: TimeSlot; label: string }[] = [
  { value: 'morning', label: '早' },
  { value: 'noon', label: '午' },
  { value: 'afternoon', label: '下午' },
  { value: 'evening', label: '下班' },
  { value: 'night', label: '夜' },
];

export default function PersonaPage() {
  const { radius, setRadius, currentStoreId, stores } = useGlobal();
  const nav = useNavigate();
  const [persona, setPersona] = useState<Persona | null>(null);
  const [copies, setCopies] = useState<CopyT[]>([]);
  const [channel, setChannel] = useState<Channel>('wechat');
  const [offer, setOffer] = useState<Offer>('coupon');
  const [timeSlot, setTimeSlot] = useState<TimeSlot>('noon');
  const [loading, setLoading] = useState(false);
  const [copiedIdx, setCopiedIdx] = useState<number | null>(null);

  const currentStore = stores.find((s) => s.id === currentStoreId);

  const load = async () => {
    if (!currentStoreId) return;
    setLoading(true);
    try {
      const [p, c] = await Promise.all([
        api.post<{ persona: Persona }>('/ai/persona', { storeId: currentStoreId, radiusKm: radius }),
        api.post<{ copies: CopyT[] }>('/ai/copywriting', { storeId: currentStoreId, radiusKm: radius, channel, offer, timeSlot }),
      ]);
      setPersona(p.persona);
      setCopies(c.copies);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [radius, channel, offer, timeSlot, currentStoreId]);

  const onCopy = async (text: string, idx: number) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedIdx(idx);
      toast.success('已复制到剪贴板', '可粘贴至企微 / 短信 / 抖音后台');
      setTimeout(() => setCopiedIdx(null), 1500);
    } catch {
      toast.error('复制失败', '请检查浏览器剪贴板权限');
    }
  };

  return (
    <div className="p-8 max-w-[1400px] mx-auto">
      {/* 顶部 */}
      <header className="flex items-end justify-between flex-wrap gap-4 mb-6">
        <div>
          <motion.div
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-widest text-cyber-200"
          >
            <Sparkles className="w-3.5 h-3.5" />
            Persona · AI 客户画像
            <span className="text-ink-500">·</span>
            <span className="inline-flex items-center gap-1 text-ember-300">
              <span className="w-1.5 h-1.5 rounded-full bg-ember-500 animate-pulse" />
              {radius} km 圈层
            </span>
          </motion.div>
          <h1 className="mt-2 text-3xl font-display font-extrabold">
            {currentStore?.name} · {radius} km 圈层洞察
          </h1>
          <p className="mt-1 text-sm text-ink-400">AI 基于人群行为 / 关键词 / 历史数据,自动生成画像与话术。</p>
        </div>
        <div className="flex items-center gap-3">
          <RadiusSelector value={radius} onChange={setRadius} size="sm" showLabel={false} />
          <button onClick={load} className="btn-ghost !px-3 !py-2">
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            重新生成
          </button>
        </div>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 左侧:画像报告 */}
        <div className="lg:col-span-1 space-y-4">
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            className="panel p-6 relative overflow-hidden"
          >
            <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-cyber-300/15 blur-3xl" />
            <div className="relative">
              <SectionHeader
                index="01"
                icon={Sparkles}
                title="AI 摘要"
                caption="AI SUMMARY"
              />
              <p className="text-base leading-relaxed text-white font-display mt-2">
                {persona?.summary || '正在生成画像摘要…'}
              </p>
              <div className="mt-5 flex flex-wrap gap-1.5">
                {persona?.keywords.map((k, i) => (
                  <motion.span
                    key={k}
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    transition={{ delay: i * 0.04 }}
                    whileHover={{ y: -1 }}
                    className="pill bg-white/5 border border-white/10 text-ink-200 text-[10px]"
                  >
                    #{k}
                  </motion.span>
                ))}
              </div>
            </div>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.05 }}
            className="panel p-5 relative overflow-hidden"
          >
            <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-ember-500/15 blur-3xl" />
            <SectionHeader
              index="02"
              icon={Target}
              title="能力雷达"
              caption="6 维画像评估"
            />
            <div className="h-[240px]">
              <ResponsiveContainer>
                <RadarChart data={persona?.radar || []} outerRadius="78%">
                  <defs>
                    <linearGradient id="g-radar-p" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#FF6A2C" stopOpacity={0.6} />
                      <stop offset="100%" stopColor="#FF6A2C" stopOpacity={0.1} />
                    </linearGradient>
                  </defs>
                  <PolarGrid stroke="rgba(255,255,255,0.08)" />
                  <PolarAngleAxis
                    dataKey="dim"
                    tick={{ fill: '#A5A5B0', fontSize: 11, fontFamily: 'JetBrains Mono' }}
                  />
                  <PolarRadiusAxis
                    angle={90}
                    domain={[0, 100]}
                    tick={{ fill: '#54545F', fontSize: 9 }}
                    stroke="rgba(255,255,255,0.05)"
                  />
                  <Radar
                    name="当前圈层"
                    dataKey="value"
                    stroke="#FF6A2C"
                    fill="url(#g-radar-p)"
                    fillOpacity={0.7}
                    strokeWidth={2}
                  />
                </RadarChart>
              </ResponsiveContainer>
            </div>
            <div className="grid grid-cols-3 gap-2 mt-3">
              {persona?.radar.map((r) => (
                <div key={r.dim} className="rounded-lg border border-white/5 p-2 text-center hover:border-ember-500/30 transition">
                  <div className="text-[10px] font-mono text-ink-400">{r.dim}</div>
                  <div className="font-mono text-lg font-bold text-white">{r.value}</div>
                  <div className="mt-1 h-1 rounded-full bg-white/5 overflow-hidden">
                    <motion.div
                      initial={{ width: 0 }}
                      animate={{ width: `${r.value}%` }}
                      transition={{ duration: 1, ease: [0.16, 1, 0.3, 1] }}
                      className="h-full bg-gradient-to-r from-ember-500 to-cyber-300"
                    />
                  </div>
                </div>
              ))}
            </div>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className="panel p-5 relative overflow-hidden"
          >
            <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-signal-violet/20 blur-3xl" />
            <SectionHeader
              index="03"
              icon={Lightbulb}
              title="AI 机会洞察"
              caption="OPPORTUNITIES"
            />
            <ul className="space-y-2.5 text-xs text-ink-200 mt-2">
              {persona?.highlights.map((h, i) => (
                <motion.li
                  key={i}
                  initial={{ opacity: 0, x: -6 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.15 + i * 0.06 }}
                  className="flex gap-2"
                >
                  <span className="shrink-0 w-5 h-5 rounded-md bg-ember-500/15 text-ember-300 grid place-items-center text-[10px] font-mono font-bold">
                    {i + 1}
                  </span>
                  <span className="leading-relaxed flex-1">{h}</span>
                </motion.li>
              ))}
            </ul>
          </motion.div>
        </div>

        {/* 右侧:话术生成 */}
        <div className="lg:col-span-2 space-y-4">
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.05 }}
            className="panel p-5 relative overflow-hidden"
          >
            <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-ember-500/15 blur-3xl" />
            <SectionHeader
              index="04"
              icon={Wand2}
              title="触达话术"
              caption="3 套候选 · 选时段 + 优惠,AI 自动重写"
              actions={
                <div className="flex items-center gap-1.5 flex-wrap">
                  <div className="flex items-center gap-1 panel p-1">
                    {TIME_OPTIONS.map((t) => (
                      <button
                        key={t.value}
                        onClick={() => setTimeSlot(t.value)}
                        className={cn(
                          'px-2.5 py-1 text-[10px] font-mono rounded-full border transition',
                          timeSlot === t.value
                            ? 'border-ember-500/50 bg-ember-500/10 text-ember-200 shadow-glow'
                            : 'border-transparent text-ink-400 hover:text-white',
                        )}
                      >
                        {t.label}
                      </button>
                    ))}
                  </div>
                  <select
                    value={offer}
                    onChange={(e) => setOffer(e.target.value as Offer)}
                    className="bg-ink-800/60 border border-white/5 rounded-lg px-2.5 py-1 text-[11px] focus:outline-none focus:border-ember-500/60"
                  >
                    {OFFER_OPTIONS.map((o) => (
                      <option key={o.value} value={o.value} className="bg-ink-900">
                        {o.label}
                      </option>
                    ))}
                  </select>
                </div>
              }
            />

            <div className="flex flex-wrap items-center gap-2 mb-4 mt-2">
              {CHANNELS.map((c) => {
                const Icon = c.icon;
                return (
                  <motion.button
                    key={c.value}
                    whileHover={{ y: -2 }}
                    onClick={() => setChannel(c.value)}
                    className={cn(
                      'inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-full border transition',
                      channel === c.value ? c.tone : 'border-white/10 text-ink-400 hover:border-white/20',
                    )}
                  >
                    <Icon className="w-3.5 h-3.5" />
                    {c.label}
                  </motion.button>
                );
              })}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              {copies.map((c, i) => (
                <motion.div
                  key={i}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.1 + i * 0.06 }}
                  whileHover={{ y: -3 }}
                  className="group relative overflow-hidden rounded-2xl border border-white/5 bg-ink-800/40 p-4 flex flex-col hover:border-ember-500/30 transition"
                >
                  <div className="absolute -top-8 -right-8 w-24 h-24 rounded-full bg-ember-500/0 group-hover:bg-ember-500/15 blur-2xl transition" />
                  <div className="relative flex items-center gap-2 mb-2">
                    <div className="w-6 h-6 rounded-md bg-gradient-to-br from-ember-500 to-ember-700 text-ink-950 text-[10px] font-mono font-bold flex items-center justify-center shadow-glow">
                      {String(i + 1).padStart(2, '0')}
                    </div>
                    <div className="text-[10px] font-mono text-ink-400 flex items-center gap-1.5">
                      <span className="text-cyber-200">打开 ~{c.estimatedOpen}%</span>
                      <span className="text-ink-500">·</span>
                      <span className="text-ember-300">转化 ~{c.estimatedConvert}%</span>
                    </div>
                  </div>
                  <div className="relative text-sm font-display font-bold text-white leading-snug">
                    {c.title}
                  </div>
                  <p className="relative mt-2 text-xs text-ink-200 leading-relaxed flex-1">{c.body}</p>
                  <div className="relative mt-3 rounded-lg border border-ember-500/20 bg-ember-500/[0.06] px-3 py-2 text-[11px] text-ember-200 font-mono">
                    <span className="text-ink-400 mr-1">CTA ·</span>
                    {c.cta}
                  </div>
                  <div className="relative mt-3 flex items-center gap-2">
                    <button
                      onClick={() => onCopy(`${c.title}\n${c.body}\n${c.cta}`, i)}
                      className="flex-1 btn-ghost !py-1.5 !text-xs"
                    >
                      {copiedIdx === i ? (
                        <>
                          <Check className="w-3.5 h-3.5 text-cyber-300" /> 已复制
                        </>
                      ) : (
                        <>
                          <Copy className="w-3.5 h-3.5" />
                          复制
                        </>
                      )}
                    </button>
                    <button
                      onClick={() => nav('/touch')}
                      className="flex-1 btn-primary !py-1.5 !text-xs"
                    >
                      <Zap className="w-3.5 h-3.5" />
                      一键群发
                    </button>
                  </div>
                </motion.div>
              ))}
            </div>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className="panel p-5 relative overflow-hidden"
          >
            <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-cyber-300/15 blur-3xl" />
            <SectionHeader
              index="05"
              icon={Layers}
              title="话术模板库"
              caption="8 个常用模板 · 一键套用"
              actions={
                <button className="text-[11px] font-mono text-cyber-200 hover:text-cyber-100">
                  + 保存当前为模板
                </button>
              }
            />
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-2 mt-2">
              {[
                '限时秒杀·午间专场',
                '会员专享·0 门槛',
                '企业团购·下午茶',
                '节日营销·节日礼盒',
                '新客尝鲜·9.9 元',
                '老客回馈·第二杯半价',
                '雨天应急·暖咖套餐',
                '亲子周末·烘焙体验',
              ].map((t, i) => (
                <motion.button
                  key={t}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.15 + i * 0.04 }}
                  whileHover={{ y: -2 }}
                  className="group text-left rounded-xl border border-white/5 bg-ink-800/30 p-3 hover:border-cyber-300/40 transition relative overflow-hidden"
                >
                  <div className="absolute -top-4 -right-4 w-12 h-12 rounded-full bg-cyber-300/0 group-hover:bg-cyber-300/20 blur-xl transition" />
                  <div className="relative text-xs font-medium text-white">{t}</div>
                  <div className="relative mt-1 text-[10px] font-mono text-ink-400">点击套用 →</div>
                </motion.button>
              ))}
            </div>
          </motion.div>
        </div>
      </div>
    </div>
  );
}
