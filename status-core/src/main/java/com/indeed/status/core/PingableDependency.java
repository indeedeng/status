package com.indeed.status.core;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * The <code>PingableDependency</code> represents the simplest of dependencies, a dependency that executes and either
 *  completes or throws an Exception.
 *
 * @see SimpleDependency
 */
public class PingableDependency extends AbstractDependency {
    @Nonnull private final Supplier<Boolean> toggle;
    @Nonnull private final Optional<Callable<Void>> pingMethod;

    /**
     * @deprecated Use a {@link SimplePingableDependencyBuilder} with a Callable instead.
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
     * @deprecated Use a {@link SimplePingableDependencyBuilder} with a Callable instead.
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
     * @deprecated Use a {@link SimplePingableDependencyBuilder} with a Callable instead.
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
     * @deprecated Use a {@link SimplePingableDependencyBuilder} with a Callable instead.
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
     * @deprecated Use a {@link SimplePingableDependencyBuilder} with a Callable instead.
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
     * @deprecated Use a {@link SimplePingableDependencyBuilder} with a Callable instead.
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
     * @deprecated Use a {@link SimplePingableDependencyBuilder} with a Callable instead.
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
     * @deprecated Use a {@link SimplePingableDependencyBuilder} with a Callable instead.
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
        this(id, Optional.<Callable<Void>>absent(), description, timeout, pingPeriod, urgency, type, servicePool, toggle);
    }

    protected PingableDependency(
            @Nonnull final String id,
            @Nonnull final Callable<Void> pingMethod,
            @Nonnull final String description,
            final long timeout,
            final long pingPeriod,
            @Nonnull final Urgency urgency,
            @Nonnull final DependencyType type,
            final String servicePool,
            @Nonnull final Supplier<Boolean> toggle
    ) {
        this(id, Optional.of(pingMethod), description, timeout, pingPeriod, urgency, type, servicePool, toggle);
    }

    /**
     * Direct constructor based on fields.
     *
     * Package-protected, because we don't want to expose the Optionality of the ping method through the public API. Most
     *  pingable dependency implementers should be encouraged to provide a Callable ping method directly.
     */
    PingableDependency(
            @Nonnull final String id,
            @Nonnull final Optional<Callable<Void>> optionalPingMethod,
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
        this.pingMethod = optionalPingMethod;
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
    public void ping() throws Exception {
        Preconditions.checkState(this.pingMethod.isPresent(),
                "Dependency '%s' neither overrides the ping() method nor provides a pingMethod implementation.", getId());

        // Execute the delegate, passing through any exception.
        this.pingMethod.get().call();
    }

    /**
     * Override this method to modify the error message
     *
     * @param e exception that was thrown by ping or null on success
     * @return error message
     */
    protected String formatErrorMessage(@Nullable final Exception e) {
        return e == null ? "ok" : "Exception thrown during ping";
    }

    public static SimplePingableDependencyBuilder newBuilder() {
        return new SimplePingableDependencyBuilder();
    }

    public static abstract class Builder<T extends PingableDependency, B extends AbstractDependency.Builder<T, B>> extends AbstractDependency.Builder<T, B> {
        /**
         * @deprecated Direct field access deprecated; use {@link #getToggle()}} instead.
         */
        @Nonnull protected Supplier<Boolean> toggle = Suppliers.ofInstance(Boolean.TRUE);
        @Nullable private Callable<Void> pingMethod;

        protected Builder() {}

        @Nullable
        public Callable<Void> getPingMethod() {
            return pingMethod;
        }

        public B setPingMethod(@Nonnull final Callable<Void> pingMethod) {
            this.pingMethod = pingMethod;
            return cast();
        }

        public B setPingMethod(@Nonnull final Runnable pingMethod) {
            return this.setPingMethod(Executors.<Void>callable(pingMethod, null));
        }

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

        public PingableDependency build() {
            final String id = getId();
            Preconditions.checkState(!Strings.isNullOrEmpty(id), "Cannot build a dependency with an empty ID");

            final String description = getDescription();
            Preconditions.checkState(!Strings.isNullOrEmpty(description), "Cannot build a dependency with an empty description");

            return new PingableDependency(
                    id,
                    Optional.fromNullable(getPingMethod()),
                    getDescription(),
                    getTimeout(),
                    getPingPeriod(),
                    getUrgency(),
                    getType(),
                    getServicePool(),
                    getToggle());
        }

        private B cast() {
            //noinspection unchecked
            return (B)this;
        }
    }

    /**
     * Simple concrete extension of the pingable dependency builder to collapse the
     *  parameterized types and avoid telescoping type definitions. This class should
     *  serve as the basic pingable dependency builder for all but a select few cases.
     */
    public static class SimplePingableDependencyBuilder
            extends Builder<PingableDependency, SimplePingableDependencyBuilder> {
    }
}
