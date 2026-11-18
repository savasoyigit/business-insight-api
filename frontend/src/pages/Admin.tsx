import { useEffect, useState } from 'react';
import type { Role } from '../api/types';
import { useSession } from '../auth/session';
import { useAsync } from '../lib/hooks';
import { fmtDate } from '../lib/format';
import { Badge, ErrorNote, Field, Modal, PageHeader, Pager, Spinner, useToast } from '../components/ui';

type Tab = 'roles' | 'users' | 'audit';

export function Admin() {
  const { can } = useSession();
  const tabs: { id: Tab; label: string; perm: string }[] = [
    { id: 'roles', label: 'Roles & permissions', perm: 'RBAC_ROLE_MANAGE' },
    { id: 'users', label: 'Users', perm: 'RBAC_USER_ROLE_ASSIGN' },
    { id: 'audit', label: 'Audit log', perm: 'RBAC_ROLE_MANAGE' },
  ];
  const visible = tabs.filter((t) => can(t.perm));
  const [tab, setTab] = useState<Tab>(visible[0]?.id ?? 'roles');

  return (
    <>
      <PageHeader eyebrow="Operations" title="Access control">
        Role-based access control. Permissions are baked into the JWT; every change lands in the audit trail.
      </PageHeader>
      <div className="seg tabs">
        {visible.map((t) => <button key={t.id} className={tab === t.id ? 'on' : ''} onClick={() => setTab(t.id)}>{t.label}</button>)}
      </div>
      {tab === 'roles' && <RolesTab />}
      {tab === 'users' && <UsersTab />}
      {tab === 'audit' && <AuditTab />}
    </>
  );
}

function groupOf(code: string) {
  if (code.startsWith('RBAC_')) return 'RBAC';
  return code.replace(/^INSIGHT_/, '').split('_')[0];
}

const GROUP_LABEL: Record<string, string> = { DS: 'Data sources', QT: 'Query templates', QUERY: 'Execution', DATASET: 'Datasets', CHART: 'Charts', DASHBOARD: 'Dashboards', RBAC: 'Admin' };

function RolesTab() {
  const { api, can } = useSession();
  const toast = useToast();
  const roles = useAsync(() => api.listRoles(), [api]);
  const perms = useAsync(async () => (can('RBAC_PERMISSION_MANAGE') ? api.listPermissions() : []), [api]);
  const [selected, setSelected] = useState<number | null>(null);
  const [draft, setDraft] = useState<Set<string>>(new Set());
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);

  const role = roles.data?.find((r) => r.id === selected) ?? null;
  useEffect(() => {
    if (!selected && roles.data?.[0]) setSelected(roles.data[0].id);
  }, [roles.data, selected]);
  useEffect(() => { setDraft(new Set(role?.permissionCodes ?? [])); }, [role]);

  const allCodes = perms.data?.length ? perms.data.map((p) => p.code) : [...new Set(roles.data?.flatMap((r) => r.permissionCodes) ?? [])];
  const groups = [...new Set(allCodes.map(groupOf))];
  const dirty = role && (draft.size !== role.permissionCodes.length || role.permissionCodes.some((c) => !draft.has(c)));

  async function save() {
    if (!role) return;
    setBusy(true);
    try {
      await api.replaceRolePermissions(role.id, [...draft]);
      toast('ok', `${role.name} updated — users get it on next login`);
      roles.reload();
    } catch (e) { toast('bad', (e as Error).message); } finally { setBusy(false); }
  }

  if (roles.loading && !roles.data) return <Spinner />;
  if (roles.error) return <ErrorNote message={roles.error} onRetry={roles.reload} />;

  return (
    <div className="split">
      <div className="split-list">
        {roles.data?.map((r: Role) => (
          <button key={r.id} className={`list-item ${r.id === selected ? 'on' : ''}`} onClick={() => setSelected(r.id)}>
            <span className="list-item-title">{r.name}</span>
            <span className="muted small">{r.permissionCodes.length} permissions</span>
          </button>
        ))}
        <button className="btn btn-ghost btn-block" onClick={() => setCreating(true)}>+ New role</button>
      </div>
      {role && (
        <section className="card split-detail">
          <div className="card-head">
            <div><h2>{role.name}</h2><span className="muted small">{role.description}</span></div>
            <button className="btn btn-primary btn-sm" disabled={!dirty || busy} onClick={save}>{busy ? 'Saving…' : 'Save permissions'}</button>
          </div>
          <div className="perm-matrix">
            {groups.map((g) => (
              <div key={g} className="perm-group">
                <h4>{GROUP_LABEL[g] ?? g}</h4>
                {allCodes.filter((c) => groupOf(c) === g).map((code) => (
                  <label key={code} className="check perm">
                    <input type="checkbox" checked={draft.has(code)} onChange={(e) => setDraft((d) => { const n = new Set(d); if (e.target.checked) n.add(code); else n.delete(code); return n; })} />
                    <span className="mono small">{code.replace(/^INSIGHT_|^RBAC_/, '').replace(/^(DS|QT|DATASET|CHART|DASHBOARD)_/, '')}</span>
                  </label>
                ))}
              </div>
            ))}
          </div>
        </section>
      )}
      {creating && <NewRole onClose={() => setCreating(false)} onSaved={(id) => { setCreating(false); setSelected(id); roles.reload(); }} />}
    </div>
  );
}

