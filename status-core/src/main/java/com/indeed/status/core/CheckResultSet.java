package com.indeed.status.core;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize.Inclusion;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.indeed.util.core.NetUtils;
import com.indeed.util.core.time.DefaultWallClock;
import com.indeed.util.core.time.WallClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;

/**
 * The <code>CheckResultSet</code> is a mutable aggregator collecting the results of individual dependency
 *  evaluations into a single object describing the current health of the overall system.
 */
public class CheckResultSet {
    private static final Logger log = LoggerFactory.getLogger(CheckResultSet.class);
    private static final DefaultWallClock DEFAULT_WALL_CLOCK = new DefaultWallClock();
    private static final SystemReporter DEFAULT_SYSTEM_REPORTER = new SystemReporter(DEFAULT_WALL_CLOCK);

    /// Epoch milliseconds at which the execution captured by this result set began
    private final long startTimeMillis;

    //  TODO Remove this reference; there's no need to inject the reporter into the data rather than vice versa.
    //       It will take some unwinding to completely extract this, as this is part of the public API.
    /// Reporter that converts this result set into a human- or machine-readable report.
    private final SystemReporter systemReporter;
    @Nullable
    private String appName = null;

    /**
     * The overall health of the system represented by this result set.
     * Assume that all systems start in a healthy state.
     */
    private final AtomicReference<CheckStatus> systemStatus = new AtomicReference<>(CheckStatus.OK);
    /**
     * Map of all currently-executing checks; provides a simple method of avoiding dependency-check-stacking.
     */
    private final ConcurrentMap<String, Tag> executingChecks = Maps.newConcurrentMap();
    private final ConcurrentMap<String, CheckResult> completedChecks = Maps.newConcurrentMap();

    /**
     * @deprecated Use {@link com.indeed.status.core.CheckResultSet.Builder} instead
     *
     * TODO Remove after a reasonable grace period.
     */
    @Deprecated
    public CheckResultSet() {
        // noinspection deprecation -- unhelpful warning in a deprecated method
        this(DEFAULT_SYSTEM_REPORTER);
    }

    public CheckResultSet(@Nonnull final SystemReporter systemReporter) {
        this.startTimeMillis = systemReporter.getWallClock().currentTimeMillis();
        this.systemReporter = systemReporter;
    }

    /**
     * Convenience factory method for default result sets. Used primarily by tests.
     */
    public static CheckResultSet newInstance() {
        return newBuilder().build();
    }

    /**
     * Set the name of the application as it should be reported downstream.
     *
     * Implemented as a mutator to avoid having to thread the application name through the DependencyChecker.
     *
     * TODO Rearrange things so that we don't need this wart.
     */
    public void setAppName(@Nullable final String appName) {
        this.appName = appName;
    }

    @Nullable
    public String getAppName() {
        return appName;
    }

