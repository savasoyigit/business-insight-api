package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.domain.insight.datasource.DataSourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SqlStatementGuardTest {

    private final SqlStatementGuard guard = new SqlStatementGuard();

    @ParameterizedTest
    @ValueSource(strings = {
            "select current_date as run_date where current_date >= :startDate",
            "select to_char(order_date, 'YYYY-MM') as month, region, sum(net_amount) as revenue\nfrom demo_sales.orders\nwhere order_date >= :startDate\ngroup by 1, 2\norder by 1",
            "select activity_date as day, count(distinct customer_id) as active_customers from crm.activity where activity_date > current_date - :days * interval '1 day' group by 1",
            "select id::text, payload->>'name' as name from events where created_at >= :from limit 10",
            "select tags[1] as first_tag from articles",
            "select region from a union all select region from b",
            "select x from (select 1 as x) t",
            "select comment, \"update\" from notes"
    })
    void acceptsReadOnlySelects(String sql) {
        assertThat(guard.check(sql, DataSourceType.POSTGRES)).as(sql).isEmpty();
    }

    @Test
    void acceptsSqlServerSyntax() {
        String sql = "select top 10 [order id], p.category from dbo.orders o join dbo.products p on p.id = o.product_id where o.created >= :from";
        assertThat(guard.check(sql, DataSourceType.MSSQL)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "select * into backup_orders from orders",
            "select * from orders for update",
            "select 1; select 2",
            "delete from orders",
            "with d as (delete from orders returning *) select * from d",
            "select from where",
            "update orders set amount = 0"
    })
    void rejectsEverythingElse(String sql) {
        assertThat(guard.check(sql, DataSourceType.POSTGRES)).as(sql).isPresent();
        assertThat(guard.check(sql, DataSourceType.MSSQL)).as(sql).isPresent();
    }
}
