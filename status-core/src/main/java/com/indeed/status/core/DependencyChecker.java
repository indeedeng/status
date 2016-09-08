package com.indeed.status.core;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.indeed.util.core.time.WallClock;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone evaluator of {@link Dependency} objects.
 *
 * Package-protected, because this evaluator is a convenience class for testing and not intended for
 *  modification through the public API.
 *
 * @author matts
 */
class DependencyChecker /*implements Terminable todo(cameron)*/ {
    @Nonnull private final DependencyExecutor dependencyExecutor;
    @Nonnull private final SystemReporter systemReporter;
    @Nonnull private final Logger log;
    @Nonnull private final ConcurrentMap<String, AtomicInteger> numberChecksInFlight = new ConcurrentHashMap<>();

    // For builder and subclass use only
    protected DependencyChecker(
            @Nonnull final Logger logger,
            @Nonnull final DependencyExecutor dependencyExecutor,
            @Nonnull final SystemReporter systemReporter
    ) {
        this.log = logger;
        this.dependencyExecutor = dependencyExecutor;
        this.systemReporter = systemReporter;
    }

    @Nonnull
    public SystemReporter getSystemReporter() {
        return systemReporter;
    }

    @Nonnull
    public WallClock getWallClock() {
        return this.systemReporter.getWallClock();
    }

    @Nonnull
    public CheckResultSet evaluate(final Collection<? extends Dependency> dependencies) {
        final CheckResultSet result = CheckResultSet.newBuilder()
                .setSystemReporter(systemReporter)
                .build();

        for (final Dependency dependency : dependencies) {
            evaluateAndRecord(dependency, result);
        }

        return result;
    }

    @Nullable
    public CheckResult evaluate(@Nonnull final Dependency dependency) {
        @Nonnull
        final CheckResultSet result = CheckResultSet.newBuilder()
                .setSystemReporter(systemReporter)
                .build();

        evaluateAndRecord(dependency, result);

        return result.get(dependency.getId());
    }

    private void evaluateAndRecord(@Nonnull final Dependency dependency, @Nonnull final CheckResultSet results) {
        if (dependency instanceof DependencyPinger) {
            // Evaluate directly, as the pinger provides its own timeout and exception protection
            evaluateDirectlyAndRecord((DependencyPinger) dependency, results);
        } else {
            // Evaluate safely, as the dependency offers no execution guarantees.
            evaluateSafelyAndRecord(dependency, results);
        }
    }

    // Direct evaluation appropriate to dependency pingers, which conditionally evaluate their internal dependency
    //  and cache the result and thus do not need to be executed via the checker themselves.
    private void evaluateDirectlyAndRecord(
            @Nonnull final DependencyPinger pinger, @Nonnull final CheckResultSet results
    ) {
        CheckResult checkResult = null;
        Throwable t = null;

        try {
            results.handleInit(pinger);
            results.handleExecute(pinger);

            checkResult = pinger.call();

        } catch (final Throwable e) {
            t = new CheckException("Background thread error", e);
            checkResult = null;

        } finally {
            if (null == checkResult) {
                checkResult = CheckResult.newBuilder(pinger, CheckStatus.OUTAGE, "Unable to check status of dependency; see exception.")
                        .setTimestamp(systemReporter.getWallClock().currentTimeMillis())
                        .setDuration(0L)
                        .setThrowable(t)
                        .build();
            }

            results.handleComplete(pinger, checkResult);
            results.handleFinalize(pinger, checkResult);
        }
    }

    // Standard evaluation appropriate to non-pingers, which need to be executed via the lifecycle for timeout protection
    private void evaluateSafelyAndRecord(@Nonnull final Dependency dependency, @Nonnull final CheckResultSet results) {
        final WallClock wallClock = systemReporter.getWallClock();

        final long timeout = dependency.getTimeout();
        final long timestamp = wallClock.currentTimeMillis();
        CheckResult evaluationResult = null;
        Throwable t = null;

        @Nullable
        Future<CheckResult> future = null;

        try {
            future = submit(dependency);

            results.handleInit(dependency);
            results.handleExecute(dependency);

            if (timeout > 0) {
                evaluationResult = future.get(timeout, TimeUnit.MILLISECONDS);

            } else {
                evaluationResult = future.get();

            }

        } catch (final InterruptedException e) {
            // Do NOT interrupt the current thread if the future was interrupted; record the failure and let the
            // master thread continue.
            // todo(cameron): Why would we not want to set Thread.interrupted()?
            t = new CheckException("Operation interrupted", e);
            cancel(future);

        } catch (final CancellationException e) {
            log.warn("Task has completed, but was previously cancelled. This is probably okay, but shouldn't happen often.");
            t = new CheckException("Health check task was cancelled.", e);

        } catch (final TimeoutException e) {
            log.debug("Timed out attempting to validate dependency '" + dependency.getId() + "'.");

            final long duration = wallClock.currentTimeMillis() - timestamp;

            // Cancel, but don't worry too much if it's not able to be cancelled.
            cancel(future);

            evaluationResult = CheckResult.newBuilder(dependency, CheckStatus.OUTAGE, "Timed out prior to completion")
                    .setTimestamp(timestamp)
                    .setDuration(duration)
                    .build();

        } catch (final RejectedExecutionException e) {
            t = new CheckException("Health check failed to launch a new thread due to pool exhaustion, which should not happen. Please dump /private/v and thread-state and contact dev.", e);

        } catch (final ExecutionException e) {
            //  nobody cares about the wrapping ExecutionException
            t = new CheckException("Health-check failed for unknown reason. Please dump /private/v and thread-state and contact dev.", e.getCause());

        } catch (final IllegalStateException e) {
            log.warn("Too many dependency checks are in flight.");
            t = new CheckException("Health check failed to launch due to too many checks already being in flight. Please dump /private/v and thread-state and contact dev.", e);
        } catch (final Throwable e) {
            t = new CheckException("Health-check failed for unknown reason. Please dump /private/v and thread-state and contact dev.", e);

        } finally {
            if (null == evaluationResult) {
                final long duration = wallClock.currentTimeMillis() - timestamp;

                evaluationResult = CheckResult.newBuilder(dependency, CheckStatus.OUTAGE, "Exception thrown during the evaluation of the dependency.")
                        .setTimestamp(timestamp)
                        .setDuration(duration)
                        .setPeriod(0L)
                        .setThrowable(t)
                        .build();
            }

            finalizeAndRecord(dependency, results, evaluationResult);
        }
    }

