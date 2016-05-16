package com.indeed.status.core;

import org.junit.Test;

/**
 *
 */
public class PingableDependencyBuilderTest {
    @Test
    public void testBuilderWithDelegate() throws Exception {
        final PingableDependency dependency = new PingableDependency.SimplePingableDependencyBuilder()
                .setId("id")
                .setDescription("description")
                .setPingMethod(new Runnable() {
                    @Override
                    public void run() {
                        // always true
                    }
                })
                .build();

        dependency.ping();
    }

    @Test
    public void testBuilderWithOverride() throws Exception {
        final PingableDependency.SimplePingableDependencyBuilder builder = new PingableDependency.SimplePingableDependencyBuilder() {
            @Override
            public PingableDependency build() {
                final PingableDependency template = super.build();

                // Create a simple dependency with an overridden ping() method.
                // This was the standard method of providing the 'core' dependency code in the original distribution.
                //noinspection deprecation -- the point is to regression test the deprecate case.
                return new PingableDependency(
                        template.getId(),
                        template.getDescription(),
                        template.getTimeout(),
                        template.getPingPeriod(),
                        template.getUrgency(),
                        template.getType(),
                        template.getServicePool(),
                        getToggle()
                ) {
                    @Override
                    public void ping() throws Exception {
                        // Always ok.
                    }
                };
            }
        };

        final PingableDependency dependency = builder
                .setId("id")
                .setDescription("description")
                .build();

        dependency.ping();
    }

    @Test(expected = IllegalStateException.class)
    public void testBuilderWithoutDelegateOrOverride() throws Exception {
        final PingableDependency dependency = new PingableDependency.SimplePingableDependencyBuilder()
                .setId("id")
                .setDescription("description")
                .build();

        dependency.ping();
    }
}
