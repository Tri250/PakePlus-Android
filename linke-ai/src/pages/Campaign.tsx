import { useEffect, useState } from 'react';
import { motion, Reorder } from 'framer-motion';
import {
  Workflow,
  Plus,
  Play,
  Pause,
  MessageSquare,
  Clock,
  Ticket,
  Send,
  ChevronRight,
  GripVertical,
  Trash2,
  Sparkles,
  Wand2,
  ListChecks,
  CalendarClock,
} from 'lucide-react';
import RadiusSelector from '@/components/RadiusSelector';
import SectionHeader from '@/components/SectionHeader';
import { toast } from '@/components/Toast';
import { useGlobal } from '@/store/useGlobal';
import { api } from '@/lib/api';
import type { Campaign, Channel, FlowNode } from '@/lib/types';
import { cn } from '@/lib/utils';

const NODE_TEMPLATES: { type: FlowNode['type']; label: string; icon: typeof MessageSquare; tone: string }[] = [
  { type: 'channel', label: '渠道触达', icon: Send, tone: 'border-ember-500/40 text-ember-200 bg-ember-500/10' },
  { type: 'wait',    label: '等待 N 分钟', icon: Clock, tone: 'border-white/10 text-ink-300 bg-white/5' },
  { type: 'card',    label: '发放卡券', icon: Ticket, tone: 'border-cyber-300/40 text-cyber-200 bg-cyber-300/10' },
  { type: 'copy',    label: '生成话术', icon: MessageSquare, tone: 'border-signal-violet/40 text-signal-violet bg-signal-violet/10' },
];

const CHANNEL_LABEL: Record<Channel, string> = {
  sms: '短信',
  wechat: '企微',
  douyin: '抖音',
  card: '卡券',
  phone: '外呼',
};

const STATUS_TONE: Record<Campaign['status'], string> = {
  draft: 'border-white/10 text-ink-300 bg-white/5',
  running: 'border-cyber-300/30 text-cyber-200 bg-cyber-300/10',
  paused: 'border-ember-500/30 text-ember-200 bg-ember-500/10',
  done: 'border-signal-violet/30 text-signal-violet bg-signal-violet/10',
};

const STATUS_LABEL: Record<Campaign['status'], string> = {
  draft: '草稿',
  running: '投放中',
  paused: '已暂停',
  done: '已结束',
};

