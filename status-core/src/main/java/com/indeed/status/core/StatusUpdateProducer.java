package com.indeed.status.core;

/**
 * @author matts
 */
public interface StatusUpdateProducer {
    // Remove all listeners
    void clear();

    // Add a single listener for all events
    void addListener(final StatusUpdateListener listener);
}
