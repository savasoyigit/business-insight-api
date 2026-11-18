import { useEffect, useState } from 'react';
import type { Dataset, Json } from '../api/types';
import { useSession } from '../auth/session';
import { useAsync } from '../lib/hooks';
import { fmtDate, relTime } from '../lib/format';
import { REFRESH_PRESETS, describeCron, pollRun } from '../lib/runs';
import { Badge, Empty, ErrorNote, Field, Modal, PageHeader, Pager, RowsTable, Spinner, parseJson, useToast } from '../components/ui';

export function Datasets() {
  const { api, orgId, can } = useSession();
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<Dataset | 'new' | null>(null);
  const list = useAsync(() => api.listDatasets(orgId, { page, size: 15, sort: 'id,asc' }), [api, orgId, page]);
  const templates = useAsync(async () => (can('INSIGHT_QT_READ') ? (await api.listQueryTemplates(orgId, { size: 200 })).content : []), [api, orgId]);

  useEffect(() => {
    if (list.data?.content.length && !list.data.content.some((d) => d.id === selected)) setSelected(list.data.content[0].id);
  }, [list.data, selected]);

  const current = list.data?.content.find((d) => d.id === selected) ?? null;
  const qtName = (id: number) => templates.data?.find((t) => t.id === id)?.name ?? `template #${id}`;

  return (
    <>
      <PageHeader eyebrow="Data" title="Datasets"
        actions={can('INSIGHT_DATASET_CREATE') && <button className="btn btn-primary" onClick={() => setEditing('new')}>+ New dataset</button>}>
        Reusable datasets on top of query templates. Each snapshot materializes up to 500 rows for charts.
      </PageHeader>

      {list.loading && !list.data && <Spinner />}
      {list.error && <ErrorNote message={list.error} onRetry={list.reload} />}
      {list.data && list.data.content.length === 0 && (
        <Empty title="No datasets yet" action={can('INSIGHT_DATASET_CREATE') && <button className="btn btn-primary" onClick={() => setEditing('new')}>Create a dataset</button>}>
          Pick a query template and default parameters to get started.
        </Empty>
      )}

      {list.data && list.data.content.length > 0 && (
        <div className="split">
          <div className="split-list">
            {list.data.content.map((d) => (
              <button key={d.id} className={`list-item ${d.id === selected ? 'on' : ''}`} onClick={() => setSelected(d.id)}>
                <span className="list-item-title">{d.name}</span>
                <span className="muted small">{qtName(d.queryTemplateId)}{d.refreshCron ? ' · ⟳ auto refresh' : ''}</span>
              </button>
            ))}
            <Pager page={list.data} onPage={setPage} />
          </div>
          {current && (
            <DatasetDetail
              key={current.id} dataset={current} templateName={qtName(current.queryTemplateId)}
              onEdit={() => setEditing(current)}
              onDeleted={() => { setSelected(null); list.reload(); }}
            />
          )}
        </div>
      )}

      {editing && (
        <DatasetForm initial={editing === 'new' ? null : editing} templates={templates.data ?? []}
          onClose={() => setEditing(null)} onSaved={(id) => { setEditing(null); setSelected(id); list.reload(); }} />
      )}
    </>
  );
}

