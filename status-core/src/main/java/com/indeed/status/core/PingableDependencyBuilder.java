package com.indeed.status.core;

/**
 * @author matts
 */
public abstract class PingableDependencyBuilder<T extends PingableDependency, B extends PingableDependencyBuilder<T,B>>
    extends AbstractDependencyBuilder<T, B>
{
    protected PingableDependencyBuilder() {}
}
