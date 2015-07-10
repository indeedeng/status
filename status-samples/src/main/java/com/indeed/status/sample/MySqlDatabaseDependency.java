package com.indeed.status.sample;

import com.indeed.status.core.PingableDependency;
import com.indeed.status.core.Urgency;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.Nonnull;

/**
 * @author pitz@indeed.com (Jeremy Pitzeruse)
 */
public class MySqlDatabaseDependency extends PingableDependency {
    private final JdbcTemplate testdb;

    public MySqlDatabaseDependency(@Nonnull final String id,
                                   @Nonnull final JdbcTemplate testdb) {
        super(
                "mysql-database-" + id,
                "MySql Database Dependency [" + id + ']',
                Urgency.REQUIRED
        );

        this.testdb = testdb;
    }

    @Override
    public void ping() throws Exception {
        testdb.queryForObject("select count(1) from testtable ", Integer.class);
    }
}
