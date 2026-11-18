import { useEffect, useState } from 'react';
import { isFinished } from '../lib/runs';
import { useSession } from '../auth/session';
import { useAsync } from '../lib/hooks';
import { fmtDate } from '../lib/format';
import { Badge, Empty, ErrorNote, PageHeader, Pager, Spinner } from '../components/ui';

export function QueryRuns() {
  const { api, orgId } = useSession();
  const [page, setPage] = useState(0);
  const runs = useAsync(() => api.listRuns(orgId, { page, size: 20, sort: 'id,desc' }), [api, orgId, page]);
  const max = Math.max(1, ...(runs.data?.content.map((r) => r.durationMs) ?? [1]));
  const active = runs.data?.content.some((r) => !isFinished(r.status)) ?? false;

  // Live view: fast while runs are in flight, slower otherwise so scheduled runs still show up.
  useEffect(() => {
    const t = setInterval(runs.reload, active ? 2000 : 10000);
    return () => clearInterval(t);
  }, [active, runs.reload]);

  return (
    <>
      <PageHeader eyebrow="Operations" title="Query runs"
        actions={<button className="btn btn-ghost" onClick={runs.reload}>⟳ Refresh</button>}>
        Every execution is recorded with status, duration, row count and a query hash for traceability.
        Asynchronous and scheduled runs show up here as they move from queued to finished.
      </PageHeader>
      {runs.loading && !runs.data && <Spinner />}
      {runs.error && <ErrorNote message={runs.error} onRetry={runs.reload} />}
      {runs.data && runs.data.content.length === 0 && <Empty title="No runs yet">Execute a template or take a dataset snapshot.</Empty>}
      {runs.data && runs.data.content.length > 0 && (
        <div className="card table-card">
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr><th>Run</th><th>Status</th><th>Template</th><th>Source</th><th>By</th><th className="num">Rows</th><th>Duration</th><th>Hash</th><th>Started</th></tr>
              </thead>
              <tbody>
                {runs.data.content.map((r) => (
                  <tr key={r.id}>
                    <td className="mono">#{r.id}</td>
                    <td>
                      <Badge tone={r.status === 'SUCCESS' ? 'ok' : r.status === 'FAILED' ? 'bad' : 'warn'}>{!isFinished(r.status) && <span className="pulse-dot" />}{r.status}</Badge>
                      {r.errorType && <div className="small note-error-text mono">{r.errorType}</div>}
                    </td>
                    <td className="mono">{r.queryTemplateId}</td>
                    <td className="mono">{r.dataSourceId}</td>
                    <td>{r.requestedBy === 'scheduler' ? <span className="muted">⟳ scheduler</span> : r.requestedBy}</td>
                    <td className="num mono">{r.rowCount ?? '—'}</td>
                    <td>
                      <div className="dur">
                        <span className={`dur-bar ${r.status === 'FAILED' ? 'bad' : ''}`} style={{ width: `${Math.max(4, (r.durationMs / max) * 100)}%` }} />
                        <span className="mono small">{r.durationMs}ms</span>
                      </div>
                    </td>
                    <td className="mono small muted">{r.queryHash?.slice(0, 10) ?? '—'}</td>
                    <td className="small">{fmtDate(r.startedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={runs.data} onPage={setPage} />
        </div>
      )}
    </>
  );
}
