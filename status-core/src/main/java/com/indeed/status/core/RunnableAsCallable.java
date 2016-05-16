package com.indeed.status.core;

import javax.annotation.Nonnull;
import java.util.concurrent.Callable;

/**
 * Simple adapter from <code>Runnable -> Callable&lt;Void&gt;</code>
 */
class RunnableAsCallable implements Callable<Void> {
    @Nonnull private final Runnable delegate;

    public RunnableAsCallable(@Nonnull final Runnable delegate) {
        this.delegate = delegate;
    }

    @Override
    public Void call() throws Exception {
        delegate.run();
        return null;
    }
}
