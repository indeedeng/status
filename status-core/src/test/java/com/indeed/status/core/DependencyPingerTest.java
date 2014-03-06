package com.indeed.status.core;

import com.google.common.base.Supplier;
import com.google.common.util.concurrent.MoreExecutors;
import com.indeed.status.core.test.TestDepControlled;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * author: cameron
 */
public class DependencyPingerTest {

    @Test
    public void testGradual() throws Exception {
        final TestDepControlled dependency = TestDepControlled.build();
        dependency.setInError(true);
        final DependencyPinger pinger = new DependencyPinger(MoreExecutors.sameThreadExecutor(), dependency);

        // with no successes
        // expect that first time, call will run something
        assertEquals(CheckStatus.OUTAGE, pinger.call().getStatus());
        assertEquals(1, dependency.getTimes());

        // run once
        dependency.setInError(false);
        pinger.run();
        assertEquals(CheckStatus.OK, pinger.call().getStatus());
        assertEquals(2, dependency.getTimes());

        // should be cached
        dependency.setInError(true);
        assertEquals(CheckStatus.OK, pinger.call().getStatus());
        assertEquals(2, dependency.getTimes());

        // run again
        pinger.run();
        assertEquals(CheckStatus.MINOR, pinger.call().getStatus());
        assertEquals(3, dependency.getTimes());

        // run again
        pinger.run();
        assertEquals(CheckStatus.MINOR, pinger.call().getStatus());
        assertEquals(4, dependency.getTimes());

        // run again 3x failure turns into outage!
        pinger.run();
        assertEquals(CheckStatus.OUTAGE, pinger.call().getStatus());
        assertSame(TestDepControlled.EXCEPTION, pinger.call().getThrowable());
        assertEquals(5, dependency.getTimes());
    }

    @Test
    public void testWithToggle() throws Exception {
        final AtomicBoolean toggle = new AtomicBoolean(true);
        final TestDepControlled dependency = TestDepControlled.builder().setToggle(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return toggle.get();
            }
        }).build();
        dependency.setInError(true);
        final DependencyPinger pinger = new DependencyPinger(MoreExecutors.sameThreadExecutor(), dependency);

        assertEquals(CheckStatus.OUTAGE, pinger.call().getStatus());
        assertEquals(1, dependency.getTimes());

        pinger.run();
        assertEquals(CheckStatus.OUTAGE, pinger.call().getStatus());
        assertEquals(2, dependency.getTimes());

        toggle.set(false);
        pinger.run();
        assertEquals(CheckStatus.OK, pinger.call().getStatus());
        assertEquals(2, dependency.getTimes());
    }
}
