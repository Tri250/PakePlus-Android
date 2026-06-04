import { useEffect, useMemo, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Send,
  Sparkles,
  CheckCircle2,
  XCircle,
  Loader2,
  Users2,
  Zap,
  Phone,
  MessageSquare,
  Ticket,
  Megaphone,
  Smartphone,
  Clock,
  TrendingUp,
  ListChecks,
  Wallet,
  Target,
  Activity,
} from 'lucide-react';
import { useGlobal } from '@/store/useGlobal';
import { api } from '@/lib/api';
import type { Channel, ChannelMeta, Copy, Lead, LeadStatus, Offer, SendResult, TimeSlot } from '@/lib/types';
import RadiusSelector from '@/components/RadiusSelector';
import SectionHeader from '@/components/SectionHeader';
import ProgressRing from '@/components/ProgressRing';
import { toast } from '@/components/Toast';
import { EmptyState } from '@/components/Misc';
import { cn } from '@/lib/utils';

const CHANNELS: { value: Channel; label: string; icon: typeof Smartphone; tone: string; desc: string; glow: string }[] = [
  { value: 'sms',    label: '短信',  icon: Smartphone,     tone: 'border-ember-500/40 text-ember-200 bg-ember-500/10',          desc: '阿里云 / 腾讯云短信通道,95%+ 到达',          glow: 'shadow-[0_0_0_1px_rgba(255,106,44,0.4),0_8px_30px_-8px_rgba(255,106,44,0.45)]' },
  { value: 'wechat', label: '企微',  icon: MessageSquare,  tone: 'border-cyber-300/40 text-cyber-200 bg-cyber-300/10',          desc: '企业微信 SCRM,模板消息 + 一键加微',          glow: 'shadow-[0_0_0_1px_rgba(60,224,198,0.4),0_8px_30px_-8px_rgba(60,224,198,0.45)]' },
  { value: 'douyin', label: '抖音同城', icon: Megaphone,   tone: 'border-signal-violet/40 text-signal-violet bg-signal-violet/10', desc: '巨量引擎 / 抖音来客,同城首页推荐',          glow: 'shadow-[0_0_0_1px_rgba(167,139,250,0.4),0_8px_30px_-8px_rgba(167,139,250,0.45)]' },
  { value: 'card',   label: '卡券',  icon: Ticket,         tone: 'border-signal-gold/40 text-signal-gold bg-signal-gold/10',     desc: '美团 / 有赞 / 抖音,卡券自动核销',            glow: 'shadow-[0_0_0_1px_rgba(250,204,21,0.4),0_8px_30px_-8px_rgba(250,204,21,0.45)]' },
  { value: 'phone',  label: 'AI 外呼', icon: Phone,        tone: 'border-signal-rose/40 text-signal-rose bg-signal-rose/10',     desc: '百应 / 容联 AI 语音,41% 接通率',             glow: 'shadow-[0_0_0_1px_rgba(251,113,133,0.4),0_8px_30px_-8px_rgba(251,113,133,0.45)]' },
];

const OFFER_OPTIONS: { value: Offer; label: string }[] = [
  { value: 'discount', label: '9.9 元起 · 第二件半价' },
  { value: 'gift',     label: '到店即送价值 38 元小食' },
  { value: 'coupon',   label: '30 元代金券 × 2(可叠加)' },
  { value: 'trial',    label: '0 元体验 1 次(限新客)' },
  { value: 'member',   label: '会员日 5 折 / 双倍积分' },
  { value: 'vip',      label: '私域专属 8 折 + 优先排队' },
];

const TIME_OPTIONS: { value: TimeSlot; label: string }[] = [
  { value: 'morning',   label: '早安 · 7:30-10:00' },
  { value: 'noon',      label: '午间 · 11:30-14:00' },
  { value: 'afternoon', label: '下午 · 14:00-17:30' },
  { value: 'evening',   label: '下班 · 17:30-21:00' },
  { value: 'night',     label: '深夜 · 21:00-24:00' },
];

