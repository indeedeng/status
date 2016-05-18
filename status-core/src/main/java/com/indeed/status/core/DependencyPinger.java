package com.indeed.status.core;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.indeed.util.core.LongRecentEventsCounter;
import com.indeed.util.core.time.DefaultWallClock;
import com.indeed.util.core.time.WallClock;
import com.indeed.util.varexport.Export;
import com.indeed.util.varexport.VarExporter;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The {@link DependencyPinger} is a modification of the HCv2 ServicePinger class that keeps the
 *  idea of consecutive failures and slowly downgrading status, but uses abstractions from the HCv3
 *  system.
 *
 * @author matts Cloned and modified from ServicePinger.java
 */
@VisibleForTesting /* Do not use in production code outside webapp-common */
public class DependencyPinger implements Dependency, StatusUpdateProducer, Runnable {
    private static final Logger log = Logger.getLogger(DependencyPinger.class);

    /// The ping period respected by this dependency. This will be equal to the particular ping period for the
    ///  underlying dependency or the specified override, depending on the constructor used.
    private final long pingPeriod;
    @SuppressWarnings ({"FieldCanBeLocal"})
    private final int consecutiveFailureThreshold = 3;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong totalSuccesses = new AtomicLong();
    private final AtomicLong totalFailures = new AtomicLong();
    private final LongRecentEventsCounter failuresOverTime = new LongRecentEventsCounter(LongRecentEventsCounter.MINUTE_TICKER, 60);
    private final StatusUpdateDelegate updateHandler = new StatusUpdateDelegate();
    @Nullable
    private volatile CheckResult lastResult = null;
    private transient Throwable lastThrown = null;
    private final WallClock wallClock;
    private transient long lastDuration = 0L;
    private transient long lastExecuted = 0L;
    private transient long lastKnownGood = 0L;

    @Nonnull
    private final DependencyChecker checker;
    @Nonnull
    private final Dependency dependency;

    /**
     * @deprecated Use {@link #DependencyPinger(Dependency, WallClock)} instead.
     */
    public DependencyPinger(@Nonnull final Dependency dependency) {
        this(dependency, new DefaultWallClock());
    }

