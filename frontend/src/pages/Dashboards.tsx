import { useEffect, useState } from 'react';
import type { Chart, Dashboard, WidgetLayout } from '../api/types';
import { useSession } from '../auth/session';
import { useAsync } from '../lib/hooks';
import { fmtDate } from '../lib/format';
import { Empty, ErrorNote, Field, Modal, PageHeader, Spinner, useToast } from '../components/ui';
import { ChartGlyph } from '../components/ChartView';
import { LiveChart } from '../components/LiveChart';

const ROW_PX = 74;

export function Dashboards() {
  const { api, orgId, can } = useSession();
  const toast = useToast();
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<Dashboard | 'new' | null>(null);
  const list = useAsync(() => api.listDashboards(orgId, { size: 50, sort: 'id,asc' }), [api, orgId]);
  const charts = useAsync(async () => (can('INSIGHT_CHART_READ') ? (await api.listCharts(orgId, { size: 200 })).content : []), [api, orgId]);

  useEffect(() => {
    if (list.data?.content.length && !list.data.content.some((d) => d.id === selected)) setSelected(list.data.content[0].id);
  }, [list.data, selected]);

  const current = list.data?.content.find((d) => d.id === selected) ?? null;

  async function remove(d: Dashboard) {
    if (!confirm(`Delete dashboard "${d.name}"?`)) return;
    try { await api.deleteDashboard(orgId, d.id); toast('ok', 'Dashboard deleted'); setSelected(null); list.reload(); }
    catch (e) { toast('bad', (e as Error).message); }
  }

  return (
    <>
      <PageHeader eyebrow="Workspace" title={current?.name ?? 'Dashboards'}
        actions={<>
          {list.data && list.data.content.length > 1 && (
            <select className="dash-switch" value={selected ?? ''} onChange={(e) => setSelected(Number(e.target.value))} aria-label="Dashboard">
              {list.data.content.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          )}
          {current && can('INSIGHT_DASHBOARD_UPDATE') && <button className="btn btn-ghost" onClick={() => setEditing(current)}>Edit</button>}
          {current && can('INSIGHT_DASHBOARD_DELETE') && <button className="btn btn-ghost btn-danger" onClick={() => remove(current)}>Delete</button>}
          {can('INSIGHT_DASHBOARD_CREATE') && <button className="btn btn-primary" onClick={() => setEditing('new')}>+ New</button>}
        </>}>
        {current ? <>By {current.createdBy} · {fmtDate(current.createdAt)} · {current.widgets.length} widgets on a 12-column grid</> : 'Compose charts into a grid layout.'}
      </PageHeader>

      {list.loading && !list.data && <Spinner />}
      {list.error && <ErrorNote message={list.error} onRetry={list.reload} />}
      {list.data && list.data.content.length === 0 && (
        <Empty title="No dashboards yet" action={can('INSIGHT_DASHBOARD_CREATE') && <button className="btn btn-primary" onClick={() => setEditing('new')}>Create a dashboard</button>}>
          Arrange charts into a shareable overview.
        </Empty>
      )}

      {current && <DashboardGrid dashboard={current} charts={charts.data ?? []} />}

      {editing && (
        <DashboardForm initial={editing === 'new' ? null : editing} charts={charts.data ?? []}
          onClose={() => setEditing(null)} onSaved={(id) => { setEditing(null); setSelected(id); list.reload(); }} />
      )}
    </>
  );
}

function DashboardGrid({ dashboard, charts }: { dashboard: Dashboard; charts: Chart[] }) {
  if (!dashboard.widgets.length) return <p className="muted">This dashboard has no widgets yet.</p>;
  return (
    <div className="dash-grid" style={{ gridAutoRows: `${ROW_PX}px` }}>
      {dashboard.widgets.map((w) => {
        const chart = charts.find((c) => c.id === w.chartId);
        const l: WidgetLayout = { x: 0, y: 0, w: 6, h: 4, ...(w.layout ?? {}) };
        return (
          <article key={w.id} className="card widget" style={{ gridColumn: `${l.x + 1} / span ${l.w}`, gridRow: `${l.y + 1} / span ${l.h}` }}>
            <div className="widget-head">
              {chart && <span className="glyph"><ChartGlyph type={chart.type} /></span>}
              <h3>{chart?.name ?? `Chart #${w.chartId}`}</h3>
            </div>
            <div className="widget-body">
              {chart ? <LiveChart chart={chart} /> : <div className="chart-empty muted">Chart unavailable</div>}
            </div>
          </article>
        );
      })}
    </div>
  );
}

const SIZES: { label: string; w: number; h: number }[] = [
  { label: 'Third', w: 4, h: 4 }, { label: 'Half', w: 6, h: 4 }, { label: 'Two-thirds', w: 8, h: 4 }, { label: 'Full', w: 12, h: 4 },
];

/** Packs widgets left-to-right into 12 columns, wrapping into new rows. */
function pack(items: { chartId: number; w: number; h: number }[]) {
  let x = 0, y = 0, rowH = 0;
  return items.map((it) => {
    if (x + it.w > 12) { x = 0; y += rowH; rowH = 0; }
    const layout = { x, y, w: it.w, h: it.h };
    x += it.w; rowH = Math.max(rowH, it.h);
    return { chartId: it.chartId, layout };
  });
}

function DashboardForm({ initial, charts, onClose, onSaved }: {
  initial: Dashboard | null; charts: Chart[]; onClose: () => void; onSaved: (id: number) => void;
}) {
  const { api, orgId } = useSession();
  const toast = useToast();
  const [name, setName] = useState(initial?.name ?? '');
  const [items, setItems] = useState(() =>
    [...(initial?.widgets ?? [])]
      .sort((a, b) => (a.layout?.y ?? 0) - (b.layout?.y ?? 0) || (a.layout?.x ?? 0) - (b.layout?.x ?? 0))
      .map((w) => ({ chartId: w.chartId, w: w.layout?.w ?? 6, h: w.layout?.h ?? 4 })));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const move = (i: number, d: -1 | 1) => setItems((xs) => {
    const next = [...xs]; const j = i + d;
    if (j < 0 || j >= next.length) return xs;
    [next[i], next[j]] = [next[j], next[i]];
    return next;
  });

  async function save() {
    setBusy(true); setError(null);
    try {
      const body = { name, widgets: pack(items) };
      const saved = initial ? await api.updateDashboard(orgId, initial.id, body) : await api.createDashboard(orgId, body);
      toast('ok', initial ? 'Dashboard updated' : 'Dashboard created');
      onSaved(saved.id);
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }

  const packed = pack(items);
  const rows = Math.max(1, ...packed.map((p) => p.layout.y + p.layout.h));

  return (
    <Modal wide title={initial ? `Edit ${initial.name}` : 'New dashboard'} onClose={onClose}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Cancel</button><button className="btn btn-primary" onClick={save} disabled={busy}>{busy ? 'Saving…' : 'Save dashboard'}</button></>}>
      <Field label="Name"><input value={name} onChange={(e) => setName(e.target.value)} placeholder="Executive overview" /></Field>
      <div className="dash-builder">
        <div>
          <span className="field-label">Widgets</span>
          {items.length === 0 && <p className="muted small">Add charts from the list below.</p>}
          <ul className="widget-list">
            {items.map((it, i) => {
              const c = charts.find((x) => x.id === it.chartId);
              return (
                <li key={i}>
                  <span className="glyph">{c && <ChartGlyph type={c.type} />}</span>
                  <span className="grow">{c?.name ?? `Chart #${it.chartId}`}</span>
                  <select value={it.w} onChange={(e) => setItems((xs) => xs.map((x, k) => k === i ? { ...x, w: Number(e.target.value) } : x))} aria-label="Width">
                    {SIZES.map((s) => <option key={s.w} value={s.w}>{s.label}</option>)}
                  </select>
                  <button className="icon-btn" onClick={() => move(i, -1)} aria-label="Move up">↑</button>
                  <button className="icon-btn" onClick={() => move(i, 1)} aria-label="Move down">↓</button>
                  <button className="icon-btn" onClick={() => setItems((xs) => xs.filter((_, k) => k !== i))} aria-label="Remove">✕</button>
                </li>
              );
            })}
          </ul>
          <span className="field-label">Add chart</span>
          <div className="chip-row">
            {charts.map((c) => (
              <button key={c.id} className="chip chip-btn" onClick={() => setItems((xs) => [...xs, { chartId: c.id, w: 6, h: 4 }])}>
                <ChartGlyph type={c.type} /> {c.name}
              </button>
            ))}
          </div>
        </div>
        <div>
          <span className="field-label">Layout preview</span>
          <div className="mini-grid" style={{ gridTemplateRows: `repeat(${rows}, 14px)` }}>
            {packed.map((p, i) => (
              <div key={i} className="mini-cell" style={{ gridColumn: `${p.layout.x + 1} / span ${p.layout.w}`, gridRow: `${p.layout.y + 1} / span ${p.layout.h}` }}>
                {i + 1}
              </div>
            ))}
          </div>
          <p className="muted small">Saved as <span className="mono">{'{x, y, w, h}'}</span> widget layouts.</p>
        </div>
      </div>
      {error && <div className="note note-error">{error}</div>}
    </Modal>
  );
}
