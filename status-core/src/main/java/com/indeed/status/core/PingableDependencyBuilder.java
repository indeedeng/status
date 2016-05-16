package com.indeed.status.core;

/**
 * @author matts
 *
 * @deprecated Use {@link PingableDependency.Builder} directly
 *
 * TODO Remove by 2017-01-01
 */
@Deprecated
public abstract class PingableDependencyBuilder<T extends PingableDependency, B extends PingableDependency.Builder<T, B>> extends PingableDependency.Builder<T, B>  {
}
