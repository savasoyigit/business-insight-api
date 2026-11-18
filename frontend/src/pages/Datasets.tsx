import { useEffect, useState } from 'react';
import type { Dataset, Json } from '../api/types';
import { useSession } from '../auth/session';
import { useAsync } from '../lib/hooks';
import { fmtDate, relTime } from '../lib/format';
import { Empty, ErrorNote, Field, Modal, PageHeader, Pager, RowsTable, Spinner, parseJson, useToast } from '../components/ui';

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
                <span className="muted small">{qtName(d.queryTemplateId)}</span>
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
  const snaps = useAsync(() => api.listSnapshots(orgId, dataset.id, { page, size: 8, sort: 'id,desc' }), [api, orgId, dataset.id, page]);

  useEffect(() => {
    if (openSnap == null && snaps.data?.content[0]) setOpenSnap(snaps.data.content[0].id);
  }, [snaps.data, openSnap]);

  async function take() {
    const [params, err] = parseJson(paramsText);
    if (err) return toast('bad', `Params: ${err}`);
    setBusy(true);
    try {
      const r = await api.createSnapshot(orgId, dataset.id, params as Json | undefined);
      toast(r.status === 'SUCCESS' ? 'ok' : 'bad', `Snapshot ${r.status.toLowerCase()} · ${r.rowCount} rows in ${r.durationMs}ms`);
      setPage(0);
      setOpenSnap(r.snapshotId);
      snaps.reload();
    } catch (e) { toast('bad', (e as Error).message); } finally { setBusy(false); }
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

      {can('INSIGHT_DATASET_CREATE') && (
        <div className="snapshot-bar">
          <Field label="Parameters for this snapshot (JSON)">
            <textarea className="mono" rows={2} value={paramsText} onChange={(e) => setParamsText(e.target.value)} />
          </Field>
          <button className="btn btn-primary" onClick={take} disabled={busy}>{busy ? 'Running…' : '⟳ Take snapshot'}</button>
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
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    const [defaultParams, err] = parseJson(paramsText);
    if (err) return setError(`Default params: ${err}`);
    setBusy(true); setError(null);
    try {
      const body = { name, queryTemplateId: qt, defaultParams: (defaultParams ?? {}) as Json };
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
      {error && <div className="note note-error">{error}</div>}
    </Modal>
  );
}
