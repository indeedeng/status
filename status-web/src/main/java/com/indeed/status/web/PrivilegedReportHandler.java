package com.indeed.status.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.indeed.status.core.CheckReportHandler;
import com.indeed.status.core.CheckResultSet;
import com.indeed.status.core.CheckResultSystemReport;
import com.indeed.status.core.CheckStatus;
import com.indeed.status.web.json.Jackson;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Matt Schemmel
 */
public class PrivilegedReportHandler extends AbstractResponseWriter implements CheckReportHandler {
    private static final int NO_STATUS_CODE = 709;
    protected final ObjectMapper mapper;
    protected final HttpServletResponse response;
    protected final Function<CheckStatus, Integer> statusCodeMapper;

    /** @deprecated use {@link #PrivilegedReportHandler(com.google.common.base.Function, javax.servlet.http.HttpServletResponse)} */
    @Deprecated
    public PrivilegedReportHandler(final Function<CheckStatus, Integer> statusCodeMapper, final HttpServletResponse response, final org.apache.log4j.Logger log) {
        this(statusCodeMapper, response);
    }

    public PrivilegedReportHandler(final Function<CheckStatus, Integer> statusCodeMapper, final HttpServletResponse response) {
        this.statusCodeMapper = statusCodeMapper;
        this.response = response;
        mapper = new ObjectMapper();
    }

    @Override
    public void handle ( final CheckResultSet resultSet ) throws IOException {
        this.setResponseHeaders(resultSet);
        this.sendResponse(response, resultSet);
    }

    protected void setResponseHeaders ( final CheckResultSet resultSet ) {
        final Integer systemStatusCode = statusCodeMapper.apply(resultSet.getSystemStatus());
        final int httpStatusCode = systemStatusCode != null ? systemStatusCode : NO_STATUS_CODE;

        response.setStatus(httpStatusCode);
        response.setContentType("application/json");
    }

    // TODO should this throw IOException or not?
    protected void sendResponse(final HttpServletResponse response, final CheckResultSet resultSet) throws IOException {
        final CheckResultSystemReport report = resultSet.summarizeBySystemReporter(isDetailed());
        final String json = Jackson.prettyPrint(report, this.mapper);

        response.getWriter().println(json);
    }

    protected boolean isDetailed() {
        return true;
    }
}
