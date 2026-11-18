import { Link } from 'react-router-dom';
import type { Chart, Page, QueryRun } from '../api/types';
import { useSession } from '../auth/session';
import { useAsync } from '../lib/hooks';
import { relTime } from '../lib/format';
import { Badge, ErrorNote, PageHeader, Spinner } from '../components/ui';
import { LiveChart } from '../components/LiveChart';

export function Overview() {
  const { api, orgId, can, username, mode } = useSession();

  const { data, error, loading, reload } = useAsync(async () => {
    const count = async <T,>(perm: string, f: () => Promise<Page<T>>) => (can(perm) ? (await f()).totalElements : null);
    const one = { size: 1 };
    const [ds, qt, dsets, charts, dashes] = await Promise.all([
      count('INSIGHT_DS_READ', () => api.listDataSources(orgId, one)),
      count('INSIGHT_QT_READ', () => api.listQueryTemplates(orgId, one)),
      count('INSIGHT_DATASET_READ', () => api.listDatasets(orgId, one)),
      count('INSIGHT_CHART_READ', () => api.listCharts(orgId, one)),
      count('INSIGHT_DASHBOARD_READ', () => api.listDashboards(orgId, one)),
    ]);
    const runs: QueryRun[] = can('INSIGHT_QUERY_EXECUTE') ? (await api.listRuns(orgId, { size: 8, sort: 'id,desc' })).content : [];
    const featured: Chart | null = can('INSIGHT_CHART_READ') ? (await api.listCharts(orgId, { size: 1, sort: 'id,asc' })).content[0] ?? null : null;
    return { counts: { ds, qt, dsets, charts, dashes }, runs, featured };
  }, [api, orgId]);

  const stages = data && [
    { label: 'Data sources', n: data.counts.ds, to: '/data-sources' },
    { label: 'Query templates', n: data.counts.qt, to: '/queries' },
    { label: 'Datasets', n: data.counts.dsets, to: '/datasets' },
    { label: 'Charts', n: data.counts.charts, to: '/charts' },
    { label: 'Dashboards', n: data.counts.dashes, to: '/dashboards' },
  ];

  const okRuns = data?.runs.filter((r) => r.status === 'SUCCESS').length ?? 0;
  const avgMs = data?.runs.length ? Math.round(data.runs.reduce((s, r) => s + r.durationMs, 0) / data.runs.length) : null;

  return (
    <>
      <PageHeader eyebrow={`Org ${orgId}${mode === 'demo' ? ' · demo sandbox' : ''}`} title={`Hello, ${username ?? 'there'}`}>
        Your insight pipeline at a glance — every stage below is a live call to the API.
      </PageHeader>

      {loading && <Spinner label="Loading workspace…" />}
      {error && <ErrorNote message={error} onRetry={reload} />}

      {stages && (
        <section className="pipeline" aria-label="Pipeline">
          {stages.map((s, i) => (
            <Link key={s.label} to={s.n == null ? '#' : s.to} className={`stage ${s.n == null ? 'stage-locked' : ''}`} style={{ animationDelay: `${i * 60}ms` }}>
              <span className="stage-n">{s.n ?? '—'}</span>
              <span className="stage-label">{s.label}</span>
              {s.n == null && <span className="muted small">no access</span>}
            </Link>
          ))}
        </section>
      )}

      {data && (
        <div className="grid-2">
          <section className="card">
            <div className="card-head">
              <h2>{data.featured ? data.featured.name : 'Featured chart'}</h2>
              {data.featured && <Link to="/charts" className="link-btn small">All charts →</Link>}
            </div>
            <div className="chart-box">
              {data.featured ? <LiveChart chart={data.featured} /> : <div className="chart-empty muted">No charts yet.</div>}
            </div>
          </section>

          <section className="card">
            <div className="card-head">
              <h2>Recent query runs</h2>
              {can('INSIGHT_QUERY_EXECUTE') && <Link to="/runs" className="link-btn small">History →</Link>}
            </div>
            {!can('INSIGHT_QUERY_EXECUTE') ? (
              <p className="muted">Your role cannot execute queries, so run history is hidden.</p>
            ) : data.runs.length === 0 ? (
              <p className="muted">No runs yet. Execute a query template to see it here.</p>
            ) : (
              <>
                <div className="kpis">
                  <div><span className="kpi-n">{Math.round((okRuns / data.runs.length) * 100)}%</span><span className="muted small">success (last {data.runs.length})</span></div>
                  <div><span className="kpi-n">{avgMs}<small>ms</small></span><span className="muted small">avg duration</span></div>
                </div>
                <ul className="run-list">
                  {data.runs.map((r) => (
                    <li key={r.id}>
                      <Badge tone={r.status === 'SUCCESS' ? 'ok' : r.status === 'FAILED' ? 'bad' : 'warn'}>{r.status}</Badge>
                      <span className="mono">#{r.id}</span>
                      <span className="muted">template {r.queryTemplateId}</span>
                      <span className="grow" />
                      <span className="mono small">{r.rowCount ?? '—'} rows · {r.durationMs}ms</span>
                      <span className="muted small">{relTime(r.startedAt)}</span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </section>
        </div>
      )}
    </>
  );
}
