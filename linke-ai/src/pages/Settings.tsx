import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Settings as SettingsIcon, Plus, Shield, Store as StoreIcon, Trash2, MapPin, Sparkles, Phone, Wallet, UserPlus2, Building2, User } from 'lucide-react';
import SectionHeader from '@/components/SectionHeader';
import { toast } from '@/components/Toast';
import { useGlobal } from '@/store/useGlobal';
import { api } from '@/lib/api';
import type { Store, User } from '@/lib/types';
import { cn } from '@/lib/utils';

interface Member {
  storeId: string;
  userId: string;
  role: 'owner' | 'manager' | 'bd';
  user?: User;
}

const ROLE_TONE: Record<Member['role'], string> = {
  owner: 'bg-ember-500/15 text-ember-200 border-ember-500/30',
  manager: 'bg-cyber-300/15 text-cyber-200 border-cyber-300/30',
  bd: 'bg-signal-violet/15 text-signal-violet border-signal-violet/30',
};

const ROLE_LABEL: Record<Member['role'], string> = {
  owner: '主账号',
  manager: '店长',
  bd: '地推 BD',
};

export default function SettingsPage() {
  const { stores, currentStoreId, setCurrentStore, user } = useGlobal();
  const [members, setMembers] = useState<Member[]>([]);
  const store = stores.find((s) => s.id === currentStoreId);

  useEffect(() => {
    if (!currentStoreId) return;
    api.get<{ members: Member[] }>(`/stores/${currentStoreId}/members`).then((r) => setMembers(r.members));
  }, [currentStoreId]);

  return (
    <div className="p-8 max-w-[1200px] mx-auto">
      <motion.header
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex items-end justify-between flex-wrap gap-4 mb-6"
      >
        <div>
          <div className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-widest text-cyber-200">
            <SettingsIcon className="w-3.5 h-3.5" />
            Settings · 门店与成员
            <span className="text-ink-500">·</span>
            <span className="inline-flex items-center gap-1 text-ember-300">
              <span className="w-1.5 h-1.5 rounded-full bg-ember-500 animate-pulse" />
              {stores.length} 门店
            </span>
          </div>
          <h1 className="mt-2 text-3xl font-display font-extrabold">门店 · 配额 · 权限</h1>
          <p className="mt-1 text-sm text-ink-400">管理你名下的所有门店、邀请 BD 加入、设置触达配额</p>
        </div>
      </motion.header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 门店列表 */}
        <div className="lg:col-span-1 space-y-3">
          <SectionHeader
            index="01"
            icon={Building2}
            title="门店列表"
            caption={`共 ${stores.length} 家`}
          />
          {stores.map((s: Store, i) => (
            <motion.button
              key={s.id}
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.04 }}
              whileHover={{ y: -2 }}
              onClick={() => setCurrentStore(s.id)}
              className={cn(
                'w-full text-left panel p-4 transition relative overflow-hidden',
                s.id === currentStoreId
                  ? 'border-ember-500/50 bg-ember-500/[0.05] shadow-glow'
                  : 'hover:border-ember-500/30',
              )}
            >
              {s.id === currentStoreId && (
                <div className="absolute -top-8 -right-8 w-20 h-20 rounded-full bg-ember-500/30 blur-2xl" />
              )}
              <div className="relative flex items-start gap-3">
                <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-cyber-300 to-cyber-500 text-ink-950 grid place-items-center shrink-0">
                  <StoreIcon className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <div className="text-sm font-semibold text-white">{s.name}</div>
                  <div className="mt-0.5 text-[10px] font-mono text-ink-400 flex items-center gap-1">
                    <MapPin className="w-3 h-3" />
                    {s.address}
                  </div>
                  <div className="mt-1.5 text-[10px] font-mono text-cyber-200 inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-cyber-300/10 border border-cyber-300/20">
                    {s.category}
                  </div>
                </div>
              </div>
            </motion.button>
          ))}
          <motion.button
            whileHover={{ y: -2 }}
            onClick={() => toast.info('新建门店功能开发中', '可联系客户经理开通')}
            className="w-full panel p-4 text-ink-300 hover:border-ember-500/30 hover:text-white transition flex items-center justify-center gap-2 text-sm relative overflow-hidden"
          >
            <div className="absolute -top-8 -right-8 w-20 h-20 rounded-full bg-cyber-300/0 hover:bg-cyber-300/20 blur-2xl transition" />
            <Plus className="w-4 h-4" /> 新建门店
          </motion.button>
        </div>

        {/* 当前门店详情 */}
        <div className="lg:col-span-2 space-y-4">
          {store && (
            <motion.div
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              className="panel p-6 relative overflow-hidden"
            >
              <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-ember-500/15 blur-3xl" />
              <div className="relative">
                <SectionHeader
                  index="02"
                  icon={StoreIcon}
                  title="当前门店详情"
                  caption={store.category}
                  actions={
                    <button className="btn-ghost !py-1.5 !text-xs">
                      <Sparkles className="w-3.5 h-3.5" />
                      编辑
                    </button>
                  }
                />
                <h2 className="text-xl font-display font-bold mt-2">{store.name}</h2>
                <div className="mt-1 text-xs text-ink-400 font-mono flex items-center gap-1.5">
                  <MapPin className="w-3 h-3 text-ember-500" />
                  {store.address}
                </div>
                <div className="mt-1 text-[10px] font-mono text-ink-500">
                  Lng {store.lng.toFixed(4)} · Lat {store.lat.toFixed(4)}
                </div>

                <div className="mt-4 grid grid-cols-3 gap-2">
                  {[
                    { l: '月触达配额', v: '12,000 / 20,000', i: Phone,    t: 'text-ember-300' },
                    { l: '本月已用',   v: '4,213',            i: Wallet,    t: 'text-cyber-200' },
                    { l: 'AI 配额',    v: '880 / 2000',       i: Sparkles,   t: 'text-signal-violet' },
                  ].map((s, i) => {
                    const Icon = s.i;
                    return (
                      <motion.div
                        key={s.l}
                        initial={{ opacity: 0, scale: 0.95 }}
                        animate={{ opacity: 1, scale: 1 }}
                        transition={{ delay: 0.1 + i * 0.05 }}
                        className="rounded-xl border border-white/5 bg-ink-800/40 p-3 hover:border-ember-500/30 transition relative overflow-hidden"
                      >
                        <div className="absolute -top-4 -right-4 w-10 h-10 rounded-full bg-ember-500/0 hover:bg-ember-500/15 blur-lg transition" />
                        <div className="relative flex items-center gap-1.5 text-[10px] font-mono uppercase tracking-widest text-ink-400">
                          <Icon className="w-3 h-3" />
                          {s.l}
                        </div>
                        <div className={cn('relative mt-1 font-mono text-lg font-bold', s.t)}>{s.v}</div>
                      </motion.div>
                    );
                  })}
                </div>
              </div>
            </motion.div>
          )}

          {/* 成员 */}
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className="panel p-5 relative overflow-hidden"
          >
            <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-cyber-300/20 blur-3xl" />
            <div className="relative">
              <SectionHeader
                index="03"
                icon={Shield}
                title="成员与权限"
                caption={`${members.length} 人`}
                actions={
                  <button
                    onClick={() => toast.info('邀请链接已复制', '分享给你的 BD / 店长即可加入')}
                    className="btn-primary !py-1.5 !text-xs"
                  >
                    <UserPlus2 className="w-3.5 h-3.5" />
                    邀请 BD
                  </button>
                }
              />

              <div className="space-y-2">
                {members.length === 0 && (
                  <div className="text-xs text-ink-400 text-center py-6">还没有成员,先邀请一个吧</div>
                )}
                {members.map((m, i) => (
                  <motion.div
                    key={m.userId}
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.15 + i * 0.05 }}
                    className="flex items-center gap-3 rounded-xl border border-white/5 bg-ink-800/30 p-3 hover:border-ember-500/30 transition relative overflow-hidden"
                  >
                    <div className="absolute -top-6 -right-6 w-14 h-14 rounded-full bg-ember-500/0 hover:bg-ember-500/15 blur-xl transition" />
                    <div className="relative w-9 h-9 rounded-full bg-gradient-to-br from-ember-500 to-ember-700 text-ink-950 font-bold grid place-items-center shrink-0">
                      {m.user?.name?.slice(0, 1) || '?'}
                    </div>
                    <div className="relative flex-1 min-w-0">
                      <div className="text-sm font-medium text-white">
                        {m.user?.name}
                        {m.userId === user?.id && (
                          <span className="ml-2 text-[10px] font-mono text-cyber-200 inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-cyber-300/10 border border-cyber-300/20">
                            <User className="w-2.5 h-2.5" /> 你
                          </span>
                        )}
                      </div>
                      <div className="text-[10px] font-mono text-ink-400 flex items-center gap-1">
                        <Phone className="w-2.5 h-2.5" /> {m.user?.phone}
                      </div>
                    </div>
                    <span className={cn('pill border text-[9px]', ROLE_TONE[m.role])}>{ROLE_LABEL[m.role]}</span>
                    {m.role !== 'owner' && (
                      <button
                        onClick={() => toast.info('已移除', `${m.user?.name} 已从当前门店移除`)}
                        className="text-ink-400 hover:text-ember-300 p-1.5 rounded-lg hover:bg-white/5 transition"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    )}
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
