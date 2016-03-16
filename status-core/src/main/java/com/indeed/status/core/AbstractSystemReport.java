package com.indeed.status.core;

import javax.annotation.Nonnull;

public abstract class AbstractSystemReport {
    @Nonnull
    public final String hostname;
    public final long duration;
    @Nonnull
    public final CheckStatus condition;

    public AbstractSystemReport(@Nonnull String hostname, long duration, @Nonnull CheckStatus condition) {
        this.hostname = hostname;
        this.duration = duration;
        this.condition = condition;
    }
}
