package com.indeed.status.core;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** @author matts */
public interface DependencyExecutor {
    Future<CheckResult> submit(final Dependency dependency);

    void resolve(final Dependency dependency);

    void shutdown();

    boolean isShutdown();

    void awaitTermination(long duration, TimeUnit unit) throws InterruptedException;
}
