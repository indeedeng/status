package com.indeed.status.core;

import com.indeed.util.core.time.DefaultWallClock;
import com.indeed.util.core.time.WallClock;

import javax.annotation.Nonnull;

public class SystemReporter {
    private static final DefaultWallClock DEFAULT_WALL_CLOCK = new DefaultWallClock();

    @Nonnull private final WallClock wallClock;

    public SystemReporter() {
        this(DEFAULT_WALL_CLOCK);
    }

    public SystemReporter(@Nonnull final WallClock wallClock) {
        this.wallClock = wallClock;
    }

    @Nonnull
    public WallClock getWallClock() {
        return wallClock;
    }

    public CheckResultSystemReport collectSystemReport(final CheckResultSet checkResultSet) {
        return checkResultSet.new SystemReport(wallClock);
    }

    public CheckResultSystemReport collectDetailedSystemReport(
            final CheckResultSet checkResultSet) {
        return checkResultSet.new DetailedSystemReport(wallClock);
    }
}
