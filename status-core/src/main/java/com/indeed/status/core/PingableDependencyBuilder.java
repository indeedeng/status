package com.indeed.status.core;

import com.google.common.base.Supplier;

import javax.annotation.Nonnull;

/**
 * @author matts
 */
public abstract class PingableDependencyBuilder<T extends PingableDependency, B extends PingableDependencyBuilder<T,B>>
    extends AbstractDependencyBuilder<T, B>
{
    protected PingableDependencyBuilder() {}

    @Override
    public B setId(String id) {
        return super.setId(id);
    }

    @Override
    public B setDescription(String description) {
        return super.setDescription(description);
    }

    @Override
    public B setTimeout(long timeout) {
        return super.setTimeout(timeout);
    }

    @Override
    public B setPingPeriod(long pingPeriod) {
        return super.setPingPeriod(pingPeriod);
    }

    @Override
    public B setUrgency(Urgency urgency) {
        return super.setUrgency(urgency);
    }

    @Override
    public B setToggle(@Nonnull Supplier<Boolean> toggle) {
        return super.setToggle(toggle);
    }
}
