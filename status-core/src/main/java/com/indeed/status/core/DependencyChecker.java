package com.indeed.status.core;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
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

/**
 * Standalone evaluator of {@link Dependency} objects.
 *
 * @author matts
 */
class DependencyChecker /*implements Terminable todo(cameron)*/ {
    @Nonnull
    private final DependencyExecutor dependencyExecutor;
    @SuppressWarnings ({"FieldCanBeLocal", "UnusedDeclaration"})
    @Nonnull
    private final Logger log;

    public DependencyChecker (
            @Nonnull final Logger logger,
            @Nonnull final DependencyExecutor dependencyExecutor
    ) {
        this.log = logger;
        this.dependencyExecutor = dependencyExecutor;
    }

    @Nonnull
    public CheckResultSet evaluate(final Collection<Dependency> dependencies) {
        final CheckResultSet result = new CheckResultSet();

        for ( final Dependency dependency : dependencies ) {
            evaluateAndRecord(dependency, result);
        }

        return result;
    }

    @Nullable
    public CheckResult evaluate(@Nonnull final Dependency dependency) {
        @Nonnull
        final CheckResultSet result = new CheckResultSet();
        evaluateAndRecord(dependency, result);

        return result.get(dependency.getId());
    }

    private void evaluateAndRecord(@Nonnull final Dependency dependency, @Nonnull final CheckResultSet results) {
        if (dependency instanceof DependencyPinger) {
            // Evaluate directly, as the pinger provides its own timeout and exception protection
            evaluateDirectlyAndRecord((DependencyPinger)dependency, results);
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

        } catch(Throwable e) {
            t = new CheckException("Background thread error", e);
            checkResult = null;

        } finally {
            if (null == checkResult) {
                checkResult = CheckResult.newBuilder(pinger, CheckStatus.OUTAGE, "Unable to check status of dependency; see exception.")
                        .setTimestamp(System.currentTimeMillis())
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
        final long timeout = dependency.getTimeout();

        final long timestamp = System.currentTimeMillis();
        CheckResult evaluationResult = null;
        Throwable t = null;

        @Nonnull
        Future<CheckResult> future = null;

        try {
            future = dependencyExecutor.submit(dependency);

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

        } catch ( final CancellationException e ) {
            log.warn("Task has completed, but was previously cancelled. This is probably okay, but shouldn't happen often.");
            t = new CheckException("Health check task was cancelled.", e);

        } catch (final TimeoutException e) {
            log.debug("Timed out attempting to validate dependency '" + dependency.getId() + "'.");

            final long duration = System.currentTimeMillis() - timestamp;

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

        } catch(Throwable e) {
            t = new CheckException("Health-check failed for unknown reason. Please dump /private/v and thread-state and contact dev.", e);

        } finally {
            if (null == evaluationResult) {
                final long duration = System.currentTimeMillis() - timestamp;

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

    private void cancel(@Nonnull final Future<?>... futures) {
        for (final Future<?> future: futures) {
            try {
                future.cancel(true);
            } catch(Exception e) {
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

            results.handleComplete (dependency, evaluationResult);

        } catch(Exception e) {
            log.error("An exception that really shouldn't ever happen, did.", e);

        } finally {
            try {
                results.handleFinalize(dependency, evaluationResult);

            } catch (Exception e) {
                log.error("Unexpected exception during supposedly safe finalization operation", e);
            }
        }
    }

    public static class DependencyExecutorSet implements DependencyExecutor {
        private static final Logger log = Logger.getLogger ( DependencyExecutorSet.class );
        @Nonnull
        private final Map<String, Future<CheckResult>> inflightChecks = Maps.newHashMapWithExpectedSize(10);
        @Nonnull
        private final ExecutorService executor;

        public DependencyExecutorSet (@Nonnull final ExecutorService executor) {
            this.executor = executor;
        }

        @Override
        @Nonnull
        public Future<CheckResult> submit(final Dependency dependency) {
            final Future<CheckResult> result;

            if (log.isTraceEnabled()) {
                log.trace(String.format("Attempting to launch dependency %s from %s.", dependency, this));
            }

            synchronized ( inflightChecks ) {
                final String id = dependency.getId();
                final Future<CheckResult> inflight = inflightChecks.get(id);

                if ( null == inflight ) {
                    Future<CheckResult> launched;

                    try {
                        launched = executor.submit(dependency);
                        inflightChecks.put(id, launched);

                    } catch ( RejectedExecutionException e ) {
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
        public void shutdown () {
            executor.shutdownNow();
        }

        @Override
        public void awaitTermination (final long duration, final TimeUnit unit) throws InterruptedException {
            executor.awaitTermination(duration, unit);
        }
    }

    /*@Override todo(cameron) */
    public void shutdown () {
            dependencyExecutor.shutdown();
    }

    public static class CheckException extends Exception {
        private static final long serialVersionUID = -5161759492011453513L;

        private CheckException(final String message, final Throwable cause) {
            super(message, cause);
        }

        @SuppressWarnings ({"UnusedDeclaration"})
        private CheckException(Throwable cause) {
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
        @Nonnull
        private ExecutorService executorService;

        private Builder() {
        }

        public Builder setLogger (@Nullable final Logger logger) {
            this._logger = Objects.firstNonNull(logger, DEFAULT_LOGGER);
            return this;
        }

        public Builder setExecutorService (@Nonnull final ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public DependencyChecker build() {
            final DependencyExecutor dependencyExecutor = new DependencyExecutorSet(executorService);

            return new DependencyChecker(_logger, dependencyExecutor);
        }
    }
}
