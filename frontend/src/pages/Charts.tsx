import { useMemo, useState } from 'react';
import type { Aggregation, Chart, ChartConfig, ChartType } from '../api/types';
import { useSession } from '../auth/session';
import { useAsync } from '../lib/hooks';
import { aggregate } from '../lib/render';
import { Empty, ErrorNote, Field, Modal, PageHeader, Pager, Spinner, useToast } from '../components/ui';
import { ChartGlyph, ChartView } from '../components/ChartView';
import { LiveChart } from '../components/LiveChart';

export function Charts() {
  const { api, orgId, can } = useSession();
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Chart | 'new' | null>(null);
  const list = useAsync(() => api.listCharts(orgId, { page, size: 12, sort: 'id,asc' }), [api, orgId, page]);
  const datasets = useAsync(async () => (can('INSIGHT_DATASET_READ') ? (await api.listDatasets(orgId, { size: 200 })).content : []), [api, orgId]);
  const dsName = (id: number) => datasets.data?.find((d) => d.id === id)?.name ?? `dataset #${id}`;

  async function remove(c: Chart) {
    if (!confirm(`Delete chart "${c.name}"?`)) return;
    try { await api.deleteChart(orgId, c.id); toast('ok', 'Chart deleted'); list.reload(); }
    catch (e) { toast('bad', (e as Error).message); }
  }

  return (
    <>
      <PageHeader eyebrow="Workspace" title="Charts"
        actions={can('INSIGHT_CHART_CREATE') && <button className="btn btn-primary" onClick={() => setEditing('new')}>+ New chart</button>}>
        Chart definitions bound to datasets. Rendering aggregates the latest snapshot server-side via <span className="mono">/render</span>.
      </PageHeader>

      {list.loading && !list.data && <Spinner />}
      {list.error && <ErrorNote message={list.error} onRetry={list.reload} />}
      {list.data && list.data.content.length === 0 && (
        <Empty title="No charts yet" action={can('INSIGHT_CHART_CREATE') && <button className="btn btn-primary" onClick={() => setEditing('new')}>Build a chart</button>}>
          Turn a dataset snapshot into a bar, line or pie chart.
        </Empty>
      )}

      <div className="cards cards-charts">
        {list.data?.content.map((c) => (
          <article key={c.id} className="card chart-card">
            <div className="card-head">
              <div className="chart-title">
                <span className="glyph"><ChartGlyph type={c.type} /></span>
                <div>
                  <h3>{c.name}</h3>
                  <span className="muted small">{dsName(c.datasetId)} · {c.config?.aggregation ?? 'sum'}({c.config?.yField}) by {c.config?.xField}{c.config?.seriesField ? ` / ${c.config.seriesField}` : ''}</span>
                </div>
              </div>
              <div className="row-gap">
                {can('INSIGHT_CHART_UPDATE') && <button className="btn btn-ghost btn-sm" onClick={() => setEditing(c)}>Edit</button>}
                {can('INSIGHT_CHART_DELETE') && <button className="btn btn-ghost btn-sm btn-danger" onClick={() => remove(c)} aria-label="Delete">✕</button>}
              </div>
            </div>
            <div className="chart-box"><LiveChart chart={c} /></div>
          </article>
        ))}
      </div>
      {list.data && <Pager page={list.data} onPage={setPage} />}

      {editing && (
        <ChartForm initial={editing === 'new' ? null : editing} datasets={datasets.data ?? []}
          onClose={() => setEditing(null)} onSaved={() => { setEditing(null); list.reload(); }} />
      )}
    </>
  );
}

