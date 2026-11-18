import type { ChartConfig, Json, RenderPoint } from '../api/types';

/** Port of ChartService.render(): group snapshot rows by (x, series) and aggregate y. */
export function aggregate(rows: Json[], config: ChartConfig): RenderPoint[] {
  if (!config.xField) throw new Error('Chart config xField is required');
  if (!config.yField) throw new Error('Chart config yField is required');
  const agg = (config.aggregation ?? 'sum').toLowerCase();
  if (!['sum', 'avg', 'count'].includes(agg)) throw new Error(`Unsupported aggregation: ${agg}`);

  const key = (v: unknown) => (v == null ? 'null' : typeof v === 'object' ? JSON.stringify(v) : String(v));
  const groups = new Map<string, { x: string; series?: string; sum: number; count: number }>();

  for (const row of rows) {
    const x = key(row[config.xField]);
    const series = config.seriesField ? key(row[config.seriesField]) : undefined;
    const k = `${x}\u0000${series ?? ''}`;
    let g = groups.get(k);
    if (!g) groups.set(k, (g = { x, series, sum: 0, count: 0 }));
    g.count += 1;
    if (agg !== 'count') {
      const raw = row[config.yField];
      const n = raw == null ? 0 : Number(raw);
      if (Number.isNaN(n)) throw new Error('Chart aggregation requires numeric yField values');
      g.sum += n;
    }
  }

  return [...groups.values()].map((g) => {
    const value = agg === 'count' ? g.count : agg === 'avg' ? +(g.sum / g.count).toFixed(4) : g.sum;
    return g.series !== undefined ? { x: g.x, series: g.series, value } : { x: g.x, value };
  });
}

/** Pivot render points into recharts rows: [{x, seriesA: n, seriesB: n}] + series names. */
export function pivot(points: RenderPoint[]): { rows: Record<string, string | number>[]; series: string[] } {
  const series: string[] = [];
  const byX = new Map<string, Record<string, string | number>>();
  for (const p of points) {
    const s = p.series ?? 'value';
    if (!series.includes(s)) series.push(s);
    let row = byX.get(p.x);
    if (!row) byX.set(p.x, (row = { x: p.x }));
    row[s] = Number(p.value);
  }
  return { rows: [...byX.values()], series };
}
