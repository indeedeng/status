package com.indeed.status.core;

import com.google.common.base.Supplier;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

/**
 * @author matts
 */
public abstract class PingableDependencyBuilder<T extends PingableDependency, B extends PingableDependencyBuilder<T,B>>
    extends AbstractDependencyBuilder<T, B>
{
    protected PingableDependencyBuilder() {}

    @Override
    public B setId(@Nonnull String id) {
        return super.setId(id);
    }

    @Override
    public B setDescription(@Nonnull String description) {
        return super.setDescription(description);
    }

    @Override
    public B setTimeout(@Nonnegative long timeout) {
        return super.setTimeout(timeout);
    }

    @Override
    public B setPingPeriod(@Nonnegative long pingPeriod) {
        return super.setPingPeriod(pingPeriod);
    }

    @Override
    public B setUrgency(@Nonnull Urgency urgency) {
        return super.setUrgency(urgency);
    }

    @Override
    public B setToggle(@Nonnull Supplier<Boolean> toggle) {
        return super.setToggle(toggle);
    }
}
