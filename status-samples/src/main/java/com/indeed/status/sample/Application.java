package com.indeed.status.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.indeed.status.core.CheckResultSet;
import com.indeed.status.core.Dependency;
import com.mongodb.MongoClient;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.StringWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** @author pitz@indeed.com (Jeremy Pitzeruse) */
public class Application {
    private static final ObjectWriter WRITER =
            new ObjectMapper().writer().withDefaultPrettyPrinter();

    private static final String DATABASE_NAME = "test";

    private CheckResultSet resultSet;

    public Application() {}

    public synchronized CheckResultSet getResultSet() {
        return resultSet;
    }

    public synchronized void setResultSet(final CheckResultSet resultSet) {
        this.resultSet = resultSet;
    }

    public static void main(final String[] args) throws IOException, InterruptedException {
        final MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setDatabaseName(DATABASE_NAME);
        final JdbcTemplate testdb = new JdbcTemplate(dataSource);
        final MongoClient client = new MongoClient("localhost");

        final Dependency mysqlDatabaseDependency =
                new MySqlDatabaseDependency(DATABASE_NAME, testdb);
        final Dependency dbDependency = new MongoDBDatabaseDependency(DATABASE_NAME, client);
        final Dependency onDiskFileDependency = new OnDiskFileDependency("file");

        final DependencyManager dependencyManager = new DependencyManager();
        dependencyManager.addDependency(mysqlDatabaseDependency);
        dependencyManager.addDependency(dbDependency);
        dependencyManager.addDependency(onDiskFileDependency);

        final Application application = new Application();
        Executors.newScheduledThreadPool(1)
                .scheduleAtFixedRate(
                        new Runnable() {
                            @Override
                            public void run() {
                                application.setResultSet(dependencyManager.evaluate());
                            }
                        },
                        0L,
                        3L,
                        TimeUnit.SECONDS);

        while (true) {
            final CheckResultSet resultSet = application.getResultSet();
            if (application.getResultSet() == null) {
                continue;
            }

            Thread.sleep(1000L);

            final StringWriter stringWriter = new StringWriter();
            WRITER.writeValue(stringWriter, resultSet.summarize(true));
            System.out.println(stringWriter);
        }
    }
}
