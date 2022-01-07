package com.indeed.status.core;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Value.Immutable
public abstract class DependencyPingerParams {
    private static final Logger log = LoggerFactory.getLogger(DependencyPinger.class);

    public abstract Dependency dependency();

    @Value.Default
    public SystemReporter systemReporter() {
        return new SystemReporter();
    }

    @Value.Default
    public DependencyChecker checker() {
        return new DependencyChecker(
                ImmutableDependencyCheckerParams.builder()
                        .executorService(executorService())
                        .systemReporter(systemReporter())
                        .build());
    }

    @Value.Default
    public ExecutorService executorService() {
        return Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder()
                        .setNameFormat("dependency-pinger-%d")
                        .setDaemon(true)
                        .setUncaughtExceptionHandler(
                                (t, e) ->
                                        log.error(
                                                "Uncaught throwable in thread "
                                                        + t.getName()
                                                        + "/"
                                                        + t.getId(),
                                                e))
                        .build());
    }

    @Value.Default
    public long pingPeriod() {
        return dependency().getPingPeriod();
    }
}
