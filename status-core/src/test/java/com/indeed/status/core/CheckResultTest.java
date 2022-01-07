package com.indeed.status.core;

import com.indeed.status.core.CheckResult.Thrown;
import com.indeed.status.core.test.ControlledDependency;
import org.junit.Test;

import static com.google.common.base.Preconditions.checkNotNull;
import static junit.framework.Assert.assertEquals;

/** author: cameron */
public class CheckResultTest {
    @Test
    public void testThrown() throws Exception {
        final CheckResult result =
                CheckResult.newBuilder(ControlledDependency.build(), CheckStatus.MAJOR, "major out")
                        .setThrowable(ControlledDependency.EXCEPTION)
                        .build();
        final Thrown thrown = checkNotNull(result.getThrown());
        assertEquals("BAD", thrown.getMessage());
        assertEquals("RuntimeException", thrown.getException());
    }
}
