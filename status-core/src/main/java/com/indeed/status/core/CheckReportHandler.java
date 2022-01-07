package com.indeed.status.core;

import java.io.IOException;

/** */
public interface CheckReportHandler {
    /**
     * Consumes the given result set and reports on the outcome.
     *
     * @param resultSet
     */
    void handle(final CheckResultSet resultSet) throws IOException;
}
