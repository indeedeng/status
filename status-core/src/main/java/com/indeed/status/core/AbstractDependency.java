package com.indeed.status.core;

/**
 * The {@link AbstractDependency} provides a convenience base class for implementers of
 *  the {@link Dependency} interface. All primitive properties are captured here as fields,
 *  leaving on the {@link #call} method to be implemented.
 */
public abstract class AbstractDependency implements Dependency {
    /*
        Default values that may be used by subclasses. Don't want to 'bless' these by
         adding to constructors here; better would be to offer a builder that used the
         default values. Lots of projects are using the convenience classes like
         pingable dependency, though, so for now we'll just push the permutation as
         low in the class hierarchy as we can.
     */
    public static final long DEFAULT_TIMEOUT = 10*1000; // 10 seconds
    public static final long DEFAULT_PING_PERIOD = 30*1000; // 30 seconds

    protected AbstractDependency(String id, String description, long timeout, long pingPeriod, Urgency urgency) {
        this.id = id;
        this.description = description;
        this.timeout = timeout;
        this.pingPeriod = pingPeriod;
        this.urgency = urgency;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public String getDocumentationUrl() {
        return "http://example.com/?" + id; // todo: this should be abstract so that everything is documented
    }

    @Override
    public long getTimeout() {
        return timeout;
    }

    public long getPingPeriod() {
        return pingPeriod;
    }

    @Override
    public Urgency getUrgency() {
        return urgency;
    }

    public String toString () {
        final StringBuilder sb = new StringBuilder();
        sb.append("Dependency");
        sb.append("{urgency=").append(urgency);
        sb.append(", id='").append(id).append('\'');
        sb.append('}');
        return sb.toString();
    }

    private final String id;
    private final String description;
    private final long timeout;
    private final long pingPeriod;
    private final Urgency urgency;
}
