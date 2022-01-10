package com.indeed.status.core;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.indeed.util.core.time.WallClock;
import com.indeed.util.varexport.Export;
import com.indeed.util.varexport.VarExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The {@link AbstractDependencyManager} is a singleton clearinghouse responsible for knowing all
 * dependencies of the system and their current availability.
 */
public abstract class AbstractDependencyManager
        implements StatusUpdateProducer, StatusUpdateListener /*,Terminable todo(cameron)*/ {
    private static final int DEFAULT_PING_PERIOD = 30 * 1000; // 30 seconds
    private static final AtomicInteger DEFAULT_THREAD_POOL_COUNT = new AtomicInteger(1);
    private static final AtomicInteger MANAGEMENT_THREAD_POOL_COUNT = new AtomicInteger(1);

    @Nonnull private final Logger log;
    @Nullable private final String appName;

    /// Timer for managing scheduled executions
    @Nonnull private final ScheduledExecutorService executor;

    /// Thread pool for running dependency checks
    @Nonnull private final ThreadPoolExecutor threadPool;

    /// Container for checking all dependencies
    @Nonnull private final DependencyChecker checker;

    /// Delegate for handling event propagation
    private final StatusUpdateDelegate updateHandler = new StatusUpdateDelegate();

    /// Collection of all dependencies governed by this manager. The keys of this map are the unique
    ///  String identifiers of each dependency. The values are the immutable objects representing
    // the
    ///  canonical view of each dependency. This map does <em>not</em> indicate the current status
    ///  of dependencies, but rather the set of dependencies that are registered with the system.
    @Nonnull private final ConcurrentMap<String, Dependency> dependencies = Maps.newConcurrentMap();

    /// Collection of all dependency pingers that have been created to monitor the health of a
    // dependency.
    /// Once a dependency is removed from the manager, its associated pinger is cancelled.
    @Nonnull
    private final ConcurrentMap<String, ScheduledFuture<?>> dependencyPingers =
            Maps.newConcurrentMap();

    private long pingPeriod = DEFAULT_PING_PERIOD;

    public static class Qualifiers {
        protected Qualifiers() {
            throw new UnsupportedOperationException("ResultType is a constants class.");
        }

        public static final String LIVE = "live";
        public static final String BACKGROUND =
                "bkgd"; // NOTE: Varying slightly from the "background" string used by client
        // projects
        //  just so we don't trip somebody up.
    }

    /**
     * @deprecated Use {@link
     *     #AbstractDependencyManager(com.indeed.status.core.AbstractDependencyManagerParams)}
     */
    @Deprecated
    public AbstractDependencyManager() {
        this(ImmutableDependencyManagerParams.builder().build());
    }

    /**
     * @deprecated Use {@link
     *     #AbstractDependencyManager(com.indeed.status.core.AbstractDependencyManagerParams)}
     */
    @Deprecated
    public AbstractDependencyManager(final String appName) {
        this(ImmutableDependencyManagerParams.builder().appName(appName).build());
    }

    /**
     * @deprecated Use {@link
     *     #AbstractDependencyManager(com.indeed.status.core.AbstractDependencyManagerParams)}
     */
    @Deprecated
    public AbstractDependencyManager(final String appName, final org.apache.log4j.Logger logger) {
        this(
                ImmutableDependencyManagerParams.builder()
                        .appName(appName)
                        .loggerName(logger == null ? null : logger.getName())
                        .build());
    }

    /**
     * @deprecated Use {@link
     *     #AbstractDependencyManager(com.indeed.status.core.AbstractDependencyManagerParams)}
     */
    @Deprecated
    public AbstractDependencyManager(
            final String appName,
            final org.apache.log4j.Logger logger,
            @Nonnull final SystemReporter systemReporter) {
        this(
                ImmutableDependencyManagerParams.builder()
                        .appName(appName)
                        .loggerName(logger == null ? null : logger.getName())
                        .systemReporter(systemReporter)
                        .build());
    }

    /**
     * @deprecated Use {@link
     *     #AbstractDependencyManager(com.indeed.status.core.AbstractDependencyManagerParams)}
     */
    @Deprecated
    public AbstractDependencyManager(
            final String appName,
            final org.apache.log4j.Logger logger,
            @Nonnull final SystemReporter systemReporter,
            final boolean throttleDependencyChecks) {
        this(
                ImmutableDependencyManagerParams.builder()
                        .appName(appName)
                        .loggerName(logger == null ? null : logger.getName())
                        .systemReporter(systemReporter)
                        .throttleDependencyChecks(throttleDependencyChecks)
                        .build());
    }

    /**
     * @deprecated Use {@link
     *     #AbstractDependencyManager(com.indeed.status.core.AbstractDependencyManagerParams)}
     */
    @Deprecated
    public AbstractDependencyManager(
            @Nullable final String appName,
            @Nullable final org.apache.log4j.Logger logger,
            @Nonnull final DependencyChecker checker) {
        this(
                ImmutableDependencyManagerParams.builder()
                        .appName(appName)
                        .loggerName(logger == null ? null : logger.getName())
                        .checker(checker)
                        .build());
    }

    /**
     * @deprecated Use {@link
     *     #AbstractDependencyManager(com.indeed.status.core.AbstractDependencyManagerParams)}
     */
    @Deprecated
    public AbstractDependencyManager(
            @Nullable final String appName,
            @Nullable final org.apache.log4j.Logger logger,
            @Nonnull final ThreadPoolExecutor threadPool) {
        this(
                ImmutableDependencyManagerParams.builder()
                        .appName(appName)
                        .loggerName(logger == null ? null : logger.getName())
                        .threadPool(threadPool)
                        .build());
    }

    /**
     * @deprecated Use {@link
     *     #AbstractDependencyManager(com.indeed.status.core.AbstractDependencyManagerParams)}
     */
    @Deprecated
    public AbstractDependencyManager(
            @Nullable final String appName,
            @Nullable final org.apache.log4j.Logger logger,
            @Nonnull final ThreadPoolExecutor threadPool,
            @Nonnull final WallClock wallClock) {
        this(
                ImmutableDependencyManagerParams.builder()
                        .appName(appName)
                        .loggerName(logger == null ? null : logger.getName())
                        .threadPool(threadPool)
                        .wallClock(wallClock)
                        .build());
    }

    /**
     * @deprecated Use {@link
     *     #AbstractDependencyManager(com.indeed.status.core.AbstractDependencyManagerParams)}
     */
    @Deprecated
    public AbstractDependencyManager(
            @Nullable final String appName,
            @Nullable final org.apache.log4j.Logger logger,
            @Nonnull final ThreadPoolExecutor threadPool,
            @Nonnull final SystemReporter systemReporter) {
        this(
                ImmutableDependencyManagerParams.builder()
                        .appName(appName)
                        .loggerName(logger == null ? null : logger.getName())
                        .threadPool(threadPool)
                        .systemReporter(systemReporter)
                        .build());
    }

    /**
     * @deprecated Use {@link
     *     #AbstractDependencyManager(com.indeed.status.core.AbstractDependencyManagerParams)}
     */
    @Deprecated
    public AbstractDependencyManager(
            @Nullable final String appName,
            @Nullable final org.apache.log4j.Logger logger,
            @Nonnull final ThreadPoolExecutor threadPool,
            @Nonnull final SystemReporter systemReporter,
            final boolean throttleDependencyChecks) {
        this(
                ImmutableDependencyManagerParams.builder()
                        .appName(appName)
                        .loggerName(logger == null ? null : logger.getName())
                        .threadPool(threadPool)
                        .systemReporter(systemReporter)
                        .throttleDependencyChecks(throttleDependencyChecks)
                        .build());
    }

    /**
     * @deprecated Use {@link
     *     #AbstractDependencyManager(com.indeed.status.core.AbstractDependencyManagerParams)}
     */
    @Deprecated
    public AbstractDependencyManager(
            @Nullable final String appName,
            @Nullable final org.apache.log4j.Logger logger,
            @Nonnull final ThreadPoolExecutor threadPool,
            @Nonnull final DependencyChecker checker) {
        this(
                ImmutableDependencyManagerParams.builder()
                        .appName(appName)
                        .loggerName(logger == null ? null : logger.getName())
                        .threadPool(threadPool)
                        .checker(checker)
                        .build());
    }

    public AbstractDependencyManager(final AbstractDependencyManagerParams params) {
        this.appName = Strings.isNullOrEmpty(params.appName()) ? null : params.appName();
        this.log =
                null == params.loggerName()
                        ? LoggerFactory.getLogger(getClass())
                        : LoggerFactory.getLogger(params.loggerName());

        this.executor =
                Executors.newSingleThreadScheduledExecutor(
                        new ThreadFactoryBuilder()
                                .setNameFormat(
                                        "dependency-management-"
                                                + MANAGEMENT_THREAD_POOL_COUNT.getAndIncrement()
                                                + "-thread-%d")
                                .setDaemon(true)
                                .setUncaughtExceptionHandler(
                                        (t, e) ->
                                                log.error(
                                                        "Uncaught throwable in thread "
                                                                + t.getName()
                                                                + "/"
                                                                + t.getId(),
                                                        e))
                                .build());

        this.threadPool = params.threadPool();

        this.checker = params.checker();

        VarExporter.forNamespace(getClass().getSimpleName()).includeInGlobal().export(this, "");
    }

    @Nullable
    public String getAppName() {
        return appName;
    }

    @Nonnull
    protected WallClock getWallClock() {
        return this.checker.getWallClock();
    }

    static ThreadPoolExecutor newDefaultThreadPool() {
        final ThreadPoolExecutor result =
                new ThreadPoolExecutor(
                        // Bound the pool. Most foreground dependency managers should be called only
                        // very rarely, so
                        //  keep a minimal core pool around and only grow it on demand.
                        1,
                        16,
                        // Threads will be kept alive in an idle state for a minute or two. After
                        // that, they may be
                        //  garbage-collected, so that we're keeping a larger thread pool only
                        // during weird periods of
                        //  congestion. (Note: the background manager will typically keep all
                        // threads pretty active, since it's
                        //  repeatedly launching new pingers. The live manager will spin them up and
                        // down based on traffic to
                        //  the rather uncommonly used /healthcheck/live uri).
                        30,
                        TimeUnit.SECONDS,
                        // Use a blocking queue just to keep track of checks when the world is going
                        // wrong. This is mostly useful
                        //  when we're adding a bunch of checks at the same time, such as during a
                        // live healthcheck. Might as well
                        //  keep this pretty small, because any nontrivial wait to execute is going
                        // to blow up a timeout anyway.
                        new SynchronousQueue<>(),
                        // Name your threads.
                        new ThreadFactoryBuilder()
                                .setNameFormat(
                                        "dependency-default-"
                                                + DEFAULT_THREAD_POOL_COUNT.getAndIncrement()
                                                + "-checker-%d")
                                .setDaemon(true)
                                .setUncaughtExceptionHandler(
                                        (t, e) ->
                                                LoggerFactory.getLogger(
                                                                AbstractDependencyManager.class)
                                                        .error(
                                                                "Uncaught throwable in thread "
                                                                        + t.getName()
                                                                        + "/"
                                                                        + t.getId(),
                                                                e))
                                .build(),
                        // Explicitly restating the default policy here, because healthchecks should
                        // Just Not Work if there
                        //  are insufficient resources to support them. Given the smallish queue
                        // above, this means that
                        //  we're going to end up throwing exceptions if we get too blocked up
                        // somehow.
                        new AbortPolicy());

        result.prestartAllCoreThreads();

        return result;
    }

    @SuppressWarnings("UnusedDeclaration")
    public Collection<String> getDependencyIds() {
        return Collections.unmodifiableCollection(dependencies.keySet());
    }

    @Nonnull
    public CheckResultSet evaluate() {
        return evaluate(getDependencies());
    }

    @Nullable
    public CheckResult evaluate(@Nonnull final String id) {
        final Dependency dependency =
                checkNotNull(dependencies.get(id), "Missing dependency '%s'", id);
        return evaluate(Collections.singleton(dependency)).get(id);
    }

    @Nonnull
    private CheckResultSet evaluate(Collection<Dependency> dependencies) {
        final CheckResultSet result = checker.evaluate(dependencies);

        result.setAppName(appName);

        return result;
    }

    /**
     * Launches a background pinger over the given dependency. The periodicity of the check is
     * controlled by the dependency manager object.
     *
     * @param dependency
     */
    public void launchPinger(final Dependency dependency) {
        final DependencyPinger pinger = newPingerFor(dependency);

        // Add a listener so that objects that want to listen for updates to ANY dependency
        // can do so. Note that this is done ONLY for background-pinger type dependency
        // checks, because it makes less sense to monitor checks that are evaluated
        // unpredictably.
        pinger.addListener(updateHandler);

        // Note: we can assume the id is unique and the dependency is not duplicated because of the
        // check
        // in AbstractDependencyManager#addDependency
        dependencyPingers.computeIfAbsent(
                dependency.getId(),
                dependencyId ->
                        executor.scheduleWithFixedDelay(
                                pinger, 0, pinger.getPingPeriod(), TimeUnit.MILLISECONDS));

        addDependency(pinger);
    }

    protected DependencyPinger newPingerFor(final Dependency dependency) {
        final DependencyPinger pinger;
        final long dependencyPingPeriod = dependency.getPingPeriod();
        if (dependencyPingPeriod <= 0
                || dependencyPingPeriod == AbstractDependency.DEFAULT_PING_PERIOD) {
            log.info("Creating pinger with ping period " + pingPeriod);
            pinger =
                    new DependencyPinger(
                            ImmutableDependencyPingerParams.builder()
                                    .dependency(dependency)
                                    .pingPeriod(pingPeriod)
                                    .checker(checker)
                                    .build());

        } else {
            log.info("Creating pinger with ping period " + dependency.getPingPeriod());
            pinger =
                    new DependencyPinger(
                            ImmutableDependencyPingerParams.builder()
                                    .dependency(dependency)
                                    .checker(checker)
                                    .build());
        }
        return pinger;
    }

    public Dependency getDependency(final String id) {
        return dependencies.get(id);
    }

    public void addDependency(final Dependency dependency) {
        final Dependency dependencyToAdd;

        if (checker.getThrottle() && !(dependency instanceof DependencyPinger)) {
            dependencyToAdd = new ThrottledDependencyWrapper(dependency);
        } else {
            dependencyToAdd = dependency;
        }

        final Dependency existing =
                dependencies.putIfAbsent(dependencyToAdd.getId(), dependencyToAdd);

        Preconditions.checkState(
                null == existing,
                "Can't have two dependencies with the same ID [%s]. Check your setup.",
                dependencyToAdd.getId());

        // Direct this through the update-handler so that we don't inadvertently alert ourselves
        // that we added a dependency
        updateHandler.onAdded(dependencyToAdd);
    }

    public Dependency removeDependency(final String id) {
        final ScheduledFuture<?> pinger = dependencyPingers.remove(id);
        // Cancel all future pings for this dependency, interrupting any current pings
        if (pinger != null) {
            pinger.cancel(true);
        }

        final Dependency removedDependency = dependencies.remove(id);

        if (removedDependency != null) {
            updateHandler.onRemoved(removedDependency);
        }

        return removedDependency;
    }

    public Collection<Dependency> getDependencies() {
        return Collections.unmodifiableCollection(dependencies.values());
    }

    @Override
    public void onChanged(
            @Nonnull final Dependency source,
            @Nullable final CheckResult original,
            @Nonnull final CheckResult updated) {
        updateHandler.onChanged(source, original, updated);
    }

    @Override
    public void onAdded(@Nonnull final Dependency dependency) {
        updateHandler.onAdded(dependency);
    }

    @Override
    public void onChecked(@Nonnull final Dependency source, @Nonnull final CheckResult result) {
        updateHandler.onChecked(source, result);
    }

    @Override
    public void onRemoved(@Nonnull final Dependency dependency) {
        updateHandler.onRemoved(dependency);
    }

    @Override
    public void clear() {
        updateHandler.clear();
    }

    @Override
    public void addListener(final StatusUpdateListener listener) {
        updateHandler.addListener(listener);
    }

    @Override
    public Iterator<StatusUpdateListener> listeners() {
        return updateHandler.listeners();
    }

    public void setPingPeriod(final long pingPeriod) {
        this.pingPeriod = pingPeriod;
    }

    /*@Override todo(cameron)*/
    @PreDestroy
    public void shutdown() {
        this.checker.shutdown();
        this.executor.shutdownNow();
    }

    @Export(name = "active-threads")
    public int getActiveDependencyThreads() {
        return threadPool.getActiveCount();
    }

    @Export(name = "core-pool-size")
    public int getCorePoolSize() {
        return threadPool.getCorePoolSize();
    }

    @Export(name = "queue-size")
    public int getQueueSize() {
        final BlockingQueue<Runnable> queue = threadPool.getQueue();
        return null == queue ? 0 : queue.size();
    }
}
