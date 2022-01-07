package com.indeed.status.core;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.Callable;

/**
 * A <code>Dependency</code> represents a single system, service, or state that the current running
 * JVM process depends on. The {@link #call()} method of the dependency performs some test of the
 * remote dependency to assert its availability. The status of the remote dependency is captured in
 * the {@link CheckResult} returned by the executable method.
 *
 * <p>The dependency implementation may be triggered by multiple threads simultaneously, and thus
 * must be implemented in a thread-safe manner. No synchronization is performed by the healthcheck
 * framework; any areas of the code that must be guarded must be explicitly locked by the
 * implementation itself.
 */
@ThreadSafe
public interface Dependency extends Callable<CheckResult>, Documented {
    /**
     * @return An identifier for this dependency that is unique in the system represented by the
     *     client application.
     */
    String getId();

    /** @return Human-readable description of the nature of this dependency */
    String getDescription();

    /** @return the URL at which additional information about this dependency can be discovered */
    String getDocumentationUrl();

    /**
     * @return The number of milliseconds that the dependency should be allowed to execute for
     *     before being considered a failure.
     */
    long getTimeout();

    /**
     * @return The number of milliseconds that the system should delay between automated checks of
     *     this dependency. Dependencies that are run on-demand need not worry about this value;
     *     this is considered only when launching background pingers
     */
    long getPingPeriod();

    /**
     * @return Functor representing the effect of this dependency on the health of the overall
     *     system
     */
    Urgency getUrgency();

    /**
     * @return The type of this dependency: resource, application or service name, categorization,
     *     etc. The interface {@link DependencyType} should be implemented for the system
     *     accordingly. Examples: mysql, mongo, http service, memory, third party dependency, etc.
     */
    DependencyType getType();

    /**
     * @return The location where the dependent service/resource exists. This will be helpful
     *     especially when those service/resources exists in several places, which means when one of
     *     these fails, this helps us detect the location immediately. We could avoid grepping the
     *     logs. Examples: "dbpool.example.com:3306/mysqldb1", "ServiceName:ZoneName", etc.
     */
    String getServicePool();
}