    DependencyPinger(
            @Nonnull final Dependency dependency,
            @Nonnull final WallClock wallClock
    ) {
        this(Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setNameFormat("dependency-pinger-%d")
                .setDaemon(true)
                .setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(final Thread t, final Throwable e) {
                        log.error("Uncaught throwable in thread " + t.getName() + "/" + t.getId(), e);
                    }
                })
                .build()
        ), dependency, wallClock);
    }

    DependencyPinger(
            @Nonnull final ExecutorService executorService,
            @Nonnull final Dependency dependency,
            @Nonnull final WallClock wallClock
    ) {
        this(executorService, dependency, dependency.getPingPeriod(), wallClock);
    }

    DependencyPinger (
            @Nonnull final ExecutorService executorService,
            @Nonnull final Dependency dependency,
            final long pingPeriod,
            @Nonnull final WallClock wallClock
    ) {
        this.checker = DependencyChecker.newBuilder()
                .setExecutorService(executorService)
                .setLogger(log)
                .setWallClock(wallClock)
                .build();
        this.dependency = dependency;
        this.pingPeriod = pingPeriod;
        this.wallClock = wallClock;

        VarExporter.forNamespace(DependencyPinger.class.getSimpleName() + "-" + this.dependency.getId()).includeInGlobal().export(this, "");
    }

    /**
     * The method intended to be executed by a worker thread; no result is returned directly here, it is simply
     *  cached for use by systems.
     *
     */
    @Override
    public void run () {
        // The result of a live execution
        CheckResult currentResult;

        synchronized(this) {
            try {
                lastExecuted = wallClock.currentTimeMillis();
                currentResult = checker.evaluate(dependency);

                if (null != currentResult && currentResult.getStatus() == CheckStatus.OK) {
                    currentResult = handleSuccess(currentResult);

                } else {
                    // Replace the result of the evaluation depending on the number of consecutive
                    //  failures, etc.
                    currentResult = handleFailure(currentResult, null);

                }

            } catch (final Throwable t) {
                currentResult = handleFailure(null, t);
            }

            notifyListeners(currentResult);
            lastResult = currentResult;
        }
    }

    /**
     * The method, derived from {@link Dependency} intended to be used by elements that check the status of the
     *  background task.
     */
    @Override
    @Nonnull
    public CheckResult call() {
        if (null == lastResult) {
            synchronized (this) {
                if (null == lastResult) {
                    run();
                }

                return checkNotNull(lastResult, "No result available after inline execution");
            }
        }

        // this will not be null, because lastResult goes from null -> notnull and never back to null
        //noinspection ConstantConditions
        return lastResult;
    }

    private void notifyListeners(@Nonnull final CheckResult currentResult) {
        // calling getStatus is safe because lastResult goes from null -> notnull and never back to null
        //noinspection ConstantConditions
        if (null == lastResult || lastResult.getStatus() != currentResult.getStatus()) {
            updateHandler.onChanged(this, lastResult, currentResult);
        }
    }

    private CheckResult handleSuccess(@Nonnull final CheckResult reportedResult) {
        if (consecutiveFailures.get() != 0) {
            consecutiveFailures.set(0);
        }
        totalSuccesses.incrementAndGet();
        lastDuration = wallClock.currentTimeMillis() - lastExecuted;
        lastKnownGood = lastExecuted;
        lastThrown = null;

        return CheckResult.newBuilder(this, reportedResult)
                .setTimestamp(lastExecuted)
                .setDuration(lastDuration)
                .setLastKnownGoodTimestamp(lastKnownGood)
                .setThrowable(null)
                .build();
    }

    private CheckResult handleFailure(@Nullable final CheckResult reportedResult, @Nullable final Throwable t) {
        consecutiveFailures.incrementAndGet();
        totalFailures.incrementAndGet();
        synchronized (failuresOverTime) {
            failuresOverTime.increment();
        }
        lastDuration = wallClock.currentTimeMillis() - lastExecuted;
        //noinspection ThrowableResultOfMethodCallIgnored
        lastThrown = null == reportedResult ? t  : (null == t ? reportedResult.getThrowable() : t);

        // The worst possible status this failure can generate. If the caller is using a full-fledged dependency rather than
        // an up/down exception-thrower, then we don't want to report an OUTAGE when it's simply been a MINOR degradation
        // for a while.
        final CheckStatus worstPossibleStatus = null == reportedResult ? CheckStatus.OUTAGE : reportedResult.getStatus();
        return newFailureNotice(worstPossibleStatus, reportedResult);
    }

    @Nonnull
    private CheckResult newFailureNotice(@Nonnull final CheckStatus outageStatus, @Nullable final CheckResult failedResult) {
        final CheckResult result;

        if (consecutiveFailures.get() < consecutiveFailureThreshold && totalSuccesses.get() > 0) {
            if (log.isDebugEnabled()) {
                log.debug ( "Most recent call to '" + dependency.getId() + "' was a failure, but recent pings succeeded. Noting impairment." );
            }

            // The most recent execution was a failure, but prior executions succeeded. Flag the
            //  target system as impaired on the assumption that it will respond properly on the
            //  next execution.
            result = CheckResult.newBuilder(this, CheckStatus.MINOR, getFailureMessage(failedResult, lastThrown))
                    .setTimestamp(lastExecuted)
                    .setDuration(lastDuration)
                    .setLastKnownGoodTimestamp(lastKnownGood)
                    .setPeriod(pingPeriod)
                    .setThrowable(lastThrown)
                    .build();

        } else {
            if (log.isDebugEnabled()) {
                log.debug ( "No recent pings to '" + getId() + "' have succeeded. Noting unavailability." );
            }

            // Use whatever the reported status was.
            result = CheckResult.newBuilder(this, outageStatus, getFailureMessage(failedResult, lastThrown))
                    .setTimestamp(lastExecuted)
                    .setDuration(lastDuration)
                    .setLastKnownGoodTimestamp(lastKnownGood)
                    .setPeriod(pingPeriod)
                    .setThrowable(lastThrown)
                    .build();
        }

        return result;
    }

    @Nonnull
    private static String getFailureMessage(@Nullable final CheckResult result, @Nullable final Throwable thrown) {
        if (null != thrown) {
            final String thrownMessage = thrown.getMessage();
            if (!Strings.isNullOrEmpty(thrownMessage)) {
                return thrownMessage;
            }
        }

        if (null != result) {
            final String resultMessage = result.getErrorMessage();
            if (!Strings.isNullOrEmpty(resultMessage)) {
                return resultMessage;
            }
        }

        // Fallback of fallbacks
        return "Timed out";
    }

    @Nonnull
    @Override
    public String toString() {
        return "background pinger for " + dependency;
    }

    @Export(name = "total-failures")
    public long getTotalFailures() {
        return totalFailures.get();
    }

    @Export(name = "total-successes")
    public long getTotalSuccesses() {
        return totalSuccesses.get();
    }

    @Export(name = "failures", doc = "Rolling count of recent failures")
    public String getFailures() {
        synchronized (failuresOverTime) {
            return failuresOverTime.toString();
        }
    }

    @Override
    public long getPingPeriod () {
        return pingPeriod;
    }

    @Override
    public String getId () {
        return dependency.getId();
    }

    @Override
    public String getDescription () {
        return dependency.getDescription();
    }

    @Override
    public String getDocumentationUrl () {
        return dependency.getDocumentationUrl();
    }

    @Override
    public long getTimeout () {
        return dependency.getTimeout();
    }

    @Override
    public Urgency getUrgency () {
        return dependency.getUrgency();
    }

    @Override
    public DependencyType getType () {
        return dependency.getType();
    }

    @Override
    public String getServicePool () {
        return dependency.getServicePool();
    }

    @Override
    public void clear () {
        updateHandler.clear();
    }

    @Override
    public void addListener (final StatusUpdateListener listener) {
        updateHandler.addListener(listener);
    }

    @Override
    public Iterator<StatusUpdateListener> listeners() {
        return updateHandler.listeners();
    }

    // Access to the pinged dependency
    @Nonnull
    public Dependency getDependency() {
        return dependency;
    }
}
