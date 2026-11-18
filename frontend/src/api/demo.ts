/**
 * In-browser mock of the Business Insight API.
 * Lets the UI run as a static showcase (GitHub Pages, Vercel...) with no backend.
 * Mirrors the real guardrails: SELECT-only validation, named params, RBAC permissions, org scoping.
 */
import type {
  Api, AuditLog, Me, Organization, RunDetails, SubmittedRun, Chart, ChartInput, Dashboard, DashboardInput, DataSource, DataSourceInput, Dataset,
  DatasetInput, ExecuteResult, Json, Page, PageRequest, Permission, QueryRun, QueryTemplate,
  QueryTemplateInput, Role, Snapshot, UserRoles, ValidateResult,
} from './types';
import { ApiError } from './types';
import { aggregate } from '../lib/render';
import { detectParams, guardSql } from '../lib/sql';

const STORAGE_KEY = 'insight-studio.demo.v5';
export const DEMO_ORG = 1;

export const ALL_PERMISSIONS: [string, string][] = [
  ['INSIGHT_DS_READ', 'Read data sources'], ['INSIGHT_DS_CREATE', 'Create data sources'],
  ['INSIGHT_DS_UPDATE', 'Update data sources'], ['INSIGHT_DS_DELETE', 'Delete data sources'],
  ['INSIGHT_QT_READ', 'Read query templates'], ['INSIGHT_QT_CREATE', 'Create query templates'],
  ['INSIGHT_QT_UPDATE', 'Update query templates'], ['INSIGHT_QT_DELETE', 'Delete query templates'],
  ['INSIGHT_QUERY_EXECUTE', 'Execute query templates'],
  ['INSIGHT_DATASET_READ', 'Read datasets'], ['INSIGHT_DATASET_CREATE', 'Create datasets'],
  ['INSIGHT_DATASET_UPDATE', 'Update datasets'], ['INSIGHT_DATASET_DELETE', 'Delete datasets'],
  ['INSIGHT_CHART_READ', 'Read charts'], ['INSIGHT_CHART_CREATE', 'Create charts'],
  ['INSIGHT_CHART_UPDATE', 'Update charts'], ['INSIGHT_CHART_DELETE', 'Delete charts'],
  ['INSIGHT_DASHBOARD_READ', 'Read dashboards'], ['INSIGHT_DASHBOARD_CREATE', 'Create dashboards'],
  ['INSIGHT_DASHBOARD_UPDATE', 'Update dashboards'], ['INSIGHT_DASHBOARD_DELETE', 'Delete dashboards'],
  ['RBAC_ROLE_MANAGE', 'Manage roles'], ['RBAC_PERMISSION_MANAGE', 'Manage permissions'],
  ['RBAC_USER_ROLE_ASSIGN', 'Assign roles to users'],
  ['RBAC_ORG_MANAGE', 'Manage organizations and memberships'],
];

const READ_PERMS = ALL_PERMISSIONS.map(([c]) => c).filter((c) => c.endsWith('_READ'));
const ANALYST_PERMS = [
  ...READ_PERMS, 'INSIGHT_QUERY_EXECUTE',
  'INSIGHT_DATASET_CREATE', 'INSIGHT_DATASET_UPDATE',
  'INSIGHT_CHART_CREATE', 'INSIGHT_CHART_UPDATE',
  'INSIGHT_DASHBOARD_CREATE', 'INSIGHT_DASHBOARD_UPDATE',
];

export const DEMO_USERS = [
  { username: 'admin', role: 'ADMIN', blurb: 'Full access, including RBAC admin' },
  { username: 'analyst', role: 'ANALYST', blurb: 'Runs queries, builds charts & dashboards' },
  { username: 'viewer', role: 'VIEWER', blurb: 'Read-only — see RBAC hide what you cannot do' },
];

interface DemoState {
  seq: number;
  dataSources: (DataSource & { org: number })[];
  templates: (QueryTemplate & { org: number })[];
  datasets: (Dataset & { org: number })[];
  snapshots: (Snapshot & { org: number })[];
  runs: (QueryRun & { org: number })[];
  charts: (Chart & { org: number })[];
  dashboards: (Dashboard & { org: number })[];
  roles: Role[];
  users: UserRoles[];
  organizations: Organization[];
  audit: AuditLog[];
}

// ---------- deterministic fake data ----------

function rng(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 2 ** 32;
  };
}

const REGIONS = ['Berlin', 'Hamburg', 'Munich', 'Cologne'];
const CATEGORIES = ['Espresso beans', 'Filter blends', 'Cold brew', 'Equipment', 'Subscriptions'];

function monthsFrom(start: string, count: number): string[] {
  const d = new Date(start);
  const out: string[] = [];
  for (let i = 0; i < count; i++) {
    out.push(new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + i, 1)).toISOString().slice(0, 7));
  }
  return out;
}

