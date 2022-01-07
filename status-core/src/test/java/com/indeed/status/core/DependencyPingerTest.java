package com.indeed.status.core;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.MoreExecutors;
import com.indeed.status.core.test.ControlledDependency;
import com.indeed.util.core.time.DefaultWallClock;
import com.indeed.util.core.time.WallClock;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** author: cameron */
public class DependencyPingerTest {
    private WallClock wallClock = new DefaultWallClock();
    private SystemReporter systemReporter = new SystemReporter(wallClock);

    @Test
    public void testListener() throws Exception {
        final StatusUpdateListener listener = EasyMock.createMock(StatusUpdateListener.class);
        final ControlledDependency dependency = ControlledDependency.build();
        dependency.setInError(true);
        final DependencyPinger pinger =
                new DependencyPinger(
                        MoreExecutors.newDirectExecutorService(), dependency, systemReporter);
        pinger.addListener(listener);

        final Capture<CheckResult> original = Capture.newInstance();
        final Capture<CheckResult> updated = Capture.newInstance();
        final Capture<CheckResult> checked = Capture.newInstance();
        EasyMock.reset(listener);
        listener.onChecked(EasyMock.same(pinger), EasyMock.capture(checked));
        listener.onChanged(
                EasyMock.same(pinger), EasyMock.<CheckResult>isNull(), EasyMock.capture(updated));
        listener.onChecked(EasyMock.same(pinger), EasyMock.capture(checked));
        listener.onChanged(
                EasyMock.same(pinger), EasyMock.capture(original), EasyMock.capture(updated));
        listener.onChecked(EasyMock.same(pinger), EasyMock.capture(checked));
        listener.onChecked(EasyMock.same(pinger), EasyMock.capture(checked));
        listener.onChanged(
                EasyMock.same(pinger), EasyMock.capture(original), EasyMock.capture(updated));
        EasyMock.replay(listener);

        dependency.setInError(false);
        pinger.run();
        assertEquals(CheckStatus.OK, updated.getValue().getStatus());
        assertEquals(updated.getValue(), checked.getValue());
        original.setValue(null);
        updated.setValue(null);

        dependency.setInError(true);
        pinger.run();
        assertEquals(CheckStatus.OK, original.getValue().getStatus());
        assertEquals(CheckStatus.MINOR, updated.getValue().getStatus());
        assertEquals(ControlledDependency.EXCEPTION, updated.getValue().getThrowable());
        assertEquals(checked.getValue(), updated.getValue());
        original.setValue(null);
        updated.setValue(null);

        pinger.run(); // no change
        assertNull(original.getValue());
        assertNull(updated.getValue());
        assertEquals(CheckStatus.MINOR, checked.getValue().getStatus());

        pinger.run(); // should change
        assertEquals(CheckStatus.MINOR, original.getValue().getStatus());
        assertEquals(CheckStatus.OUTAGE, updated.getValue().getStatus());
        assertEquals(ControlledDependency.EXCEPTION, updated.getValue().getThrowable());
        assertEquals(checked.getValue(), updated.getValue());
        original.setValue(null);
        updated.setValue(null);

        EasyMock.verify(listener);
    }

