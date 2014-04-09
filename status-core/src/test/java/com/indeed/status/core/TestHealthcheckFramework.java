package com.indeed.status.core;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.util.concurrent.ForwardingFuture;
import com.indeed.status.core.CheckResult.Thrown;
import com.indeed.status.core.DependencyChecker.DependencyExecutorSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

/**
 * @author matts
 */
@SuppressWarnings ({"ThrowableResultOfMethodCallIgnored", "ConstantConditions"})
public class TestHealthcheckFramework {
    private static final class SimpleSupplier<T> implements Supplier<T> {
            private AtomicReference<T> instance;

        public SimpleSupplier (final T instance) {
            this.instance = new AtomicReference<T>(instance);
        }

        public T get() {
            return instance.get();
        }

        public void set(final T instance) {
            this.instance.set(instance);
        }
    }

    private static final Logger log = Logger.getLogger ( TestHealthcheckFramework.class );
    @SuppressWarnings("deprecation")
    private static final PingableDependency DEP_ALWAYS_TRUE = new PingableDependency("alwaysTrue", "description", Urgency.REQUIRED) {
        @Override
        public void ping () throws Exception {
            // seriously. always ok.
        }
    };

    /// Had trouble in production with one of the futured tasks getting cancelled.
    /// Simulate the error here so that we can make sure the surrounding framework
    ///  deals with these properly.
    @Test
    public void testCancelledExecution() throws Exception {
        final SimpleSupplier<Boolean> shouldCancel = new SimpleSupplier<Boolean>(true);

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
        final SimpleSupplier<Boolean> shouldInterrupt = new SimpleSupplier<Boolean>(false);
        final SimpleSupplier<Boolean> testInvalid = new SimpleSupplier<Boolean>(false);

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
            Assert.assertTrue(!testInvalid.get());

            shouldInterrupt.set(true);
            assertEquals(CheckStatus.OUTAGE, manager.evaluate(id).getStatus());
            Assert.assertTrue(!testInvalid.get());

            // Just to confirm, make sure that we're able to reenter a good state.
            shouldInterrupt.set(false);
            assertEquals(CheckStatus.OK, manager.evaluate(id).getStatus());
            Assert.assertTrue(!testInvalid.get());
        }