function DatasetDetail({ dataset, templateName, onEdit, onDeleted }: { dataset: Dataset; templateName: string; onEdit: () => void; onDeleted: () => void }) {
  const { api, orgId, can } = useSession();
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [openSnap, setOpenSnap] = useState<number | null>(null);
  const [paramsText, setParamsText] = useState(JSON.stringify(dataset.defaultParams ?? {}, null, 2));
  const [busy, setBusy] = useState(false);
  const [runStatus, setRunStatus] = useState<string | null>(null);
  const snaps = useAsync(() => api.listSnapshots(orgId, dataset.id, { page, size: 8, sort: 'id,desc' }), [api, orgId, dataset.id, page]);

  useEffect(() => {
    if (openSnap == null && snaps.data?.content[0]) setOpenSnap(snaps.data.content[0].id);
  }, [snaps.data, openSnap]);

  async function take() {
    const [params, err] = parseJson(paramsText);
    if (err) return toast('bad', `Params: ${err}`);
    setBusy(true);
    try {
      const submitted = await api.submitSnapshot(orgId, dataset.id, params as Json | undefined);
      if (submitted.coalesced) toast('ok', `An identical snapshot is already running (run #${submitted.queryRunId})`);
      const r = await pollRun(api, orgId, submitted.queryRunId, (run) => setRunStatus(run.status));
      toast(r.status === 'SUCCESS' ? 'ok' : 'bad', r.status === 'SUCCESS'
        ? `Snapshot ready · ${r.rowCount} rows in ${r.durationMs}ms`
        : `Snapshot failed${r.errorMessage ? `: ${r.errorMessage}` : ''}`);
      setPage(0);
      setOpenSnap(r.snapshotId);
      snaps.reload();
    } catch (e) { toast('bad', (e as Error).message); } finally { setBusy(false); setRunStatus(null); }
  }

  async function remove() {
    if (!confirm(`Delete dataset "${dataset.name}"?`)) return;
    try { await api.deleteDataset(orgId, dataset.id); toast('ok', 'Dataset deleted'); onDeleted(); }
    catch (e) { toast('bad', (e as Error).message); }
  }

  const open = snaps.data?.content.find((s) => s.id === openSnap);

  return (
    <section className="card split-detail">
      <div className="card-head">
        <div>
          <h2>{dataset.name}</h2>
          <span className="muted small">from <b>{templateName}</b> · by {dataset.createdBy} · {fmtDate(dataset.createdAt)}</span>
        </div>
        <div className="row-gap">
          {can('INSIGHT_DATASET_UPDATE') && <button className="btn btn-ghost btn-sm" onClick={onEdit}>Edit</button>}
          {can('INSIGHT_DATASET_DELETE') && <button className="btn btn-ghost btn-sm btn-danger" onClick={remove}>Delete</button>}
        </div>
      </div>

      <div className="schedule-line">
        <Badge tone={dataset.refreshCron ? 'accent' : 'neutral'}>⟳ {describeCron(dataset.refreshCron)}</Badge>
        {dataset.nextRefreshAt && <span className="muted">next {fmtDate(dataset.nextRefreshAt)}</span>}
        {dataset.lastRefreshAt && <span className="muted">· last {relTime(dataset.lastRefreshAt)}</span>}
      </div>

      {can('INSIGHT_DATASET_CREATE') && (
        <div className="snapshot-bar">
          <Field label="Parameters for this snapshot (JSON)">
            <textarea className="mono" rows={2} value={paramsText} onChange={(e) => setParamsText(e.target.value)} />
          </Field>
          <button className="btn btn-primary" onClick={take} disabled={busy}>{busy ? (runStatus === 'RUNNING' ? 'Running…' : 'Queued…') : '⟳ Take snapshot'}</button>
        </div>
      )}

      <h3>Snapshots</h3>
      {snaps.loading && !snaps.data && <Spinner />}
      {snaps.error && <ErrorNote message={snaps.error} onRetry={snaps.reload} />}
      {snaps.data && snaps.data.content.length === 0 && <p className="muted">No snapshots yet.</p>}
      {snaps.data && snaps.data.content.length > 0 && (
        <>
          <div className="snap-chips">
            {snaps.data.content.map((s) => (
              <button key={s.id} className={`snap-chip ${s.id === openSnap ? 'on' : ''}`} onClick={() => setOpenSnap(s.id)}>
                <span className="mono">#{s.id}</span>
                <span className="muted small">{(s.data ?? []).length} rows · {relTime(s.createdAt)}</span>
              </button>
            ))}
          </div>
          <Pager page={snaps.data} onPage={setPage} />
          {open && <RowsTable rows={(open.data ?? []) as Record<string, unknown>[]} max={100} />}
        </>
      )}
    </section>
  );
}

function DatasetForm({ initial, templates, onClose, onSaved }: {
  initial: Dataset | null; templates: { id: number; name: string }[]; onClose: () => void; onSaved: (id: number) => void;
}) {
  const { api, orgId } = useSession();
  const toast = useToast();
  const [name, setName] = useState(initial?.name ?? '');
  const [qt, setQt] = useState<number>(initial?.queryTemplateId ?? templates[0]?.id ?? 0);
  const [paramsText, setParamsText] = useState(JSON.stringify(initial?.defaultParams ?? {}, null, 2));
  const initialCron = initial?.refreshCron ?? '';
  const [cronChoice, setCronChoice] = useState(!initialCron ? '' : REFRESH_PRESETS.some((p) => p.cron === initialCron) ? initialCron : 'custom');
  const [customCron, setCustomCron] = useState(REFRESH_PRESETS.some((p) => p.cron === initialCron) ? '' : initialCron);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    const [defaultParams, err] = parseJson(paramsText);
    if (err) return setError(`Default params: ${err}`);
    setBusy(true); setError(null);
    try {
      const refreshCron = cronChoice === 'custom' ? customCron.trim() : cronChoice;
      const body = { name, queryTemplateId: qt, defaultParams: (defaultParams ?? {}) as Json, refreshCron };
      const saved = initial ? await api.updateDataset(orgId, initial.id, body) : await api.createDataset(orgId, body);
      toast('ok', initial ? 'Dataset updated' : 'Dataset created');
      onSaved(saved.id);
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }

  return (
    <Modal title={initial ? `Edit ${initial.name}` : 'New dataset'} onClose={onClose}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Cancel</button><button className="btn btn-primary" onClick={save} disabled={busy}>{busy ? 'Saving…' : 'Save'}</button></>}>
      <Field label="Name"><input value={name} onChange={(e) => setName(e.target.value)} placeholder="Sales daily" /></Field>
      <Field label="Query template">
        <select value={qt} onChange={(e) => setQt(Number(e.target.value))}>
          {templates.length === 0 && <option value={0}>No templates visible</option>}
          {templates.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
        </select>
      </Field>
      <Field label="Default params (JSON)"><textarea className="mono" rows={4} value={paramsText} onChange={(e) => setParamsText(e.target.value)} /></Field>
      <Field label="Auto refresh" hint="Takes a snapshot on a schedule (server time zone, UTC by default).">
        <select value={cronChoice} onChange={(e) => setCronChoice(e.target.value)}>
          <option value="">Off — manual snapshots only</option>
          {REFRESH_PRESETS.map((p) => <option key={p.cron} value={p.cron}>{p.label}</option>)}
          <option value="custom">Custom cron…</option>
        </select>
      </Field>
      {cronChoice === 'custom' && (
        <Field label="Cron expression" hint={<>5 fields (minute hour day month weekday), e.g. <span className="mono">30 2 * * 1</span>; at least 5 minutes apart.</>}>
          <input className="mono" value={customCron} onChange={(e) => setCustomCron(e.target.value)} placeholder="0 6 * * *" />
        </Field>
      )}
      {error && <div className="note note-error">{error}</div>}
    </Modal>
  );
}
