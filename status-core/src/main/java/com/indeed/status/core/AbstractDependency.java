package com.indeed.status.core;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import javax.annotation.Nonnull;

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
    public static final DependencyType DEFAULT_TYPE = DependencyType.StandardDependencyTypes.OTHER;
    public static final String DEFAULT_SERVICE_POOL = "not specified";

    /**
     * @deprecated Instead, use {@link AbstractDependency#AbstractDependency(String, String, long, long, Urgency, DependencyType, String)}.
     */
    @Deprecated
    protected AbstractDependency(
            @Nonnull final String id,
            @Nonnull final String description,
            final long timeout,
            final long pingPeriod,
            @Nonnull final Urgency urgency
    ) {
        this(id, description, timeout, pingPeriod, urgency, DEFAULT_TYPE, DEFAULT_SERVICE_POOL);
    }

    protected AbstractDependency(
            @Nonnull final String id,
            @Nonnull final String description,
            final long timeout,
            final long pingPeriod,
            @Nonnull final Urgency urgency,
            @Nonnull final DependencyType type,
            final String servicePool
    ) {
        this.id = Preconditions.checkNotNull(id, "Missing id");
        // TODO This should really be check-not-empty precondition, but let's not break things.
        this.description = Strings.nullToEmpty(description);
        this.timeout = timeout;
        this.pingPeriod = pingPeriod;
        this.urgency = Preconditions.checkNotNull(urgency, "Missing urgency");
        this.type = type;
        this.servicePool = servicePool;
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

    @Override
    public DependencyType getType() {
        return type;
    }

    @Override
    public String getServicePool() {
        return servicePool;
    }

    public String toString () {
        final StringBuilder sb = new StringBuilder();
        sb.append("Dependency");
        sb.append("{urgency=").append(urgency);
        sb.append(", id='").append(id).append('\'');
        sb.append(", type='").append(type.toString()).append('\'');
        sb.append(", servicePool='").append(servicePool).append('\'');
        sb.append('}');
        return sb.toString();
    }

    private final String id;
    private final String description;
    private final long timeout;
    private final long pingPeriod;
    private final Urgency urgency;
    private final DependencyType type;
    private final String servicePool;
}
