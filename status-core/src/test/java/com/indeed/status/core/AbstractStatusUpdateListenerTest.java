package com.indeed.status.core;

import com.indeed.status.core.test.TestDepControlled;
import org.easymock.classextension.EasyMock;
import org.junit.Test;

/**
 * author: cameron
 */
public class AbstractStatusUpdateListenerTest {
    @Test
    public void testLevel() throws Exception {
        final TestDepControlled dep = TestDepControlled.build();
        final AbstractStatusUpdateListener listener = EasyMock.createMock(AbstractStatusUpdateListener.class);
        final CheckResult OK = CheckResult.newBuilder(dep, CheckStatus.OK, "ok").build();
        final CheckResult MINOR = CheckResult.newBuilder(dep, CheckStatus.MINOR, "minor out")
                .setThrowable(TestDepControlled.EXCEPTION).build();
        final CheckResult MAJOR = CheckResult.newBuilder(dep, CheckStatus.MAJOR, "major out")
                .setThrowable(TestDepControlled.EXCEPTION).build();
        final CheckResult OUTAGE = CheckResult.newBuilder(dep, CheckStatus.OUTAGE, "outage")
                .setThrowable(TestDepControlled.EXCEPTION).build();

        EasyMock.reset(listener);

        listener.onStarted(dep, null, OK); // 1
        listener.onDegraded(dep, OK, MINOR); // 2
        listener.onRestored(dep, MINOR, OK); // 3
        listener.onDegraded(dep, OK, MAJOR); // 4
        listener.onImproved(dep, MAJOR, MINOR); // 5
        listener.onRestored(dep, MAJOR, OK); // 6
        listener.onDisabled(dep, MAJOR, OUTAGE); // 7
        listener.onWorsened(dep, MINOR, MAJOR); // 8

        EasyMock.replay(listener);

        listener.onChanged(dep, null, OK); // 1
        listener.onChanged(dep, OK, MINOR); // 2
        listener.onChanged(dep, MINOR, OK); // 3
        listener.onChanged(dep, OK, MAJOR); // 4
        listener.onChanged(dep, MAJOR, MINOR); // 5
        listener.onChanged(dep, MAJOR, OK); // 6
        listener.onChanged(dep, MAJOR, OUTAGE); // 7
        listener.onChanged(dep, MINOR, MAJOR); // 8

        EasyMock.verify(listener);
    }
}