    /**
     * @deprecated Use {@link #getStartTimeMillis()} instead
     */
    public long getStartTime() {
        return getStartTimeMillis();
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    @Nullable
    public CheckResult get(@Nonnull final String id ) {
        return completedChecks.get(id);
    }

    @Nonnull
    public Collection<CheckResult> getCompleted() {
        return completedChecks.values();
    }

    @Nonnull
    public CheckStatus getSystemStatus() {
        return this.systemStatus.get();
    }


    /**
     * @deprecated This is replaced by {@link #summarizeBySystemReporter(boolean)}
     *
     * You could call {@link #CheckResultSet(SystemReporter)} to inject your own customized system reporter and
     * {@link #summarizeBySystemReporter(boolean)} would return the customized reports to you.
     */
    @Nonnull
    @Deprecated
    public SystemReport summarize(final boolean detailed) {
        final WallClock wallClock = systemReporter.getWallClock();
        return detailed ? new DetailedSystemReport(wallClock) : new SystemReport(wallClock);
    }

    @Nonnull
    public CheckResultSystemReport summarizeBySystemReporter(final boolean detailed) {
        return detailed ?
                systemReporter.collectDetailedSystemReport(this) : systemReporter.collectSystemReport(this);
    }

    protected void handleInit(@Nonnull final Dependency dependency) {
        final String id = dependency.getId();

        checkState(!executingChecks.containsKey(id), "Found another task executing with the same ID: '%s'.", id);
        checkState(!completedChecks.containsKey(id), "Found another task completed with the same ID: '%s'.", id);
    }

    protected void handleExecute(@Nonnull final Dependency dependency) {
        final String id = dependency.getId();

        // Create a new tag with the current system time.
        final Tag tag = new Tag(id, systemReporter.getWallClock().currentTimeMillis());

        @Nullable
        final Tag oldValue = executingChecks.putIfAbsent(id, tag);
        checkState(null == oldValue, "Found another tag executing with the same ID: '%s'.", oldValue);
    }

    protected void handleComplete(@Nonnull final Dependency dependency, @Nonnull final CheckResult result) {
        final String id = dependency.getId();

        try {
            Preconditions.checkNotNull(result, "Unable to handle completion with a null result.");

            // Attempt to finalize immediately on completion, so that we minimize the
            //  amount of management overhead included in the save results.

            final Tag tag = executingChecks.get(id);

            if ( null == tag ) {
                checkState(
                        result.getStatus() != CheckStatus.OK,
                        "Expected a failure of some sort from a check that isn't listed in the executing checks.");

            }

        } finally {
            executingChecks.remove(id);

            final CheckResult priorResult = completedChecks.putIfAbsent(id, result);

            // After the result is added to the completed-checks, we should not allow any further exceptions to be raised,
            //  since those exceptions should then replace the content, but won't without more code than we want to devote
            //  to this. Logsig, however, is perfectly fair game.
            if(null != priorResult) {
                log.error(
                        String.format("Attempted to record a second result for id '%s'.", id),
                        new UndesirableStateException());
            }
        }
    }

    /**
     * Do not throw exceptions here
     */
    protected void handleFinalize(@Nonnull final Dependency dependency, @Nonnull final CheckResult result ) {
        // everything after the result is finalized depends on the completed list containing all references
        final CheckResult recordedResult = completedChecks.putIfAbsent(dependency.getId(), result);
        if (null == recordedResult) {
            if (result.getStatus() == CheckStatus.OK) {
                log.error(String.format(
                        "Found a missing completed-check result for '%s' with an OK status, which is NOT OK.",
                        dependency), new IllegalStateException());
            } else {
                log.warn(
                        String.format("Restored missing completed-check result for '%s'", dependency),
                        new UndesirableStateException());
            }
        }

        if ( log.isTraceEnabled() ) {
            log.trace ( "Updating system based on dependency '" + dependency.getId() + "'." );
        }

        // Now that we have a guaranteed non-null check result, downgrade the overall
        //  system status appropriately.
        synchronized(this) {
            final CheckStatus status = systemStatus.get();
            @Nonnull
            final CheckStatus newStatus = dependency.getUrgency().downgradeWith(status, result.getStatus());

            if ( log.isTraceEnabled() ) {
                if ( !status.equals(newStatus) ) {
                    log.trace("... reducing system availability to '" + newStatus + "'.");
                } else {
                    log.trace("... no impairment recorded. Remains '" + newStatus + "'.");
                }
            }

            systemStatus.set(newStatus);
        }
    }

    private static class Tag {
        private Tag (@Nonnull final String id, final long startTimeMillis) {
            this.id = id;
            this.startTimeMillis = startTimeMillis;
        }

        @Nonnull public final String id;
        public final long startTimeMillis;
    }

    private static final Comparator<CheckResult> ID_COMPARATOR = new Comparator<CheckResult>() {
        @Override
        public int compare(final CheckResult checkResult, final CheckResult checkResult1) {
            return checkResult.getId().compareTo(checkResult1.getId());
        }
    };

    @JsonSerialize (include = Inclusion.NON_NULL)
    public class SystemReport implements CheckResultSystemReport {
        @Nonnull
        public final String hostname;
        public final long duration;
        @Nonnull
        public final CheckStatus condition;
        @Nonnull
        public final String dcStatus;

        /**
         * @deprecated Use {@link #SystemReport(WallClock)} instead
         */
        public SystemReport() {
            this(DEFAULT_WALL_CLOCK);
        }

        public SystemReport(@Nonnull final WallClock wallClock) {
            // Yeah, it isn't great for elapsed time to be derived from a wall-clock rather than from
            //  a nano ticker, but we need the absolute system time for other fields.
            duration = wallClock.currentTimeMillis() - startTimeMillis;
            hostname = NetUtils.determineHostName("unknown");

            condition = systemStatus.get();
            switch(condition) {
                case OK:
                case MINOR:
                case MAJOR:
                    this.dcStatus = "OK";
                    break;
                case OUTAGE:
                    this.dcStatus = "FAILOVER";
                    break;
                default:
                    this.dcStatus = "OK";
            }
        }
    }

    @JsonSerialize (include = Inclusion.ALWAYS)
    public class DetailedSystemReport extends SystemReport {
        @Nullable
        public final String appname;
        @Nullable
        public final String catalinaBase;
        @Nonnull
        public final String leastRecentlyExecutedDate;
        public final long leastRecentlyExecutedTimestamp;
        @Nonnull
        public final SortedMap<CheckStatus,SortedSet<CheckResult>> results;

        /**
         * @deprecated Use {@link #DetailedSystemReport(WallClock)} instead.
         */
        public DetailedSystemReport() {
            this(DEFAULT_WALL_CLOCK);
        }

        public DetailedSystemReport(@Nonnull final WallClock wallClock) {
            super(wallClock);

            appname = CheckResultSet.this.appName;
            catalinaBase = System.getProperty("catalina.base");
            results = Maps.newTreeMap();

            long earliestTimestamp = wallClock.currentTimeMillis();
            for (final CheckResult result : completedChecks.values()) {
                SortedSet<CheckResult> set;

                if (null == (set = results.get(result.getStatus()))) {
                    results.put(result.getStatus(), set = Sets.newTreeSet(ID_COMPARATOR));
                }

                set.add(result);

                final long timestamp = result.getTimestamp();
                if (timestamp > 0L && timestamp < earliestTimestamp) {
                    earliestTimestamp = timestamp;
                }
            }

            leastRecentlyExecutedTimestamp = earliestTimestamp;
            leastRecentlyExecutedDate = CheckResult.DATE_FORMAT.get().format(new Date(leastRecentlyExecutedTimestamp));
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        @Nonnull private SystemReporter systemReporter = DEFAULT_SYSTEM_REPORTER;

        public Builder setSystemReporter(@Nonnull final SystemReporter systemReporter) {
            this.systemReporter = systemReporter;
            return this;
        }

        public CheckResultSet build() {
            return new CheckResultSet(systemReporter);
        }
    }
}