const GENERATORS: Record<string, (params: Json, seed: number) => Json[]> = {
  revenue: (params, seed) => {
    const r = rng(seed);
    const start = String(params.startDate ?? '2026-01-01');
    const rows: Json[] = [];
    monthsFrom(start, 10).forEach((month, i) => {
      REGIONS.forEach((region, j) => {
        const base = [42000, 31000, 38000, 24000][j];
        rows.push({ month, region, revenue: Math.round(base * (1 + i * 0.045) * (0.88 + r() * 0.24)) });
      });
    });
    return rows;
  },
  categories: (_p, seed) => {
    const r = rng(seed);
    const rows: Json[] = [];
    CATEGORIES.forEach((category, i) => {
      for (let k = 0; k < 6; k++) rows.push({ category, order_id: 1000 + i * 10 + k, orders: Math.round((60 - i * 8) * (0.6 + r() * 0.8)) });
    });
    return rows;
  },
  customers: (params, seed) => {
    const r = rng(seed);
    const days = Math.min(Math.max(Number(params.days ?? 30), 1), 90);
    const end = Date.UTC(2026, 10, 17);
    return Array.from({ length: days }, (_, i) => {
      const day = new Date(end - (days - 1 - i) * 86400000);
      const weekend = [0, 6].includes(day.getUTCDay());
      return { day: day.toISOString().slice(0, 10), active_customers: Math.round((weekend ? 1650 : 1180) + i * 9 + r() * 220) };
    });
  },
  tickets: (_p, seed) => {
    const r = rng(seed);
    const rows: Json[] = [];
    ['Delivery', 'Billing', 'Product quality', 'Account'].forEach((topic) => {
      ['P1', 'P2', 'P3'].forEach((priority, pi) => {
        rows.push({ topic, priority, resolution_hours: +(4 + pi * 9 + r() * 12).toFixed(1) });
      });
    });
    return rows;
  },
};

function generatorFor(t: QueryTemplate): (params: Json, seed: number) => Json[] {
  const sql = t.sqlText.toLowerCase();
  if (sql.includes('revenue')) return GENERATORS.revenue;
  if (sql.includes('category')) return GENERATORS.categories;
  if (sql.includes('active_customers')) return GENERATORS.customers;
  if (sql.includes('ticket')) return GENERATORS.tickets;
  // Generic: synthesize rows from the declared output schema.
  return (_p, seed) => {
    const r = rng(seed);
    const fields = t.outputSchema?.length ? t.outputSchema : [{ name: 'label', type: 'text' }, { name: 'value', type: 'number' }];
    return Array.from({ length: 8 }, (_, i) => {
      const row: Json = {};
      for (const f of fields) {
        const ty = f.type.toLowerCase();
        row[f.name] = /int|num|dec|float|double|money/.test(ty) ? Math.round(r() * 1000)
          : /date|time/.test(ty) ? new Date(Date.UTC(2026, 0, 1 + i * 7)).toISOString().slice(0, 10)
          : /bool/.test(ty) ? r() > 0.5 : `${f.name} ${String.fromCharCode(65 + i)}`;
      }
      return row;
    });
  };
}

function iso(minutesAgo: number) {
  return new Date(Date.now() - minutesAgo * 60000).toISOString();
}

