package com.indeed.teststatus;

import com.indeed.status.PermissiveServlet;
import com.indeed.status.StrictServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.File;

/**
 * The <code>JettyServer</code> class represents a singleton that may be used to
 *  spin up a singleton Jetty instance for test purposes.
 */
public class JettyServer {

    /** Start the default instance of the server */
    public void start() throws Exception {
        start(TEST_HOST, TEST_PORT, TEST_ROOT);
    }

    public String getHost() {
        return "http://" + getHostname() + ":" + getPort();
    }

    public static String getHostname() {
        final String customHostname = System.getProperty("unit.test.http.host");

        return null == customHostname ? TEST_HOST : customHostname;
    }

    public int getPort() {
        int result;

        try {
            final String customPortName = System.getProperty("unit.test.http.port");
            result = null == customPortName ? TEST_PORT : Integer.valueOf(customPortName);

        } catch(NumberFormatException e) {
            result = TEST_PORT;
        }

        return result;
    }

    public boolean isStarted() {
        return null != server && server.isStarted();
    }

    public void start ( final String host, final int port, final File root ) throws Exception {
        if (!isStarted()) {
            final ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            handler.setContextPath("/");
            handler.addServlet(new ServletHolder(new StrictServlet()), "/public/json");
            handler.addServlet(new ServletHolder(new PermissiveServlet()), "/private/json");

            // TODO cache servers by hostspec. This will allow multiple instances to be executed e.g. in Hudson.
            server = new Server(port);
            server.setHandler(handler);
            server.start();
        }
    }

    public void stop() throws Exception {
        if ( isStarted()  ) {
            server.stop();
        }

        server = null;
    }

    /**
     * Start the test server with default values to enable debugging.
     */
    public static void main(final String[] args) throws Exception {
        INSTANCE.start();

        for(;;) {
            Thread.sleep(5000);
        }

    }

    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 23058;
    // NOTE: depends on being executed from the root context of the project
    private static final File TEST_ROOT = new File("build/test-war");

    private Server server = null;

    // Singleton instance
    public static final JettyServer INSTANCE = new JettyServer();
}
