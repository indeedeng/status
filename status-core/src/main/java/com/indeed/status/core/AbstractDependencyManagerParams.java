package com.indeed.status.core;

import com.indeed.util.core.time.WallClock;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.concurrent.ThreadPoolExecutor;

import static com.indeed.status.core.AbstractDependencyManager.newDefaultThreadPool;

@Value.Immutable
public abstract class AbstractDependencyManagerParams {
    @Nullable
    public abstract String appName();

    @Nullable
    public abstract String loggerName();

    @Value.Default
    public ThreadPoolExecutor threadPool() {
        return newDefaultThreadPool();
    }

    @Nullable
    protected abstract WallClock wallClock();

    @Value.Default
    public SystemReporter systemReporter() {
        final WallClock wallClock = wallClock();
        if (wallClock == null) {
            return new SystemReporter();
        } else {
            return new SystemReporter(wallClock);
        }
    }

    @Value.Default
    public boolean throttleDependencyChecks() {
        return false;
    }

    @Value.Default
    public DependencyChecker checker() {
        return new DependencyChecker(ImmutableDependencyCheckerParams.builder()
                .executorService(threadPool())
                .loggerName(loggerName())
                .systemReporter(systemReporter())
                .throttle(throttleDependencyChecks())
                .build());
    }
}
