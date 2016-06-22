package com.indeed.status.core;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.indeed.util.core.time.StoppedClock;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter.serializeAllExcept;
import static org.junit.Assert.assertEquals;

/**
 *
 */
public class CheckResultSystemReportTest {
    
    private static TimeZone originalTimeZone;
    private static Calendar originalCalendar;

    @BeforeClass
    public static void pinTimeZone() {
        originalTimeZone = TimeZone.getDefault();
        final TimeZone pinnedTimeZone = TimeZone.getTimeZone("GMT-6");
        final Calendar pinnedCalendar = Calendar.getInstance(pinnedTimeZone);
        TimeZone.setDefault(pinnedTimeZone);
        originalCalendar = CheckResult.DATE_FORMAT.get().getCalendar();
        CheckResult.DATE_FORMAT.get().setCalendar(pinnedCalendar);
    }

    @AfterClass
    public static void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
        CheckResult.DATE_FORMAT.get().setCalendar(originalCalendar);
    }

    @Test
    public void testSimpleReport() throws IOException {
        final StoppedClock wallClock = new StoppedClock();
        final SystemReporter systemReporter = new SystemReporter(wallClock);
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(0);
        calendar.set(2016, Calendar.JANUARY, 1);
        final Date sampleDate = calendar.getTime();
        wallClock.set(sampleDate.getTime());

        final DependencyChecker checker = DependencyChecker.newBuilder()
                .setExecutorService(Executors.newSingleThreadExecutor())
                .setSystemReporter(systemReporter)
                .build();

        final SimplePingableDependency dependency = SimplePingableDependency.newBuilder()
                .setId("id")
                .setDescription("description")
                .setPingMethod(new PingMethod() {
                    @Override
                    public void ping() throws Exception {
                        // create a duration
                        wallClock.plus(10, TimeUnit.MILLISECONDS);
                    }
                })
                .setWallClock(wallClock)
                .build();


        final CheckResultSet resultSet = checker.evaluate(ImmutableList.of(dependency));

        final CheckResultSystemReport report = resultSet.summarizeBySystemReporter(false);

        final String json = new ObjectMapper()
                .addMixIn(Object.class, PropertyFilterMixIn.class)
                .writer()
                .with(new SimpleFilterProvider()
                        .addFilter("master-filter", serializeAllExcept("hostname")))
                .withDefaultPrettyPrinter()
                .writeValueAsString(report);


        final String expectedJson = Resources.toString(
                Resources.getResource(getTestResourcePath() + "/testSimpleReport.json"),
                Charsets.UTF_8);
        assertEquals(
                "Failed to generate the expected json report",
                expectedJson, json);
    }

    @Test
    public void testDetailedReport() throws IOException {
        final StoppedClock wallClock = new StoppedClock();
        final SystemReporter systemReporter = new SystemReporter(wallClock);
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(0);
        calendar.set(2016, Calendar.JANUARY, 1);
        final Date sampleDate = calendar.getTime();
        wallClock.set(sampleDate.getTime());

        final DependencyChecker checker = DependencyChecker.newBuilder()
                .setExecutorService(Executors.newSingleThreadExecutor())
                .setSystemReporter(systemReporter)
                .build();

        final SimplePingableDependency dependency = SimplePingableDependency.newBuilder()
                .setId("id")
                .setDescription("description")
                .setPingMethod(new PingMethod() {
                    @Override
                    public void ping() throws Exception {
                        // create a duration
                        wallClock.plus(10, TimeUnit.MILLISECONDS);
                    }
                })
                .setWallClock(wallClock)
                .build();

        final CheckResultSet resultSet = checker.evaluate(ImmutableList.of(dependency));
        assertEquals(
                sampleDate.getTime(), resultSet.getStartTimeMillis());

        final CheckResult checkResult = resultSet.get("id");
        assert checkResult != null;

        assertEquals(
                "Failed to pass date through to dependency",
                sampleDate.getTime(), checkResult.getTimestamp());

        final CheckResultSystemReport report = resultSet.summarizeBySystemReporter(true);

        final String json = new ObjectMapper()
                .addMixIn(Object.class, PropertyFilterMixIn.class)
                .writer()
                .with(new SimpleFilterProvider()
                        .addFilter("master-filter", serializeAllExcept("hostname")))
                .withDefaultPrettyPrinter()
                .writeValueAsString(report);

        final String expectedJson = Resources.toString(
                Resources.getResource(getTestResourcePath() + "/testDetailedReport.json"),
                Charsets.UTF_8);
        assertEquals(
                "Failed to generate the expected json report",
                expectedJson, json);
    }

    @Test
    public void testDetailedReportBackground() throws IOException, InterruptedException {
        final StoppedClock wallClock = new StoppedClock();
        final SystemReporter systemReporter = new SystemReporter(wallClock);
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(0);
        calendar.set(2016, Calendar.JANUARY, 1);
        final Date sampleDate = calendar.getTime();
        wallClock.set(sampleDate.getTime());

        final SimpleDependencyManager manager = new SimpleDependencyManager(
                "app",
                null,
                systemReporter);

        final CountDownLatch latch = new CountDownLatch(1);
        final SimplePingableDependency dependency = SimplePingableDependency.newBuilder()
                .setId("id")
                .setDescription("description")
                .setPingMethod(new PingMethod() {
                    @Override
                    public void ping() throws Exception {
                        // Simulate taking 10 ms to complete.
                        wallClock.plus(10, TimeUnit.MILLISECONDS);
                        latch.countDown();
                    }
                })
                .setWallClock(wallClock)
                .build();

        manager.launchPinger(dependency);
        latch.await(10, TimeUnit.SECONDS);

        final CheckResultSet resultSet = manager.evaluate();

        assertEquals(
                "Expected the timestamp of the result set to match the time of the wall clock at the moment the " +
                        "result set was created, i.e. after the background ping has occurred.",
                sampleDate.getTime() + 10, resultSet.getStartTimeMillis());

        final CheckResult checkResult = resultSet.get("id");
        assert checkResult != null;

        assertEquals(
                "Expected the 'last executed' timestamp of the individual dependency to record the time " +
                        "the evaluation of dependency was initiated.",
                sampleDate.getTime(), checkResult.getTimestamp());

        final CheckResultSystemReport report = resultSet.summarizeBySystemReporter(true);

        final String json = new ObjectMapper()
                .addMixIn(Object.class, PropertyFilterMixIn.class)
                .writer()
                .with(new SimpleFilterProvider()
                        .addFilter("master-filter", serializeAllExcept("hostname")))
                .withDefaultPrettyPrinter()
                .writeValueAsString(report);

        final String expectedJson = Resources.toString(
                Resources.getResource(getTestResourcePath() + "/testDetailedReportBackground.json"),
                Charsets.UTF_8);
        assertEquals(
                "Failed to generate the expected json report",
                expectedJson, json);
    }


    @Nonnull
    private String getTestResourcePath() {
        return getClass().getName().replace('.', '/');
    }

    @JsonFilter("master-filter")
    class PropertyFilterMixIn {}

    private static class SimpleDependencyManager extends AbstractDependencyManager {
        public SimpleDependencyManager(
                @Nullable final String appName,
                @Nullable final Logger logger,
                @Nonnull final SystemReporter systemReporter
        ) {
            super(appName, logger, systemReporter);
        }
    }
}