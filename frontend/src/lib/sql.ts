// Client-side SQL checks: a preview of the server's guardrails (InsightSqlValidator + SqlStatementGuard).
// The server always has the final say; the demo sandbox uses these as its "server".

const FORBIDDEN = /\b(insert|update|delete|merge|drop|alter|create|truncate|grant|revoke|exec|execute|call)\b/i;

export function guardSql(sql: string): string[] {
  const errors: string[] = [];
  const trimmed = sql.trim();
  if (!/^(select|with)\b/i.test(trimmed)) errors.push('Only SELECT statements are allowed');
  if (trimmed.includes(';')) errors.push("';' is not allowed (single statement only)");
  if (FORBIDDEN.test(trimmed)) errors.push('DDL/DML keywords are not allowed');
  if (/\$\{|\{\{/.test(trimmed)) errors.push('Dynamic identifier templates are not allowed');
  // Structural checks done server-side by SqlStatementGuard (JSqlParser).
  if (!errors.length && /^select\b[\s\S]*\binto\b/i.test(trimmed) && !/\binsert\s+into\b/i.test(trimmed)) errors.push('SELECT ... INTO is not allowed');
  if (!errors.length && /\bfor\s+(update|share|no\s+key\s+update|key\s+share)\b/i.test(trimmed)) errors.push('Locking clauses (FOR UPDATE/SHARE) are not allowed');
  return errors;
}

export function detectParams(sql: string): string[] {
  const found = new Set<string>();
  for (const m of sql.matchAll(/(?<!:):([a-zA-Z_][a-zA-Z0-9_]*)/g)) found.add(m[1]);
  return [...found];
}
