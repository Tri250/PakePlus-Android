import { useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import {
  Users2,
  Filter,
  Search,
  Phone,
  Plus,
  Sparkles,
  CheckCircle2,
  Clock,
  XCircle,
  Award,
  Send as SendIcon,
  Smartphone,
  MessageSquare,
  Megaphone,
  Ticket,
  Headphones,
  ChevronRight,
  UserCircle2,
  ListFilter,
  Activity,
  Wand2,
} from 'lucide-react';
import { useGlobal } from '@/store/useGlobal';
import { api } from '@/lib/api';
import type { Lead, LeadEvent, LeadStatus, RadiusKm, Channel } from '@/lib/types';
import { cn } from '@/lib/utils';
import SectionHeader from '@/components/SectionHeader';
import { toast } from '@/components/Toast';
import { EmptyState } from '@/components/Misc';

const STATUS_TABS: { value: LeadStatus | 'all'; label: string; icon: typeof CheckCircle2; tone: string }[] = [
  { value: 'all',     label: '全部',   icon: Users2,     tone: 'text-white' },
  { value: 'pending', label: '待跟进', icon: Clock,      tone: 'text-signal-gold' },
  { value: 'added',   label: '已加微', icon: CheckCircle2, tone: 'text-cyber-200' },
  { value: 'visited', label: '已到店', icon: Sparkles,   tone: 'text-ember-300' },
  { value: 'won',     label: '已成交', icon: Award,      tone: 'text-signal-violet' },
  { value: 'lost',    label: '已流失', icon: XCircle,    tone: 'text-ink-400' },
];

const RADIUS_TONE: Record<RadiusKm, string> = {
  3:  'bg-ember-500/20 text-ember-200 border-ember-500/40',
  5:  'bg-ember-400/20 text-ember-200 border-ember-400/40',
  8:  'bg-cyber-300/20 text-cyber-200 border-cyber-300/40',
  10: 'bg-signal-violet/20 text-signal-violet border-signal-violet/40',
};

const STATUS_TONE: Record<LeadStatus, string> = {
  pending: 'border-signal-gold/30 text-signal-gold bg-signal-gold/5',
  added: 'border-cyber-300/30 text-cyber-200 bg-cyber-300/5',
  visited: 'border-ember-500/30 text-ember-200 bg-ember-500/5',
  won: 'border-signal-violet/40 text-signal-violet bg-signal-violet/5',
  lost: 'border-white/10 text-ink-400 bg-white/5',
};

const CHANNEL_ICON: Record<Channel, typeof Smartphone> = {
  sms: Smartphone,
  wechat: MessageSquare,
  douyin: Megaphone,
  card: Ticket,
  phone: Headphones,
};
const CHANNEL_LABEL: Record<Channel, string> = {
  sms: '短信',
  wechat: '企微',
  douyin: '抖音',
  card: '卡券',
  phone: 'AI 外呼',
};

export default function LeadsPage() {
  const { currentStoreId, radius } = useGlobal();
  const [leads, setLeads] = useState<Lead[]>([]);
  const [tab, setTab] = useState<LeadStatus | 'all'>('all');
  const [radiusFilter, setRadiusFilter] = useState<RadiusKm | 'all'>(radius);
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<Lead | null>(null);
  const [leadEvents, setLeadEvents] = useState<LeadEvent[]>([]);
  const [seeding, setSeeding] = useState(false);

  const load = () => {
    if (!currentStoreId) return;
    api.get<{ items: Lead[] }>(`/leads?storeId=${currentStoreId}`).then((r) => setLeads(r.items));
  };
  useEffect(() => { load(); }, [currentStoreId]);

  useEffect(() => {
    if (!selected) {
      setLeadEvents([]);
      return;
    }
    api.get<{ events: LeadEvent[] }>(`/touch/logs?leadId=${selected.id}`).then((r) => setLeadEvents(r.events));
  }, [selected]);

  const counts = useMemo(() => {
    const map: Record<string, number> = { all: leads.length };
    for (const s of ['pending', 'added', 'visited', 'won', 'lost'] as LeadStatus[]) {
      map[s] = leads.filter((l) => l.status === s).length;
    }
    return map;
  }, [leads]);

  const filtered = leads
    .filter((l) => (tab === 'all' ? true : l.status === tab))
    .filter((l) => (radiusFilter === 'all' ? true : l.fromRadius === radiusFilter))
    .filter((l) => (keyword.trim() ? l.name.includes(keyword.trim()) || l.phone.includes(keyword.trim()) : true));

  const onSeed = async () => {
    if (!currentStoreId) return;
    setSeeding(true);
    try {
      const r = await api.post<{ created: number }>('/leads/seed', { storeId: currentStoreId, radiusKm: radius, count: 8 });
      load();
      if (r.created) {
        toast.success(`已造 ${r.created} 个新线索`, `${radius} km 圈层可触达客户已就位`);
      } else {
        toast.info('该圈层暂无可拓展客户', '请尝试更大半径');
      }
    } catch {
      toast.error('造数据失败');
    } finally {
      setSeeding(false);
    }
  };

  const onUpdateStatus = async (id: string, status: LeadStatus) => {
    await api.patch(`/leads/${id}`, { status });
    load();
    if (selected?.id === id) setSelected((s) => (s ? { ...s, status } : s));
    toast.success('已更新状态', `标记为 · ${STATUS_TABS.find(t => t.value === status)?.label}`);
  };

  return (
    <div className="p-8 max-w-[1400px] mx-auto">
      <header className="flex items-end justify-between flex-wrap gap-4 mb-6">
        <div>
          <motion.div
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-widest text-cyber-200"
          >
            <Users2 className="w-3.5 h-3.5" />
            Leads · 线索池
            <span className="text-ink-500">·</span>
            <span className="inline-flex items-center gap-1 text-ember-300">
              <span className="w-1.5 h-1.5 rounded-full bg-ember-500 animate-pulse" />
              {leads.length} 客户
            </span>
          </motion.div>
          <h1 className="mt-2 text-3xl font-display font-extrabold">线索池 · 跟进 · 转化</h1>
          <p className="mt-1 text-sm text-ink-400">AI 已自动按状态归类 · 支持批量触达 / 重新分配 BD</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={onSeed} disabled={seeding} className="btn-ghost !py-2 !text-xs">
            <Plus className={`w-3.5 h-3.5 ${seeding ? 'animate-spin' : ''}`} />
            {seeding ? '造数据中…' : `造一批 ${radius}km 演示线索`}
          </button>
        </div>
      </header>

      {/* 状态 Tab */}
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex items-center gap-1.5 mb-4 overflow-x-auto"
      >
        {STATUS_TABS.map((t, i) => {
          const Icon = t.icon;
          const active = tab === t.value;
          return (
            <motion.button
              key={t.value}
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.03 }}
              whileHover={{ y: -2 }}
              onClick={() => setTab(t.value)}
              className={cn(
                'flex items-center gap-2 px-4 py-2 rounded-full border text-sm transition shrink-0',
                active
                  ? 'border-ember-500/50 bg-gradient-to-r from-ember-500/10 to-ember-500/0 text-white shadow-glow'
                  : 'border-white/10 text-ink-300 hover:border-white/20',
              )}
            >
              <Icon className={cn('w-3.5 h-3.5', active ? 'text-ember-300' : t.tone)} />
              {t.label}
              <span className={cn('font-mono text-[11px]', active ? 'text-ember-300' : 'text-ink-400')}>
                {counts[t.value] || 0}
              </span>
            </motion.button>
          );
        })}
      </motion.div>

      {/* 工具栏 */}
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.05 }}
        className="panel p-3 mb-4 flex items-center gap-2 flex-wrap"
      >
        <div className="flex items-center gap-2 bg-ink-800/60 border border-white/5 rounded-lg px-3 py-1.5 flex-1 min-w-[200px] focus-within:border-ember-500/40 transition">
          <Search className="w-3.5 h-3.5 text-ink-400" />
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="搜索姓名 / 手机号"
            className="flex-1 bg-transparent text-xs focus:outline-none placeholder:text-ink-500"
          />
        </div>
        <div className="flex items-center gap-1.5">
          <ListFilter className="w-3.5 h-3.5 text-ink-400" />
          {(['all', 3, 5, 8, 10] as const).map((r) => (
            <button
              key={r}
              onClick={() => setRadiusFilter(r as RadiusKm | 'all')}
              className={cn(
                'px-2.5 py-1 text-[11px] font-mono rounded-full border transition',
                radiusFilter === r
                  ? r === 'all'
                    ? 'bg-white/10 text-white border-white/20 shadow-glow'
                    : RADIUS_TONE[r as RadiusKm]
                  : 'border-white/10 text-ink-300 hover:border-white/20',
              )}
            >
              {r === 'all' ? '全部' : `${r}km`}
            </button>
          ))}
        </div>
        <button className="btn-primary !py-1.5 !text-xs ml-auto">
          <SendIcon className="w-3.5 h-3.5" />
          批量触达 ({filtered.length})
        </button>
      </motion.div>

      {/* 列表 */}
      <div className="grid grid-cols-12 gap-4">
        <div className="col-span-12 lg:col-span-8 grid grid-cols-1 md:grid-cols-2 gap-3">
          {filtered.length === 0 && (
            <div className="col-span-full">
              <EmptyState
                icon={<Users2 className="w-7 h-7 text-ink-400" />}
                title="没有匹配的线索"
                caption="试试切换 Tab / 调整半径 / 输入关键词,或点击右上角「造一批演示线索」"
                action={
                  <button onClick={onSeed} className="btn-primary !py-2 !text-xs">
                    <Plus className="w-3.5 h-3.5" />
                    造一批 {radius}km 演示线索
                  </button>
                }
              />
            </div>
          )}
          {filtered.map((l, i) => (
            <motion.div
              key={l.id}
              layout
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: Math.min(i * 0.02, 0.4) }}
              whileHover={{ y: -2 }}
              onClick={() => setSelected(l)}
              className={cn(
                'group relative overflow-hidden panel p-4 cursor-pointer hover:border-ember-500/30 transition',
                selected?.id === l.id && 'border-ember-500/60 bg-ember-500/[0.04] shadow-glow',
              )}
            >
              {selected?.id === l.id && (
                <div className="absolute -top-6 -right-6 w-20 h-20 rounded-full bg-ember-500/30 blur-2xl" />
              )}
              <div className="relative flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-full bg-gradient-to-br from-cyber-300 to-cyber-500 text-ink-950 font-bold flex items-center justify-center shadow-cyber">
                    {l.name.slice(0, 1)}
                  </div>
                  <div>
                    <div className="text-sm font-semibold text-white">{l.name}</div>
                    <div className="text-[11px] font-mono text-ink-400 flex items-center gap-1.5">
                      <Phone className="w-3 h-3" />
                      {l.phone}
                    </div>
                  </div>
                </div>
                <span className={cn('pill border', RADIUS_TONE[l.fromRadius])}>{l.fromRadius}km</span>
              </div>
              <div className="relative mt-3 flex items-center justify-between">
                <span className={cn('pill border', STATUS_TONE[l.status])}>
                  {STATUS_TABS.find((t) => t.value === l.status)?.label}
                </span>
                <span className="text-[10px] font-mono text-ink-400">
                  {new Date(l.createdAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}
                </span>
              </div>
            </motion.div>
          ))}
        </div>

        {/* 详情 */}
        <aside className="col-span-12 lg:col-span-4">
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="panel p-5 sticky top-6 relative overflow-hidden"
          >
            {selected ? (
              <>
                <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-ember-500/15 blur-3xl" />
                <div className="relative">
                  <SectionHeader
                    index="01"
                    icon={UserCircle2}
                    title="线索详情"
                    caption={`${leadEvents.length} 条触达记录`}
                  />
                  <div className="flex items-center gap-3 mb-4 mt-2">
                    <div className="w-12 h-12 rounded-full bg-gradient-to-br from-ember-500 to-ember-700 text-ink-950 font-bold text-lg flex items-center justify-center shadow-glow">
                      {selected.name.slice(0, 1)}
                    </div>
                    <div>
                      <div className="text-base font-semibold text-white">{selected.name}</div>
                      <div className="text-xs font-mono text-ink-400">{selected.phone}</div>
                    </div>
                  </div>
                  <div className="space-y-2 text-xs">
                    {[
                      { k: '来源', v: `${selected.fromRadius} km 圈层 · 自动入池` },
                      { k: '状态', v: STATUS_TABS.find((t) => t.value === selected.status)?.label || '' },
                      { k: '归属活动', v: selected.campaignId || '自然流量' },
                      { k: '进入时间', v: new Date(selected.createdAt).toLocaleString('zh-CN') },
                    ].map((r) => (
                      <div key={r.k} className="flex items-center justify-between border-b border-white/5 pb-1.5 last:border-0">
                        <span className="text-ink-400 font-mono text-[11px]">{r.k}</span>
                        <span className="text-ink-200">{r.v}</span>
                      </div>
                    ))}
                  </div>
                  <div className="mt-4">
                    <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400 mb-2">
                      推进状态
                    </div>
                    <div className="grid grid-cols-3 gap-1.5">
                      {(['added', 'visited', 'won'] as LeadStatus[]).map((s) => (
                        <button
                          key={s}
                          onClick={() => onUpdateStatus(selected.id, s)}
                          className={cn(
                            'px-2 py-1.5 text-[10px] font-mono rounded-lg border transition',
                            selected.status === s
                              ? 'border-ember-500/50 bg-ember-500/10 text-ember-200 shadow-glow'
                              : 'border-white/10 text-ink-300 hover:border-white/20',
                          )}
                        >
                          标记为 · {STATUS_TABS.find((t) => t.value === s)?.label}
                        </button>
                      ))}
                    </div>
                  </div>
                  <motion.div
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.1 }}
                    className="mt-4 rounded-xl border border-cyber-300/20 bg-cyber-300/[0.04] p-3 text-[11px] text-ink-200 relative overflow-hidden"
                  >
                    <div className="absolute -top-4 -right-4 w-16 h-16 rounded-full bg-cyber-300/30 blur-xl" />
                    <div className="relative flex items-center gap-1.5 text-cyber-200 font-mono mb-1">
                      <Wand2 className="w-3 h-3" /> AI 跟进建议
                    </div>
                    <p className="relative leading-relaxed">
                      {selected.name} 来自 {selected.fromRadius} km 圈层,建议优先推送"工作日午间"
                      朋友圈广告并附 9.9 元体验券,若 24 小时内未加微,触发短信二次触达。
                    </p>
                  </motion.div>

                  {/* 触达时间线 */}
                  {leadEvents.length > 0 && (
                    <div className="mt-4">
                      <SectionHeader
                        index="02"
                        icon={Activity}
                        title="触达时间线"
                        caption={`${leadEvents.length} 条记录`}
                      />
                      <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
                        {leadEvents.map((ev, i) => {
                          const channel = (ev.payload?.channel as Channel | undefined) || 'sms';
                          const Icon = CHANNEL_ICON[channel] || SendIcon;
                          const isSuccess = ev.type === 'touch';
                          return (
                            <motion.div
                              key={ev.id}
                              initial={{ opacity: 0, x: -6 }}
                              animate={{ opacity: 1, x: 0 }}
                              transition={{ delay: i * 0.04 }}
                              className="flex gap-2.5"
                            >
                              <div className="flex flex-col items-center pt-0.5">
                                <div
                                  className={cn(
                                    'w-7 h-7 rounded-lg grid place-items-center border shadow-glow',
                                    isSuccess
                                      ? 'bg-cyber-300/10 border-cyber-300/30 text-cyber-200'
                                      : 'bg-ember-500/10 border-ember-500/30 text-ember-300',
                                  )}
                                >
                                  <Icon className="w-3.5 h-3.5" />
                                </div>
                                <div className="w-px flex-1 bg-gradient-to-b from-white/10 to-transparent mt-1" />
                              </div>
                              <div className="flex-1 pb-2">
                                <div className="text-xs text-white flex items-center gap-1.5">
                                  <span className="font-medium">{CHANNEL_LABEL[channel]}</span>
                                  <span className={cn(
                                    'pill text-[9px] border',
                                    (ev.payload?.status as string) === 'success'
                                      ? 'border-cyber-300/30 text-cyber-200 bg-cyber-300/5'
                                      : 'border-ember-500/30 text-ember-300 bg-ember-500/5',
                                  )}>
                                    {(ev.payload?.status as string) === 'success' ? '已发送' : '失败'}
                                  </span>
                                </div>
                                <div className="text-[10px] font-mono text-ink-400">
                                  {new Date(ev.createdAt).toLocaleString('zh-CN')}
                                </div>
                                {ev.payload?.body && (
                                  <div className="mt-1 text-[10px] text-ink-300 bg-ink-800/50 rounded p-2 leading-relaxed line-clamp-3">
                                    {String(ev.payload.body)}
                                  </div>
                                )}
                                {ev.payload?.error && (
                                  <div className="mt-1 text-[10px] text-ember-300 font-mono">
                                    失败: {String(ev.payload.error)}
                                  </div>
                                )}
                              </div>
                            </motion.div>
                          );
                        })}
                      </div>
                    </div>
                  )}

                  <button
                    onClick={() => (window.location.href = '/touch')}
                    className="mt-4 w-full btn-ghost !text-xs !py-2"
                  >
                    <SendIcon className="w-3.5 h-3.5" />
                    前往触达中心群发
                    <ChevronRight className="w-3.5 h-3.5 ml-auto" />
                  </button>
                </div>
              </>
            ) : (
              <div className="text-center text-ink-400 py-12 text-sm">
                <UserCircle2 className="w-12 h-12 mx-auto mb-3 text-ink-500" />
                选择一条线索查看详情
              </div>
            )}
          </motion.div>
        </aside>
      </div>
    </div>
  );
}
