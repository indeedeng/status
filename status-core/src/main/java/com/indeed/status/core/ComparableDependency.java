package com.indeed.status.core;

import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author jplaisance
 */
public abstract class ComparableDependency<T extends Comparable<T>> extends AbstractDependency {
    @SuppressWarnings ("UnusedDeclaration")
    private static final Logger log = Logger.getLogger(ComparableDependency.class);

    @Nonnull
    private final T maxOK;

    @Nullable
    private final T maxMinor;

    @Nullable
    private final T maxMajor;

    /**
     * @deprecated Instead, use {@link ComparableDependency#ComparableDependency(String, String, long, long, Urgency, DependencyType, String, T, T ,T)}.
     */
    @Deprecated
    public ComparableDependency(
            @Nonnull final String id,
            @Nonnull final String description,
            final long timeout,
            final long pingPeriod,
            @Nonnull final Urgency urgency,
            @Nonnull final T maxOK,
            @Nullable final T maxMinor,
            @Nullable final T maxMajor
    ) {
        this(id, description, timeout, pingPeriod, urgency, DEFAULT_TYPE, DEFAULT_SERVICE_POOL, maxOK, maxMinor, maxMajor);
    }

    public ComparableDependency(
            @Nonnull final String id,
            @Nonnull final String description,
            final long timeout,
            final long pingPeriod,
            @Nonnull final Urgency urgency,
            @Nonnull final DependencyType type,
            @Nonnull final String servicePool,
            @Nonnull final T maxOK,
            @Nullable final T maxMinor,
            @Nullable final T maxMajor
    ) {
        super(id, description, timeout, pingPeriod, urgency, type, servicePool);
        this.maxOK = maxOK;
        this.maxMinor = maxMinor;
        this.maxMajor = maxMajor;
    }

    @Override
    public final CheckResult call() throws Exception {
        // TODO Replace this with a SimpleDependency and a CheckMethod that accepts a WallClock.
        final long start = System.currentTimeMillis();
        try {
            final T value = getValue();
            final CheckStatus status;
            final T threshold;
            if (value.compareTo(maxOK) <= 0) {
                threshold = null;
                status = CheckStatus.OK;
            } else if (maxMinor != null && value.compareTo(maxMinor) <= 0) {
                threshold = maxOK;
                status = CheckStatus.MINOR;
            } else if (maxMajor != null && value.compareTo(maxMajor) <= 0) {
                threshold = maxMinor;
                status = CheckStatus.MAJOR;
            } else {
                threshold = maxMajor != null ? maxMajor : (maxMinor != null ? maxMinor : maxOK);
                status = CheckStatus.OUTAGE;
            }
            final long duration = System.currentTimeMillis() - start;
            final String errorMessage = formatErrorMessage(status, value, threshold, start, duration, null);
            return CheckResult.newBuilder(this, status, errorMessage)
                    .setTimestamp(start)
                    .setDuration(duration)
                    .build();

        } catch (final Exception e) {
            final long duration = System.currentTimeMillis() - start;
            final String errorMessage = formatErrorMessage(CheckStatus.OUTAGE, null, null, start, duration, e);
            return CheckResult.newBuilder(this, CheckStatus.OUTAGE, errorMessage)
                    .setTimestamp(start)
                    .setDuration(duration)
                    .setThrowable(e)
                    .build();
        }
    }

    protected abstract T getValue() throws Exception;

    protected abstract String formatErrorMessage(CheckStatus status, @Nullable T value, @Nullable T brokenThreshold, long timestamp, long duration, @Nullable Exception e);
}
