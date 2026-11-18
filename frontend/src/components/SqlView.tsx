const KW = /^(select|from|where|and|or|not|in|is|null|as|on|join|left|right|inner|outer|full|group|by|order|having|limit|top|offset|with|case|when|then|else|end|distinct|union|all|asc|desc|between|like|interval|current_date|count|sum|avg|min|max|to_char)$/i;

/** Tiny SQL highlighter: keywords, strings, numbers and :named params. */
export function SqlView({ sql }: { sql: string }) {
  const tokens = sql.split(/('(?:[^']|'')*'|(?<!:):[a-zA-Z_][a-zA-Z0-9_]*|\b\d+(?:\.\d+)?\b|\b[a-zA-Z_]+\b)/g);
  return (
    <pre className="sql">
      <code>
        {tokens.map((t, i) => {
          if (!t) return null;
          if (t.startsWith("'")) return <span key={i} className="tk-str">{t}</span>;
          if (/^:[a-zA-Z_]/.test(t)) return <span key={i} className="tk-param">{t}</span>;
          if (/^\d/.test(t)) return <span key={i} className="tk-num">{t}</span>;
          if (KW.test(t)) return <span key={i} className="tk-kw">{t}</span>;
          return t;
        })}
      </code>
    </pre>
  );
}
