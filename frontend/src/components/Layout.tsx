import { useState, type ReactNode } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useSession } from '../auth/session';
import { useToast } from './ui';

const I = {
  home: 'M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z',
  db: 'M12 3c4.4 0 8 1.3 8 3v12c0 1.7-3.6 3-8 3s-8-1.3-8-3V6c0-1.7 3.6-3 8-3zm8 3c0 1.7-3.6 3-8 3S4 7.7 4 6m16 6c0 1.7-3.6 3-8 3s-8-1.3-8-3',
  code: 'M8 7l-5 5 5 5M16 7l5 5-5 5M14 4l-4 16',
  layers: 'M12 3l9 5-9 5-9-5zM3 13l9 5 9-5',
  chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  grid: 'M3 3h8v8H3zM13 3h8v5h-8zM13 10h8v11h-8zM3 13h8v8H3z',
  pulse: 'M3 12h4l3-8 4 16 3-8h4',
  shield: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z',
};

function Icon({ d }: { d: string }) {
  return (
    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d={d} />
    </svg>
  );
}

const NAV: { to: string; label: string; icon: string; perm?: string; group: string }[] = [
  { to: '/', label: 'Overview', icon: I.home, group: 'Workspace' },
  { to: '/dashboards', label: 'Dashboards', icon: I.grid, perm: 'INSIGHT_DASHBOARD_READ', group: 'Workspace' },
  { to: '/charts', label: 'Charts', icon: I.chart, perm: 'INSIGHT_CHART_READ', group: 'Workspace' },
  { to: '/datasets', label: 'Datasets', icon: I.layers, perm: 'INSIGHT_DATASET_READ', group: 'Data' },
  { to: '/queries', label: 'Query templates', icon: I.code, perm: 'INSIGHT_QT_READ', group: 'Data' },
  { to: '/data-sources', label: 'Data sources', icon: I.db, perm: 'INSIGHT_DS_READ', group: 'Data' },
  { to: '/runs', label: 'Query runs', icon: I.pulse, perm: 'INSIGHT_QUERY_EXECUTE', group: 'Operations' },
  { to: '/admin', label: 'Access control', icon: I.shield, perm: 'RBAC_ROLE_MANAGE', group: 'Operations' },
];

export function Layout({ children }: { children?: ReactNode }) {
  const { username, mode, orgId, setOrgId, signOut, can, resetDemo } = useSession();
  const [open, setOpen] = useState(false);
  const toast = useToast();
  const loc = useLocation();
  const groups = [...new Set(NAV.map((n) => n.group))];

  return (
    <div className={`shell ${open ? 'nav-open' : ''}`}>
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden><i /><i /><i /></span>
          <div>
            <div className="brand-name">Insight Studio</div>
            <div className="brand-sub mono">business-insight-api</div>
          </div>
        </div>

        <nav onClick={() => setOpen(false)}>
          {groups.map((g) => {
            const items = NAV.filter((n) => n.group === g && (!n.perm || can(n.perm)));
            if (!items.length) return null;
            return (
              <div key={g} className="nav-group">
                <div className="nav-group-label">{g}</div>
                {items.map((n) => (
                  <NavLink key={n.to} to={n.to} end={n.to === '/'} className="nav-link">
                    <Icon d={n.icon} />
                    <span>{n.label}</span>
                  </NavLink>
                ))}
              </div>
            );
          })}
        </nav>

        <div className="sidebar-foot">
          <label className="org-picker">
            <span>Org</span>
            <input
              type="number" min={1} value={orgId}
              onChange={(e) => { const n = Number(e.target.value); if (n > 0) setOrgId(n); }}
              disabled={mode === 'demo'}
              title={mode === 'demo' ? 'Demo data lives in org 1' : 'Organization id sent as ?orgId='}
            />
          </label>
          <div className="who">
            <div className="avatar" aria-hidden>{(username ?? '?').slice(0, 1).toUpperCase()}</div>
            <div className="who-text">
              <div className="who-name">{username}</div>
              <div className="who-mode">{mode === 'demo' ? 'Demo sandbox' : 'Live API'}</div>
            </div>
            <button className="btn btn-ghost btn-sm" onClick={signOut}>Sign out</button>
          </div>
          {mode === 'demo' && (
            <button className="link-btn small" onClick={() => { resetDemo(); toast('ok', 'Demo data reset'); }}>
              Reset demo data
            </button>
          )}
        </div>
      </aside>

      <div className="main">
        <div className="topbar">
          <button className="icon-btn menu-btn" onClick={() => setOpen((o) => !o)} aria-label="Menu">☰</button>
          <span className="topbar-title">Insight Studio</span>
          {mode === 'demo' && <span className="demo-pill">Demo</span>}
        </div>
        <main className="content" key={loc.pathname}>
          {children ?? <Outlet />}
        </main>
      </div>
      <div className="scrim" onClick={() => setOpen(false)} />
    </div>
  );
}
