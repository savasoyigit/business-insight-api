# Insight Studio

React + TypeScript frontend for the **Business Insight API**. It covers the whole BI pipeline:

`DataSource → QueryTemplate → Dataset → Snapshot → Chart → Dashboard`

plus query-run history and RBAC administration (roles, permissions, user roles, audit log).

## Highlights

- **Permission-aware UI** – permissions are read from the JWT `perms` claim; navigation, routes and actions hide what the current role cannot do.
- **Query workbench** – highlighted SQL with `:named` params, a live guardrail preview, typed parameter inputs, validate and execute.
- **Chart builder with live preview** – field pickers come from the latest snapshot; the preview uses the same aggregation as `ChartService.render` (sum / avg / count, optional series).
- **Dashboards** – widgets on a 12-column grid driven by the stored `{x, y, w, h}` layouts, with a simple layout editor.
- **Async runs and schedules** – Execute and Take snapshot queue a run and follow it live (queued → running → done);
  datasets can refresh on a schedule, and the Query runs page updates by itself.
- **Organizations and secrets** – an organization picker, membership admin, and data source passwords stored encrypted
  or referenced from the server environment.
- **Demo sandbox** – an in-browser mock of the API (seeded with a fictional coffee roastery) so the app runs with no backend. Pick `admin`, `analyst` or `viewer` to see RBAC change the UI.
- Light and dark themes (follows the OS), responsive down to phone width.

## Run against the API

```bash
# 1. start the backend (see the root README): http://localhost:8080
# 2. start the frontend
cd frontend
npm install
npm run dev            # http://localhost:5173 (/api is proxied to :8080)
```

Sign in with the seeded admin (`ADMIN_USERNAME` / `ADMIN_PASSWORD`, defaults `admin` / `ChangeMe_DevOnly_123456!`).
The sidebar lists the organizations you are a member of (from the JWT `orgs` claim, with names from `GET /api/me`),
and the selected one is sent as `?orgId=`. Admins manage organizations and memberships under Access control.

If the frontend is served from another origin, set `VITE_API_BASE_URL` (see `.env.example`) and allow that origin on the API
with `CORS_ALLOWED_ORIGINS`.

## Static showcase (no backend)

```bash
npm run build:demo     # dist/ only offers the demo sandbox
```

`dist/` uses relative paths and hash routing, so it can go straight to GitHub Pages, Netlify, Vercel or any static host.

What each build contains:

| Command | Live API | Demo sandbox |
| --- | --- | --- |
| `npm run dev` | ✓ | ✓ |
| `npm run build` | ✓ | – (set `VITE_ENABLE_DEMO=true` to include it) |
| `npm run build:demo` | – | ✓ only |

The production build replaces the demo module with a stub at build time, so the mock API and its sample data are
not shipped. Fonts are self-hosted (`@fontsource`), so the app makes no requests to third-party hosts.

The `Deploy demo to GitHub Pages` workflow (`.github/workflows/deploy-demo.yml`) does this automatically on every push to
`master` that touches `frontend/`, and can also be run by hand from the Actions tab.

## Structure

```
src/
  api/        types.ts (DTO mirrors + Api interface), http.ts (real client), demo.ts (in-browser mock)
  auth/       session.tsx (JWT, permissions, org, live/demo mode)
  components/ Layout, ChartView (Recharts), LiveChart, SqlView, UI primitives
  lib/        render.ts (port of chart aggregation), hooks, formatting
  pages/      Login, Overview, DataSources, QueryTemplates, Datasets, Charts, Dashboards, QueryRuns, Admin
```
