package com.indeed.status.web;

import com.google.common.base.Function;
import com.indeed.status.core.AbstractDependencyManager;
import com.indeed.status.core.CheckReportHandler;
import com.indeed.status.core.CheckResultSet;
import com.indeed.status.core.CheckStatus;
import org.apache.log4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Matt Schemmel
 */
abstract public class AbstractDaemonCheckReportServlet extends HttpServlet {
    /// Instance logger available for use by subclasses.
    protected final Logger log = Logger.getLogger(getClass());
    // Set-once, read-many
    private AbstractDependencyManager manager;

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        try {
            final CheckResultSet resultSet = getManager().evaluate();
            final CheckReportHandler handler = newHandler(request, response);

            handler.handle(resultSet);

        } catch (final Throwable t) {
            log.error("Received an unexpected top-level throwable.", t);

            // Note: the checker#evaluate method should never throw any sort of recoverable exception.
            // If we get here, it is due to NPEs, OOMs, and other complete failures.
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Error executing dependency check.");
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        this.manager = newManager(config);
    }

    abstract protected AbstractDependencyManager newManager(final ServletConfig config);

    protected AbstractDependencyManager getManager() {
        return this.manager;
    }

    @SuppressWarnings ( { "UnusedParameters" })
    protected CheckReportHandler newHandler ( final HttpServletRequest request, final HttpServletResponse response ) {
        final Function<CheckStatus, Integer> mapper = newStatusMapper(request);

        return newHandler(request, response, mapper);
    }

    protected CheckReportHandler newHandler(HttpServletRequest request, HttpServletResponse response, Function<CheckStatus, Integer> mapper) {
        return new PrivilegedReportHandler(mapper, response, log);
    }

    protected Function<CheckStatus, Integer> newStatusMapper(HttpServletRequest request) {
        return AbstractResponseWriter.FN_PRIVATE_RESPONSE;
    }
}