function seedState(): DemoState {
  const org = DEMO_ORG;
  const s: DemoState = {
    seq: 100, dataSources: [], templates: [], datasets: [], snapshots: [], runs: [], charts: [], dashboards: [],
    roles: [
      { id: 1, name: 'ADMIN', description: 'System administrator', permissionCodes: ALL_PERMISSIONS.map(([c]) => c).sort() },
      { id: 2, name: 'ANALYST', description: 'Builds datasets, charts and dashboards', permissionCodes: [...ANALYST_PERMS].sort() },
      { id: 3, name: 'VIEWER', description: 'Read-only access to BI assets', permissionCodes: [...READ_PERMS].sort() },
    ],
    users: [
      { id: 1, username: 'admin', email: 'admin@example.com', roleNames: ['ADMIN'], organizationIds: [1, 2] },
      { id: 2, username: 'analyst', email: 'analyst@example.com', roleNames: ['ANALYST'], organizationIds: [1] },
      { id: 3, username: 'viewer', email: 'viewer@example.com', roleNames: ['VIEWER'], organizationIds: [1] },
    ],
    organizations: [
      { id: 1, name: 'Ember & Bean Roasters', createdAt: iso(60 * 24 * 30) },
      { id: 2, name: 'Franchise pilot', createdAt: iso(60 * 24 * 3) },
    ],
    audit: [
      { id: 2, actorUsername: 'admin', action: 'USER_ROLES_REPLACED', targetType: 'USER', targetId: '3', correlationId: 'c0ffee-02', details: { roleNames: ['VIEWER'] }, createdAt: iso(60 * 26) },
      { id: 1, actorUsername: 'admin', action: 'ROLE_CREATED', targetType: 'ROLE', targetId: '3', correlationId: 'c0ffee-01', details: { name: 'VIEWER' }, createdAt: iso(60 * 27) },
    ],
  };

  s.dataSources.push(
    { org, id: 1, name: 'Reporting Postgres', type: 'POSTGRES', host: 'reporting.db.internal', port: 5432, databaseName: 'erp_reporting', username: 'readonly_user', secretRef: null, secretType: 'ENCRYPTED', options: { ssl: true }, active: true },
    { org, id: 2, name: 'Legacy ERP', type: 'MSSQL', host: 'erp-legacy.corp', port: 1433, databaseName: 'ERP_PROD', username: 'bi_reader', secretRef: 'env:ERP_LEGACY_PASSWORD', secretType: 'ENV', options: { encrypt: true }, active: true },
  );

  s.templates.push(
    { org, id: 1, dataSourceId: 1, name: 'Monthly revenue by region', description: 'Net revenue per store region, monthly.', active: true,
      sqlText: "select to_char(order_date, 'YYYY-MM') as month, region, sum(net_amount) as revenue\nfrom sales.orders\nwhere order_date >= :startDate\ngroup by 1, 2\norder by 1",
      parameters: [{ name: 'startDate', type: 'date', required: true }],
      outputSchema: [{ name: 'month', type: 'text' }, { name: 'region', type: 'text' }, { name: 'revenue', type: 'numeric' }] },
    { org, id: 2, dataSourceId: 2, name: 'Orders by product category', description: 'Order counts per category from the legacy ERP.', active: true,
      sqlText: 'select p.category, o.order_id, count(*) as orders\nfrom dbo.orders o\njoin dbo.products p on p.id = o.product_id\ngroup by p.category, o.order_id',
      parameters: [], outputSchema: [{ name: 'category', type: 'text' }, { name: 'order_id', type: 'int' }, { name: 'orders', type: 'int' }] },
    { org, id: 3, dataSourceId: 1, name: 'Daily active customers', description: 'Distinct customers with any activity per day.', active: true,
      sqlText: "select activity_date as day, count(distinct customer_id) as active_customers\nfrom crm.activity\nwhere activity_date > current_date - :days * interval '1 day'\ngroup by 1\norder by 1",
      parameters: [{ name: 'days', type: 'int', required: true }],
      outputSchema: [{ name: 'day', type: 'date' }, { name: 'active_customers', type: 'int' }] },
    { org, id: 4, dataSourceId: 1, name: 'Support ticket resolution', description: 'Average resolution time by topic and priority.', active: true,
      sqlText: 'select topic, priority, resolution_hours\nfrom support.ticket_stats',
      parameters: [], outputSchema: [{ name: 'topic', type: 'text' }, { name: 'priority', type: 'text' }, { name: 'resolution_hours', type: 'numeric' }] },
  );

  const dsDefs: [number, string, number, Json][] = [
    [1, 'Revenue 2026', 1, { startDate: '2026-01-01' }],
    [2, 'Category mix', 2, {}],
    [3, 'Active customers (30d)', 3, { days: 30 }],
    [4, 'Ticket resolution', 4, {}],
  ];
  dsDefs.forEach(([id, name, qt, params], i) => {
    const refreshCron = id === 1 ? '@hourly' : id === 3 ? '0 6 * * *' : null;
    s.datasets.push({ org, id, name, queryTemplateId: qt, defaultParams: params, createdBy: 'analyst', createdAt: iso(60 * 24 * (5 - i)),
      refreshCron, nextRefreshAt: nextFire(refreshCron), lastRefreshAt: refreshCron ? iso(37) : null });
    const t = s.templates.find((x) => x.id === qt)!;
    const rows = generatorFor(t)(params, id * 97);
    s.runs.push({ org, id, status: 'SUCCESS', requestedBy: 'analyst', queryTemplateId: qt, dataSourceId: t.dataSourceId, durationMs: 80 + id * 37, rowCount: rows.length, queryHash: hash(t.sqlText + JSON.stringify(params)), errorType: null, startedAt: iso(60 * (20 - i)), finishedAt: iso(60 * (20 - i)) });
    s.snapshots.push({ org, id, queryRunId: id, datasetId: id, storageFormat: 'JSON', data: rows, createdAt: iso(60 * (20 - i)) });
  });
  s.runs.push({ org, id: 5, status: 'FAILED', requestedBy: 'analyst', queryTemplateId: 2, dataSourceId: 2, durationMs: 30000, rowCount: null, queryHash: 'a91f03c2', errorType: 'QUERY_TIMEOUT', startedAt: iso(45), finishedAt: iso(44) });

  s.charts.push(
    { org, id: 1, name: 'Revenue trend by region', type: 'LINE', datasetId: 1, config: { xField: 'month', yField: 'revenue', seriesField: 'region', aggregation: 'sum' }, createdBy: 'analyst', createdAt: iso(60 * 19) },
    { org, id: 2, name: 'Category share', type: 'PIE', datasetId: 2, config: { xField: 'category', yField: 'orders', aggregation: 'sum' }, createdBy: 'analyst', createdAt: iso(60 * 18) },
    { org, id: 3, name: 'Active customers', type: 'LINE', datasetId: 3, config: { xField: 'day', yField: 'active_customers', aggregation: 'sum' }, createdBy: 'analyst', createdAt: iso(60 * 17) },
    { org, id: 4, name: 'Revenue per region', type: 'BAR', datasetId: 1, config: { xField: 'region', yField: 'revenue', aggregation: 'sum' }, createdBy: 'analyst', createdAt: iso(60 * 16) },
    { org, id: 5, name: 'Resolution hours by priority', type: 'BAR', datasetId: 4, config: { xField: 'topic', yField: 'resolution_hours', seriesField: 'priority', aggregation: 'avg' }, createdBy: 'analyst', createdAt: iso(60 * 15) },
  );

  s.dashboards.push({
    org, id: 1, name: 'Executive overview', createdBy: 'analyst', createdAt: iso(60 * 14),
    widgets: [
      { id: 1, chartId: 1, layout: { x: 0, y: 0, w: 8, h: 4 }, createdAt: iso(60 * 14) },
      { id: 2, chartId: 2, layout: { x: 8, y: 0, w: 4, h: 4 }, createdAt: iso(60 * 14) },
      { id: 3, chartId: 3, layout: { x: 0, y: 4, w: 6, h: 4 }, createdAt: iso(60 * 14) },
      { id: 4, chartId: 4, layout: { x: 6, y: 4, w: 6, h: 4 }, createdAt: iso(60 * 14) },
    ],
  }, {
    org, id: 2, name: 'Customer care', createdBy: 'analyst', createdAt: iso(60 * 13),
    widgets: [
      { id: 5, chartId: 5, layout: { x: 0, y: 0, w: 7, h: 4 }, createdAt: iso(60 * 13) },
      { id: 6, chartId: 3, layout: { x: 7, y: 0, w: 5, h: 4 }, createdAt: iso(60 * 13) },
    ],
  });
  return s;
}

