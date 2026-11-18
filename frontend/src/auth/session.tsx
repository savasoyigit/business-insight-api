import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import type { Api } from '../api/types';
import { HttpApi } from '../api/http';
import { DemoApi } from '@demo';

export type Mode = 'live' | 'demo';
/** Build-time constant from vite.config.ts: is the demo sandbox in this bundle? */
export const DEMO_AVAILABLE = import.meta.env.VITE_DEMO_AVAILABLE === 'true';
export const DEMO_ONLY = DEMO_AVAILABLE && import.meta.env.VITE_DEMO_ONLY === 'true';

interface StoredSession {
  mode: Mode;
  token: string;
  orgId: number;
}

interface SessionValue {
  api: Api;
  mode: Mode;
  token: string | null;
  username: string | null;
  perms: Set<string>;
  /** Organizations the caller is a member of (JWT `orgs` claim). */
  orgIds: number[];
  /** Currently selected organization; 0 when the user has none. */
  orgId: number;
  hasOrg: boolean;
  can: (perm: string) => boolean;
  setOrgId: (id: number) => void;
  signIn: (mode: Mode, username: string, password: string) => Promise<void>;
  signOut: () => void;
  resetDemo: () => void;
}

const KEY = 'insight-studio.session';
const Ctx = createContext<SessionValue | null>(null);

function readStored(): StoredSession | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as StoredSession) : null;
  } catch { return null; }
}

function writeStored(s: StoredSession | null) {
  try {
    if (s) localStorage.setItem(KEY, JSON.stringify(s));
    else localStorage.removeItem(KEY);
  } catch { /* storage unavailable: session lives in memory only */ }
}

export function decodeJwt(token: string): { sub?: string; perms?: string[]; orgs?: number[]; exp?: number } {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '=')));
  } catch { return {}; }
}

function validStored(): StoredSession | null {
  const s = readStored();
  if (!s) return null;
  if (DEMO_ONLY && s.mode !== 'demo') return null;
  if (!DEMO_AVAILABLE && s.mode === 'demo') return null;
  const { exp, orgs } = decodeJwt(s.token);
  // Tokens issued before organization membership existed carry no `orgs` claim: sign in again.
  if (!Array.isArray(orgs)) return null;
  return exp && exp * 1000 < Date.now() ? null : s;
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<StoredSession | null>(validStored);
  const [demoEpoch, setDemoEpoch] = useState(0);

  const signOut = useCallback(() => {
    writeStored(null);
    setSession(null);
  }, []);

  const tokenRef = useMemo(() => ({ current: session?.token ?? null }), [session]);

  const claims = useMemo(() => (session ? decodeJwt(session.token) : {}), [session]);

  const api = useMemo<Api>(() => {
    if ((session?.mode ?? (DEMO_ONLY ? 'demo' : 'live')) === 'demo') {
      const demo = new DemoApi();
      if (claims.sub) demo.setUser(claims.sub);
      return demo;
    }
    return new HttpApi(() => tokenRef.current, signOut);
    // demoEpoch forces a fresh DemoApi after a reset
  }, [session?.mode, claims.sub, tokenRef, signOut, demoEpoch]);

  const value = useMemo<SessionValue>(() => {
    const perms = new Set(claims.perms ?? []);
    const orgIds = (claims.orgs ?? []).map(Number);
    const orgId = session && orgIds.includes(session.orgId) ? session.orgId : orgIds[0] ?? 0;
    return {
      api,
      mode: session?.mode ?? (DEMO_ONLY ? 'demo' : 'live'),
      token: session?.token ?? null,
      username: claims.sub ?? null,
      perms,
      orgIds,
      orgId,
      hasOrg: orgId > 0,
      can: (p) => perms.has(p),
      setOrgId: (orgId) => {
        if (!session) return;
        const next = { ...session, orgId };
        writeStored(next);
        setSession(next);
      },
      signIn: async (mode, username, password) => {
        const client: Api = mode === 'demo' ? new DemoApi() : new HttpApi(() => null, () => {});
        const token = await client.login(username, password);
        const orgs = (decodeJwt(token).orgs ?? []).map(Number);
        const keep = session && orgs.includes(session.orgId) ? session.orgId : undefined;
        const next = { mode, token, orgId: keep ?? orgs[0] ?? 0 };
        writeStored(next);
        setSession(next);
      },
      signOut,
      resetDemo: () => {
        DemoApi.reset();
        setDemoEpoch((e) => e + 1);
      },
    };
  }, [api, session, claims, signOut]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useSession(): SessionValue {
  const v = useContext(Ctx);
  if (!v) throw new Error('useSession outside SessionProvider');
  return v;
}
