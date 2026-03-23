# Business Insight API

Backend service for reporting, external datasource routing, and insight query execution.

## Tech Stack

- Java 17
- Spring Boot
- Spring Security with JWT
- PostgreSQL
- Flyway
- Maven

## Architecture

- `api`: REST controllers and request/response DTOs
- `application`: business services and execution orchestration
- `domain`: JPA entities and repositories
- `infrastructure`: security and configuration
- `common`: shared error handling

## Insight Execution

`QueryTemplate` execution is routed to the external datasource attached to the template.

Datasource model:

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

Security note:

- Passwords are not stored in the app database.
- `secretRef` must currently use the `env:VAR_NAME` format.
- Example: `env:ERP_REPORTING_PASSWORD` resolves from the process environment.
- Plaintext secrets in the datasource record are intentionally not supported in this phase.

Query execution guardrails:

- SELECT-only
- single statement only
- rejects DDL/DML keywords and `;`
- named parameters supported via `:paramName`
- unexpected params rejected
- default row cap: `5000`
- snapshot row cap: `500`
- JDBC timeout: `30s`

Validation endpoint:

- `POST /api/insight/query-execution/validate`
- Returns `valid`, `errors`, `detectedParams`, `allowedParams`

Execution endpoint:

- `POST /api/insight/query-execution/execute`
- Persists `query_run`
- Persists `dataset_snapshot` when `snapshot=true`

## Local Run

### 1. Start PostgreSQL

```bash
docker compose up -d
```

### 2. Set required secrets

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

### 3. Run the app

```bash
./mvnw spring-boot:run
```

App base URL:

```text
http://localhost:8080
```

## Postman

The Postman collection contains an `Insight / Execution` folder for:

- creating a datasource
- creating a query template
- validating execution
- executing with snapshot

## RBAC

All endpoints except `/api/auth/**` require authentication.

Insight execution endpoint requires:

- `INSIGHT_QUERY_EXECUTE`
