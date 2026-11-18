import type {
  Api, AuditLog, Me, Organization, RunDetails, SubmittedRun, Chart, ConnectionTest, Dashboard, DataSource, Dataset, ExecuteResult, Json,
  Page, PageRequest, Permission, QueryRun, QueryTemplate, RenderedChart, Role, Snapshot, UserRoles,
  ValidateResult,
} from './types';
import { ApiError } from './types';

const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';

/** Spring Data may serialize Page either directly or via PagedModel ({content, page:{...}}). */
function toPage<T>(raw: any): Page<T> {
  const meta = raw?.page ?? raw ?? {};
  return {
    content: raw?.content ?? [],
    totalElements: meta.totalElements ?? 0,
    totalPages: meta.totalPages ?? 0,
    number: meta.number ?? 0,
    size: meta.size ?? 0,
  };
}

function pageQuery(p?: PageRequest): string {
  const q = new URLSearchParams();
  if (p?.page != null) q.set('page', String(p.page));
  if (p?.size != null) q.set('size', String(p.size));
  if (p?.sort) q.set('sort', p.sort);
  return q.toString();
}

export class HttpApi implements Api {
  constructor(private getToken: () => string | null, private onUnauthorized: () => void) {}

  private async req<T>(method: string, path: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    const token = this.getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';

    let res: Response;
    try {
      res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    } catch {
      throw new ApiError(0, 'Cannot reach the API. Is the backend running?');
    }

    if (res.status === 401 && token) this.onUnauthorized();
    const text = await res.text();
    let data: any = undefined;
    if (text) {
      try { data = JSON.parse(text); } catch { data = text; }
    }
    if (!res.ok) {
      const msg = (data && typeof data === 'object' && (data.message || data.error)) ||
        (res.status === 403 ? 'You do not have permission for this action.' : `Request failed (${res.status})`);
      throw new ApiError(res.status, msg);
    }
    return data as T;
  }

  private org(path: string, org: number, extra = ''): string {
    const sep = path.includes('?') ? '&' : '?';
    return `${path}${sep}orgId=${org}${extra ? '&' + extra : ''}`;
  }

  async login(username: string, password: string) {
    const r = await this.req<{ accessToken: string }>('POST', '/api/auth/login', { username, password });
    return r.accessToken;
  }
  register(email: string, username: string, password: string) {
    return this.req<void>('POST', '/api/auth/register', { email, username, password });
  }
  async health() {
    try { await this.req('GET', '/api/health'); return true; } catch { return false; }
  }

  me() { return this.req<Me>('GET', '/api/me'); }

  async listDataSources(org: number, p?: PageRequest) {
    return toPage<DataSource>(await this.req('GET', this.org('/api/insight/data-sources', org, pageQuery(p))));
  }
  createDataSource(org: number, body: unknown) {
    return this.req<DataSource>('POST', this.org('/api/insight/data-sources', org), body);
  }
  updateDataSource(org: number, id: number, body: unknown) {
    return this.req<DataSource>('PUT', this.org(`/api/insight/data-sources/${id}`, org), body);
  }
  testDataSource(org: number, id: number) {
    return this.req<ConnectionTest>('POST', this.org(`/api/insight/data-sources/${id}/test`, org));
  }

  async listQueryTemplates(org: number, p?: PageRequest) {
    return toPage<QueryTemplate>(await this.req('GET', this.org('/api/insight/query-templates', org, pageQuery(p))));
  }
  createQueryTemplate(org: number, body: unknown) {
    return this.req<QueryTemplate>('POST', this.org('/api/insight/query-templates', org), body);
  }
  updateQueryTemplate(org: number, id: number, body: unknown) {
    return this.req<QueryTemplate>('PUT', this.org(`/api/insight/query-templates/${id}`, org), body);
  }

  validateQuery(org: number, queryTemplateId: number, params: Json) {
    return this.req<ValidateResult>('POST', this.org('/api/insight/query-execution/validate', org), { queryTemplateId, params });
  }
  executeQuery(org: number, queryTemplateId: number, params: Json, snapshot: boolean) {
    return this.req<ExecuteResult>('POST', this.org('/api/insight/query-execution/execute', org), { queryTemplateId, params, snapshot });
  }
  submitQuery(org: number, queryTemplateId: number, params: Json, snapshot: boolean) {
    return this.req<SubmittedRun>('POST', this.org('/api/insight/query-execution/jobs', org), { queryTemplateId, params, snapshot });
  }
  getRun(org: number, runId: number) {
    return this.req<RunDetails>('GET', this.org(`/api/insight/query-execution/runs/${runId}`, org));
  }
  async listRuns(org: number, p?: PageRequest) {
    return toPage<QueryRun>(await this.req('GET', this.org('/api/insight/query-execution/runs', org, pageQuery(p))));
  }

