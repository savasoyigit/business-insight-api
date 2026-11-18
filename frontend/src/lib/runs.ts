import type { Api, RunDetails } from '../api/types';

export const isFinished = (status: string) => status === 'SUCCESS' || status === 'FAILED';

/** Polls an asynchronous run until it finishes, reporting every status change. */
export async function pollRun(api: Api, org: number, runId: number, onUpdate?: (run: RunDetails) => void, timeoutMs = 120_000): Promise<RunDetails> {
  const deadline = Date.now() + timeoutMs;
  let delay = 250;
  for (;;) {
    const run = await api.getRun(org, runId);
    onUpdate?.(run);
    if (isFinished(run.status)) return run;
    if (Date.now() > deadline) throw new Error(`Run #${runId} is still ${run.status.toLowerCase()}; check Query runs later.`);
    await new Promise((r) => setTimeout(r, delay));
    delay = Math.min(delay * 1.5, 2000);
  }
}

/** Refresh presets offered in the UI; anything else is shown as a custom cron. */
export const REFRESH_PRESETS: { label: string; cron: string }[] = [
  { label: 'Every hour', cron: '@hourly' },
  { label: 'Every 6 hours', cron: '0 */6 * * *' },
  { label: 'Daily at 06:00', cron: '0 6 * * *' },
  { label: 'Weekdays at 07:00', cron: '0 7 * * 1-5' },
];

export function describeCron(cron: string | null): string {
  if (!cron) return 'Manual only';
  return REFRESH_PRESETS.find((p) => p.cron === cron)?.label ?? cron;
}
