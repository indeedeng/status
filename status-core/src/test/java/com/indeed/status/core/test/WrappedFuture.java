package com.indeed.status.core.test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
* @author matts
*/
public class WrappedFuture<T> implements Future<T> {
    private final Future<T> delegate;

    public WrappedFuture (final Future<T> delegate) {
        this.delegate = delegate;
    }

    public boolean cancel (final boolean b) {
        return delegate.cancel(b);
    }

    public boolean isCancelled () {
        return delegate.isCancelled();
    }

    public boolean isDone () {
        return delegate.isDone();
    }

    public T get () throws InterruptedException, ExecutionException {
        return delegate.get();
    }

    public T get (final long l, final TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.get();
    }
}
