package com.indeed.status.core.test;

import com.google.common.base.Supplier;
import com.indeed.status.core.PingableDependency;
import com.indeed.status.core.PingableDependencyBuilder;
import com.indeed.status.core.Urgency;

import javax.annotation.Nonnull;

/**
 * author: cameron
 */
public class TestDepControlled extends PingableDependency {
    public static final RuntimeException EXCEPTION = new RuntimeException("BAD");
    @Nonnull
    private final Supplier<Boolean> toggle;
    private boolean inError = true;
    private int times;

    private TestDepControlled(@Nonnull Supplier<Boolean> toggle) {
        super("controlled-id", "controlled-description", Urgency.REQUIRED);
        this.toggle = toggle;
    }

    public static final class TestDepControlledBuilder extends PingableDependencyBuilder<TestDepControlled, TestDepControlledBuilder> {
        private TestDepControlledBuilder() {
        }

        @Override
        public TestDepControlled build() {
            return new TestDepControlled(toggle);
        }
    }

    public static TestDepControlled build() {
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
