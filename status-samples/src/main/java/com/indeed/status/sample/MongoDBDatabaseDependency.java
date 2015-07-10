package com.indeed.status.sample;

import com.indeed.status.core.PingableDependency;
import com.indeed.status.core.Urgency;
import com.mongodb.MongoClient;

/**
 * @author pitz@indeed.com (Jeremy Pitzeruse)
 */
public class MongoDBDatabaseDependency extends PingableDependency {

    private final MongoClient client;
    private final String databaseName;

    public MongoDBDatabaseDependency(final String databaseName, final MongoClient client) {

        super(
            "mongodb-database-" + databaseName,
            "MongoDB Database Dependency [" + databaseName + "]",
            Urgency.REQUIRED
        );

        this.databaseName = databaseName;
        this.client = client;
    }

    @Override
    public void ping() throws Exception {
        client.getDB(databaseName).getStats();
    }
}