    @Nonnull
    private Future<CheckResult> submit(@Nonnull final Dependency dependency) {
        final String dependencyId = dependency.getId();
        numberChecksInFlight.putIfAbsent(dependencyId, new AtomicInteger(0));
        final AtomicInteger numberInFlight = numberChecksInFlight.get(dependencyId);
        if (numberInFlight.incrementAndGet() > 2) {
            throw new IllegalStateException(String.format("Too many checks of %s are already in flight", dependencyId));
        }
        final Future<CheckResult> future = dependencyExecutor.submit(dependency);
        return future;
    }

    private void cancel(@Nonnull final Future<?>... futures) {
        for (final Future<?> future : futures) {
            try {
                future.cancel(true);
            } catch (final Exception e) {
                log.info("failed to cancel future.", e);
            }
        }
    }

    private void finalizeAndRecord(
            @Nonnull final Dependency dependency,
            @Nonnull final CheckResultSet results,
            @Nonnull final CheckResult evaluationResult
    ) {
        try {
            dependencyExecutor.resolve(dependency);

            results.handleComplete(dependency, evaluationResult);

        } catch (final Exception e) {
            log.error("An exception that really shouldn't ever happen, did.", e);

        } finally {
            try {
                results.handleFinalize(dependency, evaluationResult);

            } catch (final Exception e) {
                log.error("Unexpected exception during supposedly safe finalization operation", e);
            } finally {
                numberChecksInFlight.get(dependency.getId()).decrementAndGet();
            }
        }
    }

    public static class DependencyExecutorSet implements DependencyExecutor {
        private static final Logger log = Logger.getLogger(DependencyExecutorSet.class);
        @Nonnull
        private final Map<String, Future<CheckResult>> inflightChecks = Maps.newHashMapWithExpectedSize(10);
        @Nonnull
        private final ExecutorService executor;

        public DependencyExecutorSet(@Nonnull final ExecutorService executor) {
            this.executor = executor;
        }

        @Override
        @Nonnull
        public Future<CheckResult> submit(final Dependency dependency) {
            final Future<CheckResult> result;

            if (log.isTraceEnabled()) {
                log.trace(String.format("Attempting to launch dependency %s from %s.", dependency, this));
            }

            synchronized (inflightChecks) {
                final String id = dependency.getId();
                final Future<CheckResult> inflight = inflightChecks.get(id);

                if (null == inflight) {
                    final Future<CheckResult> launched;

                    try {
                        launched = executor.submit(dependency);
                        inflightChecks.put(id, launched);

                    } catch (final RejectedExecutionException e) {
                        throw new IllegalStateException("Unable to launch the health check.", e);
                    }

                    result = launched;

                } else {
                    result = inflight;
                }
            }

            return result;
        }

        @Override
        public void resolve(@Nonnull final Dependency dependency) {
            synchronized (inflightChecks) {
                inflightChecks.remove(dependency.getId());
            }
        }

        @Override
        public boolean isShutdown() {
            return executor.isShutdown();
        }

        @Override
        public void shutdown() {
            executor.shutdownNow();
        }

        @Override
        public void awaitTermination(final long duration, final TimeUnit unit) throws InterruptedException {
            executor.awaitTermination(duration, unit);
        }
    }

    /*@Override todo(cameron) */
    public void shutdown() {
        dependencyExecutor.shutdown();
    }

    public static class CheckException extends Exception {
        private static final long serialVersionUID = -5161759492011453513L;

        private CheckException(final String message, final Throwable cause) {
            super(message, cause);
        }

        @SuppressWarnings({"UnusedDeclaration"})
        private CheckException(final Throwable cause) {
            super(cause);
        }
    }

    @Nonnull
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private static final Logger DEFAULT_LOGGER = Logger.getLogger(DependencyChecker.class);

        @Nonnull
        private Logger _logger = DEFAULT_LOGGER;
        @Nullable
        private ExecutorService executorService;
        @Nonnull
        private SystemReporter systemReporter = new SystemReporter();

        private Builder() {
        }

        public Builder setLogger(@Nullable final Logger logger) {
            this._logger = Objects.firstNonNull(logger, DEFAULT_LOGGER);
            return this;
        }

        public Builder setExecutorService(@Nonnull final ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public Builder setSystemReporter(@Nonnull final SystemReporter systemReporter) {
            this.systemReporter = systemReporter;
            return this;
        }

        public DependencyChecker build() {
            final ExecutorService executorService = Preconditions.checkNotNull(
                    this.executorService,
                    "Cannot configure a dependency checker with a null executor service.");
            final DependencyExecutor dependencyExecutor = new DependencyExecutorSet(executorService);

            return new DependencyChecker(_logger, dependencyExecutor, systemReporter);
        }
    }
}
