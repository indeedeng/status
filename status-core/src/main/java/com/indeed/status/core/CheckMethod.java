package com.indeed.status.core;

import javax.annotation.Nonnull;

/**
 * The <code>CheckMethod</code> represents a combination between a Callable&lt;CheckResult&gt; and a
 *  Function&lt;Dependency, CheckResult&gt;. Implementers of this interface are responsible for performing some
 *  method to evaluate the availability of the given dependency. The dependency itself is passed to the
 *  execution to allow the metadata associated with the dependency to be added to the resulting CheckResult.
 * <br/>
 * CheckMethod instances are typically passed to a {@link SimpleDependency} builder for execution during scheduled
 *  health checks.
 *
 * @see SimpleDependency
 */
public interface CheckMethod {
    /**
     * @param dependency The dependency being executed. Effectively a metadata provider so that the ID, description,
     *                   et cetera, can be recorded on the CheckResult
     */
    @Nonnull
    CheckResult call(@Nonnull Dependency dependency) throws Exception;
}
