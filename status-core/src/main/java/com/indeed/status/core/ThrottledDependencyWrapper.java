package com.indeed.status.core;

import java.util.concurrent.atomic.AtomicInteger;

/** *
 * The <code>ThrottledDependencyWrapper</code> is a wrapper around another dependency, and prevents too many concurrent
 *  executions of the same healthcheck.
 *
 * @author dkraft
 */
public class ThrottledDependencyWrapper implements Dependency {
    private final Dependency dependency;
    private final AtomicInteger numberOfRunningHealthChecks;

    public ThrottledDependencyWrapper(final Dependency dependency) {
        this.dependency = dependency;
        numberOfRunningHealthChecks = new AtomicInteger(0);
    }

    @Override
    public CheckResult call() throws Exception {
        try {
            before();
            return dependency.call();
        } catch(final IllegalStateException e) {
            return CheckResult.newBuilder(this, CheckStatus.OUTAGE, "Exception thrown during ping")
                    .setThrowable(e)
                    .build();
        } finally {
            after();
        }
    }

    private void before() {
        // Limit the number of running healthchecks here if the throttle is active, since
        // DependencyChecker can't guarantee that it can cancel a running healthcheck.
        if (numberOfRunningHealthChecks.incrementAndGet() > 2) {
            throw new IllegalStateException(
                    String.format("Unable to ping dependency %s because there are already two previous pings that haven't " +
                            "returned. To turn off this behavior set throttle to false.", getId())
            );
        }
    }

    private void after() {
        numberOfRunningHealthChecks.decrementAndGet();
    }

    @Override
    public String getId() {
        return dependency.getId();
    }

    @Override
    public String getDescription() {
        return dependency.getDescription();
    }

    @Override
    public String getDocumentationUrl() {
        return dependency.getDocumentationUrl();
    }

    @Override
    public long getTimeout() {
        return dependency.getTimeout();
    }

    @Override
    public long getPingPeriod() {
        return dependency.getPingPeriod();
    }

    @Override
    public Urgency getUrgency() {
        return dependency.getUrgency();
    }

    @Override
    public DependencyType getType() {
        return dependency.getType();
    }

    @Override
    public String getServicePool() {
        return dependency.getServicePool();
    }
}
