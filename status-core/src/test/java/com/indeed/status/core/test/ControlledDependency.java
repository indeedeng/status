package com.indeed.status.core.test;

import com.google.common.base.Supplier;
import com.indeed.status.core.PingableDependency;
import com.indeed.status.core.PingableDependencyBuilder;
import com.indeed.status.core.Urgency;

import javax.annotation.Nonnull;

/** author: cameron */
public class ControlledDependency extends PingableDependency {
    public static final RuntimeException EXCEPTION = new RuntimeException("BAD");
    @Nonnull private final Supplier<Boolean> toggle;
    private boolean inError = true;
    private int times;

    private ControlledDependency(
            @Nonnull final Supplier<Boolean> toggle, @Nonnull final Urgency urgency) {
        super("controlled-id", "controlled-description", urgency);
        this.toggle = toggle;
    }

    public static final class TestDepControlledBuilder
            extends PingableDependencyBuilder<ControlledDependency, TestDepControlledBuilder> {
        private TestDepControlledBuilder() {}

        @Override
        public ControlledDependency build() {
            return new ControlledDependency(toggle, urgency != null ? urgency : Urgency.REQUIRED);
        }
    }

    public static ControlledDependency build() {
        return new TestDepControlledBuilder().build();
    }

    public static TestDepControlledBuilder builder() {
        return new TestDepControlledBuilder();
    }

    public void setInError(boolean toggle) {
        this.inError = toggle;
    }

    public int getTimes() {
        return times;
    }

    @Override
    public void ping() throws Exception {
        if (toggle.get()) {
            times++;
            if (inError) {
                throw EXCEPTION;
            }
        }
    }
}
