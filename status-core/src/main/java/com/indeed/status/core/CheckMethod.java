package com.indeed.status.core;

import javax.annotation.Nonnull;

/**
 * The <code>CheckMethod</code> represents a combination between a Callable&lt;CheckResult&gt; and a
 * Function&lt;Dependency, CheckResult&gt;. Implementers of this interface are responsible for
 * performing some method to evaluate the availability of the given dependency. The dependency
 * itself is passed to the execution to allow the metadata associated with the dependency to be
 * added to the resulting CheckResult.
 *
 * <p>CheckMethod instances are typically passed to a {@link SimpleDependency} builder for execution
 * during scheduled health checks.
 *
 * <p>The checkMethod implementation may be triggered by multiple threads simultaneously and thus
 * must be implemented in a thread-safe manner. No synchronization is performed by the healthcheck
 * framework; any areas of the code that must be guarded must be explicitly locked by the
 * implementation itself.
 *
 * @see SimpleDependency
 */
public interface CheckMethod {
    /**
     * @param dependency The dependency being executed. Effectively a metadata provider so that the
     *     ID, description, et cetera, can be recorded on the CheckResult
     */
    @Nonnull
    CheckResult call(@Nonnull Dependency dependency) throws Exception;
}