export default function CampaignPage() {
  const { currentStoreId, radius, setRadius, stores } = useGlobal();
  const [campaigns, setCampaigns] = useState<Campaign[]>([]);
  const [editing, setEditing] = useState<FlowNode[]>([
    { id: 'n1', type: 'channel', channel: 'wechat' },
    { id: 'n2', type: 'wait', min: 1440 },
    { id: 'n3', type: 'channel', channel: 'sms' },
    { id: 'n4', type: 'card' },
  ]);
  const [name, setName] = useState('3 公里写字楼午间专场');
  const [busy, setBusy] = useState(false);

  const store = stores.find((s) => s.id === currentStoreId);

  const load = () => {
    if (!currentStoreId) return;
    api.get<{ campaigns: Campaign[] }>(`/campaigns?storeId=${currentStoreId}`).then((r) => setCampaigns(r.campaigns));
  };
  useEffect(() => { load(); }, [currentStoreId]);

  const addNode = (type: FlowNode['type']) => {
    setEditing((arr) => [
      ...arr,
      {
        id: `n${arr.length + 1}_${Math.random().toString(36).slice(2, 5)}`,
        type,
        channel: type === 'channel' ? 'wechat' : undefined,
        min: type === 'wait' ? 1440 : undefined,
      } as FlowNode,
    ]);
  };

  const updateNode = (id: string, patch: Partial<FlowNode>) => {
    setEditing((arr) => arr.map((n) => (n.id === id ? { ...n, ...patch } : n)));
  };

  const removeNode = (id: string) => {
    setEditing((arr) => arr.filter((n) => n.id !== id));
  };

  const onSave = async () => {
    if (!currentStoreId) return;
    if (!name.trim()) {
      toast.error('请先填写活动名称');
      return;
    }
    setBusy(true);
    try {
      await api.post('/campaigns', {
        name,
        storeId: currentStoreId,
        radiusKm: radius,
        flow: editing,
      });
      await load();
      toast.success('活动已保存', `「${name}」已创建,可点击启动投放`);
    } catch {
      toast.error('保存失败', '请稍后重试');
    } finally {
      setBusy(false);
    }
  };

  const toggleStatus = async (c: Campaign) => {
    const next = c.status === 'running' ? 'paused' : 'running';
    await api.patch(`/campaigns/${c.id}`, { status: next });
    load();
    toast.success(c.status === 'running' ? '已暂停活动' : '已启动活动', `「${c.name}」`);
  };

  return (
    <div className="p-8 max-w-[1400px] mx-auto">
      <motion.header
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex items-end justify-between flex-wrap gap-4 mb-6"
      >
        <div>
          <div className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-widest text-cyber-200">
            <Workflow className="w-3.5 h-3.5" />
            Campaign · 智能营销中心
            <span className="text-ink-500">·</span>
            <span className="inline-flex items-center gap-1 text-ember-300">
              <span className="w-1.5 h-1.5 rounded-full bg-ember-500 animate-pulse" />
              FLOW
            </span>
          </div>
          <h1 className="mt-2 text-3xl font-display font-extrabold">触达编排 · 画布式工作流</h1>
          <p className="mt-1 text-sm text-ink-400">拖拽节点 · 设置渠道与等待 · 一键保存为活动</p>
        </div>
        <RadiusSelector value={radius} onChange={setRadius} size="sm" showLabel={false} />
      </motion.header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 左侧:画布 */}
        <div className="lg:col-span-2 space-y-4">
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            className="panel p-6 relative overflow-hidden"
          >
            <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-ember-500/15 blur-3xl" />
            <SectionHeader
              index="01"
              icon={Wand2}
              title="画布编排"
              caption={`${editing.length} 节点 · 将自动消耗约 ${editing.filter((n) => n.type === 'channel').length * 50} 触达配额`}
            />

            <div className="flex items-center gap-3 mb-5 mt-2">
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="flex-1 bg-transparent border-b border-white/10 focus:border-ember-500/60 text-lg font-display font-bold py-1.5 focus:outline-none transition"
                placeholder="活动名称"
              />
              <span className="text-[11px] font-mono text-ink-400 flex items-center gap-1.5">
                <Sparkles className="w-3 h-3 text-ember-300" />
                {store?.name} · {radius}km
              </span>
            </div>

            <Reorder.Group axis="y" values={editing} onReorder={setEditing} className="space-y-2">
              {editing.map((node, idx) => {
                const tpl = NODE_TEMPLATES.find((t) => t.type === node.type)!;
                const Icon = tpl.icon;
                return (
                  <Reorder.Item
                    key={node.id}
                    value={node}
                    className="list-none relative"
                    whileDrag={{ scale: 1.02, boxShadow: '0 20px 40px -10px rgba(0,0,0,0.6)' }}
                  >
                    <div className="flex items-stretch gap-3">
                      <div className="flex flex-col items-center justify-center w-8 text-ink-500 shrink-0">
                        <GripVertical className="w-4 h-4" />
                        <span className="text-[10px] font-mono mt-1">{String(idx + 1).padStart(2, '0')}</span>
                      </div>
                      <motion.div
                        layout
                        className={cn(
                          'flex-1 rounded-xl border p-3 flex items-center gap-3 relative overflow-hidden',
                          tpl.tone,
                        )}
                      >
                        <div className="absolute -top-4 -right-4 w-12 h-12 rounded-full bg-white/0 hover:bg-white/5 blur-xl transition" />
                        <div className="w-8 h-8 rounded-lg bg-ink-950/50 grid place-items-center shrink-0">
                          <Icon className="w-4 h-4" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="text-sm font-semibold">{tpl.label}</div>
                          {node.type === 'channel' && (
                            <div className="mt-1 flex items-center gap-1.5 flex-wrap">
                              {(['wechat', 'sms', 'douyin', 'card', 'phone'] as Channel[]).map((c) => (
                                <button
                                  key={c}
                                  onClick={() => updateNode(node.id, { channel: c })}
                                  className={cn(
                                    'px-2 py-0.5 text-[10px] font-mono rounded border transition',
                                    node.channel === c
                                      ? 'bg-white/10 text-white border-white/20 shadow-glow'
                                      : 'border-white/10 text-ink-300 hover:border-white/20',
                                  )}
                                >
                                  {CHANNEL_LABEL[c]}
                                </button>
                              ))}
                            </div>
                          )}
                          {node.type === 'wait' && (
                            <div className="mt-1 flex items-center gap-2">
                              <input
                                type="number"
                                value={node.min || 0}
                                onChange={(e) => updateNode(node.id, { min: Number(e.target.value) })}
                                className="w-20 bg-ink-950/60 border border-white/10 rounded px-2 py-0.5 text-xs font-mono focus:outline-none focus:border-ember-500/60"
                              />
                              <span className="text-[10px] font-mono text-ink-300 flex items-center gap-1">
                                <CalendarClock className="w-3 h-3" /> 分钟后执行下一步
                              </span>
                            </div>
                          )}
                          {node.type === 'card' && (
                            <div className="text-[10px] font-mono text-ink-300 mt-1">
                              发放 30 元代金券 ×2(AI 建议)
                            </div>
                          )}
                          {node.type === 'copy' && (
                            <div className="text-[10px] font-mono text-ink-300 mt-1">
                              调用 AI 文案 · 适配当前圈层
                            </div>
                          )}
                        </div>
                        <button
                          onClick={() => removeNode(node.id)}
                          className="text-ink-400 hover:text-ember-300 p-1 transition"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </motion.div>
                    </div>
                    {idx < editing.length - 1 && (
                      <div className="ml-12 my-1 flex items-center gap-2 text-[10px] font-mono text-ink-500">
                        <div className="w-px h-4 bg-gradient-to-b from-ember-500/40 to-transparent" />
                        <ChevronRight className="w-3 h-3 -rotate-90 text-ember-500/50" />
                      </div>
                    )}
                  </Reorder.Item>
                );
              })}
            </Reorder.Group>

            {/* 添加节点 */}
            <div className="mt-5 flex flex-wrap items-center gap-2">
              <span className="text-[10px] font-mono uppercase tracking-widest text-ink-400 mr-1">+ 添加</span>
              {NODE_TEMPLATES.map((t) => {
                const Icon = t.icon;
                return (
                  <motion.button
                    key={t.type}
                    whileHover={{ y: -2 }}
                    onClick={() => addNode(t.type)}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-full border border-white/10 text-ink-200 hover:border-ember-500/40 hover:shadow-glow transition"
                  >
                    <Icon className="w-3.5 h-3.5" />
                    {t.label}
                  </motion.button>
                );
              })}
            </div>

            <div className="mt-6 flex items-center justify-between border-t border-white/5 pt-4">
              <div className="text-[11px] font-mono text-ink-400 flex items-center gap-1.5">
                <ListChecks className="w-3 h-3" />
                共 {editing.length} 节点 · {editing.filter((n) => n.type === 'channel').length} 次触达
              </div>
              <div className="flex items-center gap-2">
                <button onClick={() => setEditing([])} className="btn-ghost !py-2 !text-xs">清空</button>
                <button onClick={onSave} disabled={busy} className="btn-primary !py-2 !text-xs relative overflow-hidden">
                  {busy && (
                    <motion.div
                      className="absolute inset-0 bg-gradient-to-r from-transparent via-white/20 to-transparent"
                      animate={{ x: ['-100%', '100%'] }}
                      transition={{ duration: 1.5, repeat: Infinity, ease: 'linear' }}
                    />
                  )}
                  <span className="relative inline-flex items-center gap-1.5">
                    <Plus className="w-3.5 h-3.5" />
                    {busy ? '保存中…' : '保存为活动'}
                  </span>
                </button>
              </div>
            </div>
          </motion.div>
        </div>

        {/* 右侧:活动列表 */}
        <div className="space-y-4">
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className="panel p-5 relative overflow-hidden"
          >
            <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-cyber-300/20 blur-3xl" />
            <div className="relative">
              <SectionHeader
                index="02"
                icon={Workflow}
                title="进行中的活动"
                caption={`${campaigns.length} 个`}
              />
              <div className="space-y-2 mt-2">
                {campaigns.length === 0 && (
                  <div className="text-xs text-ink-400 text-center py-6">
                    暂无活动,先在左侧画布编辑一个吧
                  </div>
                )}
                {campaigns.map((c, i) => (
                  <motion.div
                    key={c.id}
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: i * 0.05 }}
                    whileHover={{ y: -2 }}
                    className="rounded-xl border border-white/5 bg-ink-800/30 p-3 hover:border-ember-500/30 transition relative overflow-hidden"
                  >
                    <div className="absolute -top-6 -right-6 w-16 h-16 rounded-full bg-ember-500/0 hover:bg-ember-500/20 blur-2xl transition" />
                    <div className="relative flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="text-sm font-medium text-white truncate">{c.name}</div>
                        <div className="mt-0.5 text-[10px] font-mono text-ink-400 flex items-center gap-1.5">
                          <span>{c.radiusKm} km</span>
                          <span className="text-ink-500">·</span>
                          <span>节点 {c.flow.length} 个</span>
                        </div>
                      </div>
                      <span className={cn('pill text-[9px] border', STATUS_TONE[c.status])}>
                        {STATUS_LABEL[c.status]}
                      </span>
                    </div>
                    <div className="relative mt-2 flex items-center gap-1.5 flex-wrap">
                      {c.flow.map((n, i) => (
                        <span key={i} className="text-[10px] font-mono text-ink-300 inline-flex items-center gap-0.5">
                          {n.type === 'channel' && n.channel ? CHANNEL_LABEL[n.channel] : n.type}
                          {i < c.flow.length - 1 && <span className="text-ink-500 mx-0.5">→</span>}
                        </span>
                      ))}
                    </div>
                    <div className="relative mt-3 flex items-center justify-between">
                      <span className="text-[10px] font-mono text-ink-400">
                        创建 {new Date(c.createdAt).toLocaleDateString('zh-CN')}
                      </span>
                      <button
                        onClick={() => toggleStatus(c)}
                        className={cn(
                          'inline-flex items-center gap-1 text-[10px] font-mono px-2 py-1 rounded-full border transition',
                          c.status === 'running'
                            ? 'border-ember-500/30 text-ember-200 hover:bg-ember-500/10'
                            : 'border-cyber-300/30 text-cyber-200 hover:bg-cyber-300/10',
                        )}
                      >
                        {c.status === 'running' ? <Pause className="w-3 h-3" /> : <Play className="w-3 h-3" />}
                        {c.status === 'running' ? '暂停' : '启动'}
                      </button>
                    </div>
                  </motion.div>
                ))}
              </div>
            </div>
          </motion.div>
        </div>
      </div>
    </div>
  );
}
