import { useAppStore } from '../store/appStore';

export default function Toasts() {
  const toasts = useAppStore((s) => s.toasts);
  return (
    <div className="pointer-events-none fixed top-0 left-0 right-0 z-[200] flex flex-col items-center gap-2 pt-12">
      {toasts.map((t) => (
        <div key={t.id} className="toast pointer-events-auto">
          {t.icon && <span className="text-base">{t.icon}</span>}
          <span>{t.text}</span>
        </div>
      ))}
    </div>
  );
}
