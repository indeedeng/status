package com.indeed.status.core;

import org.junit.Test;

/**
 *
 */
public class PingableDependencyBuilderTest {
    @Test
    public void testBuilderWithDelegate() throws Exception {
        final PingableDependency dependency = SimplePingableDependency.newBuilder()
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
        final PingableDependency dependency = new MyPingableBuilder()
                .setId("id")
                .setDescription("description")
                .build();

        dependency.ping();
    }

    @Test(expected = NullPointerException.class)
    public void testBuilderWithoutDelegateOrOverride() throws Exception {
        final PingableDependency dependency = SimplePingableDependency.newBuilder()
                .setId("id")
                .setDescription("description")
                .build();

        dependency.ping();
    }

    private static class MyPingableBuilder extends PingableDependency.Builder<PingableDependency, MyPingableBuilder> {
        @Override
        public PingableDependency build() {
            // Create a simple dependency with an overridden ping() method.
            // This was the standard method of providing the 'core' dependency code in the original distribution.
            //noinspection deprecation -- the point is to regression test the deprecate case.
            return new PingableDependency(
                    getId(),
                    getDescription(),
                    getTimeout(),
                    getPingPeriod(),
                    getUrgency(),
                    getType(),
                    getServicePool(),
                    getToggle()
            ) {
                @Override
                public void ping() throws Exception {
                    // Always ok.
                }
            };
        }
    }
}
