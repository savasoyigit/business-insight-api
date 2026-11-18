import { useEffect, useMemo, useState } from 'react';
import type { FieldDef, Json, ParamDef, QueryTemplate, RunDetails, ValidateResult } from '../api/types';
import { isFinished, pollRun } from '../lib/runs';
import { useSession } from '../auth/session';
import { useAsync } from '../lib/hooks';
import { detectParams, guardSql } from '../lib/sql';
import { Badge, Empty, ErrorNote, Field, Modal, PageHeader, Pager, Spinner, parseJson, useToast } from '../components/ui';
import { SqlView } from '../components/SqlView';

export function QueryTemplates() {
  const { api, orgId, can } = useSession();
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<QueryTemplate | 'new' | null>(null);
  const list = useAsync(() => api.listQueryTemplates(orgId, { page, size: 15, sort: 'id,asc' }), [api, orgId, page]);
  const sources = useAsync(async () => (can('INSIGHT_DS_READ') ? (await api.listDataSources(orgId, { size: 100 })).content : []), [api, orgId]);

  useEffect(() => {
    if (list.data?.content.length && !list.data.content.some((q) => q.id === selected)) setSelected(list.data.content[0].id);
  }, [list.data, selected]);

  const current = list.data?.content.find((q) => q.id === selected) ?? null;
  const dsName = (id: number) => sources.data?.find((d) => d.id === id)?.name ?? `data source #${id}`;

  return (
    <>
      <PageHeader
        eyebrow="Data" title="Query templates"
        actions={can('INSIGHT_QT_CREATE') && <button className="btn btn-primary" onClick={() => setEditing('new')}>+ New template</button>}
      >
        Parameterized, guarded SQL. SELECT-only, single statement, named <span className="mono tk-param">:params</span>, row caps enforced server-side.
      </PageHeader>

      {list.loading && !list.data && <Spinner />}
      {list.error && <ErrorNote message={list.error} onRetry={list.reload} />}
      {list.data && list.data.content.length === 0 && (
        <Empty title="No query templates" action={can('INSIGHT_QT_CREATE') && <button className="btn btn-primary" onClick={() => setEditing('new')}>Write your first query</button>}>
          Templates turn SQL into reusable, safe building blocks for datasets.
        </Empty>
      )}

      {list.data && list.data.content.length > 0 && (
        <div className="split">
          <div className="split-list">
            {list.data.content.map((q) => (
              <button key={q.id} className={`list-item ${q.id === selected ? 'on' : ''}`} onClick={() => setSelected(q.id)}>
                <span className="list-item-title">{q.name}</span>
                <span className="muted small">{dsName(q.dataSourceId)} · {(q.parameters ?? []).length} params</span>
                {!q.active && <Badge>inactive</Badge>}
              </button>
            ))}
            <Pager page={list.data} onPage={setPage} />
          </div>

          {current && (
            <section className="card split-detail">
              <div className="card-head">
                <div>
                  <h2>{current.name}</h2>
                  <span className="muted small">{current.description || 'No description'} · <span className="mono">#{current.id}</span> on {dsName(current.dataSourceId)}</span>
                </div>
                {can('INSIGHT_QT_UPDATE') && <button className="btn btn-ghost btn-sm" onClick={() => setEditing(current)}>Edit</button>}
              </div>
              <SqlView sql={current.sqlText} />
              {(current.outputSchema ?? []).length > 0 && (
                <div className="schema">
                  <span className="muted small">Output</span>
                  {current.outputSchema!.map((f) => <span key={f.name} className="chip mono">{f.name}<i>{f.type}</i></span>)}
                </div>
              )}
              {can('INSIGHT_QUERY_EXECUTE') ? <RunPanel key={current.id} template={current} /> : <p className="muted small">Your role cannot execute queries.</p>}
            </section>
          )}
        </div>
      )}

      {editing && (
        <TemplateForm
          initial={editing === 'new' ? null : editing}
          sources={sources.data ?? []}
          onClose={() => setEditing(null)}
          onSaved={(id) => { setEditing(null); setSelected(id); list.reload(); }}
        />
      )}
    </>
  );
}

