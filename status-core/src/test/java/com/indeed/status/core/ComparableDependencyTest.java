package com.indeed.status.core;

import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/** author: cameron */
public class ComparableDependencyTest {
    private static final class CompDep extends ComparableDependency<Integer> {

        @Nonnull private final AtomicInteger val;

        private CompDep(@Nonnull final AtomicInteger val, int maxOK, int maxMinor, int maxMajor) {
            super("id", "description", 10, 10, Urgency.REQUIRED, maxOK, maxMinor, maxMajor);
            this.val = val;
        }

        @Override
        protected Integer getValue() throws Exception {
            return val.get();
        }

        @Override
        protected String formatErrorMessage(
                CheckStatus status,
                @Nullable Integer value,
                @Nullable Integer brokenThreshold,
                long timestamp,
                long duration,
                @Nullable Exception e) {
            return status.name() + "," + value + "," + brokenThreshold + "," + e;
        }
    }

    @Test
    public void testSane() throws Exception {
        final AtomicInteger val = new AtomicInteger(0);
        final CompDep compDep = new CompDep(val, 5, 10, 15);

        val.set(0);
        assertEquals("OK,0,null,null", compDep.call().getErrorMessage());

        val.set(5);
        assertEquals("OK,5,null,null", compDep.call().getErrorMessage());

        val.set(6);
        assertEquals("MINOR,6,5,null", compDep.call().getErrorMessage());

        val.set(10);
        assertEquals("MINOR,10,5,null", compDep.call().getErrorMessage());

        val.set(11);
        assertEquals("MAJOR,11,10,null", compDep.call().getErrorMessage());

        val.set(15);
        assertEquals("MAJOR,15,10,null", compDep.call().getErrorMessage());

        val.set(16);
        assertEquals("OUTAGE,16,15,null", compDep.call().getErrorMessage());
    }

    @Test
    public void testException() throws Exception {
        final CompDep compDep = new CompDep(null, 5, 10, 15);
        assertEquals(
                "OUTAGE,null,null,java.lang.NullPointerException",
                compDep.call().getErrorMessage());
    }
}
