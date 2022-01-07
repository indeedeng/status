package com.indeed.status.core;

import com.google.common.base.Supplier;
import com.indeed.util.core.time.WallClock;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

/**
 * @author matts
 * @deprecated Use {@link PingableDependency.Builder} directly
 *     <p>This class overrides all public mutators on the the parent class so that the method
 *     signatures match their pre-deprecated versions.
 *     <p>TODO Remove by 2017-01-01
 */
@Deprecated
public abstract class PingableDependencyBuilder<
                T extends PingableDependency, B extends PingableDependencyBuilder<T, B>>
        extends PingableDependency.Builder<T, B> {
    @Override
    public B setToggle(@Nonnull final Supplier<Boolean> toggle) {
        return super.setToggle(toggle);
    }

    @Override
    public B setWallClock(@Nonnull final WallClock wallClock) {
        return super.setWallClock(wallClock);
    }

    @Nonnull
    @Override
    public B setServicePool(@Nonnull final String servicePool) {
        return super.setServicePool(servicePool);
    }

    @Nonnull
    @Override
    public B setType(@Nonnull final DependencyType type) {
        return super.setType(type);
    }

    @Nonnull
    @Override
    public B setUrgency(@Nonnull final Urgency urgency) {
        return super.setUrgency(urgency);
    }

    @Nonnull
    @Override
    public B setPingPeriod(@Nonnegative final long pingPeriod) {
        return super.setPingPeriod(pingPeriod);
    }

    @Nonnull
    @Override
    public B setTimeout(@Nonnegative final long timeout) {
        return super.setTimeout(timeout);
    }

    @Nonnull
    @Override
    public B setDescription(@Nonnull final String description) {
        return super.setDescription(description);
    }

    @Nonnull
    @Override
    public B setId(@Nonnull final String id) {
        return super.setId(id);
    }
}