        // Then check what happens if the health checker itself gets twiddled.
        {
            final DependencyChecker interruptingChecker = new InterruptingChecker(shouldInterrupt, testInvalid);
            final AbstractDependencyManager manager = new AbstractDependencyManager(null, null, interruptingChecker) {};
            manager.addDependency(DEP_ALWAYS_TRUE);

            shouldInterrupt.set(false);
            assertEquals(CheckStatus.OK, Preconditions.checkNotNull(manager.evaluate(DEP_ALWAYS_TRUE.getId())).getStatus());
            Assert.assertTrue(!testInvalid.get());

            shouldInterrupt.set(true);
            assertEquals(
                    "Expected the system to report an error if the health-check thread itself got interrupted for some reason.",
                    CheckStatus.OUTAGE, Preconditions.checkNotNull(manager.evaluate(DEP_ALWAYS_TRUE.getId())).getStatus());
            Assert.assertTrue(!testInvalid.get());

            shouldInterrupt.set(false);
            final CheckResult recovery = Preconditions.checkNotNull(manager.evaluate(DEP_ALWAYS_TRUE.getId()));
            assertEquals(
                    "Didn't recover properly from the interrupted thread: " + new ObjectMapper().writeValueAsString(recovery),
                    CheckStatus.OK, recovery.getStatus());
            Assert.assertTrue(!testInvalid.get());

        }
    }

    /// Make sure timeout checks work the way that we expect.
    @Test
    public void testSubmissionDeduplication() throws Exception {
        final int timeoutInMS = 500;
        final SimpleSupplier<Boolean> ignored = new SimpleSupplier<Boolean>(false);
        final Dependency sampleDependency = new SleepyDependency("sample", timeoutInMS, ignored);

        final DependencyExecutorSet executors = new DependencyExecutorSet(Executors.newSingleThreadExecutor());
        final Future<CheckResult> firstFuture = executors.submit(sampleDependency);
        final Future<CheckResult> secondFuture = executors.submit(sampleDependency);
        Assert.assertTrue("Expected the SAME object to be retrieved for back-to-back submissions", firstFuture == secondFuture);
    }

    @Test
    public void testTimeouts() throws Exception {
        final int timeoutInMS = 500;
        final SimpleSupplier<Boolean> lengthyCheckCompleted = new SimpleSupplier<Boolean>(false);
        final Dependency lengthyDependency = new SleepyDependency("lengthy", timeoutInMS, lengthyCheckCompleted);

        final int arbitraryUnusedPingPeriod = 30000;
        final Dependency timeoutDependency = new AbstractDependency(
                "timeout", "", timeoutInMS, arbitraryUnusedPingPeriod, Urgency.REQUIRED
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
        Assert.assertTrue(
                "Expected the test to timeout well before the actual length of the dependency, within say two timeout periods. " +
                        "Instead, it took " + elapsed + "ms",
                elapsed <= (2.0 * timeoutInMS));

        Assert.assertTrue(
                "Expected that the lengthy check would not be completed, but somehow the bit got flipped inside of " + elapsed + "ms.",
                !wasLengthyCheckCompleted);
    }

    @SuppressWarnings ("ConstantConditions")
    @Test
    public void testPingerInvariants () {
        final SimpleSupplier<Throwable> excToThrow = new SimpleSupplier<Throwable>(null);
        final Dependency dependency = new AbstractDependency("id","",100,100,Urgency.REQUIRED) {
            @Override
            public CheckResult call () throws Exception {
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

                return CheckResult.newBuilder(this, CheckStatus.OK, "").build();
            }
        };

        final DependencyPinger pinger = new DependencyPinger(dependency);

        // Call the pinger to get a result BEFORE executing the pinger.
        final CheckResult checkResult = pinger.call();
        Assert.assertNotNull("Expected that the pinger would auto-execute, or at least not return null.", checkResult);
        assertEquals(CheckStatus.OK, checkResult.getStatus());

        // Test to make sure that exceptions thrown inside the dependency evaluation do not pierce the barrier
        excToThrow.set(new IOException(new NullPointerException()));
        pinger.run();
        final Thrown thrownInternal = pinger.call().getThrown();
        Assert.assertTrue(thrownInternal.getException().equals("IOException"));
        Assert.assertNotNull(thrownInternal.getThrown().getException());

        // ... even if they are runtime exceptions
        excToThrow.set(new NullPointerException());
        pinger.run();
        Assert.assertTrue(pinger.call().getThrown().getException().equals("NullPointerException"));

        // ... or errors
        excToThrow.set(new Error());
        pinger.run();
        Assert.assertTrue(pinger.call().getThrown().getException().equals("Error"));

        assertEquals(1, pinger.getTotalSuccesses());
        assertEquals(3, pinger.getTotalFailures());
        // Make sure we continue to produce the event counter unless we explicitly opt out.
        Assert.assertTrue(pinger.getFailures().startsWith("3,"));
    }

    @Test(expected=IllegalStateException.class)
    public void testInitializationFailure() throws Exception {
        final CheckResultSet resultSet = new CheckResultSet();
        final Dependency dependency = DEP_ALWAYS_TRUE;

        resultSet.handleInit(dependency);
        resultSet.handleExecute(dependency);

        // Should explode since initialization cannot happen twice.
        resultSet.handleInit(dependency);
    }

    @Test(expected=IllegalStateException.class)
    public void testExecutionFailure() throws Exception {
        final CheckResultSet resultSet = new CheckResultSet();
        final Dependency dependency = DEP_ALWAYS_TRUE;

        resultSet.handleInit(dependency);
        resultSet.handleExecute(dependency);

        // Should explode since execution cannot happen twice.
        resultSet.handleExecute(dependency);
    }

    @Test(expected = IllegalStateException.class)
    public void testDownstreamFromExecutionFailure() throws Exception {
        Logger.getLogger(CheckResultSet.class).setLevel(Level.FATAL);

        final CheckResultSet resultSet = new CheckResultSet();
        final Dependency dependency = DEP_ALWAYS_TRUE;

        final CheckResult failureResult = CheckResult.newBuilder(dependency, CheckStatus.MINOR, "Expected a failure").build();
        // This should pass since the result accurately notes the failure of the execution.
        resultSet.handleComplete(dependency, failureResult);

        final CheckResult okResult = CheckResult.newBuilder(dependency, CheckStatus.OK, "").build();

        // Expect this to fail due to status/result mismatch
        resultSet.handleComplete(dependency, okResult);
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
    private static class InterruptingChecker extends DependencyChecker {
        public InterruptingChecker (final SimpleSupplier<Boolean> shouldInterrupt, final SimpleSupplier<Boolean> testInvalid) {
            super(log, new InterruptingExecutor(Executors.newSingleThreadExecutor(), shouldInterrupt, testInvalid));
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
    private static class CancelingChecker extends DependencyChecker {
        public CancelingChecker (final SimpleSupplier<Boolean> shouldCancel) {
            super(log, new CancelingExecutor(Executors.newSingleThreadExecutor(), shouldCancel));
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
}
