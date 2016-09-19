package com.indeed.status.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ForwardingFuture;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    public void testInterruptedExecution() throws Exception {
        final SimpleSupplier<Boolean> shouldInterrupt = new SimpleSupplier<>(false);
        final SimpleSupplier<Boolean> testInvalid = new SimpleSupplier<>(false);

        // First test what happens if the checker thread gets interrupted...
        {
            final AbstractDependencyManager manager = newDependencyManager();
            final String id = "id";
            //noinspection deprecation
            final Dependency dependency = new PingableDependency(id, "description", Urgency.REQUIRED) {
                @Override
                public void ping () throws Exception {
                    if (shouldInterrupt.get()) {
                        final Thread thread = Thread.currentThread();
                        log.info("Attempting to interrupt thread '" + thread.getName() + "'.");
                        thread.interrupt();
                        Thread.sleep(1000);

                        // Shouldn't be able to get here. Can't assert inside this method since it would
                        //  just be caught by the framework. Flag it for later failure.
                        testInvalid.set(true);
                    }

                    // Otherwise, quietly pass.
                }
            };

            manager.addDependency(dependency);

            shouldInterrupt.set(false);
            assertEquals(CheckStatus.OK, manager.evaluate(id).getStatus());
            assertTrue(!testInvalid.get());

            shouldInterrupt.set(true);
            assertEquals(CheckStatus.OUTAGE, manager.evaluate(id).getStatus());
            assertTrue(!testInvalid.get());

            // Just to confirm, make sure that we're able to reenter a good state.
            shouldInterrupt.set(false);
            assertEquals(CheckStatus.OK, manager.evaluate(id).getStatus());
            assertTrue(!testInvalid.get());
        }

        // Then check what happens if the health checker itself gets twiddled.
        {
            final DependencyChecker interruptingChecker = new InterruptingChecker(shouldInterrupt, testInvalid);
            final AbstractDependencyManager manager = new AbstractDependencyManager(null, null, interruptingChecker) {};
            manager.addDependency(DEP_ALWAYS_TRUE);

            shouldInterrupt.set(false);
            assertEquals(CheckStatus.OK, Preconditions.checkNotNull(manager.evaluate(DEP_ALWAYS_TRUE.getId())).getStatus());
            assertTrue(!testInvalid.get());

            shouldInterrupt.set(true);
            assertEquals(
                    "Expected the system to report an error if the health-check thread itself got interrupted for some reason.",
                    CheckStatus.OUTAGE, Preconditions.checkNotNull(manager.evaluate(DEP_ALWAYS_TRUE.getId())).getStatus());
            assertTrue(!testInvalid.get());

            shouldInterrupt.set(false);
            final CheckResult recovery = Preconditions.checkNotNull(manager.evaluate(DEP_ALWAYS_TRUE.getId()));
            assertEquals(
                    "Didn't recover properly from the interrupted thread: " + new ObjectMapper().writeValueAsString(recovery),
                    CheckStatus.OK, recovery.getStatus());
            assertTrue(!testInvalid.get());

        }
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
        final ExecutorService executorService = Executors.newFixedThreadPool(10);
        final AbstractDependencyManager dependencyManager = new AbstractDependencyManager(null, null, AbstractDependencyManager.newDefaultThreadPool(), new SystemReporter(), true) {};
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
        int outageCount = 0;
        for (final Future<CheckResultSet> future : futures) {
            final CheckResult checkResult = future.get().get("dep");
            if (checkResult.getStatus() == CheckStatus.OUTAGE) {
                outageCount++;
                assertEquals("Health check failed to launch due to too many checks already being in flight. Please dump /private/v and thread-state and contact dev.", checkResult.getThrowable().getMessage());
                assertEquals("Unable to ping dependency dep because there are already two previous pings that haven't returned. To turn off this behavior set throttle to false.", checkResult.getThrowable().getCause().getMessage());
            } else {
                assertEquals(CheckStatus.OK, checkResult.getStatus());
            }
        }
        assertEquals(1, outageCount);
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

    private AbstractDependencyManager newDependencyManager() throws Exception {
        return new AbstractDependencyManager() {};
    }


    private static class InterruptingExecutor extends ThreadedDependencyExecutor {
        private final SimpleSupplier<Boolean> shouldInterrupt;
        private final SimpleSupplier<Boolean> testInvalid;

        private InterruptingExecutor (final ExecutorService executor, final SimpleSupplier<Boolean> shouldInterrupt, final SimpleSupplier<Boolean> testInvalid) {
            super(executor);
            this.shouldInterrupt = shouldInterrupt;
            this.testInvalid = testInvalid;
        }

        @Override
        public Future<CheckResult> submit (final Dependency dependency) {
            final Future<CheckResult> delegate = super.submit(dependency);

            return new ForwardingFuture<CheckResult>() {
                @Override
                protected Future<CheckResult> delegate() {
                    return delegate;
                }

                @Override
                public CheckResult get () throws InterruptedException, ExecutionException {
                    doInterrupt();
                    return super.get();
                }

                @Override
                public CheckResult get (final long l, @Nonnull final TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
                    doInterrupt();
                    return super.get(l, timeUnit);
                }

                private void doInterrupt() throws InterruptedException {
                    if (shouldInterrupt.get()) {
                        Thread.currentThread().interrupt();
                        Thread.sleep(1000);

                        // Shouldn't get here.
                        testInvalid.set(true);
                    }
                }
            };
        }
    }
    private class InterruptingChecker extends DependencyChecker {
        public InterruptingChecker (final SimpleSupplier<Boolean> shouldInterrupt, final SimpleSupplier<Boolean> testInvalid) {
            super(log, new InterruptingExecutor(Executors.newSingleThreadExecutor(), shouldInterrupt, testInvalid), systemReporter, false);
        }
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
