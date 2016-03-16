package com.indeed.status.core;

public class SystemReporter {

    public AbstractSystemReport collectSystemReport(final CheckResultSet checkResultSet) {
        return checkResultSet.new SystemReport();
    }

    public AbstractSystemReport collectDetailedSystemReport(final CheckResultSet checkResultSet) {
        return checkResultSet.new DetailedSystemReport();
    }
}
