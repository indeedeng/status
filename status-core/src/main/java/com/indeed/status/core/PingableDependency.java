package com.indeed.status.core;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.indeed.util.core.time.DefaultWallClock;
import com.indeed.util.core.time.WallClock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The <code>PingableDependency</code> represents the simplest of dependencies, a dependency that executes and either
 *  completes or throws an Exception.
 *
 * @see SimpleDependency
 */
@ThreadSafe
public abstract class PingableDependency extends AbstractDependency {
    @Nonnull private final WallClock wallClock;
    @Nonnull private final Supplier<Boolean> toggle;
    @Nonnull private final AtomicInteger numberOfRunningHealthChecks;
    @Nonnull private boolean throttle;

    /**
     * @deprecated Use a {@link SimplePingableDependency.Builder} with a Callable instead.
     */
    @Deprecated
    public PingableDependency(
            @Nonnull final String id,
            @Nonnull final String description,
            @Nonnull final Urgency urgency
    ) {
        //noinspection deprecation - pointless warning in a deprecated method.
        this(id, description, urgency, DEFAULT_TYPE, DEFAULT_SERVICE_POOL);
    }

    /**
     * @deprecated Use a {@link SimplePingableDependency.Builder} with a Callable instead.
     */
    @Deprecated
    public PingableDependency(
            @Nonnull final String id,
            @Nonnull final String description,
            @Nonnull final Urgency urgency,
            @Nonnull final DependencyType type,
            final String servicePool
    ) {
        //noinspection deprecation - pointless warning in a deprecated method.
        this(id, description, DEFAULT_TIMEOUT, urgency, type, servicePool);
    }

    /**
     * @deprecated Use a {@link SimplePingableDependency.Builder} with a Callable instead.
     */
    @Deprecated
    public PingableDependency (
            @Nonnull final String id,
            @Nonnull final String description,
            final long timeout,
            @Nonnull final Urgency urgency
    ) {
        //noinspection deprecation - pointless warning in a deprecated method.
        this(id, description, timeout, urgency, DEFAULT_TYPE, DEFAULT_SERVICE_POOL);
    }

    /**
     * @deprecated Use a {@link SimplePingableDependency.Builder} with a Callable instead.
     */
    public PingableDependency (
            @Nonnull final String id,
            @Nonnull final String description,
            final long timeout,
            @Nonnull final Urgency urgency,
            @Nonnull final DependencyType type,
            final String servicePool
    ) {
        //noinspection deprecation - pointless warning in a deprecated method.
        this(id, description, timeout, DEFAULT_PING_PERIOD, urgency, type, servicePool);
    }

    /**
     * @deprecated Use a {@link SimplePingableDependency.Builder} with a Callable instead.
     */
    @Deprecated
    protected PingableDependency (
            final String id,
            final String description,
            final long timeout,
            final long pingPeriod,
            final Urgency urgency
    ) {
        //noinspection deprecation - pointless warning in a deprecated method.
        this(id, description, timeout, pingPeriod, urgency, DEFAULT_TYPE, DEFAULT_SERVICE_POOL);
    }

    /**
     * @deprecated Use a {@link SimplePingableDependency.Builder} with a Callable instead.
     */
    protected PingableDependency (
            final String id,
            final String description,
            final long timeout,
            final long pingPeriod,
            final Urgency urgency,
            @Nonnull final DependencyType type,
            final String servicePool
    ) {
        //noinspection deprecation - pointless warning in a deprecated method.
        this(id, description, timeout, pingPeriod, urgency, type, servicePool, Suppliers.ofInstance(Boolean.TRUE));
    }

