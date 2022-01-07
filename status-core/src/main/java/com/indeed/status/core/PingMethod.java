package com.indeed.status.core;

/** @author matts */
import javax.annotation.concurrent.ThreadSafe;

/**
 * The <code>PingMethod</code> encapsulates a snippet of executable code that either runs to
 * completion or fails with an exception. No runtime parameters are passed.
 *
 * <p>Effectively equivalent to a {@link Runnable}.
 *
 * <p>The pingMethod implementation may be triggered by multiple threads simultaneously, and thus
 * must be implemented in a thread-safe manner. No synchronization is performed by the healthcheck
 * framework; any areas of the code that must be guarded must be explicitly locked by the
 * implementation itself.
 */
@ThreadSafe
public interface PingMethod {
    /** Runs to completion or throws an exception */
    void ping() throws Exception;
}
