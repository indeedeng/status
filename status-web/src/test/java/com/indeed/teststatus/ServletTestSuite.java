package com.indeed.teststatus;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import junit.framework.TestSuite;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * The {@link ServletTestSuite} is a suite of junit tests touching the health check operations via
 * the HTTP interface. This suite is used to blackbox test the operations of the servlet-specific
 * portions of the healthcheck operations.
 */
public abstract class ServletTestSuite extends TestSuite {

    protected String getURL(final String testcaseJSP) {
        return JettyServer.INSTANCE.getHost() + "/" + testcaseJSP;
    }

    protected String get(final String testcaseJSP) throws IOException {
        final String spec = getURL(testcaseJSP);
        final URL url = new URL(spec);
        final InputStream in = url.openStream();
        return CharStreams.toString(new InputStreamReader(in, Charsets.UTF_8));
    }

    protected void assertNotMatches(
            final String url, final String responseText, final Pattern pattern) {
        if (pattern.matcher(responseText).find()) {
            final String message =
                    "Expected not to match pattern '" + pattern.pattern() + "' at '" + url + "'.";
            log.error(message);

            Assert.fail(message);

        } else {
            if (log.isDebugEnabled()) {
                log.debug("Found no occurrences of '" + pattern.pattern() + "' at '" + url + "'.");
            }
        }
    }

    protected void assertMatches(
            final String url, final String responseText, final Pattern pattern) {
        if (!pattern.matcher(responseText).find()) {
            final String message =
                    "Expected to match pattern '" + pattern.pattern() + "' at '" + url + "'.";
            log.error(message);

            Assert.fail(message);

        } else {
            if (log.isDebugEnabled()) {
                log.debug("Found '" + pattern.pattern() + "' at '" + url + "'.");
            }
        }
    }

    protected void assertNotContains(final String url, final String html, final String redflag) {
        if (html.contains(redflag)) {
            final String message = "Expected not to find '" + redflag + "' at '" + url + "'.";
            log.error(message);

            Assert.fail(message);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Okay: Red flag '" + redflag + "' not found at '" + url + "'.");
            }
        }
    }

    protected void assertContains(final String url, final String html, final String fingerprint) {
        if (!html.contains(fingerprint)) {
            final String message = "Expected to find '" + fingerprint + "' at '" + url + "'.";
            log.error(message);

            Assert.fail(message);

        } else {
            if (log.isDebugEnabled()) {
                log.debug("Found '" + fingerprint + "' at '" + url + "'.");
            }
        }
    }

    @BeforeClass
    public static void startServer() throws Exception {
        JettyServer.INSTANCE.start();
        Assert.assertTrue(JettyServer.INSTANCE.isStarted());
    }

    @AfterClass
    public static void stopServer() throws Exception {
        JettyServer.INSTANCE.stop();
    }

    protected final Logger log = LoggerFactory.getLogger(getClass());
}
