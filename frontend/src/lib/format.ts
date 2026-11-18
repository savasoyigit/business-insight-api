export function fmtDate(v: unknown): string {
  if (v == null) return '—';
  const d = Array.isArray(v)
    ? new Date(Date.UTC(v[0], (v[1] ?? 1) - 1, v[2] ?? 1, v[3] ?? 0, v[4] ?? 0, v[5] ?? 0))
    : new Date(typeof v === 'number' && v < 1e12 ? v * 1000 : (v as string));
  if (Number.isNaN(d.getTime())) return String(v);
  return d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export function relTime(v: unknown): string {
  const d = new Date(v as string);
  if (Number.isNaN(d.getTime())) return fmtDate(v);
  const s = Math.round((Date.now() - d.getTime()) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return `${Math.floor(s / 86400)}d ago`;
}

export function fmtNum(v: unknown): string {
  const n = Number(v);
  if (!Number.isFinite(n)) return String(v);
  return n.toLocaleString(undefined, { maximumFractionDigits: 2 });
}

export function compact(v: unknown): string {
  const n = Number(v);
  if (!Number.isFinite(n)) return String(v);
  return Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(n);
}
