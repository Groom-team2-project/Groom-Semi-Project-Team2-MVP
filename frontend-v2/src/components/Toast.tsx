import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';

type Toast = { id: number; text: string; kind: 'info' | 'error' };

const ToastContext = createContext<(text: string, kind?: 'info' | 'error') => void>(() => {});

export function useToast() {
  return useContext(ToastContext);
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const push = useCallback((text: string, kind: 'info' | 'error' = 'info') => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [...prev, { id, text, kind }]);
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 3200);
  }, []);

  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="toast-wrap">
        {toasts.map((t) => (
          <div key={t.id} className={`toast ${t.kind === 'error' ? 'error' : ''}`}>{t.text}</div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