  async listDatasets(org: number, p?: PageRequest) {
    return toPage<Dataset>(await this.req('GET', this.org('/api/insight/datasets', org, pageQuery(p))));
  }
  createDataset(org: number, body: unknown) {
    return this.req<Dataset>('POST', this.org('/api/insight/datasets', org), body);
  }
  updateDataset(org: number, id: number, body: unknown) {
    return this.req<Dataset>('PUT', this.org(`/api/insight/datasets/${id}`, org), body);
  }
  deleteDataset(org: number, id: number) {
    return this.req<void>('DELETE', this.org(`/api/insight/datasets/${id}`, org));
  }
  async listSnapshots(org: number, datasetId: number, p?: PageRequest) {
    return toPage<Snapshot>(await this.req('GET', this.org(`/api/insight/datasets/${datasetId}/snapshots`, org, pageQuery(p))));
  }
  createSnapshot(org: number, datasetId: number, params?: Json) {
    return this.req<ExecuteResult>('POST', this.org(`/api/insight/datasets/${datasetId}/snapshots`, org), params ? { params } : {});
  }

  submitSnapshot(org: number, datasetId: number, params?: Json) {
    return this.req<SubmittedRun>('POST', this.org(`/api/insight/datasets/${datasetId}/snapshot-jobs`, org), params ? { params } : {});
  }

  async listCharts(org: number, p?: PageRequest) {
    return toPage<Chart>(await this.req('GET', this.org('/api/insight/charts', org, pageQuery(p))));
  }
  getChart(org: number, id: number) {
    return this.req<Chart>('GET', this.org(`/api/insight/charts/${id}`, org));
  }
  createChart(org: number, body: unknown) {
    return this.req<Chart>('POST', this.org('/api/insight/charts', org), body);
  }
  updateChart(org: number, id: number, body: unknown) {
    return this.req<Chart>('PUT', this.org(`/api/insight/charts/${id}`, org), body);
  }
  deleteChart(org: number, id: number) {
    return this.req<void>('DELETE', this.org(`/api/insight/charts/${id}`, org));
  }
  renderChart(org: number, id: number, snapshotId: number) {
    return this.req<RenderedChart>('GET', this.org(`/api/insight/charts/${id}/render`, org, `snapshotId=${snapshotId}`));
  }

  async listDashboards(org: number, p?: PageRequest) {
    return toPage<Dashboard>(await this.req('GET', this.org('/api/insight/dashboards', org, pageQuery(p))));
  }
  getDashboard(org: number, id: number) {
    return this.req<Dashboard>('GET', this.org(`/api/insight/dashboards/${id}`, org));
  }
  createDashboard(org: number, body: unknown) {
    return this.req<Dashboard>('POST', this.org('/api/insight/dashboards', org), body);
  }
  updateDashboard(org: number, id: number, body: unknown) {
    return this.req<Dashboard>('PUT', this.org(`/api/insight/dashboards/${id}`, org), body);
  }
  deleteDashboard(org: number, id: number) {
    return this.req<void>('DELETE', this.org(`/api/insight/dashboards/${id}`, org));
  }

  listPermissions() { return this.req<Permission[]>('GET', '/api/admin/rbac/permissions'); }
  listRoles() { return this.req<Role[]>('GET', '/api/admin/rbac/roles'); }
  createRole(name: string, description: string) {
    return this.req<Role>('POST', '/api/admin/rbac/roles', { name, description });
  }
  replaceRolePermissions(id: number, permissionCodes: string[]) {
    return this.req<Role>('PUT', `/api/admin/rbac/roles/${id}/permissions`, { permissionCodes });
  }
  listUsers(query?: string) {
    return this.req<UserRoles[]>('GET', '/api/admin/rbac/users' + (query ? `?query=${encodeURIComponent(query)}` : ''));
  }
  replaceUserRoles(id: number, roleNames: string[]) {
    return this.req<UserRoles>('PUT', `/api/admin/rbac/users/${id}/roles`, { roleNames });
  }
  async listAuditLogs(p?: PageRequest) {
    const q = pageQuery(p);
    return toPage<AuditLog>(await this.req('GET', '/api/admin/rbac/audit-logs' + (q ? `?${q}` : '')));
  }

  listOrganizations() { return this.req<Organization[]>('GET', '/api/admin/rbac/organizations'); }
  createOrganization(name: string) {
    return this.req<Organization>('POST', '/api/admin/rbac/organizations', { name });
  }
  replaceUserOrganizations(id: number, organizationIds: number[]) {
    return this.req<UserRoles>('PUT', `/api/admin/rbac/users/${id}/organizations`, { organizationIds });
  }
}
