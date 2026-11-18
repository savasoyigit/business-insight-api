import type { Chart } from '../api/types';
import { useSession } from '../auth/session';
import { useAsync } from '../lib/hooks';
import { ChartView } from './ChartView';
import { Spinner } from './ui';

/** Renders a chart against the latest snapshot of its dataset (or a given snapshot). */
export function LiveChart({ chart, snapshotId, height = '100%' }: { chart: Chart; snapshotId?: number; height?: number | string }) {
  const { api, orgId } = useSession();
  const { data, error, loading } = useAsync(async () => {
    let sid = snapshotId;
    if (sid == null) {
      const page = await api.listSnapshots(orgId, chart.datasetId, { size: 1, sort: 'id,desc' });
      sid = page.content[0]?.id;
      if (sid == null) return { empty: true as const };
    }
    return { empty: false as const, rendered: await api.renderChart(orgId, chart.id, sid) };
  }, [api, orgId, chart.id, chart.datasetId, chart.type, JSON.stringify(chart.config), snapshotId]);

  if (loading) return <div className="chart-empty"><Spinner /></div>;
  if (error) return <div className="chart-empty note-error-text">{error}</div>;
  if (!data || data.empty) return <div className="chart-empty muted">No snapshot yet — take one from the dataset page.</div>;
  return <ChartView type={chart.type} points={data.rendered.points} height={height} />;
}
