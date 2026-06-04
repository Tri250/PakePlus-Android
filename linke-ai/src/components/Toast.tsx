import { motion, AnimatePresence } from 'framer-motion';
import { useEffect, useState, useCallback } from 'react';
import { CheckCircle2, AlertCircle, Sparkles, X } from 'lucide-react';
import { cn } from '@/lib/utils';

export type ToastTone = 'success' | 'error' | 'info';
export interface Toast {
  id: string;
  tone: ToastTone;
  title: string;
  body?: string;
}

let listeners: ((t: Toast) => void)[] = [];
export const toast = {
  success: (title: string, body?: string) => emit('success', title, body),
  error: (title: string, body?: string) => emit('error', title, body),
  info: (title: string, body?: string) => emit('info', title, body),
};
const emit = (tone: ToastTone, title: string, body?: string) => {
  const t: Toast = { id: `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`, tone, title, body };
  listeners.forEach((fn) => fn(t));
};

export default function ToastHost() {
  const [items, setItems] = useState<Toast[]>([]);

  useEffect(() => {
    const fn = (t: Toast) => {
      setItems((arr) => [...arr, t]);
      setTimeout(() => setItems((arr) => arr.filter((x) => x.id !== t.id)), 4000);
    };
    listeners.push(fn);
    return () => {
      listeners = listeners.filter((x) => x !== fn);
    };
  }, []);

  const dismiss = useCallback((id: string) => setItems((arr) => arr.filter((x) => x.id !== id)), []);

  return (
    <div className="fixed bottom-6 right-6 z-50 flex flex-col gap-2 w-80 pointer-events-none">
      <AnimatePresence>
        {items.map((t) => (
          <motion.div
            key={t.id}
            initial={{ opacity: 0, x: 60, scale: 0.96 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, x: 60, scale: 0.96 }}
            transition={{ type: 'spring', stiffness: 320, damping: 28 }}
            className={cn(
              'pointer-events-auto rounded-xl border backdrop-blur-xl shadow-panel p-3 flex items-start gap-2.5',
              t.tone === 'success' && 'bg-cyber-300/10 border-cyber-300/30',
              t.tone === 'error' && 'bg-ember-500/10 border-ember-500/30',
              t.tone === 'info' && 'bg-white/5 border-white/10',
            )}
          >
            <div
              className={cn(
                'w-7 h-7 rounded-lg grid place-items-center shrink-0',
                t.tone === 'success' && 'bg-cyber-300/20 text-cyber-200',
                t.tone === 'error' && 'bg-ember-500/20 text-ember-300',
                t.tone === 'info' && 'bg-white/10 text-ink-200',
              )}
            >
              {t.tone === 'success' && <CheckCircle2 className="w-4 h-4" />}
              {t.tone === 'error' && <AlertCircle className="w-4 h-4" />}
              {t.tone === 'info' && <Sparkles className="w-4 h-4" />}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm font-medium text-white">{t.title}</div>
              {t.body && <div className="text-xs text-ink-300 mt-0.5 leading-relaxed">{t.body}</div>}
            </div>
            <button
              onClick={() => dismiss(t.id)}
              className="text-ink-400 hover:text-white -mr-1 -mt-1 p-1 rounded"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}
