package com.indeed.status.core;

import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * The <code>SimpleDependency</code> represents the most common form of dependency with a tiered result level.
 * <br/>
 * See {@link SimplePingableDependency} for a version with a binary result level.
 *
 * @author matts
 */
@ThreadSafe
public class SimpleDependency extends AbstractDependency {
    @Nonnull private final CheckMethod checkMethod;

    // For builder use only
    private SimpleDependency(@Nonnull final Builder builder) {
        super(builder);
        this.checkMethod = Preconditions.checkNotNull(builder.getCheckMethod(), "Cannot construct a simple dependency with a null check method");;
    }

    @Override
    public final CheckResult call() throws Exception {
        // Execute the delegate, passing through any exception.
        return this.checkMethod.call(this);
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

        @Nullable
        public CheckMethod getCheckMethod() {
            return checkMethod;
        }

        @Override
        public SimpleDependency build() {
            final CheckMethod checkMethod = Preconditions.checkNotNull(this.checkMethod, "Cannot construct a simple dependency with a null check method");
            return new SimpleDependency(this);
        }
    }
}