const STATUS_TONE: Record<LeadStatus, string> = {
  pending: 'border-signal-gold/30 text-signal-gold bg-signal-gold/5',
  added: 'border-cyber-300/30 text-cyber-200 bg-cyber-300/5',
  visited: 'border-ember-500/30 text-ember-200 bg-ember-500/5',
  won: 'border-signal-violet/40 text-signal-violet bg-signal-violet/5',
  lost: 'border-white/10 text-ink-400 bg-white/5',
};

export default function TouchCenter() {
  const { currentStoreId, radius, setRadius, stores } = useGlobal();

  // 渠道元数据
  const [channelMeta, setChannelMeta] = useState<Record<Channel, ChannelMeta> | null>(null);
  // 选中的渠道
  const [selectedChannels, setSelectedChannels] = useState<Channel[]>(['wechat', 'sms']);
  // 文案 / 优惠 / 时段
  const [offer, setOffer] = useState<Offer>('coupon');
  const [timeSlot, setTimeSlot] = useState<TimeSlot>('noon');
  const [title, setTitle] = useState('3 公里内午间福利');
  const [body, setBody] = useState(
    '在 3 km 内的你,今天午间好。我们想请你 11:30 - 14:00 到店 体验一份「30 元代金券 × 2」,名额有限,先到先得。'
  );
  const [cta, setCta] = useState('点击查看门店 · 限 3 公里内到店');
  // AI 生成
  const [aiCopies, setAiCopies] = useState<Copy[]>([]);
  const [aiLoading, setAiLoading] = useState(false);
  const [activeCopyIdx, setActiveCopyIdx] = useState<number | null>(null);

  // 触达目标
  const [leads, setLeads] = useState<Lead[]>([]);
  const [tab, setTab] = useState<LeadStatus | 'all'>('all');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  // 发送状态
  const [sending, setSending] = useState(false);
  const [sendProgress, setSendProgress] = useState<{ total: number; done: number; success: number; failed: number } | null>(null);
  const [results, setResults] = useState<SendResult[]>([]);
  const [summary, setSummary] = useState<{ success: number; failed: number; totalCost: number; avgOpen: number } | null>(null);

  // 初始化
  useEffect(() => {
    api.get<{ channels: Record<Channel, ChannelMeta> }>('/touch/channels').then((r) => setChannelMeta(r.channels));
  }, []);
  useEffect(() => {
    if (!currentStoreId) return;
    api.get<{ items: Lead[] }>(`/leads?storeId=${currentStoreId}`).then((r) => setLeads(r.items));
  }, [currentStoreId]);

  // AI 文案生成
  const generate = async (channel: Channel) => {
    if (!currentStoreId) return;
    setAiLoading(true);
    try {
      const r = await api.post<{ copies: Copy[] }>('/ai/copywriting', {
        storeId: currentStoreId,
        radiusKm: radius,
        channel,
        offer,
        timeSlot,
      });
      setAiCopies(r.copies);
      // 自动套用第一套
      const first = r.copies[0];
      if (first) {
        setTitle(first.title);
        setBody(first.body);
        setCta(first.cta);
        setActiveCopyIdx(0);
      }
      toast.success('AI 文案已生成', `基于「${TIME_OPTIONS.find(t => t.value === timeSlot)?.label}」+「${OFFER_OPTIONS.find(o => o.value === offer)?.label}」生成 3 套候选`);
    } catch {
      toast.error('AI 生成失败', '请稍后重试');
    } finally {
      setAiLoading(false);
    }
  };

  // 一键造线索
  const oneClickSeeds = async () => {
    if (!currentStoreId) return;
    try {
      const r = await api.post<{ created: number }>('/leads/seed', {
        storeId: currentStoreId,
        radiusKm: radius,
        count: 24,
      });
      const list = await api.get<{ items: Lead[] }>(`/leads?storeId=${currentStoreId}`);
      setLeads(list.items);
      if (r.created) {
        toast.success(`已造 ${r.created} 个新线索`, `${radius} km 圈层可触达客户已就位`);
      } else {
        toast.info('该圈层暂无可拓展客户', '请尝试更大半径或更换门店');
      }
    } catch {
      toast.error('造线索失败', '请稍后重试');
    }
  };

  // 切换渠道
  const toggleChannel = (c: Channel) => {
    setSelectedChannels((cur) => (cur.includes(c) ? cur.filter((x) => x !== c) : [...cur, c]));
  };

  // 切换线索
  const toggleLead = (id: string) => {
    setSelectedIds((cur) => {
      const next = new Set(cur);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const filteredLeads = useMemo(
    () => leads.filter((l) => (tab === 'all' ? true : l.status === tab) && l.fromRadius <= radius),
    [leads, tab, radius],
  );

  // 一键全选当前过滤
  const selectAll = () => setSelectedIds(new Set(filteredLeads.map((l) => l.id)));
  const clearSel = () => setSelectedIds(new Set());

  // 一键群发
  const send = async () => {
    if (selectedChannels.length === 0 || selectedIds.size === 0 || !currentStoreId) return;
    setSending(true);
    setResults([]);
    setSummary(null);
    const total = selectedIds.size * selectedChannels.length;
    setSendProgress({ total, done: 0, success: 0, failed: 0 });
    try {
      const allResults: SendResult[] = [];
      let totalCost = 0;
      let totalOpen = 0;
      let successCount = 0;
      let failedCount = 0;
      for (const channel of selectedChannels) {
        const r = await api.post<{ summary: { success: number; failed: number; totalCost: number; avgOpen: number }; results: SendResult[] }>('/touch/send', {
          storeId: currentStoreId,
          channel,
          leadIds: Array.from(selectedIds),
          title,
          body,
          cta,
        });
        allResults.push(...r.results);
        totalCost += r.summary.totalCost;
        totalOpen += r.summary.avgOpen * r.results.length;
        successCount += r.summary.success;
        failedCount += r.summary.failed;
        setSendProgress({ total, done: allResults.length, success: successCount, failed: failedCount });
        setResults([...allResults]);
      }
      setSummary({
        success: successCount,
        failed: failedCount,
        totalCost: +totalCost.toFixed(2),
        avgOpen: Math.round(totalOpen / Math.max(1, allResults.length)),
      });
      const list = await api.get<{ items: Lead[] }>(`/leads?storeId=${currentStoreId}`);
      setLeads(list.items);
      if (successCount > 0) {
        toast.success(
          `群发完成 · 成功 ${successCount}`,
          `总成本 ¥${totalCost.toFixed(2)} · 平均打开率 ${Math.round(totalOpen / Math.max(1, allResults.length))}%`,
        );
      } else {
        toast.error('群发失败', '所有渠道均失败,请检查配置');
      }
    } catch {
      toast.error('群发出错', '请稍后重试');
    } finally {
      setSending(false);
      setSendProgress(null);
    }
  };

  const totalCost = selectedChannels.reduce(
    (s, c) => s + (channelMeta?.[c].cost || 0) * selectedIds.size,
    0,
  );
  const estimatedOpen =
    selectedIds.size === 0
      ? 0
      : Math.round(
          (selectedChannels.reduce(
            (s, c) => s + (channelMeta?.[c].openRate || 0) * selectedIds.size,
            0,
          ) /
            Math.max(1, selectedChannels.length)) *
            100,
        );

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
            <Send className="w-3.5 h-3.5" />
            Touch Center · 触达中心
            <span className="text-ink-500">·</span>
            <span className="inline-flex items-center gap-1 text-ember-300">
              <span className="w-1.5 h-1.5 rounded-full bg-ember-500 animate-pulse" />
              MULTI-CHANNEL
            </span>
          </motion.div>
          <h1 className="mt-2 text-3xl font-display font-extrabold">一键群发 · 多渠道触达</h1>
          <p className="mt-1 text-sm text-ink-400">
            短信 / 企微 / 抖音同城 / 美团卡券 / AI 外呼 · AI 自动选文案 · 触达结果实时回写
          </p>
        </div>
        <div className="flex items-center gap-2">
          <RadiusSelector value={radius} onChange={setRadius} size="sm" showLabel={false} />
          <button onClick={oneClickSeeds} className="btn-ghost !py-2 !text-xs">
            <Zap className="w-3.5 h-3.5" />
            一键造 {radius}km 线索
          </button>
        </div>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 左侧:文案 + 渠道选择 */}
        <div className="lg:col-span-2 space-y-4">
          {/* 渠道 */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="panel p-5 relative overflow-hidden"
          >
            <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-ember-500/15 blur-3xl" />
            <SectionHeader
              index="01"
              icon={Megaphone}
              title="触达渠道 · 5 大通道"
              caption={`已选 ${selectedChannels.length} 个 · 预计成本 ¥${totalCost.toFixed(2)}`}
              actions={
                <span className="text-[10px] font-mono text-ink-400">多选可叠加触达</span>
              }
            />
            <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
              {CHANNELS.map((c, i) => {
                const active = selectedChannels.includes(c.value);
                const meta = channelMeta?.[c.value];
                return (
                  <motion.button
                    key={c.value}
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: i * 0.04 }}
                    whileHover={{ y: -2 }}
                    onClick={() => toggleChannel(c.value)}
                    className={cn(
                      'relative rounded-xl border p-3 text-left transition overflow-hidden',
                      active ? `${c.tone} ${c.glow}` : 'border-white/5 bg-ink-800/30 text-ink-300 hover:border-white/20',
                    )}
                  >
                    {active && (
                      <div className="absolute -top-6 -right-6 w-20 h-20 rounded-full bg-current opacity-20 blur-2xl" />
                    )}
                    <div className="relative flex items-center gap-2">
                      <c.icon className="w-4 h-4" />
                      <span className="text-sm font-semibold">{c.label}</span>
                    </div>
                    <div className="relative mt-1 text-[10px] text-ink-400 font-mono">
                      ¥{meta?.cost.toFixed(3) ?? '-'} / {meta?.unit ?? '-'}
                    </div>
                    <div className="relative mt-0.5 text-[10px] text-ink-400 line-clamp-2 leading-relaxed">{c.desc}</div>
                  </motion.button>
                );
              })}
            </div>
          </motion.div>

          {/* 文案 */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className="panel p-5 relative overflow-hidden"
          >
            <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-cyber-300/20 blur-3xl" />
            <SectionHeader
              index="02"
              icon={Sparkles}
              title="AI 文案"
              caption="选时段 + 优惠,AI 自动生成"
              actions={
                <div className="flex items-center gap-2">
                  <select
                    value={timeSlot}
                    onChange={(e) => setTimeSlot(e.target.value as TimeSlot)}
                    className="bg-ink-800/60 border border-white/5 rounded-lg px-3 py-1.5 text-xs focus:outline-none focus:border-ember-500/60"
                  >
                    {TIME_OPTIONS.map((t) => (
                      <option key={t.value} value={t.value} className="bg-ink-900">
                        {t.label}
                      </option>
                    ))}
                  </select>
                  <select
                    value={offer}
                    onChange={(e) => setOffer(e.target.value as Offer)}
                    className="bg-ink-800/60 border border-white/5 rounded-lg px-3 py-1.5 text-xs focus:outline-none focus:border-ember-500/60"
                  >
                    {OFFER_OPTIONS.map((o) => (
                      <option key={o.value} value={o.value} className="bg-ink-900">
                        {o.label}
                      </option>
                    ))}
                  </select>
                  <button
                    onClick={() => generate(selectedChannels[0] || 'wechat')}
                    disabled={aiLoading}
                    className="btn-ghost !py-1.5 !text-xs"
                  >
                    {aiLoading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Sparkles className="w-3.5 h-3.5" />}
                    AI 重新生成
                  </button>
                </div>
              }
            />

            <div className="space-y-3">
              <div>
                <label className="text-[10px] font-mono uppercase tracking-widest text-ink-400">标题</label>
                <input
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  className="mt-1 w-full bg-ink-800/60 border border-white/5 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-ember-500/60 transition"
                />
              </div>
              <div>
                <label className="text-[10px] font-mono uppercase tracking-widest text-ink-400">正文</label>
                <textarea
                  value={body}
                  onChange={(e) => setBody(e.target.value)}
                  rows={3}
                  className="mt-1 w-full bg-ink-800/60 border border-white/5 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-ember-500/60 resize-none transition"
                />
              </div>
              <div>
                <label className="text-[10px] font-mono uppercase tracking-widest text-ink-400">行动号召 CTA</label>
                <input
                  value={cta}
                  onChange={(e) => setCta(e.target.value)}
                  className="mt-1 w-full bg-ink-800/60 border border-white/5 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-ember-500/60 transition"
                />
              </div>
            </div>

            {aiCopies.length > 0 && (
              <div className="mt-4">
                <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400 mb-2">
                  AI 候选 · 点击套用
                </div>
                <div className="grid grid-cols-3 gap-2">
                  {aiCopies.map((c, i) => {
                    const active = activeCopyIdx === i;
                    return (
                      <motion.button
                        key={i}
                        initial={{ opacity: 0, y: 6 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: i * 0.05 }}
                        whileHover={{ y: -2 }}
                        onClick={() => {
                          setTitle(c.title);
                          setBody(c.body);
                          setCta(c.cta);
                          setActiveCopyIdx(i);
                        }}
                        className={cn(
                          'text-left rounded-lg border p-2.5 transition',
                          active
                            ? 'border-ember-500/50 bg-ember-500/[0.06] shadow-glow'
                            : 'border-white/5 bg-ink-800/30 hover:border-ember-500/30',
                        )}
                      >
                        <div className="text-xs font-medium text-white line-clamp-1">{c.title}</div>
                        <div className="text-[10px] text-ink-300 mt-1 line-clamp-2 leading-relaxed">{c.body}</div>
                        <div className="mt-1.5 flex items-center gap-2 text-[9px] font-mono text-cyber-200">
                          <span>打开率 ~{c.estimatedOpen}%</span>
                          <span>转化 ~{c.estimatedConvert}%</span>
                        </div>
                      </motion.button>
                    );
                  })}
                </div>
              </div>
            )}
          </motion.div>

          {/* 触达结果 */}
          <AnimatePresence>
            {(sending || summary) && (
              <motion.div
                key="result"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0 }}
                className="panel p-5 relative overflow-hidden"
              >
                <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-cyber-300/15 blur-3xl" />
                <SectionHeader
                  index="03"
                  icon={Activity}
                  title="触达结果"
                  caption={sending && sendProgress ? `发送中 ${sendProgress.done} / ${sendProgress.total}` : '已完成'}
                />

                {sending && sendProgress && (
                  <div className="flex items-center gap-4 mb-4">
                    <ProgressRing
                      value={(sendProgress.done / sendProgress.total) * 100}
                      size={64}
                      stroke={6}
                      tone="cyber"
                    />
                    <div className="flex-1 grid grid-cols-3 gap-2 text-xs">
                      <div>
                        <div className="text-[10px] font-mono text-ink-400">已发送</div>
                        <div className="font-mono text-base font-bold text-white">{sendProgress.done}</div>
                      </div>
                      <div>
                        <div className="text-[10px] font-mono text-ink-400">成功</div>
                        <div className="font-mono text-base font-bold text-cyber-200">{sendProgress.success}</div>
                      </div>
                      <div>
                        <div className="text-[10px] font-mono text-ink-400">失败</div>
                        <div className="font-mono text-base font-bold text-ember-300">{sendProgress.failed}</div>
                      </div>
                    </div>
                  </div>
                )}

                {summary && (
                  <div className="grid grid-cols-4 gap-3 mb-4">
                    {[
                      { l: '总发送', v: summary.success + summary.failed, t: 'text-white' },
                      { l: '成功',   v: summary.success, t: 'text-cyber-200' },
                      { l: '失败',   v: summary.failed, t: 'text-ember-300' },
                      { l: '总成本', v: `¥${summary.totalCost}`, t: 'text-ember-200' },
                    ].map((c, i) => (
                      <motion.div
                        key={c.l}
                        initial={{ opacity: 0, y: 6 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: i * 0.04 }}
                        className="rounded-xl border border-white/5 bg-ink-800/40 p-3 hover:border-cyber-300/30 transition"
                      >
                        <div className="text-[10px] font-mono text-ink-400">{c.l}</div>
                        <div className={cn('mt-1 font-mono text-lg font-bold', c.t)}>{c.v}</div>
                      </motion.div>
                    ))}
                  </div>
                )}

                {results.length > 0 && (
                  <div className="max-h-64 overflow-y-auto space-y-1.5">
                    {results.slice(0, 50).map((r, i) => (
                      <motion.div
                        key={i}
                        initial={{ opacity: 0, x: -4 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: Math.min(i * 0.01, 0.5) }}
                        className="flex items-center gap-2 rounded-lg border border-white/5 bg-ink-800/30 px-3 py-2 text-xs hover:border-white/10 transition"
                      >
                        {r.status === 'success' ? (
                          <CheckCircle2 className="w-3.5 h-3.5 text-cyber-300" />
                        ) : (
                          <XCircle className="w-3.5 h-3.5 text-ember-300" />
                        )}
                        <span className="font-mono text-ink-300 w-12">
                          {({ sms: '短信', wechat: '企微', douyin: '抖音', card: '卡券', phone: '外呼' } as Record<Channel, string>)[r.channel]}
                        </span>
                        <span className="text-white font-medium">{r.name}</span>
                        <span className="text-ink-400 font-mono">{r.phone}</span>
                        {r.messageId && (
                          <span className="text-ink-500 font-mono ml-auto truncate">mid: {r.messageId}</span>
                        )}
                        {r.error && <span className="text-ember-300 ml-auto">{r.error}</span>}
                      </motion.div>
                    ))}
                    {results.length > 50 && (
                      <div className="text-[10px] font-mono text-ink-500 text-center py-1">
                        还有 {results.length - 50} 条结果…
                      </div>
                    )}
                  </div>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* 右侧:线索选择 + 发送按钮 */}
        <div className="space-y-4">
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.05 }}
            className="panel p-5"
          >
            <SectionHeader
              index="04"
              icon={Users2}
              title="选择触达对象"
              caption={`${radius} km 内 · 已选 ${selectedIds.size} / ${filteredLeads.length}`}
              actions={
                <div className="flex items-center gap-1.5">
                  <button onClick={selectAll} className="text-[10px] font-mono text-cyber-200 hover:text-cyber-100">
                    全选
                  </button>
                  <span className="text-ink-500">·</span>
                  <button onClick={clearSel} className="text-[10px] font-mono text-ink-400 hover:text-ink-200">
                    清空
                  </button>
                </div>
              }
            />

            {/* 状态 Tab */}
            <div className="flex items-center gap-1 mb-3 overflow-x-auto">
              {(['all', 'pending', 'added', 'visited', 'won', 'lost'] as const).map((t) => (
                <button
                  key={t}
                  onClick={() => setTab(t)}
                  className={cn(
                    'px-2.5 py-1 rounded-full text-[10px] font-mono border shrink-0 transition',
                    tab === t
                      ? 'border-ember-500/50 bg-ember-500/10 text-ember-200 shadow-glow'
                      : 'border-white/10 text-ink-400 hover:border-white/20',
                  )}
                >
                  {({ all: '全部', pending: '待跟进', added: '已加微', visited: '已到店', won: '已成交', lost: '已流失' } as Record<string, string>)[t]}
                </button>
              ))}
            </div>

            <div className="max-h-[480px] overflow-y-auto space-y-1.5 pr-1">
              {filteredLeads.length === 0 && (
                <EmptyState
                  icon={<Users2 className="w-7 h-7 text-ink-400" />}
                  title="暂无符合的线索"
                  caption={`${radius} km 内还没有待触达客户,点击"一键造 24 个"立即生成。`}
                  action={
                    <button onClick={oneClickSeeds} className="btn-primary !py-2 !text-xs">
                      <Zap className="w-3.5 h-3.5" />
                      一键造 {radius}km 线索
                    </button>
                  }
                />
              )}
              {filteredLeads.map((l, i) => {
                const sel = selectedIds.has(l.id);
                return (
                  <motion.label
                    key={l.id}
                    initial={{ opacity: 0, y: 4 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: Math.min(i * 0.02, 0.5) }}
                    className={cn(
                      'flex items-center gap-2 rounded-lg border p-2 cursor-pointer transition',
                      sel ? 'border-ember-500/50 bg-ember-500/[0.06] shadow-glow' : 'border-white/5 hover:border-white/10',
                    )}
                  >
                    <input
                      type="checkbox"
                      checked={sel}
                      onChange={() => toggleLead(l.id)}
                      className="accent-ember-500"
                    />
                    <div className="w-7 h-7 rounded-full bg-gradient-to-br from-cyber-300 to-cyber-500 text-ink-950 font-bold text-[10px] grid place-items-center shrink-0">
                      {l.name.slice(0, 1)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="text-xs font-medium text-white truncate">{l.name}</div>
                      <div className="text-[9px] font-mono text-ink-400 truncate">{l.phone} · {l.fromRadius}km</div>
                    </div>
                    <span className={cn('pill text-[9px] border', STATUS_TONE[l.status])}>
                      {l.status === 'pending' ? '待' : l.status === 'added' ? '加' : l.status === 'visited' ? '店' : l.status === 'won' ? '成' : '失'}
                    </span>
                  </motion.label>
                );
              })}
            </div>
          </motion.div>

          {/* 发送按钮 + 预算面板 */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className="panel p-5 sticky top-4 relative overflow-hidden"
          >
            <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-ember-500/20 blur-3xl" />
            <SectionHeader
              index="05"
              icon={Wallet}
              title="触达预算"
              caption={`${selectedChannels.length} 渠道 × ${selectedIds.size} 人`}
            />
            <div className="grid grid-cols-2 gap-2 mb-4 relative">
              {[
                { l: '目标数', v: selectedIds.size, t: 'text-white', icon: Users2 },
                { l: '预估成本', v: `¥${totalCost.toFixed(2)}`, t: 'text-ember-300', icon: Wallet },
                { l: '预估打开', v: estimatedOpen, t: 'text-cyber-200', icon: TrendingUp },
                { l: '预计转化', v: Math.round(selectedIds.size * 0.18 * (selectedChannels.length || 1)), t: 'text-ember-200', icon: Target },
              ].map((c, i) => (
                <motion.div
                  key={c.l}
                  initial={{ opacity: 0, scale: 0.96 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: 0.15 + i * 0.04 }}
                  className="rounded-lg border border-white/5 bg-ink-800/40 p-2.5 hover:border-ember-500/30 transition"
                >
                  <div className="flex items-center gap-1.5 text-[10px] font-mono text-ink-400">
                    <c.icon className="w-3 h-3" />
                    {c.l}
                  </div>
                  <div className={cn('mt-1 font-mono text-lg font-bold', c.t)}>{c.v}</div>
                </motion.div>
              ))}
            </div>

            <button
              onClick={send}
              disabled={sending || selectedIds.size === 0 || selectedChannels.length === 0}
              className="relative w-full btn-primary disabled:opacity-40 disabled:cursor-not-allowed overflow-hidden"
            >
              {sending && (
                <motion.div
                  className="absolute inset-0 bg-gradient-to-r from-transparent via-white/20 to-transparent"
                  animate={{ x: ['-100%', '100%'] }}
                  transition={{ duration: 1.5, repeat: Infinity, ease: 'linear' }}
                />
              )}
              <span className="relative inline-flex items-center gap-2">
                {sending ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" /> 群发中…
                  </>
                ) : (
                  <>
                    <Send className="w-4 h-4" />
                    一键群发 {selectedChannels.length} 渠道 × {selectedIds.size} 人
                  </>
                )}
              </span>
            </button>

            <div className="mt-3 text-[10px] font-mono text-ink-400 leading-relaxed flex items-start gap-1.5">
              <Clock className="w-3 h-3 mt-0.5 shrink-0 text-cyber-300" />
              <span>触达结果会实时回写到线索状态:<b className="text-ink-200">待跟进 → 已加微</b>,失败原因在下方日志可见</span>
            </div>
          </motion.div>
        </div>
      </div>
    </div>
  );
}
