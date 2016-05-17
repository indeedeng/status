package com.indeed.status.core;

/**
 * @author matts
 */

/**
 * The <code>PingMethod</code> encapsulates a snippet of executable code that either runs to completion or fails
 *  with an exception. No runtime parameters are passed.
 * <br/>
 * Effectively equivalent to a {@link Runnable}.
 */
public interface PingMethod {
    /**
     * Runs to completion or throws an exception
     */
    void ping() throws Exception;
}
