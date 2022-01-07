package com.indeed.status.core;

import com.google.common.base.Preconditions;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;

@Value.Immutable
public abstract class DependencyCheckerParams {
    @Nullable
    protected abstract String loggerName();

    @Nullable
    public abstract ExecutorService executorService();

    @Value.Default
    public SystemReporter systemReporter() {
        return new SystemReporter();
    }

    @Value.Default
    public boolean throttle() {
        return false;
    }

    @Value.Default
    public DependencyExecutor dependencyExecutor() {
        Preconditions.checkNotNull(executorService());
        return new DependencyChecker.DependencyExecutorSet(executorService());
    }
}
