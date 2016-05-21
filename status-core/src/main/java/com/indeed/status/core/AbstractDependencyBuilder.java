package com.indeed.status.core;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

/**
 * @author matts
 * @deprecated Use {@link AbstractDependency.Builder} directly
 * <p/>
 * This class overrides all public mutators on the the parent class so that the method signatures match their
 * pre-deprecated versions.
 * <p/>
 * TODO Remove by 2017-01-01
 */
public abstract class AbstractDependencyBuilder<T extends AbstractDependency, B extends AbstractDependencyBuilder<T, B>>
        extends AbstractDependency.Builder<T, B> {
    @Nonnull
    @Override
    public B setId(@Nonnull final String id) {
        return super.setId(id);
    }

    @Nonnull
    @Override
    public B setDescription(@Nonnull final String description) {
        return super.setDescription(description);
    }

    @Nonnull
    @Override
    public B setTimeout(@Nonnegative final long timeout) {
        return super.setTimeout(timeout);
    }

    @Nonnull
    @Override
    public B setPingPeriod(@Nonnegative final long pingPeriod) {
        return super.setPingPeriod(pingPeriod);
    }

    @Nonnull
    @Override
    public B setUrgency(@Nonnull final Urgency urgency) {
        return super.setUrgency(urgency);
    }

    @Nonnull
    @Override
    public B setType(@Nonnull final DependencyType type) {
        return super.setType(type);
    }

    @Nonnull
    @Override
    public B setServicePool(@Nonnull final String servicePool) {
        return super.setServicePool(servicePool);
    }
}