function NewRole({ onClose, onSaved }: { onClose: () => void; onSaved: (id: number) => void }) {
  const { api } = useSession();
  const toast = useToast();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  async function save() {
    try { const r = await api.createRole(name, description); toast('ok', `Role ${r.name} created`); onSaved(r.id); }
    catch (e) { setError((e as Error).message); }
  }
  return (
    <Modal title="New role" onClose={onClose}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Cancel</button><button className="btn btn-primary" onClick={save}>Create</button></>}>
      <Field label="Name"><input value={name} onChange={(e) => setName(e.target.value)} placeholder="REPORT_VIEWER" /></Field>
      <Field label="Description"><input value={description} onChange={(e) => setDescription(e.target.value)} /></Field>
      {error && <div className="note note-error">{error}</div>}
    </Modal>
  );
}

function UsersTab() {
  const { api } = useSession();
  const toast = useToast();
  const [query, setQuery] = useState('');
  const users = useAsync(() => api.listUsers(query || undefined), [api, query]);
  const roles = useAsync(() => api.listRoles().catch(() => [] as Role[]), [api]);

  async function toggle(userId: number, current: string[], role: string) {
    const next = current.includes(role) ? current.filter((r) => r !== role) : [...current, role];
    if (!next.length) return toast('bad', 'A user needs at least one role');
    try { await api.replaceUserRoles(userId, next); toast('ok', 'Roles updated'); users.reload(); }
    catch (e) { toast('bad', (e as Error).message); }
  }

  return (
    <div className="card table-card">
      <div className="table-toolbar"><input placeholder="Search users…" value={query} onChange={(e) => setQuery(e.target.value)} /></div>
      {users.error && <ErrorNote message={users.error} onRetry={users.reload} />}
      <div className="table-scroll">
        <table className="table">
          <thead><tr><th>User</th><th>Email</th><th>Roles</th></tr></thead>
          <tbody>
            {users.data?.map((u) => (
              <tr key={u.id}>
                <td><b>{u.username}</b> <span className="mono muted small">#{u.id}</span></td>
                <td className="muted">{u.email}</td>
                <td>
                  <div className="chip-row">
                    {(roles.data ?? []).map((r) => (
                      <button key={r.id} className={`chip chip-btn ${u.roleNames.includes(r.name) ? 'chip-on' : ''}`} onClick={() => toggle(u.id, u.roleNames, r.name)}>
                        {r.name}
                      </button>
                    ))}
                    {!roles.data?.length && u.roleNames.map((r) => <Badge key={r}>{r}</Badge>)}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function AuditTab() {
  const { api } = useSession();
  const [page, setPage] = useState(0);
  const logs = useAsync(() => api.listAuditLogs({ page, size: 15, sort: 'id,desc' }), [api, page]);
  if (logs.loading && !logs.data) return <Spinner />;
  if (logs.error) return <ErrorNote message={logs.error} onRetry={logs.reload} />;
  return (
    <div className="card">
      {logs.data?.content.length === 0 && <p className="muted">No audit entries yet.</p>}
      <ol className="timeline">
        {logs.data?.content.map((a) => (
          <li key={a.id}>
            <div className="timeline-head">
              <Badge tone="accent">{a.action}</Badge>
              <span><b>{a.actorUsername}</b> → {a.targetType.toLowerCase()} <span className="mono">#{a.targetId}</span></span>
              <span className="grow" />
              <span className="muted small">{fmtDate(a.createdAt)}</span>
            </div>
            {a.details && <pre className="mono small details">{JSON.stringify(a.details)}</pre>}
            {a.correlationId && <span className="mono muted small">cid {a.correlationId}</span>}
          </li>
        ))}
      </ol>
      {logs.data && <Pager page={logs.data} onPage={setPage} />}
    </div>
  );
}
