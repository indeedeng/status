package com.indeed.status.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Longs;
import com.indeed.status.core.DependencyChecker.CheckException;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The {@link CheckResult} represents the outcome of a single dependency check. This object is
 * immutable and may be persisted indefinitely to communicate the snapshot status of the dependency
 * to interested parties.
 */
@SuppressWarnings({
    "UnusedDeclaration"
}) // Suppress unused declarations since most are there for serialization
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class CheckResult {
    /// The final status resulting from the dependency evaluation
    @Nonnull private final CheckStatus status;
    /// More detailed description of the dependency, for use by operators reading the report.
    @Nonnull private final String description;
    /// More detailed status message describing the final result of the dependency check
    @Nonnull private final String errorMessage;
    /// The time this result was generated
    @Nullable private final Date timestamp;
    /// The duration of the check
    @Nonnegative private final long duration;
    /// The last known time this result was OK'd.
    @Nonnegative private final long lastKnownGoodTimestamp;
    /// The periodicity of this result
    @Nonnegative private final long period;
    /// The id of the dependency that generated this result;
    @Nonnull private final String id;
    /// The urgency of this dependency
    @Nonnull private final Urgency urgency;
    /// The documentation URL giving additional info about the result
    @Nonnull private final String documentationUrl;
    /// The exception thrown during execution, if any.
    @Nonnull private final DependencyType type;
    @Nonnull private final String servicePool;
    @JsonIgnore private final Throwable throwable;

    public static final ThreadLocal<DateFormat> DATE_FORMAT =
            new ThreadLocal<DateFormat>() {
                @Override
                protected DateFormat initialValue() {
                    return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                }
            };

    /** @deprecated Use {@link CheckResult.Builder} instead */
    @Deprecated
    public CheckResult(
            @Nonnull final Dependency dependency,
            @Nonnull final CheckStatus status,
            @Nonnull final String errorMessage,
            @Nonnegative final long timestamp,
            @Nonnegative final long duration,
            @Nonnegative final long period,
            @Nullable final Throwable t) {
        this(
                dependency,
                status,
                errorMessage,
                timestamp,
                duration,
                0L, /* lastKnownGoodTimestamp */
                period,
                t);
    }

    private CheckResult(
            @Nonnull final Dependency dependency,
            @Nonnull final CheckStatus status,
            @Nonnull final String errorMessage,
            @Nonnegative final long timestamp,
            @Nonnegative final long duration,
            @Nonnegative final long lastKnownGoodTimestamp,
            @Nonnegative final long period,
            @Nullable final Throwable t) {
        this.id = dependency.getId();
        this.status = status;
        this.description = dependency.getDescription();
        this.errorMessage = errorMessage;
        this.documentationUrl = dependency.getDocumentationUrl();
        this.urgency = dependency.getUrgency();
        this.type = dependency.getType();
        this.servicePool = dependency.getServicePool();
        this.timestamp = 0 == timestamp ? null : new Date(timestamp);
        this.duration = duration;
        this.lastKnownGoodTimestamp = lastKnownGoodTimestamp;
        this.period = period;
        this.throwable = t;
    }

    @Nonnull
    public String getId() {
        return id;
    }

    @Nonnull
    public CheckStatus getStatus() {
        return status;
    }

    @Nonnull
    public String getDescription() {
        return description;
    }

    @Nonnull
    public String getErrorMessage() {
        return errorMessage;
    }

    @Nonnull
    public String getDocumentationUrl() {
        return documentationUrl;
    }

    @Nonnegative
    public long getDuration() {
        return duration;
    }

    @Nonnegative
    public long getLastKnownGoodTimestamp() {
        return lastKnownGoodTimestamp;
    }

    @Nonnegative
    public String getUrgency() {
        return String.valueOf(urgency);
    }

    @JsonSerialize(using = ToStringSerializer.class)
    @Nonnull
    public DependencyType getType() {
        return type;
    }

    @Nonnull
    public String getServicePool() {
        return servicePool;
    }

    @Nonnegative
    public long getTimestamp() {
        return null == timestamp ? 0L : timestamp.getTime();
    }

    @Nullable
    public String getDate() {
        return null == timestamp ? null : DATE_FORMAT.get().format(timestamp);
    }

    @Nonnegative
    public long getPeriod() {
        return period;
    }

    @Nullable
    public Thrown getThrown() {
        return null == throwable ? null : new Thrown(throwable);
    }

    @JsonIgnore
    public Throwable getThrowable() {
        return throwable;
    }

    @Nonnull
    public String toString() {
        return "{'id':'" + getId() + "';'status':'" + status + "';}";
    }

    @SuppressWarnings({"UnusedDeclaration"})
    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    public static class Thrown {
        private static final int MAX_DEPTH = 20;
        private static final int MAX_TRACE = 20;

        public Thrown(@Nonnull final Throwable throwable) {
            this(
                    throwable instanceof CheckException ? throwable.getCause() : throwable,
                    new HashSet<Throwable>(),
                    0);
        }

        private Thrown(
                @Nonnull final Throwable throwable, final Set<Throwable> seen, final int depth) {
            this.message = throwable.getMessage();
            this.exception = throwable.getClass().getSimpleName();

            final StackTraceElement[] trace = throwable.getStackTrace();
            if (null != trace) {
                this.stack =
                        trace.length > MAX_TRACE
                                ? Lists.newArrayList(Arrays.asList(trace).subList(0, MAX_TRACE))
                                : Lists.newArrayList(trace);
            }

            if (depth <= MAX_DEPTH) {
                final Throwable cause = throwable.getCause();
                if (null != cause && !seen.contains(cause)) {
                    seen.add(cause);
                    this.thrown = new Thrown(cause, seen, depth + 1);
                } else {
                    this.thrown = null;
                }
            } else {
                this.thrown = null;
            }
        }

        public String getException() {
            return exception;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getStack() {
            // TODO - ketan's right that collapsing the rich data on the stack trace isn't right,
            // but
            //  as long it's mostly humans reading the output, the terser format is more usable. We
            //  could bridge the gap with a nondefault pretty-printer if we wanted to, but for now
            //  let's stick with the well-recognized format.
            return Lists.transform(this.stack, Functions.toStringFunction());
        }

        public Thrown getThrown() {
            return thrown;
        }

        private final String exception;
        private final String message;
        private final Thrown thrown;
        private volatile List<StackTraceElement> stack;
    }

    @Nonnull
    public static Builder newBuilder(
            @Nonnull final Dependency dependency,
            @Nonnull final CheckStatus status,
            @Nonnull final String errorMessage) {
        return new Builder(dependency, status, errorMessage);
    }

    @Nonnull
    public static Builder newBuilder(
            @Nonnull final Dependency dependency, @Nonnull final CheckResult source) {
        return newBuilder(dependency, source.getStatus(), source.getErrorMessage())
                .setTimestamp(source.getTimestamp())
                .setDuration(source.getDuration())
                .setLastKnownGoodTimestamp(source.getLastKnownGoodTimestamp())
                .setPeriod(source.getPeriod())
                .setThrowable(source.getThrowable());
    }

    public static class Builder {
        @Nonnull private Dependency dependency;
        @Nonnull private CheckStatus status;
        @Nonnull private String errorMessage;
        @Nonnegative private long timestamp = 0L;
        @Nonnegative private long duration = 0L;
        @Nonnegative private long lastKnownGoodTimestamp = 0L;
        @Nonnegative private long period = 0L;
        @Nullable private Throwable t;

        private Builder(
                @Nonnull final Dependency dependency,
                @Nonnull final CheckStatus status,
                @Nonnull final String errorMessage) {
            this.dependency = dependency;
            this.status = status;
            this.errorMessage = errorMessage;
        }

        public Builder setDependency(@Nonnull final Dependency dependency) {
            this.dependency =
                    Preconditions.checkNotNull(dependency, "Missing dependency reference");
            return this;
        }

        public Builder setStatus(@Nonnull final CheckStatus status) {
            this.status = Preconditions.checkNotNull(status, "Missing status");
            return this;
        }

        public Builder setErrorMessage(@Nonnull final String errorMessage) {
            this.errorMessage = Preconditions.checkNotNull(errorMessage, "Missing error message");
            return this;
        }

        public Builder setTimestamp(@Nonnegative final long timestamp) {
            this.timestamp = Longs.max(0, timestamp);
            return this;
        }

        public Builder setDuration(@Nonnegative final long duration) {
            this.duration = Longs.max(0, duration);
            return this;
        }

        public Builder setLastKnownGoodTimestamp(@Nonnegative final long lastKnownGoodTimestamp) {
            this.lastKnownGoodTimestamp = Longs.max(0, lastKnownGoodTimestamp);
            return this;
        }

        public Builder setPeriod(@Nonnegative final long period) {
            this.period = Longs.max(0, period);
            return this;
        }

        public Builder setThrowable(@Nullable final Throwable t) {
            this.t = t;
            return this;
        }

        public CheckResult build() {
            return new CheckResult(
                    dependency,
                    status,
                    errorMessage,
                    timestamp,
                    duration,
                    lastKnownGoodTimestamp,
                    period,
                    t);
        }
    }
}
