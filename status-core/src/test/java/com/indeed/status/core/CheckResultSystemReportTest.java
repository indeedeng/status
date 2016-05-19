package com.indeed.status.core;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.indeed.util.core.time.StoppedClock;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter.serializeAllExcept;
import static org.junit.Assert.assertEquals;

/**
 *
 */
public class CheckResultSystemReportTest {
    private final StoppedClock wallClock = new StoppedClock();
    private final SystemReporter systemReporter = new SystemReporter(wallClock);

    @Test
    public void testSimpleReport() throws IOException {
        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("US/Chicago"));
        calendar.set(2016, 01, 01);
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
        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT-6"));
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

    @Nonnull
    private String getTestResourcePath() {
        return getClass().getName().replace('.', '/');
    }

    @JsonFilter("master-filter")
    class PropertyFilterMixIn {}
}