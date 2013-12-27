package com.indeed.status.core;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
public class CheckResultSet {

    public CheckResultSet() {
        this.startTime = System.currentTimeMillis();
    }

    // Set after the fact, because the dependency checker is unaware of the app.
    public void setAppName(@Nullable final String appName) {
        this.appName = appName;
    }

    @Nullable
    public CheckResult get (@Nonnull final String id ) {
        return completedChecks.get(id);
    }

    public Collection<CheckResult> getCompleted() {
        return completedChecks.values();
    }

    @Nonnull
    public CheckStatus getSystemStatus() {
        return this.systemStatus.get();
    }

    @Nonnull
    public SystemReport summarize ( final boolean detailed ) {
        return detailed ? new DetailedSystemReport() : new SystemReport();
    }

    protected void handleInit (@Nonnull final Dependency dependency) {
        final String id = dependency.getId();

        Preconditions.checkState(!executingChecks.containsKey(id), "Found another task executing with the same ID: '%s'.", id);
        Preconditions.checkState(!completedChecks.containsKey(id), "Found another task completed with the same ID: '%s'.", id);
    }

    protected void handleExecute (@Nonnull final Dependency dependency) {
        final String id = dependency.getId();

        // Create a new tag with the current system time.
        final Tag tag = new Tag(id);

        @Nullable
        final Tag oldValue = executingChecks.putIfAbsent(id, tag);
        Preconditions.checkState(null == oldValue, "Found another tag executing with the same ID: '%s'.", oldValue);
    }

    protected void handleComplete (@Nonnull final Dependency dependency, @Nonnull final CheckResult result) {
        final String id = dependency.getId();

        try {
            Preconditions.checkNotNull(result, "Unable to handle completion with a null result.");

            // Attempt to finalize immediately on completion, so that we minimize the
            //  amount of management overhead included in the save results.

            final Tag tag = executingChecks.get(id);

            if ( null == tag ) {
                Preconditions.checkState(
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
    protected void handleFinalize (@Nonnull final Dependency dependency, @Nonnull final CheckResult result ) {
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
        private Tag (@Nonnull final String id) {
            this(id, System.currentTimeMillis());
        }

        private Tag (@Nonnull final String id, final long startTime) {
            this.id = id;
            this.startTime = startTime;
        }

        @Nonnull public final String id;
        public final long startTime;
    }

    @JsonSerialize (include = Inclusion.NON_NULL)
    public class SystemReport {
        public SystemReport() {
            duration = System.currentTimeMillis() - startTime;
            this.hostname = "todo"; // todo: bring in netutils //NetUtils.determineHostName("unknown");

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
                    // Not really sensible. Don't let dynect fail this out.
                    this.dcStatus = "OK";
            }
        }

        public final String hostname;
        public final long duration;
        @Nonnull
        public final CheckStatus condition;
        /// Status code used by the dynect DNS manager to determine whether to fail over the entire DC. Play nicely.
        ///  Must include the string "OK" to pass dynect.
        @Nonnull
        public final String dcStatus;
    }

    private static final Comparator<CheckResult> ID_COMPARATOR = new Comparator<CheckResult>() {
        @Override
        public int compare (final CheckResult checkResult, final CheckResult checkResult1) {
            return checkResult.getId().compareTo(checkResult1.getId());
        }
    };

    public class DetailedSystemReport extends SystemReport {
        public DetailedSystemReport() {
            appname = CheckResultSet.this.appName;
            catalinaBase = System.getProperty("catalina.base");
            results = Maps.newTreeMap();

            long earliestTimestamp = System.currentTimeMillis();
            for ( final CheckResult result : completedChecks.values() ) {
                SortedSet<CheckResult> set;

                if ( null == (set = results.get(result.getStatus()))) {
                    results.put(result.getStatus(), set = Sets.newTreeSet(ID_COMPARATOR));
                }

                set.add(result);

                final long timestamp = result.getTimestamp();
                if ( timestamp > 0L && timestamp < earliestTimestamp ) {
                    earliestTimestamp = timestamp;
                }
            }

            this.leastRecentlyExecutedTimestamp = earliestTimestamp;
            this.leastRecentlyExecutedDate = CheckResult.DATE_FORMAT.get().format(new Date(leastRecentlyExecutedTimestamp));
        }

        @Nullable
        public final String appname;
        @Nullable
        public final String catalinaBase;
        public final String leastRecentlyExecutedDate;
        public final long leastRecentlyExecutedTimestamp;
        public final SortedMap<CheckStatus,SortedSet<CheckResult>> results;
    }

    private final long startTime;
    @Nullable
    private String appName = null;

    /// The overall health of the system represented by this result set. Assume that all systems
    ///  start in a healthy state.
    private final AtomicReference<CheckStatus> systemStatus = new AtomicReference<CheckStatus>(CheckStatus.OK);

    private final ConcurrentMap<String, Tag> executingChecks = Maps.newConcurrentMap();
    private final ConcurrentMap<String, CheckResult> completedChecks = Maps.newConcurrentMap();

    private static final Logger log = Logger.getLogger(CheckResultSet.class);
}
