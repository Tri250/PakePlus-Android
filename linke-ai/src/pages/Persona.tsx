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
import { Sparkles, Copy, Send, RefreshCw, Check } from 'lucide-react';
import RadiusSelector from '@/components/RadiusSelector';
import { useGlobal } from '@/store/useGlobal';
import { api } from '@/lib/api';
import type { Copy as CopyT, Persona, Channel } from '@/lib/types';

const CHANNELS: { value: Channel; label: string; tone: string }[] = [
  { value: 'wechat', label: '企微 / 朋友圈', tone: 'bg-cyber-300/15 text-cyber-200 border-cyber-300/30' },
  { value: 'sms', label: '短信', tone: 'bg-ember-500/15 text-ember-200 border-ember-500/30' },
  { value: 'douyin', label: '抖音同城', tone: 'bg-signal-violet/15 text-signal-violet border-signal-violet/30' },
  { value: 'card', label: '美团 / 卡券', tone: 'bg-signal-gold/15 text-signal-gold border-signal-gold/30' },
];

export default function PersonaPage() {
  const { radius, setRadius, currentStoreId, stores } = useGlobal();
  const [persona, setPersona] = useState<Persona | null>(null);
  const [copies, setCopies] = useState<CopyT[]>([]);
  const [channel, setChannel] = useState<Channel>('wechat');
  const [loading, setLoading] = useState(false);
  const [copiedIdx, setCopiedIdx] = useState<number | null>(null);

  const currentStore = stores.find((s) => s.id === currentStoreId);

  const load = async () => {
    if (!currentStoreId) return;
    setLoading(true);
    try {
      const [p, c] = await Promise.all([
        api.post<{ persona: Persona }>('/ai/persona', { storeId: currentStoreId, radiusKm: radius }),
        api.post<{ copies: CopyT[] }>('/ai/copywriting', { storeId: currentStoreId, radiusKm: radius, channel }),
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
  }, [radius, channel, currentStoreId]);

  const onCopy = async (text: string, idx: number) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedIdx(idx);
      setTimeout(() => setCopiedIdx(null), 1500);
    } catch {
      /* ignore */
    }
  };

  return (
    <div className="p-8 max-w-[1400px] mx-auto">
      {/* 顶部 */}
      <header className="flex items-end justify-between flex-wrap gap-4 mb-6">
        <div>
          <div className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-widest text-cyber-200">
            <Sparkles className="w-3.5 h-3.5" />
            Persona · AI 客户画像
          </div>
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
          <div className="panel p-6 relative overflow-hidden">
            <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-cyber-300/15 blur-3xl" />
            <div className="text-[10px] font-mono uppercase tracking-widest text-cyber-200 mb-2">
              AI Summary
            </div>
            <p className="text-base leading-relaxed text-white font-display">
              {persona?.summary || '正在生成画像摘要…'}
            </p>

            <div className="mt-5 flex flex-wrap gap-1.5">
              {persona?.keywords.map((k) => (
                <span key={k} className="pill bg-white/5 border border-white/10 text-ink-200">
                  #{k}
                </span>
              ))}
            </div>
          </div>

          <div className="panel p-5">
            <div className="flex items-center justify-between mb-3">
              <div className="text-sm font-semibold text-white">能力雷达</div>
              <div className="text-[10px] font-mono text-ink-400">6 维画像</div>
            </div>
            <div className="h-[280px]">
              <ResponsiveContainer>
                <RadarChart data={persona?.radar || []} outerRadius="78%">
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
                    fill="#FF6A2C"
                    fillOpacity={0.35}
                    strokeWidth={2}
                  />
                </RadarChart>
              </ResponsiveContainer>
            </div>
            <div className="grid grid-cols-3 gap-2 mt-3">
              {persona?.radar.map((r) => (
                <div key={r.dim} className="rounded-lg border border-white/5 p-2 text-center">
                  <div className="text-[10px] font-mono text-ink-400">{r.dim}</div>
                  <div className="font-mono text-lg font-bold text-white">{r.value}</div>
                </div>
              ))}
            </div>
          </div>

          <div className="panel p-5">
            <div className="text-sm font-semibold text-white mb-3">AI 机会洞察</div>
            <ul className="space-y-2 text-xs text-ink-200">
              {persona?.highlights.map((h, i) => (
                <li key={i} className="flex gap-2">
                  <span className="text-ember-300 font-mono">▸</span>
                  <span className="leading-relaxed">{h}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* 右侧:话术生成 */}
        <div className="lg:col-span-2 space-y-4">
          <div className="panel p-5">
            <div className="flex items-center justify-between mb-4">
              <div>
                <div className="text-sm font-semibold text-white">触达话术</div>
                <div className="text-[10px] font-mono text-ink-400">3 套候选 · 任选一套投放</div>
              </div>
              <div className="flex flex-wrap items-center gap-1.5">
                {CHANNELS.map((c) => (
                  <button
                    key={c.value}
                    onClick={() => setChannel(c.value)}
                    className={`px-3 py-1.5 text-xs font-medium rounded-full border transition ${
                      channel === c.value
                        ? c.tone
                        : 'border-white/10 text-ink-400 hover:border-white/20'
                    }`}
                  >
                    {c.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              {copies.map((c, i) => (
                <motion.div
                  key={i}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.06 }}
                  className="rounded-2xl border border-white/5 bg-ink-800/40 p-4 flex flex-col"
                >
                  <div className="flex items-center gap-2 mb-2">
                    <div className="w-6 h-6 rounded-md bg-gradient-to-br from-ember-500 to-ember-700 text-ink-950 text-[10px] font-mono font-bold flex items-center justify-center">
                      {String(i + 1).padStart(2, '0')}
                    </div>
                    <div className="text-xs text-ink-400 font-mono">CANDIDATE</div>
                  </div>
                  <div className="text-sm font-display font-bold text-white leading-snug">
                    {c.title}
                  </div>
                  <p className="mt-2 text-xs text-ink-200 leading-relaxed flex-1">{c.body}</p>
                  <div className="mt-3 rounded-lg border border-ember-500/20 bg-ember-500/[0.06] px-3 py-2 text-[11px] text-ember-200 font-mono">
                    CTA · {c.cta}
                  </div>
                  <div className="mt-3 flex items-center gap-2">
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
                    <button className="flex-1 btn-primary !py-1.5 !text-xs">
                      <Send className="w-3.5 h-3.5" />
                      一键投放
                    </button>
                  </div>
                </motion.div>
              ))}
            </div>
          </div>

          <div className="panel p-5">
            <div className="flex items-center justify-between mb-3">
              <div className="text-sm font-semibold text-white">话术模板库</div>
              <button className="text-[11px] font-mono text-cyber-200 hover:text-cyber-100">
                + 保存当前为模板
              </button>
            </div>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-2">
              {[
                '限时秒杀·午间专场',
                '会员专享·0 门槛',
                '企业团购·下午茶',
                '节日营销·节日礼盒',
                '新客尝鲜·9.9 元',
                '老客回馈·第二杯半价',
                '雨天应急·暖咖套餐',
                '亲子周末·烘焙体验',
              ].map((t) => (
                <button
                  key={t}
                  className="text-left rounded-xl border border-white/5 bg-ink-800/30 p-3 hover:border-cyber-300/30 transition"
                >
                  <div className="text-xs font-medium text-white">{t}</div>
                  <div className="mt-1 text-[10px] font-mono text-ink-400">点击套用 →</div>
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
