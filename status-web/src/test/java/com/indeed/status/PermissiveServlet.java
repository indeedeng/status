package com.indeed.status;

import com.indeed.status.core.AbstractDependencyManager;
import com.indeed.status.core.CheckResult;
import com.indeed.status.core.CheckStatus;
import com.indeed.status.core.Dependency;
import com.indeed.status.core.DependencyType;
import com.indeed.status.core.Urgency;
import com.indeed.status.web.AbstractDaemonCheckReportServlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

/**
 *
 */
public class PermissiveServlet extends AbstractDaemonCheckReportServlet {
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        this.getManager().addDependency(newSampleDependency());
    }

    @Override
    protected AbstractDependencyManager newManager(ServletConfig config) {
        return new DependencyManager();
    }

    protected Dependency newSampleDependency() {
        return new Dependency() {
            @Override
            public String getId() {
                return "sample";
            }

            @Override
            public String getDescription() {
                return "Sample dependency used for testing healthcheck behavior";
            }

            @Override
            public String getDocumentationUrl() {
                return "";
            }

            @Override
            public long getTimeout() {
                return -1;
            }

            @Override
            public long getPingPeriod() {
                return 1000;
            }

            @Override
            public CheckResult call() throws Exception {
                return CheckResult.newBuilder(this, CheckStatus.OK, "Check passed.").build();
            }

            @Override
            public Urgency getUrgency() {
                return Urgency.BuiltIns.REQUIRED;
            }

            @Override
            public DependencyType getType() {
                return DependencyType.StandardDependencyTypes.OTHER;
            }

            @Override
            public String getServicePool() {
                return "not specified";
            }
        };
    }
}
