package com.indeed.status.core;

import org.junit.Test;

/** */
public class AbstractDependencyBuilderTest {
    /**
     * Mimic the common existing extended builders in client applications. Ensure any parametric
     * typing changes we make in status-core do not cause downstream ripples.
     */
    @Test
    public void testExistingMethods() throws Exception {
        AbstractDependencyBuilder<SimpleDependency, ExistingExtendedBuilder> builder =
                new ExistingExtendedBuilder()
                        .setId("abc")
                        // Ensure we get the proper cast object back from the superclass mutator
                        .setFoo("foo");
    }

    private static class ExistingExtendedBuilder
            extends AbstractDependencyBuilder<SimpleDependency, ExistingExtendedBuilder> {
        public ExistingExtendedBuilder setFoo(final String foo) {
            // noop
            return this;
        }

        @Override
        public AbstractDependency build() {
            return SimpleDependency.newBuilder()
                    .setId(getId())
                    .setDescription(getDescription())
                    .build();
        }
    }
}
