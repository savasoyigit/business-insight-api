# Business Insight API

Spring Boot backend for external datasource routing, SQL-based insight execution, and BI asset management.

It supports this flow:

`DataSource -> QueryTemplate -> Dataset -> Snapshot -> Chart -> Dashboard`

## Stack

- Java 17
- Spring Boot
- Spring Boot Actuator (Micrometer metrics)
- Spring Security with JWT
- PostgreSQL
- Flyway
- Maven

## Project Structure

- `api`: REST controllers and DTOs
- `application`: business services and orchestration
- `domain`: JPA entities and repositories
- `infrastructure`: security, bootstrap, config
- `common`: shared error handling

## Core Concepts

### DataSource

Represents an external database connection definition.

Example:

```json
{
  "name": "Reporting Postgres",
  "type": "POSTGRES",
  "host": "localhost",
  "port": 5432,
  "databaseName": "erp_reporting",
  "username": "readonly_user",
  "secretRef": "env:ERP_REPORTING_PASSWORD",
  "options": {
    "ssl": false
  }
}
```

Security notes:

- Passwords are not stored in plaintext in the app database.
- `secretRef` currently supports `env:VAR_NAME`.
- Example: `env:ERP_REPORTING_PASSWORD`.
- **Multi-database support**: Postgres and MSSQL connectors with pluggable SQL dialect architecture.

**Supported Database Types:**
- `POSTGRES` - PostgreSQL databases
- `MSSQL` - Microsoft SQL Server databases

**Testing Connections:**

Use the connection test endpoint to verify datasource connectivity:

```http
POST /api/insight/data-sources/{id}/test?orgId={orgId}
```

Response:
```json
{
  "ok": true,
  "latencyMs": 45,
  "error": null
}
```

### QueryTemplate

Parameterized, guarded SQL definition bound to a datasource.

Guardrails:

- SELECT-only (dialect-aware validation)
- single statement only
- `;` rejected
- DDL/DML keywords rejected
- named parameters supported via `:paramName`
- unexpected params rejected
- default row cap: `5000` (applied via `LIMIT` or `TOP` depending on database type)
- snapshot row cap: `500`
- JDBC timeout: `30s`

Example:

```json
{
  "dataSourceId": 1,
  "name": "Current Date Validation",
  "description": "Execution routing smoke test",
  "sqlText": "select current_date as run_date where current_date >= :startDate",
  "parameters": [
    { "name": "startDate", "type": "date", "required": true }
  ],
  "outputSchema": [
    { "name": "run_date", "type": "date" }
  ]
}
```

### Dataset

Represents a reusable BI dataset built on top of a query template.

Contains:

- `queryTemplateId`
- `defaultParams`
- `createdBy`

Example:

```json
{
  "name": "Sales Daily",
  "queryTemplateId": 1,
  "defaultParams": {
    "startDate": "2026-01-01"
  }
}
```

### Snapshot

Materialized dataset result stored in `dataset_snapshot`.

- linked to `query_run`
- optionally linked to `dataset`
- stored as JSON for MVP

### Chart

Chart metadata bound to a dataset.

Supported chart types:

- `BAR`
- `LINE`
- `PIE`

Config example:

```json
{
  "xField": "run_date",
  "yField": "amount",
  "seriesField": "region",
  "aggregation": "sum"
}
```

Render endpoint reads a snapshot and returns chart-ready points.

### Dashboard

Dashboard contains widgets referencing charts plus a JSON layout.

Layout example:

```json
{
  "x": 0,
  "y": 0,
  "w": 6,
  "h": 4
}
```

## Security

All endpoints except `/api/auth/**` require authentication.

Method security is enabled with `@EnableMethodSecurity`.

Main permission groups:

- datasource: `INSIGHT_DS_*`
- query template: `INSIGHT_QT_*`
- query execution: `INSIGHT_QUERY_EXECUTE`
- dataset: `INSIGHT_DATASET_*`
- chart: `INSIGHT_CHART_*`
- dashboard: `INSIGHT_DASHBOARD_*`
- admin RBAC: `RBAC_*`

