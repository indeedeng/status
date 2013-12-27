package com.indeed.status.core;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author matts
 */
public interface DependencyExecutor {
    public Future<CheckResult> submit(final Dependency dependency);

    public void resolve(final Dependency dependency);

    public void shutdown();
    public boolean isShutdown();
    public void awaitTermination(long duration, TimeUnit unit) throws InterruptedException;
}