function hash(text: string): string {
  let h = 2166136261;
  for (let i = 0; i < text.length; i++) h = Math.imul(h ^ text.charCodeAt(i), 16777619);
  return (h >>> 0).toString(16).padStart(8, '0');
}

// ---------- scheduled refresh (mirrors DatasetRefreshScheduler, presets only) ----------

/** Rough next fire time for the UI presets; other expressions are assumed hourly in the demo. */
function nextFire(cron: string | null): string | null {
  if (!cron) return null;
  const now = new Date();
  const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), now.getUTCHours() + 1));
  const daily = /^0 (\d{1,2}) \* \* (\*|1-5)$/.exec(cron);
  const every = /^0 \*\/(\d{1,2}) \* \* \*$/.exec(cron);
  if (daily) {
    d.setUTCHours(Number(daily[1]), 0, 0, 0);
    if (d <= now) d.setUTCDate(d.getUTCDate() + 1);
    if (daily[2] === '1-5') while ([0, 6].includes(d.getUTCDay())) d.setUTCDate(d.getUTCDate() + 1);
  } else if (every) {
    const step = Number(every[1]);
    d.setUTCHours(Math.ceil((now.getUTCHours() + 1) / step) * step, 0, 0, 0);
  } else if (cron === '@daily') {
    d.setUTCHours(24, 0, 0, 0);
  }
  return d.toISOString();
}

function schedule(cron: string | undefined | null): { refreshCron: string | null; nextRefreshAt: string | null } {
  const c = (cron ?? '').trim().replace(/\s+/g, ' ');
  if (!c) return { refreshCron: null, nextRefreshAt: null };
  const macro = ['@hourly', '@daily', '@midnight', '@weekly', '@monthly', '@yearly', '@annually'].includes(c);
  if (!macro && c.split(' ').length !== 5) throw new ApiError(400, 'refreshCron is not a valid cron expression');
  const minute = c.split(' ')[0];
  if (!macro && (minute === '*' || /^\*\/[1-4]$/.test(minute))) throw new ApiError(400, 'refreshCron fires too often; the minimum interval is 5 minutes');
  return { refreshCron: c, nextRefreshAt: nextFire(c) };
}

// ---------- SQL guardrails (port of InsightSqlValidator) ----------



// ---------- the API ----------

function fakeJwt(username: string, perms: string[], orgs: number[]): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  const now = Math.floor(Date.now() / 1000);
  return `${b64({ alg: 'none', typ: 'JWT' })}.${b64({ sub: username, iss: 'demo', perms, orgs, iat: now, exp: now + 3600 * 8 })}.demo`;
}

const delay = (ms = 180 + Math.random() * 220) => new Promise((r) => setTimeout(r, ms));

export class DemoApi implements Api {
  private s: DemoState;
  private user = 'admin';

  constructor() {
    this.s = this.load() ?? seedState();
  }

  static reset() {
    try { localStorage.removeItem(STORAGE_KEY); } catch { /* storage unavailable */ }
  }

