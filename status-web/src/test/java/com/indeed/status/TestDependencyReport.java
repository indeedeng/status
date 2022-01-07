package com.indeed.status;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.indeed.status.core.CheckResultSet;
import com.indeed.status.core.CheckStatus;
import com.indeed.status.core.Dependency;
import com.indeed.status.core.PingableDependency;
import com.indeed.status.core.Urgency.BuiltIns;
import com.indeed.status.web.AbstractResponseWriter;
import com.indeed.teststatus.ServletTestSuite;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** */
public class TestDependencyReport extends ServletTestSuite {
    @Test
    public void testBasicOperation() throws IOException {
        final String url = "private/json";
        final String jsonResponse = get(url);

        log.debug("Received response '" + jsonResponse + "'.");

        assertMatches(url, jsonResponse, patternFor("id", "sample"));

        final String publicFacingURL = "public/json";
        final String publicResponse = get(publicFacingURL);
        log.debug("Received response '" + publicResponse + "'.");

        assertNotMatches(publicFacingURL, publicResponse, patternFor("id", "sample"));
    }

    /// Ensure that the public and private facing report handlers are reporting status
    /// accurately.
    @Test
    public void testErrorLevel() throws Exception {
        final AtomicBoolean weakAvailable = new AtomicBoolean(true);
        final Dependency weakDependency =
                new PingableDependency("weak", "", BuiltIns.WEAK) {
                    public void ping() throws Exception {
                        if (!weakAvailable.get()) {
                            throw new Exception();
                        }
                    }

                    @Override
                    public String getDocumentationUrl() {
                        return null;
                    }
                };

        final AtomicBoolean strongAvailable = new AtomicBoolean(true);
        final Dependency strongDependency =
                new PingableDependency("strong", "", BuiltIns.STRONG) {
                    public void ping() throws Exception {
                        if (!strongAvailable.get()) {
                            throw new Exception();
                        }
                    }

                    @Override
                    public String getDocumentationUrl() {
                        return null;
                    }
                };

        final AtomicBoolean requiredAvailable = new AtomicBoolean(true);
        final Dependency requiredDependency =
                new PingableDependency("required", "", BuiltIns.REQUIRED) {
                    public void ping() throws Exception {
                        if (!requiredAvailable.get()) {
                            throw new Exception();
                        }
                    }

                    @Override
                    public String getDocumentationUrl() {
                        return null;
                    }
                };

        final DependencyManager manager = new DependencyManager();
        manager.addDependency(weakDependency);
        manager.addDependency(strongDependency);
        manager.addDependency(requiredDependency);

        final CheckResultSet sanityCheckResults = manager.evaluate();
        Assert.assertEquals(CheckStatus.OK, sanityCheckResults.getSystemStatus());

        weakAvailable.set(false);
        final CheckResultSet weakFailureResults = manager.evaluate();
        Assert.assertEquals(
                CheckStatus.OUTAGE, manager.evaluate(weakDependency.getId()).getStatus());
        // Should NOT be able to downgrade a system status beyond minor.
        Assert.assertEquals(CheckStatus.MINOR, weakFailureResults.getSystemStatus());
        Assert.assertEquals(
                "Expected the default public response to a weak dependency to be 'ok'. Netscalers, leave us alone.",
                HttpServletResponse.SC_OK,
                (long)
                        AbstractResponseWriter.FN_PUBLIC_RESPONSE.apply(
                                weakFailureResults.getSystemStatus()));
        weakAvailable.set(true);

        strongAvailable.set(false);
        final CheckResultSet strongFailureResults = manager.evaluate();
        Assert.assertEquals(
                CheckStatus.OUTAGE, manager.evaluate(strongDependency.getId()).getStatus());
        // Should NOT be able to downgrade a system status beyond minor.
        Assert.assertEquals(CheckStatus.MAJOR, strongFailureResults.getSystemStatus());
        Assert.assertEquals(
                "Expected the default public response to a strong dependency to be 'ok'. Netscalers, leave us alone.",
                HttpServletResponse.SC_OK,
                (long)
                        AbstractResponseWriter.FN_PUBLIC_RESPONSE.apply(
                                weakFailureResults.getSystemStatus()));
    }

    @Test
    public void testJsonInvariants() throws Exception {
        final Dependency alwaysDies =
                new PingableDependency("alwaysDies", "", BuiltIns.REQUIRED) {
                    public void ping() throws Exception {
                        // throw a new checked exception caused by an unchecked exceptions
                        throw new IOException(new NullPointerException());
                    }

                    @Override
                    public String getDocumentationUrl() {
                        return null;
                    }
                };
        final DependencyManager manager = new DependencyManager();
        manager.addDependency(alwaysDies);

        final CheckResultSet results = manager.evaluate();
        final StringWriter out = new StringWriter();
        new JsonFactory(new ObjectMapper())
                .createJsonGenerator(out)
                .setPrettyPrinter(new DefaultPrettyPrinter())
                .writeObject(results.summarize(true));
        final String json = out.toString();

        final List<String> expectedJsonFingerprints =
                ImmutableList.of(
                        "\"status\" : \"OUTAGE\"",
                        "\"exception\" : \"IOException\"",
                        "\"exception\" : \"NullPointerException\"");
        for (final String fingerprint : expectedJsonFingerprints) {
            Assert.assertTrue(
                    "Expected to find fingerprint '" + fingerprint + "' in json: " + json,
                    json.contains(fingerprint));
        }
    }

    /// Returns a matcher that will find a JSON name/value pair on a single line.
    protected Pattern patternFor(final String name, final String value) {
        return Pattern.compile("\"" + name + "\"\\s*:\\s*\"" + value + "\"");
    }
}
