package com.indeed.status.core;

/**
 * @author matts
 */

import javax.annotation.concurrent.ThreadSafe;

/**
 * <p>
 * The <code>PingMethod</code> encapsulates a snippet of executable code that either runs to completion or fails
 *  with an exception. No runtime parameters are passed.
 * </p><p>
 * Effectively equivalent to a {@link Runnable}.
 * </p><p>
 * The pingMethod implementation may be triggered by multiple threads simultaneously, and thus must be
 *  implemented in a thread-safe manner. No synchronization is performed by the healthcheck framework;
 *  any areas of the code that must be guarded must be explicitly locked by the implementation itself.
 * </p>
 */
@ThreadSafe
public interface PingMethod {
    /**
     * Runs to completion or throws an exception
     */
    void ping() throws Exception;
}
