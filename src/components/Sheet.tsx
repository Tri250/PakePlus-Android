import { useAppStore } from '../store/appStore';
import { useState, useEffect } from 'react';

interface Props {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
}

export default function Sheet({ open, onClose, title, children }: Props) {
  useEffect(() => {
    if (open) {
      // 保存当前状态
    }
  }, [open]);

  if (!open) return null;
  return (
    <>
      <div className="sheet-mask" onClick={onClose} />
      <div className="sheet" onClick={(e) => e.stopPropagation()}>
        <div className="sheet-handle" />
        {title && (
          <h3 className="text-lg font-bold mb-3" style={{ color: 'var(--text-primary)' }}>
            {title}
          </h3>
        )}
        {children}
      </div>
    </>
  );
}