function RunPanel({ template }: { template: QueryTemplate }) {
  const { api, orgId } = useSession();
  const params = template.parameters ?? [];
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(params.map((p) => [p.name, p.type === 'date' ? '2026-01-01' : p.type === 'int' ? '30' : ''])));
  const [snapshot, setSnapshot] = useState(false);
  const [busy, setBusy] = useState<'validate' | 'execute' | null>(null);
  const [validation, setValidation] = useState<ValidateResult | null>(null);
  const [result, setResult] = useState<RunDetails | null>(null);
  const [error, setError] = useState<string | null>(null);

  const payload = (): Json => Object.fromEntries(
    Object.entries(values).filter(([, v]) => v !== '').map(([k, v]) => {
      const t = params.find((p) => p.name === k)?.type ?? 'text';
      return [k, /int|num|dec/.test(t) && v.trim() !== '' && !Number.isNaN(Number(v)) ? Number(v) : v];
    }));

  async function go(kind: 'validate' | 'execute') {
    setBusy(kind); setError(null);
    if (kind === 'validate') setResult(null); else setValidation(null);
    try {
      if (kind === 'validate') setValidation(await api.validateQuery(orgId, template.id, payload()));
      else {
        // Asynchronous: the run is queued, then polled until it finishes.
        const submitted = await api.submitQuery(orgId, template.id, payload(), snapshot);
        await pollRun(api, orgId, submitted.queryRunId, setResult);
      }
    } catch (e) { setError((e as Error).message); } finally { setBusy(null); }
  }

  return (
    <div className="run-panel">
      <h3>Run</h3>
      {params.length === 0 ? <p className="muted small">No parameters.</p> : (
        <div className="param-grid">
          {params.map((p: ParamDef) => (
            <Field key={p.name} label={`:${p.name}${p.required ? ' *' : ''}`} hint={p.type}>
              <input
                type={p.type === 'date' ? 'date' : /int|num/.test(p.type) ? 'number' : 'text'}
                value={values[p.name] ?? ''} onChange={(e) => setValues((v) => ({ ...v, [p.name]: e.target.value }))}
              />
            </Field>
          ))}
        </div>
      )}
      <div className="run-actions">
        <label className="check"><input type="checkbox" checked={snapshot} onChange={(e) => setSnapshot(e.target.checked)} /> Store snapshot</label>
        <span className="grow" />
        <button className="btn btn-ghost" onClick={() => go('validate')} disabled={!!busy}>{busy === 'validate' ? 'Validating…' : 'Validate'}</button>
        <button className="btn btn-primary" onClick={() => go('execute')} disabled={!!busy}>{busy === 'execute' ? 'Running…' : '▶ Execute'}</button>
      </div>

      {error && <div className="note note-error">{error}</div>}
      {validation && (
        <div className={`note ${validation.valid ? 'note-ok' : 'note-error'}`}>
          <b>{validation.valid ? 'Valid — safe to execute.' : 'Validation failed'}</b>
          {validation.errors.length > 0 && <ul>{validation.errors.map((e) => <li key={e}>{e}</li>)}</ul>}
          <div className="small mono">detected: {validation.detectedParams.join(', ') || '—'} · allowed: {validation.allowedParams.join(', ') || '—'}</div>
        </div>
      )}
      {result && (
        <div className={`result ${!isFinished(result.status) ? '' : result.status === 'SUCCESS' ? 'ok' : 'bad'}`}>
          <div className="result-main">
            <Badge tone={result.status === 'SUCCESS' ? 'ok' : result.status === 'FAILED' ? 'bad' : 'warn'}>
              {!isFinished(result.status) && <span className="pulse-dot" />}{result.status}
            </Badge>
            {isFinished(result.status) ? (
              <>
                <span className="kpi-n">{result.rowCount ?? 0}</span><span className="muted">rows</span>
                <span className="kpi-n">{result.durationMs}</span><span className="muted">ms</span>
              </>
            ) : <span className="muted">{result.status === 'QUEUED' ? 'Waiting for a worker…' : 'Running on the data source…'}</span>}
          </div>
          {result.errorMessage && <div className="small note-error-text">{result.errorMessage}</div>}
          <div className="small mono muted">
            run #{result.id} · hash {result.queryHash ?? '—'}{result.snapshotId ? ` · snapshot #${result.snapshotId}` : ''}{result.errorType ? ` · ${result.errorType}` : ''}
          </div>
          <p className="small muted">Tip: wrap this template in a dataset to materialize rows as snapshots and chart them.</p>
        </div>
      )}
    </div>
  );
}

