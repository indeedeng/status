package com.indeed.status.core;

/**
 * Interface for handler that receives updates on webapp state.
 * @author jack@indeed.com (Jack Humphrey)
 */
public interface StatusManager {

    /** States of the webapp */
    public enum State {
        STARTUP,
        SHUTDOWN,
        UPDATE,
        ERROR,
    };

    /**
     * Update the state of the webapp
     * @param webappDisplayName webapp to update
     * @param newState webapp state
     */
    void updateState(String webappDisplayName, State newState);
}
