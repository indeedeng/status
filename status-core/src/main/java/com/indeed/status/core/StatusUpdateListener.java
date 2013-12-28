package com.indeed.status.core;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 *
 * @author matts
 */
public interface StatusUpdateListener {
    /**
     * Triggered when the status of a dependency has changed.
     *
     * @param source
     * @param original
     * @param updated
     */
    void onChanged(
            @Nonnull final Dependency source,
            @Nullable final CheckResult original,
            @Nonnull final CheckResult updated
    );

    /**
     * Triggered when a new dependency is added
     * @param dependency
     */
    void onAdded(@Nonnull final Dependency dependency);
}
