import { useEffect, useState, type FormEvent } from 'react';
import { DEMO_AVAILABLE, DEMO_ONLY, useSession, type Mode } from '../auth/session';
import { DEMO_USERS } from '@demo';
import { HttpApi } from '../api/http';

const FLOW = ['DataSource', 'QueryTemplate', 'Dataset', 'Snapshot', 'Chart', 'Dashboard'];

export function Login() {
  const { signIn } = useSession();
  const [tab, setTab] = useState<Mode>(DEMO_ONLY ? 'demo' : 'live');
  const [register, setRegister] = useState(false);
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [apiUp, setApiUp] = useState<boolean | null>(null);

  useEffect(() => {
    if (DEMO_ONLY) return;
    new HttpApi(() => null, () => {}).health().then(setApiUp);
  }, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null); setInfo(null); setBusy('live');
    try {
      if (register) {
        await new HttpApi(() => null, () => {}).register(email, username, password);
        setInfo('Account created. New users start without roles — ask an admin to assign one, then sign in.');
        setRegister(false);
      } else {
        await signIn('live', username, password);
      }
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(null);
    }
  }

  async function demo(user: string) {
    setError(null); setBusy(user);
    try { await signIn('demo', user, ''); } catch (err) { setError((err as Error).message); setBusy(null); }
  }

  return (
    <div className="login">
      <section className="login-hero">
        <div className="brand brand-lg">
          <span className="brand-mark" aria-hidden><i /><i /><i /></span>
          <div className="brand-name">Insight Studio</div>
        </div>
        <h1>From raw SQL to boardroom dashboards — with guardrails.</h1>
        <p className="lede">
          A BI workspace on top of the <span className="mono">business-insight-api</span> Spring Boot backend:
          multi-database routing, SELECT-only query templates, snapshots, charts and permission-aware dashboards.
        </p>
        <ol className="flow" aria-label="Pipeline">
          {FLOW.map((step, i) => (
            <li key={step} style={{ animationDelay: `${i * 90}ms` }}>
              <span className="flow-n mono">{String(i + 1).padStart(2, '0')}</span>
              <span>{step}</span>
            </li>
          ))}
        </ol>
        <ul className="hero-facts">
          <li><b>JWT + RBAC</b><span>26 fine-grained permissions</span></li>
          <li><b>Postgres · MSSQL</b><span>pluggable SQL dialects</span></li>
          <li><b>Audit trail</b><span>every role change logged</span></li>
        </ul>
      </section>

      <section className="login-panel">
        {DEMO_AVAILABLE && !DEMO_ONLY && (
          <div className="seg" role="tablist">
            <button role="tab" aria-selected={tab === 'live'} className={tab === 'live' ? 'on' : ''} onClick={() => setTab('live')}>Live API</button>
            <button role="tab" aria-selected={tab === 'demo'} className={tab === 'demo' ? 'on' : ''} onClick={() => setTab('demo')}>Demo sandbox</button>
          </div>
        )}

        {tab === 'live' || !DEMO_AVAILABLE ? (
          <form onSubmit={submit} className="login-form">
            <h2>{register ? 'Create an account' : 'Sign in'}</h2>
            <p className="muted small api-status">
              <span className={`dot ${apiUp ? 'dot-ok' : apiUp === false ? 'dot-bad' : ''}`} />
              {apiUp == null ? 'Checking API…' : apiUp ? 'API reachable' : 'API not reachable — start the backend or try the demo'}
            </p>
            {register && (
              <label className="field"><span className="field-label">Email</span>
                <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
              </label>
            )}
            <label className="field"><span className="field-label">Username</span>
              <input required value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" autoFocus />
            </label>
            <label className="field"><span className="field-label">Password</span>
              <input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} autoComplete={register ? 'new-password' : 'current-password'} />
              {register && <span className="field-hint">At least 12 characters (server-side password policy).</span>}
            </label>
            {error && <div className="note note-error">{error}</div>}
            {info && <div className="note note-ok">{info}</div>}
            <button className="btn btn-primary btn-block" disabled={!!busy}>{busy ? 'Working…' : register ? 'Register' : 'Sign in'}</button>
            <button type="button" className="link-btn" onClick={() => { setRegister((r) => !r); setError(null); }}>
              {register ? 'Have an account? Sign in' : 'No account? Register'}
            </button>
          </form>
        ) : (
          <div className="login-form">
            <h2>Explore without a backend</h2>
            <p className="muted">
              Runs a faithful in-browser mock of the API with sample data from a fictional coffee roastery.
              Pick a persona — each carries different permissions, so the UI changes with the role.
            </p>
            <div className="personas">
              {DEMO_USERS.map((u) => (
                <button key={u.username} className="persona" onClick={() => demo(u.username)} disabled={!!busy}>
                  <span className="avatar">{u.username[0].toUpperCase()}</span>
                  <span className="persona-text">
                    <b>{u.username} <span className="badge badge-neutral">{u.role}</span></b>
                    <span className="muted small">{u.blurb}</span>
                  </span>
                  <span className="persona-go">{busy === u.username ? '…' : '→'}</span>
                </button>
              ))}
            </div>
            {error && <div className="note note-error">{error}</div>}
          </div>
        )}
      </section>
    </div>
  );
}
