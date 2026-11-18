# Insight Studio

React + TypeScript frontend for the **Business Insight API**. It covers the whole BI pipeline:

`DataSource → QueryTemplate → Dataset → Snapshot → Chart → Dashboard`

plus query-run history and RBAC administration (roles, permissions, user roles, audit log).

## Highlights

- **Permission-aware UI** – permissions are read from the JWT `perms` claim; navigation, routes and actions hide what the current role cannot do.
- **Query workbench** – highlighted SQL with `:named` params, a live guardrail preview, typed parameter inputs, validate and execute.
- **Chart builder with live preview** – field pickers come from the latest snapshot; the preview uses the same aggregation as `ChartService.render` (sum / avg / count, optional series).
- **Dashboards** – widgets on a 12-column grid driven by the stored `{x, y, w, h}` layouts, with a simple layout editor.
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
The organization id sent as `?orgId=` can be switched in the sidebar.

If the frontend is served from another origin, set `VITE_API_BASE_URL` (see `.env.example`) and allow that origin on the API
with `CORS_ALLOWED_ORIGINS`.

## Static showcase (no backend)

```bash
npm run build:demo     # dist/ only offers the demo sandbox
```

`dist/` uses relative paths and hash routing, so it can go straight to GitHub Pages, Netlify, Vercel or any static host.

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
