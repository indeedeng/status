package com.indeed.status.core;

import com.google.common.collect.Lists;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple delegate that can be used by StatusUpdateProducer implementers as a delegate
 *
 * @author matts
 */
class StatusUpdateDelegate implements StatusUpdateProducer, StatusUpdateListener {
    private static final Logger log = Logger.getLogger(StatusUpdateDelegate.class);

    private final List<StatusUpdateListener> listeners;

    StatusUpdateDelegate () {
        final List<StatusUpdateListener> l = Lists.newArrayListWithExpectedSize(2);
        listeners = new CopyOnWriteArrayList<StatusUpdateListener>(l);
    }

    @Override
    public void onChanged (@Nonnull final Dependency source, @Nullable final CheckResult original, @Nonnull final CheckResult updated) {
        if (log.isTraceEnabled()) {
            log.trace("Notifying " + listeners.size() + " listeners of the change to " + source + " from " + original + " to " + updated);
        }

        for (final StatusUpdateListener listener: listeners) {
            try {
                listener.onChanged(source, original, updated);

            } catch(RuntimeException e) {
                log.error("Status update listeners should not throw errors. Something must be tragically wrong.", e);

                // Swallow runtime exceptions. Allow Errors through.
            }
        }
    }

    @Override
    public void onAdded(@Nonnull final Dependency dependency) {
        for (final StatusUpdateListener listener: listeners) {
            try {
                listener.onAdded(dependency);

            } catch(RuntimeException e) {
                log.error("Status update listeners should not throw errors. Something must be tragically wrong.", e);

                // Swallow runtime exceptions. Allow Errors through.
            }
        }
    }

    @Override
    public void addListener (final StatusUpdateListener listener) {
        listeners.add(listener);
    }

    @Override
    public Iterator<StatusUpdateListener> listeners() {
        return listeners.iterator();
    }

    @Override
    public void clear() {
        listeners.clear();
    }
}
