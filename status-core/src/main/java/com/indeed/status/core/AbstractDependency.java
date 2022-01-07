package com.indeed.status.core;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * The {@link AbstractDependency} provides a convenience base class for implementers of the {@link
 * Dependency} interface. All primitive properties are captured here as fields, leaving on the
 * {@link #call} method to be implemented.
 */
@ThreadSafe
public abstract class AbstractDependency implements Dependency {
    /*
       Default values that may be used by subclasses. Don't want to 'bless' these by
        adding to constructors here; better would be to offer a builder that used the
        default values. Lots of projects are using the convenience classes like
        pingable dependency, though, so for now we'll just push the permutation as
        low in the class hierarchy as we can.
    */
    public static final long DEFAULT_TIMEOUT = 10 * 1000; // 10 seconds
    public static final long DEFAULT_PING_PERIOD = 30 * 1000; // 30 seconds
    public static final DependencyType DEFAULT_TYPE = DependencyType.StandardDependencyTypes.OTHER;
    public static final String DEFAULT_SERVICE_POOL = "not specified";
    protected static final String DEFAULT_DOCUMENTATION_URL =
            "http://www.example.com/<dependency-id>";

    /**
     * @deprecated Instead, use {@link AbstractDependency#AbstractDependency(String, String, long,
     *     long, Urgency, DependencyType, String)}.
     */
    @Deprecated
    protected AbstractDependency(
            @Nonnull final String id,
            @Nonnull final String description,
            final long timeout,
            final long pingPeriod,
            @Nonnull final Urgency urgency) {
        this(id, description, timeout, pingPeriod, urgency, DEFAULT_TYPE, DEFAULT_SERVICE_POOL);
    }

    /**
     * @deprecated Instead, use {@link AbstractDependency#AbstractDependency(String, String, long,
     *     long, Urgency, DependencyType, String)}.
     */
    @Deprecated
    protected AbstractDependency(
            @Nonnull final String id,
            @Nonnull final String description,
            final long timeout,
            final long pingPeriod,
            @Nonnull final Urgency urgency,
            @Nonnull final DependencyType type,
            final String servicePool) {
        this.id = Preconditions.checkNotNull(id, "Missing id");
        this.description = Strings.nullToEmpty(description);
        this.timeout = timeout;
        this.pingPeriod = pingPeriod;
        this.urgency = Preconditions.checkNotNull(urgency, "Missing urgency");
        this.type = type;
        this.servicePool = servicePool;
        this.documentationUrl = DEFAULT_DOCUMENTATION_URL;
    }

    protected AbstractDependency(
            @Nonnull final AbstractDependency.Builder<? extends AbstractDependency, ?> builder) {
        this.id = Preconditions.checkNotNull(builder.getId(), "Missing id");
        this.description = Strings.nullToEmpty(builder.getDescription());
        this.timeout = builder.getTimeout();
        this.pingPeriod = builder.getPingPeriod();
        this.urgency = Preconditions.checkNotNull(builder.getUrgency(), "Missing urgency");
        this.type = Preconditions.checkNotNull(builder.getType(), "Missing type");
        this.servicePool = Strings.nullToEmpty(builder.getServicePool());
        this.documentationUrl = Strings.nullToEmpty(builder.getDocumentationUrl());
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getDocumentationUrl() {
        return documentationUrl;
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

    public String toString() {
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
    private final String documentationUrl;

    public abstract static class Builder<T extends AbstractDependency, B extends Builder<T, B>> {
        /** @deprecated Direct field access deprecated; use {@link #getId()} instead. */
        @Nonnull protected String id = "";
        /** @deprecated Direct field access deprecated; use {@link #getDescription()} instead. */
        @Nonnull protected String description = "";
        /** @deprecated Direct field access deprecated; use {@link #getTimeout()} instead. */
        @Nonnegative protected long timeout = DEFAULT_TIMEOUT;
        /** @deprecated Direct field access deprecated; use {@link #getPingPeriod()} instead. */
        @Nonnegative protected long pingPeriod = DEFAULT_PING_PERIOD;
        /** @deprecated Direct field access deprecated; use {@link #getUrgency()} instead. */
        @Nonnull protected Urgency urgency = Urgency.UNKNOWN;
        /** @deprecated Direct field access deprecated; use {@link #getType()} instead. */
        @Nonnull protected DependencyType type = DEFAULT_TYPE;
        /** @deprecated Direct field access deprecated; use {@link #getServicePool()} instead. */
        @Nonnull protected String servicePool = DEFAULT_SERVICE_POOL;
        /**
         * @deprecated Direct field access deprecated; use {@link #getDocumentationUrl()} instead.
         */
        @Nonnull protected String documentationUrl = DEFAULT_DOCUMENTATION_URL;

        protected Builder() {}

        public abstract AbstractDependency build();

        @Nonnull
        protected String getId() {
            //noinspection deprecation -- not deprecated for internal use.
            return id;
        }

        @Nonnull
        public B setId(@Nonnull final String id) {
            //noinspection deprecation -- not deprecated for internal use.
            this.id = id;
            return cast();
        }

        @Nonnull
        protected String getDescription() {
            //noinspection deprecation -- not deprecated for internal use.
            return description;
        }

        @Nonnull
        public B setDescription(@Nonnull final String description) {
            //noinspection deprecation -- not deprecated for internal use.
            this.description = description;
            return cast();
        }

        protected long getTimeout() {
            //noinspection deprecation -- not deprecated for internal use.
            return timeout;
        }

        @Nonnull
        public B setTimeout(@Nonnegative final long timeout) {
            //noinspection deprecation -- not deprecated for internal use.
            this.timeout = timeout;
            return cast();
        }

        protected long getPingPeriod() {
            //noinspection deprecation -- not deprecated for internal use.
            return pingPeriod;
        }

        @Nonnull
        public B setPingPeriod(@Nonnegative final long pingPeriod) {
            //noinspection deprecation -- not deprecated for internal use.
            this.pingPeriod = pingPeriod;
            return cast();
        }

        @Nonnull
        protected Urgency getUrgency() {
            //noinspection deprecation -- not deprecated for internal use.
            return urgency;
        }

        @Nonnull
        public B setUrgency(@Nonnull final Urgency urgency) {
            //noinspection deprecation -- not deprecated for internal use.
            this.urgency = urgency;
            return cast();
        }

        @Nonnull
        protected DependencyType getType() {
            //noinspection deprecation -- not deprecated for internal use.
            return type;
        }

        @Nonnull
        public B setType(@Nonnull final DependencyType type) {
            //noinspection deprecation -- not deprecated for internal use.
            this.type = type;
            return cast();
        }

        @Nonnull
        protected String getServicePool() {
            //noinspection deprecation -- not deprecated for internal use.
            return servicePool;
        }

        @Nonnull
        public B setServicePool(@Nonnull final String servicePool) {
            //noinspection deprecation -- not deprecated for internal use.
            this.servicePool = servicePool;
            return cast();
        }

        @Nonnull
        public String getDocumentationUrl() {
            //noinspection deprecation -- not deprecated for internal use.
            return documentationUrl;
        }

        @Nonnull
        public B setDocumentationUrl(@Nonnull final String documentationUrl) {
            //noinspection deprecation -- not deprecated for internal use.
            this.documentationUrl = documentationUrl;
            return cast();
        }

        private B cast() {
            //noinspection unchecked
            return (B) this;
        }
    }
}
