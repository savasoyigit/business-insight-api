import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import type { ChartType, RenderPoint } from '../api/types';
import { pivot } from '../lib/render';
import { compact, fmtNum } from '../lib/format';

export const PALETTE = ['#f2a93b', '#2bb5a6', '#8b7cf6', '#ef6f6c', '#4aa3f0', '#9ccc65', '#e879b9', '#c8a26b'];

const tooltipProps = {
  contentStyle: {
    background: 'var(--surface-raised)', border: '1px solid var(--line)', borderRadius: 10,
    color: 'var(--text)', fontSize: 12, boxShadow: 'var(--shadow)',
  },
  labelStyle: { color: 'var(--muted)', marginBottom: 4 },
  formatter: (v: unknown) => fmtNum(v),
};

export function ChartView({ type, points, height = '100%' }: { type: ChartType; points: RenderPoint[]; height?: number | string }) {
  if (!points.length) return <div className="chart-empty muted">Snapshot has no rows.</div>;
  const { rows, series } = pivot(points);
  const multi = series.length > 1;

  if (type === 'PIE') {
    // Pie: collapse series, one slice per x.
    const slices = rows.map((r) => ({ name: String(r.x), value: series.reduce((s, k) => s + (Number(r[k]) || 0), 0) }));
    return (
      <ResponsiveContainer width="100%" height={height}>
        <PieChart>
          <Pie data={slices} dataKey="value" nameKey="name" innerRadius="52%" outerRadius="80%" paddingAngle={2} stroke="var(--surface)" strokeWidth={2}>
            {slices.map((_, i) => <Cell key={i} fill={PALETTE[i % PALETTE.length]} />)}
          </Pie>
          <Tooltip {...tooltipProps} />
          <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
        </PieChart>
      </ResponsiveContainer>
    );
  }

  const axes = (
    <>
      <CartesianGrid vertical={false} strokeDasharray="2 4" />
      <XAxis dataKey="x" tickLine={false} axisLine={false} tick={{ fontSize: 11 }} minTickGap={12} />
      <YAxis tickLine={false} axisLine={false} tick={{ fontSize: 11 }} tickFormatter={compact} width={44} />
      <Tooltip {...tooltipProps} cursor={{ fill: 'var(--hover)', stroke: 'var(--line)' }} />
      {multi && <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />}
    </>
  );

  if (type === 'LINE') {
    return (
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={rows} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
          {axes}
          {series.map((s, i) => (
            <Line key={s} type="monotone" dataKey={s} stroke={PALETTE[i % PALETTE.length]} strokeWidth={2.2} dot={rows.length < 16} activeDot={{ r: 4 }} />
          ))}
        </LineChart>
      </ResponsiveContainer>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={rows} margin={{ top: 8, right: 12, left: 0, bottom: 0 }} barCategoryGap="22%">
        {axes}
        {series.map((s, i) => (
          <Bar key={s} dataKey={s} fill={PALETTE[i % PALETTE.length]} radius={[4, 4, 0, 0]} maxBarSize={48} />
        ))}
      </BarChart>
    </ResponsiveContainer>
  );
}

export function ChartGlyph({ type }: { type: ChartType }) {
  const paths: Record<ChartType, JSX.Element> = {
    BAR: <><rect x="3" y="11" width="4" height="10" rx="1" /><rect x="10" y="6" width="4" height="15" rx="1" /><rect x="17" y="3" width="4" height="18" rx="1" /></>,
    LINE: <polyline points="3,18 9,11 13,14 21,5" fill="none" strokeWidth="2.2" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" />,
    PIE: <><path d="M12 3a9 9 0 1 0 9 9h-9z" /><path d="M14 1.5a8.5 8.5 0 0 1 8.5 8.5H14z" opacity=".55" /></>,
  };
  return <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden>{paths[type]}</svg>;
}
