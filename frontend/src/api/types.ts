// Mirrors the DTOs exposed by the Spring Boot API (src/main/java/.../api).

export type Json = Record<string, unknown>;

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface PageRequest {
  page?: number;
  size?: number;
  sort?: string;
}

export type DataSourceType = 'POSTGRES' | 'MSSQL';

export interface DataSource {
  id: number;
  name: string;
  type: DataSourceType;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  secretRef: string;
  options: Json | null;
  active: boolean;
}

export type DataSourceInput = Omit<DataSource, 'id' | 'active'> & { active?: boolean };

export interface ConnectionTest {
  ok: boolean;
  latencyMs: number;
  error: string | null;
}

export interface ParamDef {
  name: string;
  type: string;
  required?: boolean;
}

export interface FieldDef {
  name: string;
  type: string;
}

export interface QueryTemplate {
  id: number;
  dataSourceId: number;
  name: string;
  description: string | null;
  sqlText: string;
  parameters: ParamDef[] | null;
  outputSchema: FieldDef[] | null;
  active: boolean;
}

export interface QueryTemplateInput {
  dataSourceId: number;
  name: string;
  description?: string;
  sqlText: string;
  parameters?: ParamDef[];
  outputSchema?: FieldDef[];
}

export interface ValidateResult {
  valid: boolean;
  errors: string[];
  detectedParams: string[];
  allowedParams: string[];
}

export interface ExecuteResult {
  queryRunId: number;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED';
  rowCount: number;
  durationMs: number;
  requestedBy: string;
  dataSourceId: number;
  queryTemplateId: number;
  snapshotId: number | null;
  queryHash: string | null;
  errorType: string | null;
}

export interface QueryRun {
  id: number;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED';
  requestedBy: string;
  queryTemplateId: number;
  dataSourceId: number;
  durationMs: number;
  rowCount: number | null;
  queryHash: string | null;
  errorType: string | null;
  startedAt: string;
  finishedAt: string | null;
}

export interface Dataset {
  id: number;
  name: string;
  queryTemplateId: number;
  defaultParams: Json | null;
  createdBy: string;
  createdAt: string;
}

export interface DatasetInput {
  name: string;
  queryTemplateId: number;
  defaultParams?: Json;
}

export interface Snapshot {
  id: number;
  queryRunId: number;
  datasetId: number | null;
  storageFormat: string;
  data: Json[] | null;
  createdAt: string;
}

export type ChartType = 'BAR' | 'LINE' | 'PIE';
export type Aggregation = 'sum' | 'avg' | 'count';

export interface ChartConfig {
  xField?: string;
  yField?: string;
  seriesField?: string;
  aggregation?: Aggregation;
}

export interface Chart {
  id: number;
  name: string;
  type: ChartType;
  datasetId: number;
  config: ChartConfig | null;
  createdBy: string;
  createdAt: string;
}

export interface ChartInput {
  name: string;
  type: ChartType;
  datasetId: number;
  config: ChartConfig;
}

export interface RenderPoint {
  x: string;
  series?: string;
  value: number | string;
}

export interface RenderedChart {
  chartId: number;
  type: ChartType;
  snapshotId: number;
  points: RenderPoint[];
}

export interface WidgetLayout {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface DashboardWidget {
  id: number;
  chartId: number;
  layout: WidgetLayout | null;
  createdAt: string;
}

export interface Dashboard {
  id: number;
  name: string;
  createdBy: string;
  createdAt: string;
  widgets: DashboardWidget[];
}

export interface DashboardInput {
  name: string;
  widgets: { chartId: number; layout: WidgetLayout }[];
}

export interface Permission {
  id: number;
  code: string;
  description: string;
}

export interface Role {
  id: number;
  name: string;
  description: string | null;
  permissionCodes: string[];
}

export interface UserRoles {
  id: number;
  username: string;
  email: string;
  roleNames: string[];
  organizationIds: number[];
}

export interface Organization {
  id: number;
  name: string;
  createdAt?: string;
}

export interface Me {
  username: string;
  permissions: string[];
  organizations: Organization[];
}

export interface AuditLog {
  id: number;
  actorUsername: string;
  action: string;
  targetType: string;
  targetId: string;
  correlationId: string | null;
  details: Json | null;
  createdAt: string;
}

/** Everything the UI needs from a backend. Implemented by the real HTTP client and the demo API. */
export interface Api {
  login(username: string, password: string): Promise<string>;
  register(email: string, username: string, password: string): Promise<void>;
  health(): Promise<boolean>;
  me(): Promise<Me>;

  listDataSources(org: number, p?: PageRequest): Promise<Page<DataSource>>;
  createDataSource(org: number, body: DataSourceInput): Promise<DataSource>;
  updateDataSource(org: number, id: number, body: Partial<DataSourceInput>): Promise<DataSource>;
  testDataSource(org: number, id: number): Promise<ConnectionTest>;

  listQueryTemplates(org: number, p?: PageRequest): Promise<Page<QueryTemplate>>;
  createQueryTemplate(org: number, body: QueryTemplateInput): Promise<QueryTemplate>;
  updateQueryTemplate(org: number, id: number, body: Partial<QueryTemplateInput> & { active?: boolean }): Promise<QueryTemplate>;

  validateQuery(org: number, queryTemplateId: number, params: Json): Promise<ValidateResult>;
  executeQuery(org: number, queryTemplateId: number, params: Json, snapshot: boolean): Promise<ExecuteResult>;
  listRuns(org: number, p?: PageRequest): Promise<Page<QueryRun>>;

  listDatasets(org: number, p?: PageRequest): Promise<Page<Dataset>>;
  createDataset(org: number, body: DatasetInput): Promise<Dataset>;
  updateDataset(org: number, id: number, body: Partial<DatasetInput>): Promise<Dataset>;
  deleteDataset(org: number, id: number): Promise<void>;
  listSnapshots(org: number, datasetId: number, p?: PageRequest): Promise<Page<Snapshot>>;
  createSnapshot(org: number, datasetId: number, params?: Json): Promise<ExecuteResult>;

  listCharts(org: number, p?: PageRequest): Promise<Page<Chart>>;
  getChart(org: number, id: number): Promise<Chart>;
  createChart(org: number, body: ChartInput): Promise<Chart>;
  updateChart(org: number, id: number, body: Partial<ChartInput>): Promise<Chart>;
  deleteChart(org: number, id: number): Promise<void>;
  renderChart(org: number, id: number, snapshotId: number): Promise<RenderedChart>;

  listDashboards(org: number, p?: PageRequest): Promise<Page<Dashboard>>;
  getDashboard(org: number, id: number): Promise<Dashboard>;
  createDashboard(org: number, body: DashboardInput): Promise<Dashboard>;
  updateDashboard(org: number, id: number, body: Partial<DashboardInput>): Promise<Dashboard>;
  deleteDashboard(org: number, id: number): Promise<void>;

  listPermissions(): Promise<Permission[]>;
  listRoles(): Promise<Role[]>;
  createRole(name: string, description: string): Promise<Role>;
  replaceRolePermissions(id: number, codes: string[]): Promise<Role>;
  listUsers(query?: string): Promise<UserRoles[]>;
  replaceUserRoles(id: number, roleNames: string[]): Promise<UserRoles>;
  listAuditLogs(p?: PageRequest): Promise<Page<AuditLog>>;
  listOrganizations(): Promise<Organization[]>;
  createOrganization(name: string): Promise<Organization>;
  replaceUserOrganizations(id: number, organizationIds: number[]): Promise<UserRoles>;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}
