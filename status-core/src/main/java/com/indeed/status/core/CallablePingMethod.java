package com.indeed.status.core;

import javax.annotation.Nonnull;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** @author matts */
public class CallablePingMethod implements PingMethod {
    @Nonnull private final Callable<Void> delegate;

    public CallablePingMethod(@Nonnull final Callable<Void> delegate) {
        this.delegate = delegate;
    }

    public CallablePingMethod(@Nonnull final Runnable delegate) {
        this.delegate = Executors.callable(delegate, null);
    }

    @Override
    public void ping() throws Exception {
        delegate.call();
    }
}
