import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import type { Api } from '../api/types';
import { HttpApi } from '../api/http';
import { DemoApi, DEMO_ORG } from '../api/demo';

export type Mode = 'live' | 'demo';
export const DEMO_ONLY = import.meta.env.VITE_DEMO_ONLY === 'true';

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
  orgId: number;
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

export function decodeJwt(token: string): { sub?: string; perms?: string[]; exp?: number } {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '=')));
  } catch { return {}; }
}

function validStored(): StoredSession | null {
  const s = readStored();
  if (!s) return null;
  if (DEMO_ONLY && s.mode !== 'demo') return null;
  const { exp } = decodeJwt(s.token);
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
    return {
      api,
      mode: session?.mode ?? (DEMO_ONLY ? 'demo' : 'live'),
      token: session?.token ?? null,
      username: claims.sub ?? null,
      perms,
      orgId: session?.orgId ?? 1,
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
        const next = { mode, token, orgId: mode === 'demo' ? DEMO_ORG : session?.orgId ?? 1 };
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
