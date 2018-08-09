package com.indeed.status.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.indeed.status.core.CheckStatus;

/*
 * This class is for use in the {@JsonPingableDependency} to represent the
 * response from another service's healthcheck.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DependencyStatus {
    private String hostname;
    private long Duration;
    private CheckStatus condition;
    private CheckStatus dcStatus;
    private String appname;
    private String leastRecentlyExecutedDate;
    private long leastRecentlyExecutedTimestamp;

    public String getHostname() {
        return hostname;
    }

    public void setHostname(final String hostname) {
        this.hostname = hostname;
    }

    public long getDuration() {
        return Duration;
    }

    public void setDuration(final long duration) {
        Duration = duration;
    }

    public CheckStatus getCondition() {
        return condition;
    }

    public void setCondition(final CheckStatus condition) {
        this.condition = condition;
    }

    public CheckStatus getDcStatus() {
        return dcStatus;
    }

    public void setDcStatus(final CheckStatus dcStatus) {
        this.dcStatus = dcStatus;
    }

    public String getAppname() {
        return appname;
    }

    public void setAppname(final String appname) {
        this.appname = appname;
    }

    public String getLeastRecentlyExecutedDate() {
        return leastRecentlyExecutedDate;
    }

    public void setLeastRecentlyExecutedDate(final String leastRecentlyExecutedDate) {
        this.leastRecentlyExecutedDate = leastRecentlyExecutedDate;
    }

    public long getLeastRecentlyExecutedTimestamp() {
        return leastRecentlyExecutedTimestamp;
    }

    public void setLeastRecentlyExecutedTimestamp(final long leastRecentlyExecutedTimestamp) {
        this.leastRecentlyExecutedTimestamp = leastRecentlyExecutedTimestamp;
    }
}
