package com.indeed.status.core;

/**
 * @author matts
 *
 * @deprecated Use {@link AbstractDependency.Builder} directly
 *
 * TODO Remove by 2017-01-01
 */
public abstract class AbstractDependencyBuilder<T extends AbstractDependency, B extends AbstractDependency.Builder<T, B>>
        extends AbstractDependency.Builder<T,B>
{

}