function ChartForm({ initial, datasets, onClose, onSaved }: {
  initial: Chart | null; datasets: { id: number; name: string }[]; onClose: () => void; onSaved: () => void;
}) {
  const { api, orgId } = useSession();
  const toast = useToast();
  const [name, setName] = useState(initial?.name ?? '');
  const [type, setType] = useState<ChartType>(initial?.type ?? 'BAR');
  const [datasetId, setDatasetId] = useState<number>(initial?.datasetId ?? datasets[0]?.id ?? 0);
  const [cfg, setCfg] = useState<ChartConfig>(initial?.config ?? { aggregation: 'sum' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Latest snapshot rows drive field pickers and the live preview.
  const snap = useAsync(async () => {
    if (!datasetId) return null;
    return (await api.listSnapshots(orgId, datasetId, { size: 1, sort: 'id,desc' })).content[0] ?? null;
  }, [api, orgId, datasetId]);
  const rows = (snap.data?.data ?? []) as Record<string, unknown>[];
  const columns = rows[0] ? Object.keys(rows[0]) : [];
  const numeric = columns.filter((c) => rows.some((r) => typeof r[c] === 'number'));

  const preview = useMemo(() => {
    if (!cfg.xField || !cfg.yField || !rows.length) return null;
    try { return { points: aggregate(rows, cfg) }; } catch (e) { return { error: (e as Error).message }; }
  }, [rows, cfg]);

  async function save() {
    setBusy(true); setError(null);
    const config: ChartConfig = { xField: cfg.xField, yField: cfg.yField, aggregation: cfg.aggregation ?? 'sum', ...(cfg.seriesField ? { seriesField: cfg.seriesField } : {}) };
    try {
      if (initial) await api.updateChart(orgId, initial.id, { name, type, datasetId, config });
      else await api.createChart(orgId, { name, type, datasetId, config });
      toast('ok', initial ? 'Chart updated' : 'Chart created');
      onSaved();
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }

  const fieldSelect = (key: 'xField' | 'yField' | 'seriesField', opts: string[], optional = false) => (
    columns.length ? (
      <select value={cfg[key] ?? ''} onChange={(e) => setCfg((c) => ({ ...c, [key]: e.target.value || undefined }))}>
        <option value="">{optional ? '— none —' : 'Select a column'}</option>
        {opts.map((c) => <option key={c} value={c}>{c}</option>)}
      </select>
    ) : (
      <input className="mono" value={cfg[key] ?? ''} placeholder="column name" onChange={(e) => setCfg((c) => ({ ...c, [key]: e.target.value || undefined }))} />
    )
  );

  return (
    <Modal wide title={initial ? `Edit ${initial.name}` : 'New chart'} onClose={onClose}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Cancel</button><button className="btn btn-primary" onClick={save} disabled={busy}>{busy ? 'Saving…' : 'Save chart'}</button></>}>
      <div className="chart-builder">
        <div className="chart-builder-form">
          <Field label="Name"><input value={name} onChange={(e) => setName(e.target.value)} placeholder="Revenue by region" /></Field>
          <Field label="Dataset">
            <select value={datasetId} onChange={(e) => { setDatasetId(Number(e.target.value)); setCfg({ aggregation: cfg.aggregation }); }}>
              {datasets.length === 0 && <option value={0}>No datasets visible</option>}
              {datasets.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          </Field>
          <div className="field">
            <span className="field-label">Type</span>
            <div className="seg seg-sm">
              {(['BAR', 'LINE', 'PIE'] as ChartType[]).map((t) => (
                <button key={t} type="button" className={type === t ? 'on' : ''} onClick={() => setType(t)}><ChartGlyph type={t} /> {t.toLowerCase()}</button>
              ))}
            </div>
          </div>
          <div className="form-grid">
            <Field label="X field">{fieldSelect('xField', columns)}</Field>
            <Field label="Y field">{fieldSelect('yField', cfg.aggregation === 'count' ? columns : numeric.length ? numeric : columns)}</Field>
            <Field label="Series (optional)">{fieldSelect('seriesField', columns, true)}</Field>
            <Field label="Aggregation">
              <select value={cfg.aggregation ?? 'sum'} onChange={(e) => setCfg((c) => ({ ...c, aggregation: e.target.value as Aggregation }))}>
                <option value="sum">sum</option><option value="avg">avg</option><option value="count">count</option>
              </select>
            </Field>
          </div>
          {!snap.loading && !snap.data && datasetId > 0 && <p className="muted small">This dataset has no snapshot yet — type column names manually, or take a snapshot first.</p>}
        </div>
        <div className="chart-builder-preview">
          <span className="field-label">Live preview {snap.data && <span className="muted">· snapshot #{snap.data.id}</span>}</span>
          <div className="chart-box">
            {snap.loading ? <Spinner /> : !preview ? <div className="chart-empty muted">Pick X and Y fields to preview.</div>
              : 'error' in preview ? <div className="chart-empty note-error-text">{preview.error}</div>
              : <ChartView type={type} points={preview.points!} />}
          </div>
        </div>
      </div>
      {error && <div className="note note-error">{error}</div>}
    </Modal>
  );
}