function TemplateForm({ initial, sources, onClose, onSaved }: {
  initial: QueryTemplate | null; sources: { id: number; name: string }[]; onClose: () => void; onSaved: (id: number) => void;
}) {
  const { api, orgId } = useSession();
  const toast = useToast();
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [dataSourceId, setDataSourceId] = useState<number>(initial?.dataSourceId ?? sources[0]?.id ?? 0);
  const [sql, setSql] = useState(initial?.sqlText ?? 'select region, sum(net_amount) as revenue\nfrom sales.orders\nwhere order_date >= :startDate\ngroup by region');
  const [types, setTypes] = useState<Record<string, ParamDef>>(() => Object.fromEntries((initial?.parameters ?? []).map((p) => [p.name, p])));
  const [schemaText, setSchemaText] = useState(JSON.stringify(initial?.outputSchema ?? [{ name: 'region', type: 'text' }, { name: 'revenue', type: 'numeric' }], null, 2));
  const [active, setActive] = useState(initial?.active ?? true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const detected = useMemo(() => detectParams(sql), [sql]);
  const issues = useMemo(() => guardSql(sql), [sql]);
  const param = (n: string): ParamDef => types[n] ?? { name: n, type: 'text', required: true };

  async function save() {
    const [schema, err] = parseJson(schemaText);
    if (err) return setError(`Output schema: ${err}`);
    setBusy(true); setError(null);
    const parameters = detected.map(param);
    try {
      const saved = initial
        ? await api.updateQueryTemplate(orgId, initial.id, { name, description, sqlText: sql, parameters, outputSchema: schema as FieldDef[], active })
        : await api.createQueryTemplate(orgId, { dataSourceId, name, description, sqlText: sql, parameters, outputSchema: schema as FieldDef[] });
      toast('ok', initial ? 'Template updated' : 'Template created');
      onSaved(saved.id);
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }

  return (
    <Modal wide title={initial ? `Edit ${initial.name}` : 'New query template'} onClose={onClose}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Cancel</button><button className="btn btn-primary" onClick={save} disabled={busy}>{busy ? 'Saving…' : 'Save'}</button></>}>
      <div className="form-grid">
        <Field label="Name"><input value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <Field label="Data source">
          <select value={dataSourceId} onChange={(e) => setDataSourceId(Number(e.target.value))} disabled={!!initial}>
            {sources.length === 0 && <option value={0}>No data sources visible</option>}
            {sources.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </Field>
        <div className="span-2"><Field label="Description"><input value={description} onChange={(e) => setDescription(e.target.value)} /></Field></div>
        <div className="span-2">
          <Field label="SQL" hint={issues.length ? <span className="note-error-text">Preview: {issues.join(' · ')}</span> : 'Guardrails pass (preview — the server has the final say).'}>
            <textarea className="mono sql-input" rows={8} value={sql} onChange={(e) => setSql(e.target.value)} spellCheck={false} />
          </Field>
        </div>
        <div className="span-2">
          <span className="field-label">Parameters (auto-detected)</span>
          {detected.length === 0 ? <p className="muted small">No <span className="mono">:named</span> params in the SQL.</p> : (
            <div className="param-rows">
              {detected.map((n) => (
                <div key={n} className="param-row">
                  <span className="mono tk-param">:{n}</span>
                  <select value={param(n).type} onChange={(e) => setTypes((t) => ({ ...t, [n]: { ...param(n), type: e.target.value } }))}>
                    {['text', 'int', 'numeric', 'date', 'boolean'].map((t) => <option key={t}>{t}</option>)}
                  </select>
                  <label className="check"><input type="checkbox" checked={!!param(n).required} onChange={(e) => setTypes((t) => ({ ...t, [n]: { ...param(n), required: e.target.checked } }))} /> required</label>
                </div>
              ))}
            </div>
          )}
        </div>
        <div className="span-2"><Field label="Output schema (JSON)"><textarea className="mono" rows={4} value={schemaText} onChange={(e) => setSchemaText(e.target.value)} /></Field></div>
        {initial && (
          <Field label="Status">
            <select value={String(active)} onChange={(e) => setActive(e.target.value === 'true')}>
              <option value="true">Active</option><option value="false">Inactive</option>
            </select>
          </Field>
        )}
      </div>
      {error && <div className="note note-error">{error}</div>}
    </Modal>
  );
}
