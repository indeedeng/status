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
    String getId();

    /**
     * @return Human-readable description of the nature of this dependency
     */
    String getDescription();

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
}
