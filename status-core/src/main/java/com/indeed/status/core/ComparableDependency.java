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

    private final @Nonnull T maxOK;

    private final @Nullable T maxMinor;

    private final @Nullable T maxMajor;

    public ComparableDependency(
            @Nonnull String id,
            @Nonnull String description,
            long timeout,
            long pingPeriod,
            @Nonnull Urgency urgency,
            @Nonnull T maxOK,
            @Nullable T maxMinor,
            @Nullable T maxMajor
    ) {
        super(id, description, timeout, pingPeriod, urgency);
        this.maxOK = maxOK;
        this.maxMinor = maxMinor;
        this.maxMajor = maxMajor;
    }

    @Override
    public final CheckResult call() throws Exception {
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

        } catch (Exception e) {
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