  private load(): DemoState | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as DemoState) : null;
    } catch { return null; }
  }

  private save() {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(this.s)); } catch { /* storage unavailable */ }
  }

  setUser(username: string) { this.user = username; }

  private perms(): Set<string> {
    const u = this.s.users.find((x) => x.username === this.user);
    const codes = new Set<string>();
    u?.roleNames.forEach((r) => this.s.roles.find((x) => x.name === r)?.permissionCodes.forEach((c) => codes.add(c)));
    return codes;
  }

  private async guard(perm: string) {
    await delay();
    if (!this.perms().has(perm)) throw new ApiError(403, 'You do not have permission for this action.');
  }

  /** Mirrors OrganizationAccessInterceptor: org-scoped calls require membership. */
  private async guardOrg(org: number, perm: string) {
    await this.guard(perm);
    if (!this.orgIds().includes(org)) throw new ApiError(403, 'Access denied');
  }

  private orgIds(): number[] {
    return this.s.users.find((x) => x.username === this.user)?.organizationIds ?? [];
  }

  private id() { return ++this.s.seq; }

  private page<T>(items: T[], p?: PageRequest): Page<T> {
    let list = [...items];
    const [field, dir] = (p?.sort ?? 'id,desc').split(',');
    list.sort((a: any, b: any) => (a[field] > b[field] ? 1 : a[field] < b[field] ? -1 : 0) * (dir === 'asc' ? 1 : -1));
    const size = p?.size ?? 20;
    const number = p?.page ?? 0;
    return {
      content: list.slice(number * size, number * size + size).map((x: any) => { const { org: _o, ...rest } = x; return rest as T; }),
      totalElements: list.length,
      totalPages: Math.max(1, Math.ceil(list.length / size)),
      number, size,
    };
  }

  private strip<T extends { org: number }>(x: T): Omit<T, 'org'> {
    const { org: _o, ...rest } = x;
    return rest;
  }

  private find<T extends { id: number; org: number }>(list: T[], org: number, id: number, label: string): T {
    const item = list.find((x) => x.id === id && x.org === org);
    if (!item) throw new ApiError(400, `${label} not found`);
    return item;
  }

  private audit(action: string, targetType: string, targetId: string, details: Json) {
    this.s.audit.unshift({ id: this.id(), actorUsername: this.user, action, targetType, targetId, correlationId: hash(String(Math.random())).slice(0, 8), details, createdAt: new Date().toISOString() });
  }

  // ----- auth -----
  async login(username: string, _password: string) {
    await delay(350);
    const u = this.s.users.find((x) => x.username === username.trim().toLowerCase());
    if (!u) throw new ApiError(401, 'Unknown demo user. Try admin, analyst or viewer.');
    this.user = u.username;
    return fakeJwt(u.username, [...this.perms()], u.organizationIds);
  }
  async register(email: string, username: string, _password: string) {
    await delay();
    if (this.s.users.some((u) => u.email === email)) throw new ApiError(400, 'Email already registered');
    this.s.users.push({ id: this.id(), username, email, roleNames: ['VIEWER'], organizationIds: [] });
    this.save();
  }
  async health() { return true; }
  async me(): Promise<Me> {
    await delay(80);
    const ids = this.orgIds();
    return { username: this.user, permissions: [...this.perms()].sort(), organizations: this.s.organizations.filter((o) => ids.includes(o.id)) };
  }

  // ----- data sources -----
  async listDataSources(org: number, p?: PageRequest) {
    await this.guardOrg(org, 'INSIGHT_DS_READ');
    return this.page(this.s.dataSources.filter((x) => x.org === org), p);
  }
  async createDataSource(org: number, body: DataSourceInput) {
    await this.guardOrg(org, 'INSIGHT_DS_CREATE');
    const { password, secretRef, ...rest } = body;
    const ds = { ...rest, ...this.secret(secretRef, password, true)!, org, id: this.id(), active: true, options: body.options ?? {} };
    this.s.dataSources.push(ds);
    this.save();
    return this.strip(ds);
  }
  async updateDataSource(org: number, id: number, body: Partial<DataSourceInput>) {
    await this.guardOrg(org, 'INSIGHT_DS_UPDATE');
    const ds = this.find(this.s.dataSources, org, id, 'DataSource');
    const { password, secretRef, ...rest } = body;
    Object.assign(ds, Object.fromEntries(Object.entries(rest).filter(([, v]) => v !== undefined)), this.secret(secretRef, password, false) ?? {});
    this.save();
    return this.strip(ds);
  }

  /** Mirrors DataSourceService: either env:VAR or a password (kept only as "encrypted"). */
  private secret(secretRef: string | undefined, password: string | undefined, required: boolean): Pick<DataSource, 'secretRef' | 'secretType'> | null {
    if (secretRef && password) throw new ApiError(400, 'Provide either secretRef or password, not both');
    if (password) return { secretRef: null, secretType: 'ENCRYPTED' };
    if (secretRef) {
      if (!/^env:.+/.test(secretRef.trim())) throw new ApiError(400, 'secretRef must look like env:VAR_NAME; send a password to store it encrypted');
      return { secretRef: secretRef.trim(), secretType: 'ENV' };
    }
    if (required) throw new ApiError(400, 'secretRef or password is required');
    return null;
  }
  async testDataSource(org: number, id: number) {
    await this.guardOrg(org, 'INSIGHT_DS_READ');
    const ds = this.find(this.s.dataSources, org, id, 'DataSource');
    await delay(400);
    const ok = ds.active && !ds.host.includes('invalid');
    return { ok, latencyMs: ok ? Math.round(18 + Math.random() * 40) : 0, error: ok ? null : 'Connection refused: ' + ds.host };
  }

  // ----- query templates -----
  async listQueryTemplates(org: number, p?: PageRequest) {
    await this.guardOrg(org, 'INSIGHT_QT_READ');
    return this.page(this.s.templates.filter((x) => x.org === org), p);
  }
  async createQueryTemplate(org: number, body: QueryTemplateInput) {
    await this.guardOrg(org, 'INSIGHT_QT_CREATE');
    this.find(this.s.dataSources, org, body.dataSourceId, 'DataSource');
    const errors = guardSql(body.sqlText);
    if (errors.length) throw new ApiError(400, errors.join(', '));
    const qt = { org, id: this.id(), dataSourceId: body.dataSourceId, name: body.name, description: body.description ?? null, sqlText: body.sqlText, parameters: body.parameters ?? [], outputSchema: body.outputSchema ?? [], active: true };
    this.s.templates.push(qt);
    this.save();
    return this.strip(qt);
  }
  async updateQueryTemplate(org: number, id: number, body: Partial<QueryTemplateInput> & { active?: boolean }) {
    await this.guardOrg(org, 'INSIGHT_QT_UPDATE');
    const qt = this.find(this.s.templates, org, id, 'QueryTemplate');
    if (body.sqlText) {
      const errors = guardSql(body.sqlText);
      if (errors.length) throw new ApiError(400, errors.join(', '));
    }
    Object.assign(qt, Object.fromEntries(Object.entries(body).filter(([, v]) => v !== undefined)));
    this.save();
    return this.strip(qt);
  }

  // ----- execution -----
  private validateInternal(org: number, qtId: number, params: Json): ValidateResult {
    const qt = this.find(this.s.templates, org, qtId, 'QueryTemplate');
    const errors = guardSql(qt.sqlText);
    const detected = detectParams(qt.sqlText);
    const allowed = (qt.parameters ?? []).map((p) => p.name);
    for (const k of Object.keys(params)) if (!detected.includes(k)) errors.push(`Unexpected parameter: ${k}`);
    for (const p of qt.parameters ?? []) {
      if (p.required && (params[p.name] == null || params[p.name] === '')) errors.push(`Missing required parameter: ${p.name}`);
    }
    if (!qt.active) errors.push('QueryTemplate is inactive');
    return { valid: errors.length === 0, errors, detectedParams: detected, allowedParams: allowed };
  }

  async validateQuery(org: number, qtId: number, params: Json) {
    await this.guardOrg(org, 'INSIGHT_QUERY_EXECUTE');
    return this.validateInternal(org, qtId, params);
  }

  private run(org: number, qtId: number, params: Json, snapshot: boolean, datasetId: number | null): ExecuteResult {
    const qt = this.find(this.s.templates, org, qtId, 'QueryTemplate');
    const v = this.validateInternal(org, qtId, params);
    if (!v.valid) throw new ApiError(400, v.errors.join(', '));
    const runId = this.id();
    const rows = generatorFor(qt)(params, runId * 31).slice(0, 5000);
    const durationMs = Math.round(60 + Math.random() * 380);
    const now = new Date().toISOString();
    this.s.runs.push({ org, id: runId, status: 'SUCCESS', requestedBy: this.user, queryTemplateId: qtId, dataSourceId: qt.dataSourceId, durationMs, rowCount: rows.length, queryHash: hash(qt.sqlText + JSON.stringify(params)), errorType: null, startedAt: now, finishedAt: now });
    let snapshotId: number | null = null;
    if (snapshot) {
      snapshotId = this.id();
      this.s.snapshots.push({ org, id: snapshotId, queryRunId: runId, datasetId, storageFormat: 'JSON', data: rows.slice(0, 500), createdAt: now });
    }
    this.save();
    return { queryRunId: runId, status: 'SUCCESS', rowCount: rows.length, durationMs, requestedBy: this.user, dataSourceId: qt.dataSourceId, queryTemplateId: qtId, snapshotId, queryHash: hash(qt.sqlText + JSON.stringify(params)), errorType: null };
  }

  /** Mirrors QueryRunJobService: validate now, record QUEUED, then RUNNING, then SUCCESS a moment later. */
  private runLater(org: number, qtId: number, params: Json, snapshot: boolean, datasetId: number | null, requestedBy = this.user): SubmittedRun {
    const qt = this.find(this.s.templates, org, qtId, 'QueryTemplate');
    const v = this.validateInternal(org, qtId, params);
    if (!v.valid) throw new ApiError(400, v.errors.join(', '));
    const runId = this.id();
    const run: QueryRun & { org: number } = { org, id: runId, status: 'QUEUED', requestedBy, queryTemplateId: qtId, dataSourceId: qt.dataSourceId, durationMs: 0, rowCount: null, queryHash: hash(qt.sqlText + JSON.stringify(params)), errorType: null, startedAt: new Date().toISOString(), finishedAt: null };
    this.s.runs.push(run);
    this.save();
    setTimeout(() => { run.status = 'RUNNING'; run.startedAt = new Date().toISOString(); this.save(); }, 500);
    setTimeout(() => {
      const rows = generatorFor(qt)(params, runId * 31).slice(0, 5000);
      Object.assign(run, { status: 'SUCCESS', rowCount: rows.length, durationMs: Math.round(700 + Math.random() * 600), finishedAt: new Date().toISOString() });
      if (snapshot) this.s.snapshots.push({ org, id: this.id(), queryRunId: runId, datasetId, storageFormat: 'JSON', data: rows.slice(0, 500), createdAt: run.finishedAt! });
      this.save();
    }, 1600);
    return { queryRunId: runId, status: 'QUEUED', coalesced: false };
  }

  async submitQuery(org: number, qtId: number, params: Json, snapshot: boolean) {
    await this.guardOrg(org, 'INSIGHT_QUERY_EXECUTE');
    return this.runLater(org, qtId, params, snapshot, null);
  }

  async getRun(org: number, runId: number): Promise<RunDetails> {
    await this.guardOrg(org, 'INSIGHT_QUERY_EXECUTE');
    const run = this.find(this.s.runs, org, runId, 'QueryRun');
    const snap = run.status === 'SUCCESS' ? this.s.snapshots.find((x) => x.org === org && x.queryRunId === runId) : undefined;
    return { ...this.strip(run), errorMessage: run.status === 'FAILED' ? 'Query failed' : null, snapshotId: snap?.id ?? null };
  }

  async submitSnapshot(org: number, datasetId: number, params?: Json) {
    await this.guardOrg(org, 'INSIGHT_DATASET_CREATE');
    const d = this.find(this.s.datasets, org, datasetId, 'Dataset');
    return this.runLater(org, d.queryTemplateId, { ...(d.defaultParams ?? {}), ...(params ?? {}) }, true, datasetId);
  }

  async executeQuery(org: number, qtId: number, params: Json, snapshot: boolean) {
    await this.guardOrg(org, 'INSIGHT_QUERY_EXECUTE');
    await delay(300);
    return this.run(org, qtId, params, snapshot, null);
  }
  async listRuns(org: number, p?: PageRequest) {
    await this.guardOrg(org, 'INSIGHT_QUERY_EXECUTE');
    return this.page(this.s.runs.filter((x) => x.org === org), p);
  }

  // ----- datasets -----
  async listDatasets(org: number, p?: PageRequest) {
    await this.guardOrg(org, 'INSIGHT_DATASET_READ');
    return this.page(this.s.datasets.filter((x) => x.org === org), p);
  }
  async createDataset(org: number, body: DatasetInput) {
    await this.guardOrg(org, 'INSIGHT_DATASET_CREATE');
    this.find(this.s.templates, org, body.queryTemplateId, 'QueryTemplate');
    const d = { org, id: this.id(), name: body.name, queryTemplateId: body.queryTemplateId, defaultParams: body.defaultParams ?? {}, createdBy: this.user, createdAt: new Date().toISOString(), lastRefreshAt: null, ...schedule(body.refreshCron) };
    this.s.datasets.push(d);
    this.save();
    return this.strip(d);
  }
  async updateDataset(org: number, id: number, body: Partial<DatasetInput>) {
    await this.guardOrg(org, 'INSIGHT_DATASET_UPDATE');
    const d = this.find(this.s.datasets, org, id, 'Dataset');
    const { refreshCron, ...rest } = body;
    Object.assign(d, Object.fromEntries(Object.entries(rest).filter(([, v]) => v !== undefined)));
    if (refreshCron !== undefined) Object.assign(d, schedule(refreshCron));
    this.save();
    return this.strip(d);
  }
  async deleteDataset(org: number, id: number) {
    await this.guardOrg(org, 'INSIGHT_DATASET_DELETE');
    this.find(this.s.datasets, org, id, 'Dataset');
    if (this.s.charts.some((c) => c.datasetId === id && c.org === org)) throw new ApiError(400, 'Dataset is used by charts');
    this.s.datasets = this.s.datasets.filter((x) => !(x.id === id && x.org === org));
    this.save();
  }
  async listSnapshots(org: number, datasetId: number, p?: PageRequest) {
    await this.guardOrg(org, 'INSIGHT_DATASET_READ');
    this.find(this.s.datasets, org, datasetId, 'Dataset');
    return this.page(this.s.snapshots.filter((x) => x.org === org && x.datasetId === datasetId), p);
  }
  async createSnapshot(org: number, datasetId: number, params?: Json) {
    await this.guardOrg(org, 'INSIGHT_DATASET_CREATE');
    const d = this.find(this.s.datasets, org, datasetId, 'Dataset');
    await delay(300);
    return this.run(org, d.queryTemplateId, { ...(d.defaultParams ?? {}), ...(params ?? {}) }, true, datasetId);
  }

  // ----- charts -----
  async listCharts(org: number, p?: PageRequest) {
    await this.guardOrg(org, 'INSIGHT_CHART_READ');
    return this.page(this.s.charts.filter((x) => x.org === org), p);
  }
  async getChart(org: number, id: number) {
    await this.guardOrg(org, 'INSIGHT_CHART_READ');
    return this.strip(this.find(this.s.charts, org, id, 'Chart'));
  }
  async createChart(org: number, body: ChartInput) {
    await this.guardOrg(org, 'INSIGHT_CHART_CREATE');
    this.find(this.s.datasets, org, body.datasetId, 'Dataset');
    const c = { org, id: this.id(), ...body, createdBy: this.user, createdAt: new Date().toISOString() };
    this.s.charts.push(c);
    this.save();
    return this.strip(c);
  }
  async updateChart(org: number, id: number, body: Partial<ChartInput>) {
    await this.guardOrg(org, 'INSIGHT_CHART_UPDATE');
    const c = this.find(this.s.charts, org, id, 'Chart');
    Object.assign(c, Object.fromEntries(Object.entries(body).filter(([, v]) => v !== undefined)));
    this.save();
    return this.strip(c);
  }
  async deleteChart(org: number, id: number) {
    await this.guardOrg(org, 'INSIGHT_CHART_DELETE');
    this.find(this.s.charts, org, id, 'Chart');
    this.s.charts = this.s.charts.filter((x) => !(x.id === id && x.org === org));
    this.s.dashboards.forEach((d) => { d.widgets = d.widgets.filter((w) => w.chartId !== id); });
    this.save();
  }
  async renderChart(org: number, id: number, snapshotId: number) {
    await this.guardOrg(org, 'INSIGHT_CHART_READ');
    const c = this.find(this.s.charts, org, id, 'Chart');
    const snap = this.find(this.s.snapshots, org, snapshotId, 'DatasetSnapshot');
    if (snap.datasetId !== c.datasetId) throw new ApiError(400, 'Snapshot does not belong to chart dataset');
    try {
      return { chartId: c.id, type: c.type, snapshotId, points: aggregate(snap.data ?? [], c.config ?? {}) };
    } catch (e) {
      throw new ApiError(400, (e as Error).message);
    }
  }

  // ----- dashboards -----
  async listDashboards(org: number, p?: PageRequest) {
    await this.guardOrg(org, 'INSIGHT_DASHBOARD_READ');
    return this.page(this.s.dashboards.filter((x) => x.org === org), p);
  }
  async getDashboard(org: number, id: number) {
    await this.guardOrg(org, 'INSIGHT_DASHBOARD_READ');
    return this.strip(this.find(this.s.dashboards, org, id, 'Dashboard'));
  }
  private widgets(org: number, input: DashboardInput['widgets']) {
    return input.map((w) => {
      this.find(this.s.charts, org, w.chartId, 'Chart');
      return { id: this.id(), chartId: w.chartId, layout: w.layout, createdAt: new Date().toISOString() };
    });
  }
  async createDashboard(org: number, body: DashboardInput) {
    await this.guardOrg(org, 'INSIGHT_DASHBOARD_CREATE');
    const d = { org, id: this.id(), name: body.name, createdBy: this.user, createdAt: new Date().toISOString(), widgets: this.widgets(org, body.widgets ?? []) };
    this.s.dashboards.push(d);
    this.save();
    return this.strip(d);
  }
  async updateDashboard(org: number, id: number, body: Partial<DashboardInput>) {
    await this.guardOrg(org, 'INSIGHT_DASHBOARD_UPDATE');
    const d = this.find(this.s.dashboards, org, id, 'Dashboard');
    if (body.name) d.name = body.name;
    if (body.widgets) d.widgets = this.widgets(org, body.widgets);
    this.save();
    return this.strip(d);
  }
  async deleteDashboard(org: number, id: number) {
    await this.guardOrg(org, 'INSIGHT_DASHBOARD_DELETE');
    this.find(this.s.dashboards, org, id, 'Dashboard');
    this.s.dashboards = this.s.dashboards.filter((x) => !(x.id === id && x.org === org));
    this.save();
  }

  // ----- RBAC admin -----
  async listPermissions(): Promise<Permission[]> {
    await this.guard('RBAC_PERMISSION_MANAGE');
    return ALL_PERMISSIONS.map(([code, description], i) => ({ id: i + 1, code, description }));
  }
  async listRoles() {
    await this.guard('RBAC_ROLE_MANAGE');
    return structuredClone(this.s.roles);
  }
  async createRole(name: string, description: string) {
    await this.guard('RBAC_ROLE_MANAGE');
    const n = name.trim().toUpperCase();
    if (this.s.roles.some((r) => r.name === n)) throw new ApiError(400, 'Role already exists');
    const role = { id: this.id(), name: n, description, permissionCodes: [] };
    this.s.roles.push(role);
    this.audit('ROLE_CREATED', 'ROLE', String(role.id), { name: n, description });
    this.save();
    return structuredClone(role);
  }
  async replaceRolePermissions(id: number, codes: string[]) {
    await this.guard('RBAC_ROLE_MANAGE');
    const role = this.s.roles.find((r) => r.id === id);
    if (!role) throw new ApiError(400, 'Role not found');
    const before = role.permissionCodes;
    role.permissionCodes = [...codes].sort();
    this.audit('ROLE_PERMISSIONS_REPLACED', 'ROLE', String(id), { before, after: role.permissionCodes });
    this.save();
    return structuredClone(role);
  }
  async listUsers(query?: string) {
    await this.guard('RBAC_USER_ROLE_ASSIGN');
    const q = query?.toLowerCase() ?? '';
    return structuredClone(this.s.users.filter((u) => !q || u.username.includes(q) || u.email.includes(q)));
  }
  async replaceUserRoles(id: number, roleNames: string[]) {
    await this.guard('RBAC_USER_ROLE_ASSIGN');
    const u = this.s.users.find((x) => x.id === id);
    if (!u) throw new ApiError(400, 'User not found');
    const before = u.roleNames;
    u.roleNames = [...roleNames].sort();
    this.audit('USER_ROLES_REPLACED', 'USER', String(id), { before, after: u.roleNames });
    this.save();
    return structuredClone(u);
  }
  async listAuditLogs(p?: PageRequest) {
    await this.guard('RBAC_ROLE_MANAGE');
    return this.page(this.s.audit.map((a) => ({ ...a, org: 0 })), p);
  }

  async listOrganizations() {
    await this.guard('RBAC_ORG_MANAGE');
    return structuredClone(this.s.organizations);
  }
  async createOrganization(name: string) {
    await this.guard('RBAC_ORG_MANAGE');
    if (!name.trim()) throw new ApiError(400, 'name is required');
    const org = { id: this.id(), name: name.trim(), createdAt: new Date().toISOString() };
    this.s.organizations.push(org);
    this.audit('ORGANIZATION_CREATED', 'ORGANIZATION', String(org.id), { name: org.name });
    this.save();
    return structuredClone(org);
  }
  async replaceUserOrganizations(id: number, organizationIds: number[]) {
    await this.guard('RBAC_ORG_MANAGE');
    const u = this.s.users.find((x) => x.id === id);
    if (!u) throw new ApiError(400, 'User not found');
    if (organizationIds.some((o) => !this.s.organizations.some((x) => x.id === o))) throw new ApiError(400, 'One or more organization ids are invalid');
    const before = u.organizationIds;
    u.organizationIds = [...new Set(organizationIds)].sort((a, b) => a - b);
    this.audit('USER_ORGANIZATIONS_REPLACED', 'USER', String(id), { before, after: u.organizationIds });
    this.save();
    return structuredClone(u);
  }
}
