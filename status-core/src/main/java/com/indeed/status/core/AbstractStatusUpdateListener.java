package com.indeed.status.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Convenience class for handling common "important" status updates.
 *
 * @author matts
 */
@SuppressWarnings({"UnusedParameters"})
public class AbstractStatusUpdateListener implements StatusUpdateListener {
    private static final Logger log = LoggerFactory.getLogger(AbstractStatusUpdateListener.class);

    /**
     * Use provided functions if you would like to use this class. If you want to make your own
     * onChanged method implement StatusUpdateListener instead
     *
     * @param source
     * @param original
     * @param updated
     */
    @Override
    public final void onChanged(
            @Nonnull final Dependency source,
            @Nullable final CheckResult original,
            @Nonnull final CheckResult updated) {
        final CheckStatus previous = null == original ? null : original.getStatus();
        final CheckStatus current = updated.getStatus();

        if (log.isTraceEnabled()) {
            log.trace(
                    "Notified of the change in status of "
                            + source.getId()
                            + " from "
                            + previous
                            + " to "
                            + current);
        }

        if (null == previous) {
            onStarted(source, original, updated);

        } else {
            switch (previous) {
                case OK:
                    onDegraded(source, original, updated);
                    break;
                default:
                    switch (current) {
                        case OK:
                            onRestored(source, original, updated);
                            break;
                        case OUTAGE:
                            onDisabled(source, original, updated);
                            break;
                        default:
                            if (previous.isWorseThan(current)) {
                                onImproved(source, original, updated);

                            } else {
                                onWorsened(source, original, updated);
                            }
                    }
            }
        }
    }

    @Override
    public void onChecked(@Nonnull final Dependency source, @Nonnull final CheckResult result) {
        // Do not do anything by default
    }

    @Override
    public void onAdded(@Nonnull final Dependency dependency) {
        // Nothing to do by default
    }

    /**
     * Triggered when the given <code>dependency</code> has fallen out of OK status.
     *
     * @param dependency
     * @param original
     * @param updated
     */
    protected void onDegraded(
            @Nonnull final Dependency dependency,
            @Nullable final CheckResult original,
            @Nonnull final CheckResult updated) {
        // No-op in the base class.
    }

    /**
     * Triggered when the given <code>dependency</code> has improved but is still not OK.
     *
     * @param dependency
     * @param original
     * @param updated
     */
    protected void onImproved(
            @Nonnull final Dependency dependency,
            @Nullable final CheckResult original,
            @Nonnull final CheckResult updated) {
        // No-op in the base class.
    }

    /**
     * Triggered when the given <code>dependency</code> has gotten worse.
     *
     * @param dependency
     * @param original
     * @param updated
     */
    protected void onWorsened(
            @Nonnull final Dependency dependency,
            @Nullable final CheckResult original,
            @Nonnull final CheckResult updated) {
        // No-op in the base class.
    }

    /**
     * Triggered when the given <code>dependency</code> has returned to OK status.
     *
     * @param dependency
     * @param original
     * @param updated
     */
    protected void onRestored(
            @Nonnull final Dependency dependency,
            @Nullable final CheckResult original,
            @Nonnull final CheckResult updated) {
        // No-op in the base class.
    }

    /**
     * Triggered when the given <code>dependency</code> has entered a full outage state.
     *
     * @param dependency
     * @param original
     * @param updated
     */
    protected void onDisabled(
            @Nonnull final Dependency dependency,
            @Nullable final CheckResult original,
            @Nonnull final CheckResult updated) {
        // No-op in the base class.
    }

    /**
     * Triggered when the given <code>dependency</code> has first started.
     *
     * @param dependency
     * @param original
     * @param updated
     */
    protected void onStarted(
            @Nonnull final Dependency dependency,
            @Nullable final CheckResult original,
            @Nonnull final CheckResult updated) {
        // No-op in the base class.
    }
}
