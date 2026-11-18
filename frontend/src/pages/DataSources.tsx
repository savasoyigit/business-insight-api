import { useState } from 'react';
import type { ConnectionTest, DataSource, DataSourceInput, DataSourceType } from '../api/types';
import { useSession } from '../auth/session';
import { useAsync } from '../lib/hooks';
import { Badge, Empty, ErrorNote, Field, Modal, PageHeader, Pager, Spinner, parseJson, useToast } from '../components/ui';

const DEFAULT_PORT: Record<DataSourceType, number> = { POSTGRES: 5432, MSSQL: 1433 };

export function DataSources() {
  const { api, orgId, can } = useSession();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<DataSource | 'new' | null>(null);
  const [tests, setTests] = useState<Record<number, ConnectionTest | 'pending'>>({});
  const list = useAsync(() => api.listDataSources(orgId, { page, size: 12, sort: 'id,asc' }), [api, orgId, page]);

  async function test(id: number) {
    setTests((t) => ({ ...t, [id]: 'pending' }));
    try {
      const r = await api.testDataSource(orgId, id);
      setTests((t) => ({ ...t, [id]: r }));
    } catch (e) {
      setTests((t) => ({ ...t, [id]: { ok: false, latencyMs: 0, error: (e as Error).message } }));
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="Data" title="Data sources"
        actions={can('INSIGHT_DS_CREATE') && <button className="btn btn-primary" onClick={() => setEditing('new')}>+ New data source</button>}
      >
        External databases queries are routed to. Passwords never live in the app DB — only a <span className="mono">secretRef</span>.
      </PageHeader>

      {list.loading && !list.data && <Spinner />}
      {list.error && <ErrorNote message={list.error} onRetry={list.reload} />}
      {list.data && list.data.content.length === 0 && (
        <Empty title="No data sources yet" action={can('INSIGHT_DS_CREATE') && <button className="btn btn-primary" onClick={() => setEditing('new')}>Connect a database</button>}>
          Connect a Postgres or SQL Server database to start building insights.
        </Empty>
      )}

      <div className="cards">
        {list.data?.content.map((ds) => {
          const t = tests[ds.id];
          return (
            <article key={ds.id} className="card ds-card">
              <div className="card-head">
                <div className="ds-title">
                  <span className={`engine engine-${ds.type.toLowerCase()}`}>{ds.type === 'POSTGRES' ? 'PG' : 'MS'}</span>
                  <div>
                    <h3>{ds.name}</h3>
                    <span className="mono muted small">#{ds.id} · {ds.type}</span>
                  </div>
                </div>
                <Badge tone={ds.active ? 'ok' : 'neutral'}>{ds.active ? 'active' : 'inactive'}</Badge>
              </div>
              <dl className="kv">
                <dt>Host</dt><dd className="mono">{ds.host}:{ds.port}</dd>
                <dt>Database</dt><dd className="mono">{ds.databaseName}</dd>
                <dt>User</dt><dd className="mono">{ds.username}</dd>
                <dt>Secret</dt><dd className="mono">{ds.secretRef}</dd>
              </dl>
              <div className="card-foot">
                <button className="btn btn-ghost btn-sm" onClick={() => test(ds.id)} disabled={t === 'pending'}>
                  {t === 'pending' ? 'Testing…' : 'Test connection'}
                </button>
                {t && t !== 'pending' && (
                  <span className={`test-result ${t.ok ? 'ok' : 'bad'}`} title={t.error ?? ''}>
                    <span className={`dot ${t.ok ? 'dot-ok' : 'dot-bad'}`} />
                    {t.ok ? `Connected · ${t.latencyMs}ms` : t.error ?? 'Failed'}
                  </span>
                )}
                <span className="grow" />
                {can('INSIGHT_DS_UPDATE') && <button className="btn btn-ghost btn-sm" onClick={() => setEditing(ds)}>Edit</button>}
              </div>
            </article>
          );
        })}
      </div>
      {list.data && <Pager page={list.data} onPage={setPage} />}

      {editing && (
        <DataSourceForm
          initial={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); list.reload(); }}
        />
      )}
    </>
  );
}

function DataSourceForm({ initial, onClose, onSaved }: { initial: DataSource | null; onClose: () => void; onSaved: () => void }) {
  const { api, orgId } = useSession();
  const toast = useToast();
  const [f, setF] = useState<DataSourceInput & { active: boolean }>({
    name: initial?.name ?? '', type: initial?.type ?? 'POSTGRES', host: initial?.host ?? 'localhost',
    port: initial?.port ?? 5432, databaseName: initial?.databaseName ?? '', username: initial?.username ?? '',
    secretRef: initial?.secretRef ?? 'env:', options: initial?.options ?? null, active: initial?.active ?? true,
  });
  const [optionsText, setOptionsText] = useState(initial?.options ? JSON.stringify(initial.options, null, 2) : '{\n  "ssl": false\n}');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = <K extends keyof typeof f>(k: K, v: (typeof f)[K]) => setF((x) => ({ ...x, [k]: v }));

  async function save() {
    const [options, err] = parseJson(optionsText);
    if (err) return setError(`Options: ${err}`);
    setBusy(true); setError(null);
    try {
      const body = { ...f, options: (options as DataSourceInput['options']) ?? null };
      if (initial) await api.updateDataSource(orgId, initial.id, body);
      else await api.createDataSource(orgId, body);
      toast('ok', initial ? 'Data source updated' : 'Data source created');
      onSaved();
    } catch (e) {
      setError((e as Error).message);
    } finally { setBusy(false); }
  }

  return (
    <Modal
      title={initial ? `Edit ${initial.name}` : 'New data source'} onClose={onClose}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Cancel</button><button className="btn btn-primary" onClick={save} disabled={busy}>{busy ? 'Saving…' : 'Save'}</button></>}
    >
      <div className="form-grid">
        <Field label="Name"><input value={f.name} onChange={(e) => set('name', e.target.value)} placeholder="Reporting Postgres" /></Field>
        <Field label="Engine">
          <select value={f.type} onChange={(e) => { const t = e.target.value as DataSourceType; setF((x) => ({ ...x, type: t, port: DEFAULT_PORT[t] })); }}>
            <option value="POSTGRES">PostgreSQL</option>
            <option value="MSSQL">SQL Server</option>
          </select>
        </Field>
        <Field label="Host"><input value={f.host} onChange={(e) => set('host', e.target.value)} /></Field>
        <Field label="Port"><input type="number" value={f.port} onChange={(e) => set('port', Number(e.target.value))} /></Field>
        <Field label="Database"><input value={f.databaseName} onChange={(e) => set('databaseName', e.target.value)} /></Field>
        <Field label="Username"><input value={f.username} onChange={(e) => set('username', e.target.value)} /></Field>
        <Field label="Secret reference" hint={<>Resolved server-side, e.g. <span className="mono">env:ERP_REPORTING_PASSWORD</span></>}>
          <input className="mono" value={f.secretRef} onChange={(e) => set('secretRef', e.target.value)} />
        </Field>
        {initial && (
          <Field label="Status">
            <select value={String(f.active)} onChange={(e) => set('active', e.target.value === 'true')}>
              <option value="true">Active</option><option value="false">Inactive</option>
            </select>
          </Field>
        )}
        <div className="span-2">
          <Field label="Options (JSON)"><textarea className="mono" rows={4} value={optionsText} onChange={(e) => setOptionsText(e.target.value)} /></Field>
        </div>
      </div>
      {error && <div className="note note-error">{error}</div>}
    </Modal>
  );
}
