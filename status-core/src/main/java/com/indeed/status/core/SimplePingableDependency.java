package com.indeed.status.core;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.indeed.util.core.time.WallClock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.Callable;

/**
 * Simple pingable dependency that determines health status by evaluating an injected
 *  {@link PingMethod} that either runs to completion or throws an exception.
 *
 * @author matts
 */
@ThreadSafe
public class SimplePingableDependency extends PingableDependency {
    @Nonnull private final PingMethod pingMethod;

    /**
     * Package-protected, because we don't want to expose the Optionality of the ping method through the public API. Most
     *  pingable dependency implementers should be encouraged to provide a Callable ping method directly.
     */
    private SimplePingableDependency(@Nonnull final Builder builder) {
        super(builder);
        final String id = builder.getId();
        Preconditions.checkState(!Strings.isNullOrEmpty(id), "Cannot build a dependency with an empty ID");

        final String description = builder.getDescription();
        Preconditions.checkState(!Strings.isNullOrEmpty(description), "Cannot build a dependency with an empty description");

        this.pingMethod = Preconditions.checkNotNull(builder.getPingMethod(), "Cannot build a dependency with no ping method");
    }

    /**
     * Cloned from the PingableService interface, this is a convenience wrapper for the usual pattern of creating
     * dependency checkers that return nothing and throw an exception on any error.
     *
     * @throws Exception If any piece of the dependency check fails.
     */
    public final void ping() throws Exception {
        // Execute the delegate, passing through any exception.
        this.pingMethod.ping();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Simple concrete extension of the pingable dependency builder to collapse the
     *  parameterized types and avoid telescoping type definitions. This class should
     *  serve as the basic pingable dependency builder for all but a select few cases.
     */
    public static class Builder extends PingableDependency.Builder<SimplePingableDependency, Builder> {
        @Nullable PingMethod pingMethod;

        @Nullable
        public PingMethod getPingMethod() {
            return pingMethod;
        }

        @Nonnull
        public Builder setPingMethod(@Nonnull final PingMethod pingMethod) {
            this.pingMethod = pingMethod;
            return this;
        }

        @Nonnull
        public Builder setPingMethod(@Nonnull final Callable<Void> pingMethod) {
            return setPingMethod(new CallablePingMethod(pingMethod));
        }

        public Builder setPingMethod(@Nonnull final Runnable pingMethod) {
            return setPingMethod(new CallablePingMethod(pingMethod));
        }

        public SimplePingableDependency build() {
            return new SimplePingableDependency(this);
        }
    }
}
