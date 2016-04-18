package com.indeed.status.core;

import java.util.concurrent.Callable;

/**
 *
 */
public interface Dependency extends Callable<CheckResult>, Documented {
    /**
     * @return An identifier for this dependency that is unique in the system
     *  represented by the client application.
     */
    String getId ();

    /**
     * @return Human-readable description of the nature of this dependency
     */
    String getDescription();

    /**
     * @return the URL at which additional information about this dependency can be discovered
     */
    String getDocumentationUrl();

    /**
     * @return The number of milliseconds that the dependency should be allowed to execute for before being
     *  considered a failure.
     *
     */
    long getTimeout();

    /**
     * @return The number of milliseconds that the system should delay between automated checks of this
     *         dependency. Dependencies that are run on-demand need not worry about this value; this is
     *         considered only when launching background pingers
     */
    long getPingPeriod();

    /**
     * @return Functor representing the effect of this dependency on the health of the overall system
     */
    Urgency getUrgency();

    /**
     * @return The type of this dependency: resource, application or service name, categorization, etc.
     *         The interface {@link DependencyType} should be implemented for the system accordingly.
     *         Examples: mysql, mongo, http service, memory, third party dependency, etc.
     */
    DependencyType getType();

    /**
     * @return The location where the dependent service/resource exists. This will be helpful especially
     *         when those service/resources exists in several places, which means when one of these fails,
     *         this helps us detect the location immediately. We could avoid grepping the logs.
     *         Examples: "dbpool.example.com:3306/mysqldb1", "ServiceName:ZoneName", etc.
     */
    String getServicePool();
}
