package com.indeed.status.core;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.indeed.status.core.CheckResult.Thrown;
import com.indeed.status.core.DependencyChecker.DependencyExecutorSet;
import com.indeed.util.core.time.StoppedClock;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author matts
 */
@SuppressWarnings ({"ThrowableResultOfMethodCallIgnored", "ConstantConditions"})
public class TestHealthcheckFramework {
    private final  StoppedClock wallClock = new StoppedClock();
    private final SystemReporter systemReporter = new SystemReporter(wallClock);

    private static final class SimpleSupplier<T> implements Supplier<T> {
            private final AtomicReference<T> instance;

        public SimpleSupplier (final T instance) {
            this.instance = new AtomicReference<>(instance);
        }

        public T get() {
            return instance.get();
        }

        public void set(final T instance) {
            this.instance.set(instance);
        }
    }

    private static final Logger log = Logger.getLogger ( TestHealthcheckFramework.class );
    private static final PingableDependency DEP_ALWAYS_TRUE = new AlwaysTrueDependencyBuilder().build();

    /// Had trouble in production with one of the futured tasks getting cancelled.
    /// Simulate the error here so that we can make sure the surrounding framework
    ///  deals with these properly.
    @Test
    public void testCancelledExecution() throws Exception {
        final SimpleSupplier<Boolean> shouldCancel = new SimpleSupplier<>(true);

        //noinspection deprecation
        final Dependency longDependency = new PingableDependency("dep", "description", Urgency.REQUIRED) {
            @Override
            public void ping () throws Exception {
                // Sleep for long enough that the cancellation should stick.
                Thread.sleep(1000);
            }
        };
        final DependencyChecker cancellingChecker = new CancelingChecker(shouldCancel);
        final AbstractDependencyManager manager = new AbstractDependencyManager("", log, cancellingChecker){};
        manager.addDependency(longDependency);


        // Make sure the first attempt at this FAILED, since the task was cancelled.
        shouldCancel.set(true);
        assertEquals(CheckStatus.OUTAGE, manager.evaluate(longDependency.getId()).getStatus());


        // Now make sure that a second attempt with no cancelled task succeeds.
        // (Prior to the implementation of COMMON-217, this failed)
        shouldCancel.set(false);
        assertEquals(CheckStatus.OK, manager.evaluate(longDependency.getId()).getStatus());
    }

