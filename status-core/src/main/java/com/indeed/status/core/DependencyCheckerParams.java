package com.indeed.status.core;

import com.google.common.base.Preconditions;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;

@Value.Immutable
public abstract class DependencyCheckerParams {
    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(DependencyChecker.class);

    @Nullable
    protected abstract String loggerName();

    @Value.Lazy
    public Logger logger() {
        final String loggerName = loggerName();
        if (loggerName != null) {
            return LoggerFactory.getLogger(loggerName);
        } else {
            return DEFAULT_LOGGER;
        }
    }

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
