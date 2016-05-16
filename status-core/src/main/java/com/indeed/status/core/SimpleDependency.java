package com.indeed.status.core;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The <code>SimpleDependency</code> represents the most common form of dependency with a tiered result level.
 *
 * @see PingableDependency
 *
 * @author matts
 */
public class SimpleDependency extends AbstractDependency {
    @Nonnull private final Optional<CheckMethod> checkMethod;

    // For builder use only
    private SimpleDependency(
            @Nonnull final String id,
            @Nonnull final CheckMethod checkMethod,
            @Nonnull final String description,
            final long timeout,
            final long pingPeriod,
            @Nonnull final Urgency urgency,
            @Nonnull final DependencyType type,
            final String servicePool
    ) {
        super(id, description, timeout, pingPeriod, urgency, type, servicePool);
        this.checkMethod = Optional.of(checkMethod);
    }

    @Override
    public CheckResult call() throws Exception {
        Preconditions.checkState(this.checkMethod.isPresent(),
                "Dependency '%s' neither overrides the call() method nor provides a checkMethod implementation.", getId());

        // Execute the delegate, passing through any exception.
        return this.checkMethod.get().call(this);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder extends AbstractDependency.Builder<SimpleDependency, Builder> {
        @Nullable private CheckMethod checkMethod;

        protected Builder() {

        }

        public Builder setCheckMethod(@Nonnull final CheckMethod checkMethod) {
            this.checkMethod = checkMethod;
            return this;
        }

        @Override
        public SimpleDependency build() {
            final CheckMethod checkMethod = Preconditions.checkNotNull(this.checkMethod, "Cannot construct a simple dependency with a null check method");
            return new SimpleDependency(
                    getId(),
                    checkMethod,
                    getDescription(),
                    getTimeout(),
                    getPingPeriod(),
                    getUrgency(),
                    getType(),
                    getServicePool());
        }
    }
}
