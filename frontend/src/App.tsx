import type { ReactNode } from 'react';
import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { SessionProvider, useSession } from './auth/session';
import { Layout } from './components/Layout';
import { Empty, ToastProvider } from './components/ui';
import { Login } from './pages/Login';
import { Overview } from './pages/Overview';
import { DataSources } from './pages/DataSources';
import { QueryTemplates } from './pages/QueryTemplates';
import { Datasets } from './pages/Datasets';
import { Charts } from './pages/Charts';
import { Dashboards } from './pages/Dashboards';
import { QueryRuns } from './pages/QueryRuns';
import { Admin } from './pages/Admin';

function Guard({ perm, children }: { perm: string; children: ReactNode }) {
  const { can } = useSession();
  return can(perm) ? <>{children}</> : <Empty title="No access">Your role is missing <span className="mono">{perm}</span>.</Empty>;
}

function Routed() {
  const { token } = useSession();
  if (!token) return <Login />;
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Overview />} />
        <Route path="dashboards" element={<Guard perm="INSIGHT_DASHBOARD_READ"><Dashboards /></Guard>} />
        <Route path="charts" element={<Guard perm="INSIGHT_CHART_READ"><Charts /></Guard>} />
        <Route path="datasets" element={<Guard perm="INSIGHT_DATASET_READ"><Datasets /></Guard>} />
        <Route path="queries" element={<Guard perm="INSIGHT_QT_READ"><QueryTemplates /></Guard>} />
        <Route path="data-sources" element={<Guard perm="INSIGHT_DS_READ"><DataSources /></Guard>} />
        <Route path="runs" element={<Guard perm="INSIGHT_QUERY_EXECUTE"><QueryRuns /></Guard>} />
        <Route path="admin" element={<Guard perm="RBAC_ROLE_MANAGE"><Admin /></Guard>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

export function App() {
  return (
    <SessionProvider>
      <ToastProvider>
        <HashRouter>
          <Routed />
        </HashRouter>
      </ToastProvider>
    </SessionProvider>
  );
}