    @Test
    public void testInterruptedExceptionInPing() {
        final SimplePingableDependency dependency = new SimplePingableDependency.Builder()
                .setId("an-id")
                .setDescription("a-description")
                .setPingMethod(() -> {
                    throw new InterruptedException("a-test-interrupted-exception");
                })
                .build();

        final AbstractDependencyManager manager = newDependencyManager();
        manager.addDependency(
                dependency
        );

        try {
            assertEquals(CheckStatus.OUTAGE, manager.evaluate(dependency.getId()).getStatus());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testEvaluateRespsectsInterruption() throws InterruptedException {
        final AtomicBoolean pingCanceled = new AtomicBoolean(false);
        final SimplePingableDependency dependency = new SimplePingableDependency.Builder()
                .setId("an-id")
                .setDescription("a-description")
                .setPingMethod((Runnable) () -> {
                    final int forever = 42_000_000;
                    try {
                        Thread.sleep(forever);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        pingCanceled.set(true);
                    }
                })
                .build();

        final CheckResult checkResult;
        final boolean interrupted;
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final AbstractDependencyManager manager = new AbstractDependencyManager(
                    "an-app",
                    log,
                    new DependencyChecker(log, new ThreadedDependencyExecutor(executor), systemReporter, false)
            ) {
            };
            manager.addDependency(dependency);

            Thread.currentThread().interrupt();
            checkResult = manager.evaluate(dependency.getId());
        } finally {
            interrupted = Thread.interrupted();
            executor.shutdownNow();
            assertTrue(
                    "Executor did not shut down fast enough; " +
                            "see if the submitted task respects interruption",
                    executor.awaitTermination(1, TimeUnit.SECONDS)
            );
        }

        assertTrue("manager.evaluate() reset interrupted flag unexpectedly", interrupted);

        assertNotNull(checkResult);
        assertEquals(CheckStatus.OUTAGE, checkResult.getStatus());
    }

    /// Make sure timeout checks work the way that we expect.
    @Test
    public void testSubmissionDeduplication() throws Exception {
        final int timeoutInMS = 500;
        final SimpleSupplier<Boolean> ignored = new SimpleSupplier<>(false);
        final Dependency sampleDependency = new SleepyDependency("sample", timeoutInMS, ignored);

        final DependencyExecutorSet executors = new DependencyExecutorSet(Executors.newSingleThreadExecutor());
        final Future<CheckResult> firstFuture = executors.submit(sampleDependency);
        final Future<CheckResult> secondFuture = executors.submit(sampleDependency);
        assertTrue("Expected the SAME object to be retrieved for back-to-back submissions", firstFuture == secondFuture);
    }

    @Test
    public void testTimeouts() throws Exception {
        final int timeoutInMS = 500;
        final SimpleSupplier<Boolean> lengthyCheckCompleted = new SimpleSupplier<>(false);
        final Dependency lengthyDependency = new SleepyDependency("lengthy", timeoutInMS, lengthyCheckCompleted);

        final int arbitraryUnusedPingPeriod = 30000;
        final Dependency timeoutDependency = new AbstractDependency(
                "timeout", "", timeoutInMS, arbitraryUnusedPingPeriod, Urgency.REQUIRED, DependencyType.StandardDependencyTypes.OTHER, ""
        ) {
            @Override
            public CheckResult call () throws Exception {
                return lengthyDependency.call();
            }
        };

        final AbstractDependencyManager manager = newDependencyManager();
        manager.addDependency(lengthyDependency);
        manager.addDependency(timeoutDependency);

        final CheckResult okResult = manager.evaluate(lengthyDependency.getId());
        assertEquals(CheckStatus.OK, Preconditions.checkNotNull(okResult.getStatus()));

        // reset the flag indicating that the long pole was achieved.
        lengthyCheckCompleted.set(false);
        long elapsed = -System.currentTimeMillis();
        final CheckResult timeoutResult = Preconditions.checkNotNull(manager.evaluate(timeoutDependency.getId()));
        final boolean wasLengthyCheckCompleted = lengthyCheckCompleted.get();
        elapsed += System.currentTimeMillis();

        assertEquals(
                "Expected the test to timeout, which should result in an outage.",
                CheckStatus.OUTAGE, timeoutResult.getStatus());
        assertTrue(
                "Expected the test to timeout well before the actual length of the dependency, within say two timeout periods. " +
                        "Instead, it took " + elapsed + "ms",
                elapsed <= (2.0 * timeoutInMS));

        assertTrue(
                "Expected that the lengthy check would not be completed, but somehow the bit got flipped inside of " + elapsed + "ms.",
                !wasLengthyCheckCompleted);
    }

    @SuppressWarnings ("ConstantConditions")
    @Test
    public void testPingerInvariants () {
        final SimpleSupplier<Throwable> excToThrow = new SimpleSupplier<>(null);
        final Dependency dependency = SimpleDependency.newBuilder()
                .setId("id")
                .setDescription("")
                .setTimeout(100)
                .setPingPeriod(100)
                .setUrgency(Urgency.REQUIRED)
                .setCheckMethod(new CheckMethod() {
                    @Override
                    @Nonnull
                    public CheckResult call(@Nonnull final Dependency dependency) throws Exception {
                        final Throwable throwable = excToThrow.get();
                        if (null != throwable) {
                            if (throwable instanceof Exception) {
                                throw (Exception)throwable;
                            } else if(throwable instanceof Error) {
                                throw (Error)throwable;
                            } else {
                                throw new Exception(throwable);
                            }
                        }

                        return CheckResult.newBuilder(dependency, CheckStatus.OK, "").build();
                    }
                })
                .build();

        final DependencyPinger pinger = new DependencyPinger(dependency, systemReporter);

        // Call the pinger to get a result BEFORE executing the pinger.
        final CheckResult checkResult = pinger.call();
        Assert.assertNotNull("Expected that the pinger would auto-execute, or at least not return null.", checkResult);
        assertEquals(CheckStatus.OK, checkResult.getStatus());

        // Test to make sure that exceptions thrown inside the dependency evaluation do not pierce the barrier
        excToThrow.set(new IOException(new NullPointerException()));
        pinger.run();
        final Thrown thrownInternal = pinger.call().getThrown();
        assertTrue(thrownInternal.getException().equals("IOException"));
        Assert.assertNotNull(thrownInternal.getThrown().getException());

        // ... even if they are runtime exceptions
        excToThrow.set(new NullPointerException());
        pinger.run();
        assertTrue(pinger.call().getThrown().getException().equals("NullPointerException"));

        // ... or errors
        excToThrow.set(new Error());
        pinger.run();
        assertTrue(pinger.call().getThrown().getException().equals("Error"));

        assertEquals(1, pinger.getTotalSuccesses());
        assertEquals(3, pinger.getTotalFailures());
        // Make sure we continue to produce the event counter unless we explicitly opt out.
        assertTrue(pinger.getFailures().startsWith("3,"));
    }

    @Test(expected=IllegalStateException.class)
    public void testInitializationFailure() throws Exception {
        final CheckResultSet resultSet = CheckResultSet.newInstance();
        final Dependency dependency = DEP_ALWAYS_TRUE;

        resultSet.handleInit(dependency);
        resultSet.handleExecute(dependency);

        // Should explode since initialization cannot happen twice.
        resultSet.handleInit(dependency);
    }

    @Test(expected=IllegalStateException.class)
    public void testExecutionFailure() throws Exception {
        final CheckResultSet resultSet = CheckResultSet.newInstance();
        final Dependency dependency = DEP_ALWAYS_TRUE;

        resultSet.handleInit(dependency);
        resultSet.handleExecute(dependency);

        // Should explode since execution cannot happen twice.
        resultSet.handleExecute(dependency);
    }

    @Test(expected = IllegalStateException.class)
    public void testDownstreamFromExecutionFailure() throws Exception {
        Logger.getLogger(CheckResultSet.class).setLevel(Level.FATAL);

        final CheckResultSet resultSet = CheckResultSet.newInstance();
        final Dependency dependency = DEP_ALWAYS_TRUE;

        final CheckResult failureResult = CheckResult.newBuilder(dependency, CheckStatus.MINOR, "Expected a failure").build();
        // This should pass since the result accurately notes the failure of the execution.
        resultSet.handleComplete(dependency, failureResult);

        final CheckResult okResult = CheckResult.newBuilder(dependency, CheckStatus.OK, "").build();

        // Expect this to fail due to status/result mismatch
        resultSet.handleComplete(dependency, okResult);
    }

    @Test
    public void testCustomWallClock() throws InterruptedException {
        final long now = System.currentTimeMillis(); // arbitrary time
        this.wallClock.set(now);

        final DependencyChecker checkerWithStoppedClock = DependencyChecker.newBuilder()
                .setExecutorService(Executors.newSingleThreadExecutor())
                .setSystemReporter(systemReporter)
                .build();

        // Allow the system clock to drift from the stopped clock, thus asserting that any epoch millis values
        //  that have changed are due to the incorrect use of a DefaultWallClock somewhere.
        Thread.sleep(10);
        assertTrue(
                "Failed to advance the system clock by sleeping; remainder of test invalid.",
                System.currentTimeMillis() > now);

        final PingableDependency dependency = new AlwaysTrueDependencyBuilder().setWallClock(wallClock).build();
        final CheckResultSet resultSet = checkerWithStoppedClock.evaluate(ImmutableList.of(dependency));

        final long recordedStartTime = resultSet.getStartTimeMillis();
        assertEquals(
                "Expected the check result set to be pinned to the given wall clock time, not the current moment in time.",
                now, recordedStartTime);

        final Collection<CheckResult> completed = resultSet.getCompleted();
        assertEquals(
                "Expected all results to be completed",
                1, completed.size());
        final CheckResult result = completed.iterator().next();
        assertEquals(
                "Expected the timestamp for the result to be equal to the wall-clock time, not the actual execution time",
                now, result.getTimestamp());
        assertEquals(
                "Expected the recorded execution date to match the given wall clock, not the system time",
                CheckResult.DATE_FORMAT.get().format(new Date(now)), result.getDate());
    }

    @Test
    public void testConcurrentDependencyChecksWithSameID() throws Exception {
        final AbstractDependencyManager dependencyManager = new AbstractDependencyManager(null, null, AbstractDependencyManager.newDefaultThreadPool(), new SystemReporter(), true) {};
        final Dependency longDependency = new PingableDependency("dep", "description", 100, Urgency.REQUIRED) {
            @Override
            public void ping() throws Exception {
                long endTime = System.currentTimeMillis() + 1000;
                while (System.currentTimeMillis() < endTime) {
                }
            }
        };
        dependencyManager.addDependency(longDependency);

        final List<CheckResultSet> evaluateResults = new LinkedList<>();
        evaluateResults.add(dependencyManager.evaluate());
        evaluateResults.add(dependencyManager.evaluate());
        evaluateResults.add(dependencyManager.evaluate());

        int timeoutCount = 0;
        for (final CheckResultSet result : evaluateResults) {
            final CheckResult checkResult = result.get("dep");
            assertEquals(CheckStatus.OUTAGE, checkResult.getStatus());
            if (checkResult.getErrorMessage().equals("Timed out prior to completion")) {
                timeoutCount++;
            } else {
                assertEquals("Exception thrown during ping", checkResult.getErrorMessage());
                assertEquals(IllegalStateException.class, checkResult.getThrowable().getClass());
                assertEquals("Unable to ping dependency dep because there are already two previous pings that haven't "
                                + "returned. To turn off this behavior set throttle to false.", checkResult.getThrowable().getMessage());
            }
        }
        assertEquals(2, timeoutCount);
    }

    @Test
    public void testConcurrentDependencyChecksWithSameIDNoThrottle() throws Exception {
        final ExecutorService executorService = Executors.newFixedThreadPool(10);
        final AbstractDependencyManager dependencyManager = newDependencyManager();
        final Dependency longDependency = new PingableDependency("dep", "description", Urgency.REQUIRED) {
            @Override
            public void ping() throws Exception {
                Thread.sleep(1000);
            }
        };
        dependencyManager.addDependency(longDependency);

        final Callable<CheckResultSet> testcallable = new Callable<CheckResultSet>() {
            @Override
            public CheckResultSet call() throws Exception {
                return dependencyManager.evaluate();
            }
        };

        final List<Future<CheckResultSet>> futures = executorService.invokeAll(ImmutableList.of(testcallable, testcallable, testcallable));
        for (final Future<CheckResultSet> future : futures) {
            final CheckResult checkResult = future.get().get("dep");
            assertEquals(CheckStatus.OK, checkResult.getStatus());
        }
    }

    @Test
    public void testDependencyManagerWithDependencyPingerDoesNotCreateNewThreads() throws Exception {
        final DependencyExecutor dependencyExecutor = new DependencyExecutor() {
            @Override
            public Future<CheckResult> submit(final Dependency dependency) {
                throw new AssertionError("Test should not be calling anything on the executor");
            }

            @Override
            public void resolve(final Dependency dependency) {
                throw new AssertionError("Test should not be calling anything on the executor");
            }

            @Override
            public void shutdown() {
                throw new AssertionError("Test should not be calling anything on the executor");
            }

            @Override
            public boolean isShutdown() {
                throw new AssertionError("Test should not be calling anything on the executor");
            }

            @Override
            public void awaitTermination(final long duration, final TimeUnit unit) throws InterruptedException {
                throw new AssertionError("Test should not be calling anything on the executor");
            }
        };
        final Dependency dependency = new DependencyPinger(new AlwaysTrueDependencyBuilder().build());
        final CheckResult result = dependency.call();
        assertEquals(CheckStatus.OK, result.getStatus());

        final DependencyChecker dependencyCheckerThrottled = new DependencyChecker(log, dependencyExecutor, new SystemReporter(), true);
        final AbstractDependencyManager dependencyManagerThrottled = new AbstractDependencyManager(null, null, AbstractDependencyManager.newDefaultThreadPool(), dependencyCheckerThrottled) {};
        dependencyManagerThrottled.addDependency(dependency);
        final CheckResultSet resultSet = dependencyManagerThrottled.evaluate();
        final CheckResultSet resultSet2 = dependencyManagerThrottled.evaluate();
        assertEquals(CheckStatus.OK, resultSet.getSystemStatus());
        assertEquals(CheckStatus.OK, resultSet2.getSystemStatus());

        final DependencyChecker dependencyCheckerNotThrottled = new DependencyChecker(log, dependencyExecutor, new SystemReporter(), false);
        final AbstractDependencyManager dependencyManagerNotThrottled = new AbstractDependencyManager(null, null, AbstractDependencyManager.newDefaultThreadPool(), dependencyCheckerNotThrottled) {};
        dependencyManagerNotThrottled.addDependency(dependency);
        final CheckResultSet resultSet3 = dependencyManagerNotThrottled.evaluate();
        final CheckResultSet resultSet4 = dependencyManagerNotThrottled.evaluate();
        assertEquals(CheckStatus.OK, resultSet3.getSystemStatus());
        assertEquals(CheckStatus.OK, resultSet4.getSystemStatus());
    }

    private AbstractDependencyManager newDependencyManager() {
        return new AbstractDependencyManager() {};
    }

    private static class CancelingExecutor extends ThreadedDependencyExecutor {
        private final SimpleSupplier<Boolean> shouldCancel;

        private CancelingExecutor (final ExecutorService executor, final SimpleSupplier<Boolean> shouldCancel) {
            super(executor);
            this.shouldCancel = shouldCancel;
        }

        @Override
        public Future<CheckResult> submit (final Dependency dependency) {
            final Future<CheckResult> result = super.submit(dependency);
            if (shouldCancel.get()) {
                result.cancel(true);
            }
            return result;
        }
    }
    private class CancelingChecker extends DependencyChecker {
        public CancelingChecker (final SimpleSupplier<Boolean> shouldCancel) {
            super(log, new CancelingExecutor(Executors.newSingleThreadExecutor(), shouldCancel), systemReporter, false);
        }
    }

    private static class SleepyDependency extends PingableDependency {
        private final int timeoutInMS;
        private final SimpleSupplier<Boolean> lengthyCheckCompleted;

        public SleepyDependency(final String id, final int timeoutInMS, final SimpleSupplier<Boolean> lengthyCheckCompleted) {
            //noinspection deprecation
            super(id, "", Urgency.REQUIRED);
            this.timeoutInMS = timeoutInMS;
            this.lengthyCheckCompleted = lengthyCheckCompleted;
        }

        @Override
        public void ping () throws Exception {
            final long startTime = System.currentTimeMillis();

            try {
                final int sleepIncrement = 250;

                for (int i = 0; i < (6 * timeoutInMS); i+=sleepIncrement) {
                    Thread.sleep(sleepIncrement);
                }

                // shouldn't have gotten here.
                lengthyCheckCompleted.set(true);

            } finally {
                log.info(String.format("Evaluated lengthy dependency for %d ms from thread %s.",
                        System.currentTimeMillis() - startTime,
                        Thread.currentThread().getName()));
            }
        }
    }

    private static class MinimalDependencyBuilder extends SimplePingableDependency.Builder {
        // Defaults for convenience
        public MinimalDependencyBuilder() {
            this.setId("id");
            this.setDescription("description");
        }
    }

    private static class AlwaysTrueDependencyBuilder extends MinimalDependencyBuilder {
        public AlwaysTrueDependencyBuilder() {
            this.setPingMethod(new Runnable() {
                @Override
                public void run() {
                    // Always ok
                }
            });
        }
    }
}
