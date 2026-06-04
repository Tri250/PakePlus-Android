import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Settings as SettingsIcon, Plus, Shield, Store as StoreIcon, Trash2 } from 'lucide-react';
import { useGlobal } from '@/store/useGlobal';
import { api } from '@/lib/api';
import type { Store, User } from '@/lib/types';

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
      <header className="flex items-end justify-between flex-wrap gap-4 mb-6">
        <div>
          <div className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-widest text-cyber-200">
            <SettingsIcon className="w-3.5 h-3.5" />
            Settings · 门店与成员
          </div>
          <h1 className="mt-2 text-3xl font-display font-extrabold">门店 · 配额 · 权限</h1>
          <p className="mt-1 text-sm text-ink-400">管理你名下的所有门店、邀请 BD 加入、设置触达配额</p>
        </div>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 门店列表 */}
        <div className="lg:col-span-1 space-y-3">
          {stores.map((s: Store) => (
            <button
              key={s.id}
              onClick={() => setCurrentStore(s.id)}
              className={`w-full text-left panel p-4 transition ${
                s.id === currentStoreId ? 'border-ember-500/50 bg-ember-500/[0.05]' : 'hover:border-white/10'
              }`}
            >
              <div className="flex items-start gap-3">
                <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-cyber-300 to-cyber-500 text-ink-950 grid place-items-center">
                  <StoreIcon className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <div className="text-sm font-semibold text-white">{s.name}</div>
                  <div className="mt-0.5 text-[10px] font-mono text-ink-400 truncate">{s.address}</div>
                  <div className="mt-1.5 text-[10px] font-mono text-cyber-200">{s.category}</div>
                </div>
              </div>
            </button>
          ))}
          <button className="w-full panel p-4 text-ink-300 hover:border-ember-500/30 transition flex items-center justify-center gap-2 text-sm">
            <Plus className="w-4 h-4" /> 新建门店
          </button>
        </div>

        {/* 当前门店详情 */}
        <div className="lg:col-span-2 space-y-4">
          {store && (
            <div className="panel p-6">
              <div className="flex items-start justify-between mb-4">
                <div>
                  <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400">当前门店</div>
                  <h2 className="text-xl font-display font-bold mt-1">{store.name}</h2>
                  <div className="mt-1 text-xs text-ink-400 font-mono">
                    {store.category} · {store.address}
                  </div>
                  <div className="mt-1 text-[10px] font-mono text-ink-500">
                    Lng {store.lng.toFixed(4)} · Lat {store.lat.toFixed(4)}
                  </div>
                </div>
                <button className="btn-ghost !py-1.5 !text-xs">编辑</button>
              </div>

              <div className="grid grid-cols-3 gap-2">
                {[
                  { l: '月触达配额', v: '12,000 / 20,000' },
                  { l: '本月已用',   v: '4,213' },
                  { l: 'AI 配额',    v: '880 / 2000' },
                ].map((s) => (
                  <div key={s.l} className="rounded-xl border border-white/5 bg-ink-800/40 p-3">
                    <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400">{s.l}</div>
                    <div className="mt-1 font-mono text-lg font-bold text-white">{s.v}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 成员 */}
          <div className="panel p-5">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Shield className="w-4 h-4 text-cyber-300" />
                <div className="text-sm font-semibold text-white">成员与权限</div>
              </div>
              <button className="btn-primary !py-1.5 !text-xs">
                <Plus className="w-3.5 h-3.5" />
                邀请 BD
              </button>
            </div>

            <div className="space-y-2">
              {members.map((m) => (
                <motion.div
                  key={m.userId}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="flex items-center gap-3 rounded-xl border border-white/5 bg-ink-800/30 p-3"
                >
                  <div className="w-9 h-9 rounded-full bg-gradient-to-br from-ember-500 to-ember-700 text-ink-950 font-bold grid place-items-center">
                    {m.user?.name?.slice(0, 1) || '?'}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-medium text-white">
                      {m.user?.name}
                      {m.userId === user?.id && (
                        <span className="ml-2 text-[10px] font-mono text-cyber-200">· 你</span>
                      )}
                    </div>
                    <div className="text-[10px] font-mono text-ink-400">{m.user?.phone}</div>
                  </div>
                  <span className={`pill border ${ROLE_TONE[m.role]}`}>{ROLE_LABEL[m.role]}</span>
                  {m.role !== 'owner' && (
                    <button className="text-ink-400 hover:text-ember-300 p-1.5 rounded-lg">
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </motion.div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
