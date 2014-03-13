package com.indeed.status.core;

import com.google.common.base.Supplier;
import com.google.common.util.concurrent.MoreExecutors;
import com.indeed.status.core.test.TestDepControlled;
import org.easymock.Capture;
import org.easymock.classextension.EasyMock;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * author: cameron
 */
public class DependencyPingerTest {

    @Test
    public void testListener() throws Exception {
        final StatusUpdateListener listener = EasyMock.createMock(StatusUpdateListener.class);
        final TestDepControlled dependency = TestDepControlled.build();
        dependency.setInError(true);
        final DependencyPinger pinger = new DependencyPinger(MoreExecutors.sameThreadExecutor(), dependency);
        pinger.addListener(listener);

        final Capture<CheckResult> original = new Capture<CheckResult>();
        final Capture<CheckResult> updated = new Capture<CheckResult>();
        EasyMock.reset(listener);
        listener.onChanged(EasyMock.eq(dependency), EasyMock.<CheckResult>isNull(), EasyMock.capture(updated));
        listener.onChanged(EasyMock.eq(dependency), EasyMock.capture(original), EasyMock.capture(updated));
        listener.onChanged(EasyMock.eq(dependency), EasyMock.capture(original), EasyMock.capture(updated));
        EasyMock.replay(listener);

        dependency.setInError(false);
        pinger.run();
        assertEquals(CheckStatus.OK, updated.getValue().getStatus());
        original.setValue(null);
        updated.setValue(null);

        dependency.setInError(true);
        pinger.run();
        assertEquals(CheckStatus.OK, original.getValue().getStatus());
        assertEquals(CheckStatus.MINOR, updated.getValue().getStatus());
        assertEquals(TestDepControlled.EXCEPTION, updated.getValue().getThrowable());
        original.setValue(null);
        updated.setValue(null);

        pinger.run(); // no change
        pinger.run(); // should change
        assertEquals(CheckStatus.MINOR, original.getValue().getStatus());
        assertEquals(CheckStatus.OUTAGE, updated.getValue().getStatus());
        assertEquals(TestDepControlled.EXCEPTION, updated.getValue().getThrowable());
        original.setValue(null);
        updated.setValue(null);

        EasyMock.verify(listener);
    }

    @Test
    public void testGradual() throws Exception {
        final TestDepControlled dependency = TestDepControlled.build();
        dependency.setInError(true);
        final DependencyPinger pinger = new DependencyPinger(MoreExecutors.sameThreadExecutor(), dependency);

        // with no successes
        // expect that first time, call will run something
        assertEquals(CheckStatus.OUTAGE, pinger.call().getStatus());
        assertSame(TestDepControlled.EXCEPTION, pinger.call().getThrowable());
        assertEquals(1, dependency.getTimes());

        // run once
        dependency.setInError(false);
        pinger.run();
        assertEquals(CheckStatus.OK, pinger.call().getStatus());
        assertNull(pinger.call().getThrowable());
        assertEquals(2, dependency.getTimes());

        // should be cached
        dependency.setInError(true);
        assertEquals(CheckStatus.OK, pinger.call().getStatus());
        assertNull(pinger.call().getThrowable());
        assertEquals(2, dependency.getTimes());

        // run again
        pinger.run();
        assertEquals(CheckStatus.MINOR, pinger.call().getStatus());
        assertSame(TestDepControlled.EXCEPTION, pinger.call().getThrowable());
        assertEquals(3, dependency.getTimes());

        // run again
        pinger.run();
        assertEquals(CheckStatus.MINOR, pinger.call().getStatus());
        assertSame(TestDepControlled.EXCEPTION, pinger.call().getThrowable());
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