    @Test
    public void testWithUrgencyNone() throws Exception {
        final StatusUpdateListener listener = EasyMock.createMock(StatusUpdateListener.class);
        final ControlledDependency dependency =
                ControlledDependency.builder().setUrgency(Urgency.NONE).build();
        final DependencyPinger pinger =
                new DependencyPinger(
                        MoreExecutors.newDirectExecutorService(), dependency, systemReporter);
        pinger.addListener(listener);

        final Capture<CheckResult> original = Capture.newInstance();
        final Capture<CheckResult> updated = Capture.newInstance();
        final Capture<CheckResult> checked = Capture.newInstance();
        EasyMock.reset(listener);

        listener.onChecked(EasyMock.same(pinger), EasyMock.capture(checked));
        listener.onChanged(
                EasyMock.same(pinger), EasyMock.<CheckResult>isNull(), EasyMock.capture(updated));
        listener.onChecked(EasyMock.same(pinger), EasyMock.capture(checked));
        listener.onChanged(
                EasyMock.same(pinger), EasyMock.capture(original), EasyMock.capture(updated));
        listener.onChecked(EasyMock.same(pinger), EasyMock.capture(checked));
        listener.onChecked(EasyMock.same(pinger), EasyMock.capture(checked));
        listener.onChanged(
                EasyMock.same(pinger), EasyMock.capture(original), EasyMock.capture(updated));

        EasyMock.replay(listener);

        dependency.setInError(false);
        pinger.run();
        assertEquals(CheckStatus.OK, pinger.call().getStatus());
        assertEquals(CheckStatus.OK, updated.getValue().getStatus());
        assertEquals(checked.getValue(), updated.getValue());

        dependency.setInError(true);
        pinger.run();
        assertEquals(CheckStatus.MINOR, pinger.call().getStatus());
        assertEquals(CheckStatus.OK, original.getValue().getStatus());
        assertEquals(CheckStatus.MINOR, updated.getValue().getStatus());
        assertEquals(checked.getValue(), updated.getValue());

        dependency.setInError(true);
        pinger.run();
        assertEquals(CheckStatus.MINOR, pinger.call().getStatus());
        // no call to listen
        assertEquals(CheckStatus.MINOR, checked.getValue().getStatus());

        dependency.setInError(true);
        pinger.run();
        assertEquals(CheckStatus.OUTAGE, pinger.call().getStatus());
        assertEquals(CheckStatus.MINOR, original.getValue().getStatus());
        assertEquals(CheckStatus.OUTAGE, updated.getValue().getStatus());
        assertEquals(checked.getValue(), updated.getValue());

        EasyMock.verify(listener);
    }

    @Test
    public void testGradual() throws Exception {
        final ControlledDependency dependency = ControlledDependency.build();
        dependency.setInError(true);
        final DependencyPinger pinger =
                new DependencyPinger(
                        MoreExecutors.newDirectExecutorService(), dependency, systemReporter);

        // with no successes
        // expect that first time, call will run something
        assertEquals(CheckStatus.OUTAGE, pinger.call().getStatus());
        assertSame(ControlledDependency.EXCEPTION, pinger.call().getThrowable());
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
        assertSame(ControlledDependency.EXCEPTION, pinger.call().getThrowable());
        assertEquals(3, dependency.getTimes());

        // run again
        pinger.run();
        assertEquals(CheckStatus.MINOR, pinger.call().getStatus());
        assertSame(ControlledDependency.EXCEPTION, pinger.call().getThrowable());
        assertEquals(4, dependency.getTimes());

        // run again 3x failure turns into outage!
        pinger.run();
        assertEquals(CheckStatus.OUTAGE, pinger.call().getStatus());
        assertSame(ControlledDependency.EXCEPTION, pinger.call().getThrowable());
        assertEquals(5, dependency.getTimes());
    }

    @Test
    public void testWithToggle() throws Exception {
        final AtomicBoolean toggle = new AtomicBoolean(true);
        final ControlledDependency dependency =
                ControlledDependency.builder()
                        .setToggle(
                                new Supplier<Boolean>() {
                                    @Override
                                    public Boolean get() {
                                        return toggle.get();
                                    }
                                })
                        .build();
        dependency.setInError(true);
        final DependencyPinger pinger =
                new DependencyPinger(
                        MoreExecutors.newDirectExecutorService(), dependency, systemReporter);

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

    @Test
    public void testListeners() {
        final StatusUpdateListener listener1 = EasyMock.createMock(StatusUpdateListener.class);
        final StatusUpdateListener listener2 = EasyMock.createMock(StatusUpdateListener.class);
        final StatusUpdateListener listener3 = EasyMock.createMock(StatusUpdateListener.class);
        final ControlledDependency dependency = ControlledDependency.build();
        dependency.setInError(true);
        final DependencyPinger pinger =
                new DependencyPinger(
                        MoreExecutors.newDirectExecutorService(), dependency, systemReporter);
        pinger.addListener(listener1);
        pinger.addListener(listener2);
        pinger.addListener(listener3);
        final Iterator<StatusUpdateListener> actual = pinger.listeners();
        assertEquals(
                ImmutableList.of(listener1, listener2, listener3),
                Arrays.asList(Iterators.toArray(actual, StatusUpdateListener.class)));

        // verify no ConcurrentModificationException
        for (final Iterator<StatusUpdateListener> it = pinger.listeners();
                it.hasNext();
                it.next()) {
            pinger.addListener(EasyMock.createMock(StatusUpdateListener.class));
        }
    }
}
