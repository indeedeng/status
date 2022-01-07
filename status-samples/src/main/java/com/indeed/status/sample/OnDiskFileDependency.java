package com.indeed.status.sample;

import com.indeed.status.core.CheckStatus;
import com.indeed.status.core.ComparableDependency;
import com.indeed.status.core.Urgency;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.concurrent.TimeUnit;

/** @author pitz@indeed.com (Jeremy Pitzeruse) */
public class OnDiskFileDependency extends ComparableDependency<Long> {

    public OnDiskFileDependency(@Nonnull final String id) {
        super(
                id,
                id,
                DEFAULT_TIMEOUT,
                DEFAULT_PING_PERIOD,
                Urgency.REQUIRED,
                TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES),
                TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES),
                TimeUnit.MILLISECONDS.convert(15, TimeUnit.MINUTES));
    }

    @Override
    protected Long getValue() throws Exception {
        return System.currentTimeMillis() - new File("/tmp/tst-file").lastModified();
    }

    @Override
    protected String formatErrorMessage(
            @Nonnull final CheckStatus status,
            @Nullable final Long value,
            @Nullable final Long brokenThreshold,
            final long timestamp,
            final long duration,
            @Nullable final Exception e) {
        if (value != null) {
            return "/tmp/tst-file.log has not been updated in " + value + " milliseconds";
        }

        return "Failed to load /tmp/tst-file";
    }
}
