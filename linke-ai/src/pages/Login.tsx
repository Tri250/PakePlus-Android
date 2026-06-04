import { useState } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { Radar, Sparkles, ShieldCheck, ArrowRight } from 'lucide-react';
import { useGlobal } from '@/store/useGlobal';
import { toast } from '@/components/Toast';

export default function Login() {
  const nav = useNavigate();
  const { login } = useGlobal();
  const [phone, setPhone] = useState('13800000000');
  const [code, setCode] = useState('0000');
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await login(phone.trim(), code.trim());
      toast.success('登录成功', '欢迎回到邻客 AI · 3-5-8-10 km 获客驾驶舱');
      nav('/cockpit');
    } catch (e: unknown) {
      toast.error('登录失败', e instanceof Error ? e.message : '请检查手机号 / 验证码');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen flex">
      {/* 左侧视觉 */}
      <div className="hidden lg:flex flex-1 relative items-center justify-center overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(700px_500px_at_30%_30%,rgba(255,106,44,0.25),transparent_60%)]" />
        <div className="absolute inset-0 bg-[radial-gradient(700px_500px_at_70%_70%,rgba(60,224,198,0.18),transparent_60%)]" />
        <div className="absolute inset-0 bg-grid-faint bg-grid-32 opacity-30" />

        {/* 同心圆雷达动画 */}
        <div className="relative w-[560px] h-[560px]">
          {[1, 2, 3, 4, 5].map((i) => (
            <motion.div
              key={i}
              className="absolute inset-0 rounded-full border border-ember-500/30"
              animate={{ scale: [0.6, 1.05], opacity: [0.7, 0] }}
              transition={{ duration: 4, repeat: Infinity, delay: i * 0.7, ease: 'easeOut' }}
            />
          ))}
          <div className="absolute inset-[18%] rounded-full border-2 border-ember-500/40 shadow-glow" />
          <div className="absolute inset-[36%] rounded-full border border-cyber-300/30" />
          <div className="absolute inset-[54%] rounded-full border border-white/10" />
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-6 h-6 rounded-full bg-ember-500 shadow-glow" />

          {/* 角标 */}
          {['3', '5', '8', '10'].map((km, idx) => {
            const angle = (idx / 4) * Math.PI * 2 - Math.PI / 2;
            const r = 280;
            return (
              <div
                key={km}
                className="absolute top-1/2 left-1/2 font-mono"
                style={{
                  transform: `translate(${Math.cos(angle) * r}px, ${Math.sin(angle) * r}px)`,
                }}
              >
                <div className="-translate-x-1/2 -translate-y-1/2 flex flex-col items-center">
                  <span className="text-[10px] text-ink-400">RADIUS</span>
                  <span className="text-3xl font-display font-bold text-white tracking-tight">{km}</span>
                  <span className="text-[10px] text-cyber-200">km</span>
                </div>
              </div>
            );
          })}
        </div>

        {/* 文案 */}
        <div className="absolute bottom-16 left-16 max-w-md">
          <div className="flex items-center gap-2 mb-3">
            <Sparkles className="w-4 h-4 text-cyber-300" />
            <span className="text-xs font-mono uppercase tracking-widest text-cyber-200">
              AI-Powered Local Acquisition
            </span>
          </div>
          <h1 className="text-4xl font-display font-extrabold leading-tight">
            圈选 <span className="text-ember-500">3-5-8-10 公里</span><br />
            内的下一个客户
          </h1>
          <p className="mt-4 text-ink-300 text-sm leading-relaxed">
            邻客 AI 把"周边流量"变成可量化的获客漏斗 —— 智能选址、画像生成、话术编排、转化追踪,一站完成。
          </p>
        </div>
      </div>

      {/* 右侧表单 */}
      <div className="flex-1 flex items-center justify-center p-8">
        <motion.form
          onSubmit={onSubmit}
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
          className="w-full max-w-sm"
        >
          <div className="flex items-center gap-2 mb-2">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-ember-500 to-ember-700 flex items-center justify-center shadow-glow">
              <Radar className="w-5 h-5 text-ink-950" strokeWidth={2.5} />
            </div>
            <div>
              <div className="text-lg font-display font-bold">邻客 AI</div>
              <div className="text-[10px] font-mono uppercase tracking-widest text-ink-400">Linke Workspace</div>
            </div>
          </div>

          <h2 className="text-2xl font-display font-bold mt-6 mb-1">登录工作台</h2>
          <p className="text-sm text-ink-400 mb-8">手机号 + 验证码 · 开发期验证码 0000</p>

          <div className="space-y-3">
            <div>
              <label className="text-[10px] font-mono uppercase tracking-widest text-ink-400">
                手机号
              </label>
              <input
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                className="mt-1 w-full bg-ink-800/60 border border-white/5 rounded-xl px-4 py-3 text-white placeholder:text-ink-500 focus:outline-none focus:border-ember-500/60 font-mono"
                placeholder="138 0000 0000"
              />
            </div>
            <div>
              <label className="text-[10px] font-mono uppercase tracking-widest text-ink-400">
                验证码
              </label>
              <input
                value={code}
                onChange={(e) => setCode(e.target.value)}
                maxLength={4}
                className="mt-1 w-full bg-ink-800/60 border border-white/5 rounded-xl px-4 py-3 text-white placeholder:text-ink-500 focus:outline-none focus:border-ember-500/60 font-mono tracking-[0.5em] text-center"
                placeholder="0000"
              />
            </div>
          </div>

          {busy && (
            <div className="mt-4 text-xs text-cyber-200 font-mono flex items-center gap-2">
              <motion.span
                className="w-1.5 h-1.5 rounded-full bg-cyber-300"
                animate={{ opacity: [1, 0.3, 1] }}
                transition={{ duration: 1.2, repeat: Infinity }}
              />
              正在登录中…
            </div>
          )}

          <button
            type="submit"
            disabled={busy}
            className="mt-6 w-full btn-primary disabled:opacity-50 relative overflow-hidden"
          >
            {busy && (
              <motion.div
                className="absolute inset-0 bg-gradient-to-r from-transparent via-white/20 to-transparent"
                animate={{ x: ['-100%', '100%'] }}
                transition={{ duration: 1.5, repeat: Infinity, ease: 'linear' }}
              />
            )}
            <span className="relative inline-flex items-center gap-1.5">
              {busy ? '登录中…' : '进入工作台'}
              <ArrowRight className="w-4 h-4" />
            </span>
          </button>

          <div className="mt-6 flex items-center gap-2 text-[11px] font-mono text-ink-400">
            <ShieldCheck className="w-3.5 h-3.5 text-cyber-300" />
            演示环境 · 数据本地存储,不外发
          </div>
        </motion.form>
      </div>
    </div>
  );
}