    /**
     * @deprecated Use a {@link SimplePingableDependency.Builder} with a Callable instead.
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
        //noinspection deprecation - pointless warning in a deprecated method.
        this(id, description, timeout, pingPeriod, urgency, DEFAULT_TYPE, DEFAULT_SERVICE_POOL, toggle);
    }

    /**
     * @deprecated Use a {@link SimplePingableDependency.Builder} with a Callable instead.
     */
    @Deprecated
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
        //noinspection deprecation - pointless warning in a deprecated method.
        this(id, description, timeout, pingPeriod, urgency, type, servicePool, new DefaultWallClock(), toggle);
    }

    /**
     * @deprecated Use a {@link SimplePingableDependency.Builder} with a Callable instead.
     */
    @Deprecated
    protected PingableDependency(
            @Nonnull final String id,
            @Nonnull final String description,
            final long timeout,
            final long pingPeriod,
            @Nonnull final Urgency urgency,
            @Nonnull final DependencyType type,
            final String servicePool,
            @Nonnull final WallClock wallClock,
            @Nonnull final Supplier<Boolean> toggle
    ) {
        super(id, description, timeout, pingPeriod, urgency, type, servicePool);

        this.wallClock = wallClock;
        this.toggle = toggle;
        this.numberOfRunningHealthChecks = new AtomicInteger(0);
        this.throttle = false;
    }

    protected PingableDependency(
            final PingableDependency.Builder<? extends PingableDependency, ?> builder
    ) {
        super(builder);

        this.wallClock = Preconditions.checkNotNull(builder.getWallClock(), "wallclock required");
        this.toggle = Preconditions.checkNotNull(builder.getToggle(), "toggle required");
        this.numberOfRunningHealthChecks = new AtomicInteger(0);
        this.throttle = false;
    }

    public CheckResult call() throws Exception {
        CheckResult result;
        final long timestamp = wallClock.currentTimeMillis();

        try {
            // Execute the unreliable method only if the toggle is set, allow products that have
            // dependencies on optionally available subsystems to avoid impossible healthchecks.
            if (toggle.get()) {
                // Limit the number of running healthchecks here if the throttle is active, since
                // DependencyChecker can't guarantee that it can cancel a running healthcheck.
                if (throttle && numberOfRunningHealthChecks.incrementAndGet() > 2) {
                    throw new IllegalStateException(
                            String.format("Unable to ping dependency %s because there are already two previous pings that haven't " +
                                    "returned. To turn off this behavior set throttle to false.", getId())
                    );
                }

                ping();
            }

            final long duration = wallClock.currentTimeMillis() - timestamp;
            result = CheckResult.newBuilder(this, CheckStatus.OK, formatErrorMessage(null))
                    .setTimestamp(timestamp)
                    .setDuration(duration)
                    .build();

        } catch(final Exception e) {
            final long duration = wallClock.currentTimeMillis() - timestamp;
            result = CheckResult.newBuilder(this, CheckStatus.OUTAGE, formatErrorMessage(e))
                    .setTimestamp(timestamp)
                    .setDuration(duration)
                    .setThrowable(e)
                    .build();
        } finally {
            if (toggle.get() && throttle) {
                numberOfRunningHealthChecks.decrementAndGet();
            }
        }

        return result;
    }

    /**
     * Cloned from the PingableService interface, this is a convenience wrapper for the usual pattern of creating
     * dependency checkers that return nothing and throw an exception on any error.
     *
     * @throws Exception If any piece of the dependency check fails.
     */
    public abstract void ping() throws Exception;

    /**
     * Override this method to modify the error message
     *
     * @param e exception that was thrown by ping or null on success
     * @return error message
     */
    protected String formatErrorMessage(@Nullable final Exception e) {
        return e == null ? "ok" : "Exception thrown during ping";
    }

    /**
     * Sets whether this dependency will prevent ping from being called too many times
     *
     * @param throttle
     */
    protected void setThrottle(final boolean throttle) {
        this.throttle = throttle;
    }

    public static abstract class Builder<T extends PingableDependency, B extends PingableDependency.Builder<T, B>> extends AbstractDependency.Builder<T, B> {
        /**
         * @deprecated Direct field access deprecated; use {@link #getToggle()}} instead.
         */
        @Nonnull protected Supplier<Boolean> toggle = Suppliers.ofInstance(Boolean.TRUE);
        @Nonnull private WallClock wallClock = new DefaultWallClock();

        protected Builder() {}

        @Nonnull
        protected Supplier<Boolean> getToggle() {
            //noinspection deprecation -- only deprecated for direct acces
            return toggle;
        }

        public B setToggle(@Nonnull final Supplier<Boolean> toggle) {
            //noinspection deprecation -- only deprecated for direct acces
            this.toggle = toggle;
            return cast();
        }

        @Nonnull
        protected WallClock getWallClock() {
            return wallClock;
        }

        public B setWallClock(@Nonnull final WallClock wallClock) {
            this.wallClock = wallClock;
            return cast();
        }

        @Override
        public abstract PingableDependency build();

        private B cast() {
            //noinspection unchecked
            return (B)this;
        }
    }
}
