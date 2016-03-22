package com.indeed.status.core;

public class SystemReporter {

    public CheckResultSystemReport collectSystemReport(final CheckResultSet checkResultSet) {
        return checkResultSet.new SystemReport();
    }

    public CheckResultSystemReport collectDetailedSystemReport(final CheckResultSet checkResultSet) {
        return checkResultSet.new DetailedSystemReport();
    }
}
