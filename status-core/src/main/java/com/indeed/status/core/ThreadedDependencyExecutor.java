package com.indeed.status.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Adapter between the executor service and dependency executor
 *
 * @author matts
 */
public class ThreadedDependencyExecutor implements DependencyExecutor {
    private final ExecutorService executor;

    public ThreadedDependencyExecutor(final ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public Future<CheckResult> submit(final Dependency dependency) {
        return executor.submit(dependency);
    }

    @Override
    public void resolve(final Dependency dependency) {
        // no-op
    }

    @Override
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }

    @Override
    public void awaitTermination(final long duration, final TimeUnit unit)
            throws InterruptedException {
        executor.awaitTermination(duration, unit);
    }
}
