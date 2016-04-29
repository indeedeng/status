package com.indeed.status.core;

import java.util.Iterator;

/**
 * @author matts
 */
public interface StatusUpdateProducer {
    /**
     * Remove all listeners
     */
    void clear();

    /**
     * Add a single listener for all events
     *
     * @param listener
     */
    void addListener(final StatusUpdateListener listener);

    /**
     * Allow iterating over all the added listeners
     */
    Iterator<StatusUpdateListener> listeners();
}
