import { NavLink } from 'react-router-dom';
import type { ReactNode } from 'react';

const TABS = [
  { to: '/admin', label: '상품' },
  { to: '/admin/categories', label: '카테고리' },
  { to: '/admin/stock', label: '재고' }
];

export function AdminShell({ title, children }: { title: string; children: ReactNode }) {
  return (
    <>
      <header style={{ marginBottom: 28 }} className="rise">
        <span className="eyebrow">Admin Console</span>
        <h1 className="h-display" style={{ fontSize: 'clamp(28px,4vw,40px)' }}>{title}</h1>
        <div className="row" style={{ marginTop: 18 }}>
          {TABS.map((t) => (
            <NavLink
              key={t.to}
              to={t.to}
              end={t.to === '/admin'}
              className={({ isActive }) => `btn btn-sm ${isActive ? 'btn-primary' : 'btn-ghost'}`}
            >
              {t.label}
            </NavLink>
          ))}
        </div>
      </header>
      {children}
    </>
  );
}
