package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.domain.insight.datasource.DataSourceType;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Structural check of a query template: it must parse as exactly one read-only SELECT.
 * Complements the keyword checks in the dialects (which can be fooled, e.g. {@code SELECT ... INTO new_table}
 * creates a table without any forbidden keyword). The database-level read-only transaction remains the final guard.
 */
@Component
public class SqlStatementGuard {

    /** Returns an error message, or empty when the statement is a single plain SELECT. */
    public Optional<String> check(String sqlText, DataSourceType type) {
        // [quoted identifiers] are SQL Server syntax; in Postgres brackets mean array access.
        boolean squareBrackets = type == DataSourceType.MSSQL;
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sqlText, parser -> parser.withSquareBracketQuotation(squareBrackets));
        } catch (JSQLParserException | RuntimeException ex) {
            return Optional.of("SQL could not be parsed as a single SELECT statement");
        }

        List<Statement> list = statements.getStatements();
        if (list.size() != 1) {
            return Optional.of("Only a single SELECT statement is allowed");
        }
        if (!(list.get(0) instanceof Select select)) {
            return Optional.of("Only SELECT queries are allowed");
        }
        return checkSelect(select);
    }

    private Optional<String> checkSelect(Select select) {
        if (select.getWithItemsList() != null) {
            for (WithItem<?> withItem : select.getWithItemsList()) {
                // A WITH item may hold INSERT/UPDATE/DELETE (... RETURNING) in Postgres.
                if (!(withItem.getParenthesedStatement() instanceof ParenthesedSelect inner)) {
                    return Optional.of("Data-modifying WITH clauses are not allowed");
                }
                Optional<String> innerError = checkSelect(inner);
                if (innerError.isPresent()) {
                    return innerError;
                }
            }
        }
        if (select instanceof PlainSelect plain) {
            if (plain.getIntoTables() != null && !plain.getIntoTables().isEmpty()) {
                return Optional.of("SELECT ... INTO is not allowed");
            }
            if (plain.getForMode() != null || plain.getForUpdateTable() != null) {
                return Optional.of("Locking clauses (FOR UPDATE/SHARE) are not allowed");
            }
        } else if (select instanceof SetOperationList setOperations) {
            for (Select part : setOperations.getSelects()) {
                Optional<String> partError = checkSelect(part);
                if (partError.isPresent()) {
                    return partError;
                }
            }
        } else if (select instanceof ParenthesedSelect parenthesed) {
            return checkSelect(parenthesed.getSelect());
        }
        return Optional.empty();
    }
}