Phase 5 hardening:

- login rate limiting (per user and IP window)
- account lockout after repeated failed login attempts
- password policy enforcement on registration
- RBAC admin audit logs for role and assignment changes
- SQL safety checks block dynamic identifier templates (for example `${...}` and `{{...}}`)

## Observability

- Correlation ID support via `X-Correlation-Id` request/response header
- Log correlation with `correlationId` in log context
- Query execution metrics and timings via Micrometer
- Actuator endpoints exposed:
  - `/actuator/health`
  - `/actuator/info`
  - `/actuator/metrics`
  - `/actuator/prometheus`

## Main Endpoints

### DataSource

- `GET /api/insight/data-sources`
- `GET /api/insight/data-sources/{id}`
- `POST /api/insight/data-sources`
- `PUT /api/insight/data-sources/{id}`
- `POST /api/insight/data-sources/{id}/test` - Test database connectivity

### Query Execution

- `POST /api/insight/query-execution/validate`
- `POST /api/insight/query-execution/execute`
- `GET /api/insight/query-execution/runs` (paged)

### Dataset

- `GET /api/insight/datasets`
- `GET /api/insight/datasets/{id}`
- `POST /api/insight/datasets`
- `PUT /api/insight/datasets/{id}`
- `DELETE /api/insight/datasets/{id}`
- `GET /api/insight/datasets/{id}/snapshots` (paged)
- `POST /api/insight/datasets/{id}/snapshots`

### RBAC Admin

- `GET /api/admin/rbac/permissions`
- `GET /api/admin/rbac/roles`
- `POST /api/admin/rbac/roles`
- `PUT /api/admin/rbac/roles/{id}`
- `PUT /api/admin/rbac/roles/{id}/permissions`
- `GET /api/admin/rbac/users`
- `PUT /api/admin/rbac/users/{id}/roles`
- `GET /api/admin/rbac/audit-logs` (paged)

### Chart

- `GET /api/insight/charts`
- `GET /api/insight/charts/{id}`
- `POST /api/insight/charts`
- `PUT /api/insight/charts/{id}`
- `DELETE /api/insight/charts/{id}`
- `GET /api/insight/charts/{id}/render?snapshotId=...`

### Dashboard

- `GET /api/insight/dashboards`
- `GET /api/insight/dashboards/{id}`
- `POST /api/insight/dashboards`
- `PUT /api/insight/dashboards/{id}`
- `DELETE /api/insight/dashboards/{id}`

## Running Locally

### 1. Start PostgreSQL

```bash
docker compose up -d
```

### 2. Set required environment variables

Windows PowerShell:

```powershell
$env:JWT_SECRET="your_long_random_secret_here"
$env:ERP_REPORTING_PASSWORD="your_readonly_password"
```

Linux/macOS:

```bash
export JWT_SECRET=your_long_random_secret_here
export ERP_REPORTING_PASSWORD=your_readonly_password
```

### 3. Run the application

```bash
./mvnw spring-boot:run
```

Base URL:

```text
http://localhost:8080
```

## Postman Collection

Collection path:

`postman/business-insight-api.postman_collection.json`

Current folders:

- `Auth`
- `Insight / Execution`
- `Insight / BI Assets`
- `RBAC Admin`

Recommended execution order in Postman:

1. Login
2. Create Data Source
3. Create Query Template
4. Validate Query Execution
5. Create Dataset
6. Create Dataset Snapshot
7. List Query Runs (paged)
8. Create Chart
9. Render Chart
10. Create Dashboard
11. RBAC audit logs (admin)

## Example BI Flow

1. Data engineer creates a datasource.
2. Data engineer creates a query template.
3. Product or analytics user creates a dataset on top of that template.
4. Dataset snapshot is generated.
5. Chart is created against the dataset.
6. Dashboard is created with one or more chart widgets.

## Build Check

Compile locally with:

```bash
./mvnw -q -DskipTests compile
```
