package com.indeed.status.core;

import com.indeed.status.core.test.ControlledDependency;
import org.junit.Test;

import static com.indeed.status.core.CheckStatus.MAJOR;
import static com.indeed.status.core.CheckStatus.MINOR;
import static com.indeed.status.core.CheckStatus.OK;
import static com.indeed.status.core.CheckStatus.OUTAGE;
import static com.indeed.status.core.Urgency.NONE;
import static com.indeed.status.core.Urgency.REQUIRED;
import static com.indeed.status.core.Urgency.STRONG;
import static com.indeed.status.core.Urgency.WEAK;
import static org.junit.Assert.assertEquals;

public class CheckResultSetTest {
    @Test
    public void testRequired() throws Exception {
        assertDowngradeStatus(REQUIRED, OK, OK);
        assertDowngradeStatus(REQUIRED, MINOR, MINOR);
        assertDowngradeStatus(REQUIRED, MAJOR, MAJOR);
        assertDowngradeStatus(REQUIRED, OUTAGE, OUTAGE);
    }

    @Test
    public void testStrong() throws Exception {
        assertDowngradeStatus(STRONG, OK, OK);
        assertDowngradeStatus(STRONG, MINOR, MINOR);
        assertDowngradeStatus(STRONG, MAJOR, MAJOR);
        assertDowngradeStatus(STRONG, OUTAGE, MAJOR);
    }

    @Test
    public void testWeak() throws Exception {
        assertDowngradeStatus(WEAK, OK, OK);
        assertDowngradeStatus(WEAK, MINOR, MINOR);
        assertDowngradeStatus(WEAK, MAJOR, MINOR);
        assertDowngradeStatus(WEAK, OUTAGE, MINOR);
    }

    @Test
    public void testNone() throws Exception {
        assertDowngradeStatus(NONE, OK, OK);
        assertDowngradeStatus(NONE, MINOR, OK);
        assertDowngradeStatus(NONE, MAJOR, OK);
        assertDowngradeStatus(NONE, OUTAGE, OK);
    }

    private void assertDowngradeStatus(Urgency depUrgency, CheckStatus checkStatus, CheckStatus sysStatus) {
        final CheckResultSet set = new CheckResultSet();
        final ControlledDependency dep = ControlledDependency.builder().setUrgency(depUrgency).build();
        dep.setInError(false);

        set.handleInit(dep);
        set.handleExecute(dep);

        final CheckResult res = CheckResult.newBuilder(dep, checkStatus, "ERROR").build();
        set.handleComplete(dep, res);
        set.handleFinalize(dep, res);

        assertEquals(sysStatus, set.getSystemStatus());
    }
}