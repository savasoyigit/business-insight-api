package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.domain.insight.datasource.DataSourceType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refuses calls to functions that reach outside the query's own data: server files, other servers, dynamic SQL,
 * server administration. A SELECT can call them, and the read-only transaction does not stop all of them.
 * The real protection is a database login without those rights; this is a second layer.
 *
 * <p>Function names are searched in the SQL with string literals and comments blanked out and quoted identifiers
 * unquoted, so a quoted name or a comment between name and parenthesis still matches, while a literal such as
 * {@code 'pg_sleep('} does not. Where the database's lexing rules depend on settings or are easy to get subtly wrong
 * (backslash escapes, nested comments, dollar quoting), every variant is scanned and any match refuses the query:
 * a wrong guess can then only refuse too much, never hide a call.
 */
@Component
public class SqlFunctionGuard {

    private static final Pattern POSTGRES = forbidden(
            // server files
            "pg_read_file", "pg_read_binary_file", "pg_ls_\\w+", "pg_stat_file", "pg_file_\\w+", "pg_logdir_ls",
            // large objects (lo_import / lo_export touch server files)
            "lo_\\w+",
            // other servers
            "dblink\\w*",
            // run SQL given as a string, past every check here
            "query_to_xml\\w*", "cursor_to_xml\\w*", "ts_stat",
            // server administration and side effects beyond the session
            // (pg_sleep and advisory locks stay allowed: statement_timeout bounds them, and each run's session ends)
            "pg_terminate_backend", "pg_cancel_backend", "pg_reload_conf", "pg_rotate_logfile", "pg_promote",
            "pg_switch_wal", "pg_create_restore_point", "pg_backup_start", "pg_backup_stop", "set_config", "pg_notify");

    private static final Pattern MSSQL = forbidden(
            // other servers and server files
            "openrowset", "opendatasource", "openquery",
            "xp_\\w+",
            "fn_trace_gettable", "fn_xe_file_target_read_file", "fn_get_audit_file", "fn_dblog", "fn_dump_dblog");

    private static Pattern forbidden(String... names) {
        // Not preceded by an identifier character, so my_lo_total( is fine; a schema prefix (pg_catalog.) is not.
        return Pattern.compile("(?<![A-Za-z0-9_$])(" + String.join("|", names) + ")\\s*\\(", Pattern.CASE_INSENSITIVE);
    }

    /** Returns an error message, or empty when no forbidden function is called. */
    public Optional<String> check(String sqlText, DataSourceType type) {
        if (sqlText == null) {
            return Optional.empty();
        }
        Pattern pattern = type == DataSourceType.MSSQL ? MSSQL : POSTGRES;
        for (LexRules rules : variants(type)) {
            Matcher matcher = pattern.matcher(code(sqlText, rules));
            if (matcher.find()) {
                return Optional.of("Function " + matcher.group(1).toLowerCase(Locale.ROOT) + "() is not allowed in query templates");
            }
        }
        return Optional.empty();
    }

    record LexRules(boolean backslashEscapes, boolean nestedComments, boolean dollarQuotes, boolean bracketIdentifiers) {}

    private static List<LexRules> variants(DataSourceType type) {
        boolean mssql = type == DataSourceType.MSSQL;
        List<LexRules> variants = new ArrayList<>();
        for (boolean backslash : new boolean[]{false, true}) {
            for (boolean nested : new boolean[]{true, false}) {
                // SQL Server has no dollar quoting; Postgres has it, but scan without it too.
                for (boolean dollar : mssql ? new boolean[]{false} : new boolean[]{true, false}) {
                    variants.add(new LexRules(backslash, nested, dollar, mssql));
                }
            }
        }
        return variants;
    }

    /**
     * The SQL as code only: string literals become {@code ''}, comments a space, quoted identifiers their bare name.
     * An unterminated literal or comment swallows the rest, as it would in the database (which then fails to parse).
     */
    static String code(String sql, LexRules rules) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            char next = i + 1 < n ? sql.charAt(i + 1) : '\0';
            if (c == '-' && next == '-') {
                int end = sql.indexOf('\n', i);
                i = end < 0 ? n : end;
                out.append(' ');
            } else if (c == '/' && next == '*') {
                i = skipBlockComment(sql, i, rules.nestedComments());
                out.append(' ');
            } else if (c == '\'') {
                i = skipQuoted(sql, i + 1, '\'', rules.backslashEscapes());
                out.append("''");
            } else if (c == '"') {
                int end = skipQuoted(sql, i + 1, '"', false);
                out.append(sql, i + 1, Math.max(i + 1, end - 1));
                i = end;
            } else if (c == '[' && rules.bracketIdentifiers()) {
                int end = skipQuoted(sql, i + 1, ']', false);
                out.append(sql, i + 1, Math.max(i + 1, end - 1));
                i = end;
            } else if (c == '$' && rules.dollarQuotes() && !isIdentifierChar(i > 0 ? sql.charAt(i - 1) : ' ')) {
                int tagEnd = dollarTagEnd(sql, i);
                if (tagEnd < 0) {
                    out.append(c);
                    i++;
                } else {
                    String tag = sql.substring(i, tagEnd + 1);
                    int close = sql.indexOf(tag, tagEnd + 1);
                    i = close < 0 ? n : close + tag.length();
                    out.append("''");
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Index just past the closing quote; a doubled quote (and, if enabled, a backslash) escapes it. */
    private static int skipQuoted(String sql, int from, char quote, boolean backslashEscapes) {
        int i = from;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (backslashEscapes && c == '\\') {
                i += 2;
            } else if (c == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                } else {
                    return i + 1;
                }
            } else {
                i++;
            }
        }
        return sql.length();
    }

    private static int skipBlockComment(String sql, int from, boolean nested) {
        int depth = 0;
        int i = from;
        while (i < sql.length() - 1) {
            if (sql.charAt(i) == '/' && sql.charAt(i + 1) == '*') {
                depth = nested ? depth + 1 : 1;
                i += 2;
            } else if (sql.charAt(i) == '*' && sql.charAt(i + 1) == '/') {
                depth--;
                i += 2;
                if (depth == 0) {
                    return i;
                }
            } else {
                i++;
            }
        }
        return sql.length();
    }

    /** Index of the closing $ of a dollar-quote tag ($$ or $tag$) starting at {@code from}, or -1. */
    private static int dollarTagEnd(String sql, int from) {
        int i = from + 1;
        if (i < sql.length() && (Character.isLetter(sql.charAt(i)) || sql.charAt(i) == '_')) {
            while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) {
                i++;
            }
        }
        return i < sql.length() && sql.charAt(i) == '$' ? i : -1;
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
