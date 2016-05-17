package com.indeed.status.core;

/**
 * @author matts
 */

public interface PingMethod {
    /**
     * Runs to completion or throws an exception
     */
    void ping() throws Exception;
}
