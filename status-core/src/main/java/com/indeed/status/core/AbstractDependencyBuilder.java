package com.indeed.status.core;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

/**
 * @author matts
 */
public abstract class AbstractDependencyBuilder<T extends AbstractDependency, B extends AbstractDependencyBuilder<T,B>> {
    @Nonnull
    protected String id;
    @Nonnull
    protected String description;
    @Nonnegative
    protected long timeout = PingableDependency.DEFAULT_TIMEOUT;
    @Nonnegative
    protected long pingPeriod = PingableDependency.DEFAULT_PING_PERIOD;
    @Nonnull
    protected Urgency urgency;
    @Nonnull
    protected DependencyType type = PingableDependency.DEFAULT_TYPE;
    @Nonnull
    protected String servicePool = PingableDependency.DEFAULT_SERVICE_POOL;
    @Nonnull
    protected Supplier<Boolean> toggle = Suppliers.ofInstance(Boolean.TRUE);

    protected AbstractDependencyBuilder() {}

    public abstract T build();

    public B setId(@Nonnull final String id) {
        this.id = id;
        return cast();
    }

    public B setDescription(@Nonnull final String description) {
        this.description = description;
        return cast();
    }

    public B setTimeout(@Nonnegative final long timeout) {
        this.timeout = timeout;
        return cast();
    }

    public B setPingPeriod(@Nonnegative final long pingPeriod) {
        this.pingPeriod = pingPeriod;
        return cast();
    }

    public B setUrgency(@Nonnull final Urgency urgency) {
        this.urgency = urgency;
        return cast();
    }

    public B setType(@Nonnull final DependencyType type) {
        this.type = type;
        return cast();
    }

    public B setServicePool(@Nonnull final String servicePool) {
        this.servicePool = servicePool;
        return cast();
    }

    public B setToggle(@Nonnull final Supplier<Boolean> toggle) {
        this.toggle = toggle;
        return cast();
    }

    private B cast() {
        //noinspection unchecked
        return (B)this;
    }
}
