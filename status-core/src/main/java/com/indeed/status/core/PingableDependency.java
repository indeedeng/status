package com.indeed.status.core;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Similar to the PingableService interface in HCv2, this is a convenience base class for
 * dependencies that do not need to worry about different levels of check status and only
 * want to report their gross availability.
 */
public abstract class PingableDependency extends AbstractDependency {
    private final Supplier<Boolean> toggle;

    /**
     * @deprecated Instead, use {@link PingableDependency#PingableDependency(String, String, Urgency, DependencyType, String)}.
     */
    @Deprecated
    public PingableDependency(
            @Nonnull final String id,
            @Nonnull final String description,
            @Nonnull final Urgency urgency
    ) {
        this(id, description, urgency, DEFAULT_TYPE, DEFAULT_SERVICE_POOL);
    }

    public PingableDependency(
            @Nonnull final String id,
            @Nonnull final String description,
            @Nonnull final Urgency urgency,
            @Nonnull final DependencyType type,
            final String servicePool
    ) {
        this(id, description, DEFAULT_TIMEOUT, urgency, type, servicePool);
    }

    /**
     * @deprecated Instead, use {@link PingableDependency#PingableDependency(String, String, long, Urgency, DependencyType, String)}.
     */
    @Deprecated
    public PingableDependency (
            @Nonnull final String id,
            @Nonnull final String description,
            final long timeout,
            @Nonnull final Urgency urgency
    ) {
        this(id, description, timeout, urgency, DEFAULT_TYPE, DEFAULT_SERVICE_POOL);
    }

    public PingableDependency (
            @Nonnull final String id,
            @Nonnull final String description,
            final long timeout,
            @Nonnull final Urgency urgency,
            @Nonnull final DependencyType type,
            final String servicePool
    ) {
        this(id, description, timeout, DEFAULT_PING_PERIOD, urgency, type, servicePool);
    }

    /**
     * @deprecated Instead, use {@link PingableDependency#PingableDependency(String, String, long, long, Urgency, DependencyType, String)}.
     */
    @Deprecated
    protected PingableDependency (
            final String id,
            final String description,
            final long timeout,
            final long pingPeriod,
            final Urgency urgency
    ) {
        this(id, description, timeout, pingPeriod, urgency, DEFAULT_TYPE, DEFAULT_SERVICE_POOL);
    }

    protected PingableDependency (
            final String id,
            final String description,
            final long timeout,
            final long pingPeriod,
            final Urgency urgency,
            @Nonnull final DependencyType type,
            final String servicePool
    ) {
        this(id, description, timeout, pingPeriod, urgency, type, servicePool, Suppliers.ofInstance(Boolean.TRUE));
    }

    /**
     * @deprecated Instead, use {@link PingableDependency#PingableDependency(String, String, long, long, Urgency, DependencyType, String, Supplier<Boolean>)}.
     */
    @Deprecated
    protected PingableDependency(
            @Nonnull final String id,
            @Nonnull final String description,
            final long timeout,
            final long pingPeriod,
            @Nonnull final Urgency urgency,
            @Nonnull final Supplier<Boolean> toggle
    ) {
        this(id, description, timeout, pingPeriod, urgency, DEFAULT_TYPE, DEFAULT_SERVICE_POOL, toggle);
    }

    protected PingableDependency(
            @Nonnull final String id,
            @Nonnull final String description,
            final long timeout,
            final long pingPeriod,
            @Nonnull final Urgency urgency,
            @Nonnull final DependencyType type,
            final String servicePool,
            @Nonnull final Supplier<Boolean> toggle
    ) {
        super(id, description, timeout, pingPeriod, urgency, type, servicePool);
        this.toggle = toggle;
    }

    public CheckResult call () throws Exception {
        CheckResult result;
        final long timestamp = System.currentTimeMillis();

        try {
            // Execute the unreliable method only if the toggle is set, allow products that have
            // dependencies on optionally available subsystems to avoid impossible healthchecks.
            if (toggle.get()) {
                ping();
            }

            final long duration = System.currentTimeMillis() - timestamp;
            result = CheckResult.newBuilder(this, CheckStatus.OK, formatErrorMessage(null))
                    .setTimestamp(timestamp)
                    .setDuration(duration)
                    .build();

        } catch(Exception e) {
            final long duration = System.currentTimeMillis() - timestamp;
            result = CheckResult.newBuilder(this, CheckStatus.OUTAGE, formatErrorMessage(e))
                    .setTimestamp(timestamp)
                    .setDuration(duration)
                    .setThrowable(e)
                    .build();
        }

        return result;
    }

    /**
     * Cloned from the PingableService interface, this is a convenience wrapper for the usual pattern of creating
     * dependency checkers that return nothing and throw an exception on any error.
     *
     * @throws Exception If any piece of the dependency check fails.
     */
    public abstract void ping () throws Exception;

    /**
     * Override this method to modify the error message
     *
     * @param e exception that was thrown by ping or null on success
     * @return error message
     */
    protected String formatErrorMessage(@Nullable Exception e) {
        return e == null ? "ok" : "Exception thrown during ping";
    }
}
