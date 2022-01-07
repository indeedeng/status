package com.indeed.status.core;

import com.google.common.util.concurrent.AtomicDouble;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

/** @author xinjianz */
public class SlideWindowDependencyTest {

    private static final class TestDependency extends SlideWindowDependency {
        private final AtomicDouble failedRatio;
        private final AtomicLong time;

        TestDependency(
                final AtomicDouble failedRatio,
                final AtomicLong time,
                final double maxOK,
                final double maxMinor,
                final double maxMajor,
                final long timeInterval) {
            super(
                    "testId",
                    "testDescription",
                    10,
                    10,
                    Urgency.REQUIRED,
                    maxOK,
                    maxMinor,
                    maxMajor,
                    timeInterval);
            this.failedRatio = failedRatio;
            this.time = time;
        }

        @Override
        protected double ping() throws Exception {
            final double dVal = failedRatio.get();
            if (dVal > 1) {
                throw new Exception();
            } else {
                return dVal;
            }
        }

        @Override
        protected Event pingWrapper() {
            try {
                return new Event(ping(), time.get());
            } catch (final Exception e) {
                return new Event(1, time.get());
            }
        }

        @Override
        protected String formatErrorMessage(final long timeInterval, final double failedRatio) {
            return String.valueOf(failedRatio);
        }
    }

    @Test
    public void test() throws Exception {
        final AtomicDouble failedRatio = new AtomicDouble(0);
        final AtomicLong time = new AtomicLong(System.currentTimeMillis());
        final double errorRange = 0.0001;
        final TestDependency testDependency =
                new TestDependency(failedRatio, time, 0.05, 0.1, 0.2, 600 * 1000);
        // Test OK
        CheckResult checkResult = testDependency.call();
        assertEquals(CheckStatus.OK, checkResult.getStatus());
        assertEquals(0, Double.valueOf(checkResult.getErrorMessage()), errorRange);
        // Test MINOR
        failedRatio.set(0.12);
        time.addAndGet(100 * 1000);
        checkResult = testDependency.call();
        assertEquals(CheckStatus.MINOR, checkResult.getStatus());
        assertEquals(0.06, Double.valueOf(checkResult.getErrorMessage()), errorRange);
        // Test MAJOR
        failedRatio.set(0.24);
        time.addAndGet(100 * 1000);
        checkResult = testDependency.call();
        assertEquals(CheckStatus.MAJOR, checkResult.getStatus());
        assertEquals(0.12, Double.valueOf(checkResult.getErrorMessage()), errorRange);
        // Test OUTAGE
        failedRatio.set(0.48);
        time.addAndGet(200 * 1000);
        checkResult = testDependency.call();
        assertEquals(CheckStatus.OUTAGE, checkResult.getStatus());
        assertEquals(0.24, Double.valueOf(checkResult.getErrorMessage()), errorRange);
        // Test ping throw Exception
        failedRatio.set(100);
        time.addAndGet(100 * 1000);
        checkResult = testDependency.call();
        assertEquals(CheckStatus.OUTAGE, checkResult.getStatus());
        assertEquals(0.34, Double.valueOf(checkResult.getErrorMessage()), errorRange);
        // Remove one point
        failedRatio.set(0.48);
        time.addAndGet(100 * 1000);
        checkResult = testDependency.call();
        assertEquals(CheckStatus.OUTAGE, checkResult.getStatus());
        assertEquals(0.476, Double.valueOf(checkResult.getErrorMessage()), errorRange);
        // Remove two point
        failedRatio.set(0);
        time.addAndGet(200 * 1000);
        checkResult = testDependency.call();
        assertEquals(CheckStatus.OUTAGE, checkResult.getStatus());
        assertEquals(0.49, Double.valueOf(checkResult.getErrorMessage()), errorRange);
        // Remove all points and add a new one.
        failedRatio.set(0.04);
        time.addAndGet(700 * 1000);
        checkResult = testDependency.call();
        assertEquals(CheckStatus.OK, checkResult.getStatus());
        assertEquals(0.04, Double.valueOf(checkResult.getErrorMessage()), errorRange);
        // Test recalculate
        failedRatio.set(0.15);
        time.addAndGet(10000 * 1000);
        checkResult = testDependency.call();
        assertEquals(CheckStatus.MAJOR, checkResult.getStatus());
        assertEquals(0.15, Double.valueOf(checkResult.getErrorMessage()), errorRange);
        // Test recalculate with multiple points.
        failedRatio.set(0.1);
        time.addAndGet(3400 * 1000);
        testDependency.call();
        failedRatio.set(0.1);
        time.addAndGet(100 * 1000);
        testDependency.call();
        failedRatio.set(0.5);
        time.addAndGet(300 * 1000);
        checkResult = testDependency.call();
        assertEquals(CheckStatus.OUTAGE, checkResult.getStatus());
        assertEquals(0.25, Double.valueOf(checkResult.getErrorMessage()), errorRange);
    }
}
