# Status

Status is a project to help report the current state of external systems that an application depends on, as well as 
the current health of any internal aspects of the application.

## Dependency Management

Constructing dependencies for various services is easy.
Simply extend the `PingableDependency` and implement the ping method.
If the ping method throws an exception, then the status of the dependency will be failing.
The amount it affects the service is dependent upon the severity specified in the constructor.
REQUIRED dependencies will cause the application to report an OUTAGE.
STRONG will report a MAJOR status, WEAK will report a MINOR, and NONE will not change the state.

```java
import com.indeed.status.core.PingableDependency
import com.indeed.status.core.Urgency;
 
public class SimpleDependency extends PingableDependency {
    private SimpleDependency() {
        super("simple", "my description", DEFAULT_TIMEOUT, DEFAULT_PING_PERIOD, Urgency.REQUIRED);
    }
 
    @Override
    public void ping() throws Exception {
        // code to check dependency (return void for GOOD! and throw and exception for BAD)
    }
}
```

### Servlet Reporting

By default, the status-web package provides an `AbstractDaemonCheckReportServlet` that can be extended to pass the dependency manager appropriately.

```java
import com.indeed.status.web.AbstractDaemonCheckReportServlet;
 
public class StatusServlet extends AbstractDaemonCheckReportServlet {
    private final AbstractDependencyManager manager;
 
    public StatusServlet(AbstractDependencyManager manager) {
        this.manager = manager;
    }
 
    @Override
    protected AbstractDependencyManager newManager(ServletConfig config) {
        return manager;
    }
}
```

Once extended, the servlet can be mounted on any path and accessed in the browser.

```java
final ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);        
handler.setContextPath("/");

final DependencyManager manager = new DependencyManager();
manager.addDependency(new SimpleDependency());

final StatusServlet servlet = new StatusServlet(manager);
handler.addServlet(new ServletHolder(new StatusServlet()), "/private/status");

final Server server = new Server(port);
server.setHandler(handler);
server.start();
```

### Evaluating Status

The servlet approach is great for developers working with web applications, however, some tools do not provide a web interface.
For these tools, you can interact with the dependency manager directly to evaluate the status of all dependencies.

```java
final CheckResultSet status = dependencyManager.evaluate();
```

By calling the getSystemStatus method, you can get the overall status of the system.
This is useful for bottle-necking services that may be in a broken or degraded state.

```java
status.getSystemStatus();
```

Another option available to developers is the ability to check the status of a single dependency.
This will allow applications to gracefully degrade by circumventing problematic code paths.

```java
status.get("simple").getStatus();
```

## Healthchecks

Healthchecks are pages within applications that take all the dependencies and dump their statuses in a JSON format
They can help shed light into services that are currently unavailable or unhealthy.
This feature is only available through the status-web package.

### Failing Healthchecks

Failing healthchecks make it easy to determine good starting points when it comes to debugging problems in development, staging, and production environments.
When a healthcheck is failing, it can fail for a variety of reasons with a different status (see the section on Dependency Management).
Below is a sample of what a failing healthcheck might look like through the reporting system.

```json
{
    "hostname": "pitz.local",
    "duration": 19,
    "condition": "OUTAGE",
    "dcStatus": "FAILOVER",
    "appname": "crm.api",
    "catalinaBase": "/var/folders/7t/vsd_gsrn6y99fpl2ydmlszyw0000gn/T/tomcat.5084685826050345209.8000",
    "leastRecentlyExecutedDate": "2015-02-24T22:48:37.782-0600",
    "leastRecentlyExecutedTimestamp": 1424839717782,
    "results": {
        "OUTAGE": [{
            "status": "OUTAGE",
            "description": "mysql",
            "errorMessage": "Exception thrown during ping",
            "timestamp": 1424839717782,
            "duration": 18,
            "lastKnownGoodTimestamp": 0,
            "period": 0,
            "id": "mysql",
            "urgency": "Required: Failure of this dependency would result in complete system outage",
            "documentationUrl": "http://www.mysql.com/",
            "type" : "mysql",
            "servicePool" : "dbpool.example.com:3306/mysqldb1",
            "thrown": {
                "exception": "RuntimeException",
                "message": "Failed to communicate with the following tables: user_authorities, oauth_code, oauth_approvals, oauth_client_token, oauth_refresh_token, oauth_client_details, oauth_access_token",
                "stack": [
                    "io.github.jpitz.example.MySQLDependency.ping(MySQLDependency.java:68)",
                    "com.indeed.status.core.PingableDependency.call(PingableDependency.java:59)",
                    "com.indeed.status.core.PingableDependency.call(PingableDependency.java:15)",
                    "java.util.concurrent.FutureTask.run(FutureTask.java:262)",
                    "java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)",
                    "java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:615)",
                    "java.lang.Thread.run(Thread.java:745)"
                ]
            },
            "date": "2015-02-24T22:48:37.782-0600"
        }],
        "OK": [{
            "status": "OK",
            "description": "mongo",
            "errorMessage": "ok",
            "timestamp": 1424839717782,
            "duration": 0,
            "lastKnownGoodTimestamp": 0,
            "period": 0,
            "id": "mongo",
            "urgency": "Required: Failure of this dependency would result in complete system outage",
            "documentationUrl": "http://www.mongodb.org/",
            "type" : "mongo",
            "servicePool" : "dbpool.example.com:27017/mongodb1",
            "date": "2015-02-24T22:48:37.782-0600"
        }]
    }
}
```

### Passing Healthchecks

When all the dependencies are passing, they will report a healthy status of OK.
The JSON dump below is a sample of what a passing healthcheck looks like.

```json
{
    "hostname": "pitz.local",
    "duration": 186,
    "condition": "OK",
    "dcStatus": "OK",
    "appname": "crm.api",
    "catalinaBase": "/var/folders/7t/vsd_gsrn6y99fpl2ydmlszyw0000gn/T/tomcat.5084685826050345209.8000",
    "leastRecentlyExecutedDate": "2015-02-24T22:56:16.568-0600",
    "leastRecentlyExecutedTimestamp": 1424840176568,
    "results": {
        "OK": [{
            "status": "OK",
            "description": "mongo",
            "errorMessage": "ok",
            "timestamp": 1424840176568,
            "duration": 1,
            "lastKnownGoodTimestamp": 0,
            "period": 0,
            "id": "mongo",
            "urgency": "Required: Failure of this dependency would result in complete system outage",
            "documentationUrl": "http://www.mongodb.org/",
            "type" : "mongo",
            "servicePool" : "dbpool.example.com:27017/mongodb1",
            "date": "2015-02-24T22:56:16.568-0600"
        }, {
            "status": "OK",
            "description": "mysql",
            "errorMessage": "ok",
            "timestamp": 1424840176569,
            "duration": 185,
            "lastKnownGoodTimestamp": 0,
            "period": 0,
            "id": "mysql",
            "urgency": "Required: Failure of this dependency would result in complete system outage",
            "documentationUrl": "http://www.mysql.com/",
            "type" : "mysql",
            "servicePool" : "dbpool.example.com:3306/mysqldb1",
            "date": "2015-02-24T22:56:16.569-0600"
        }]
    }
}
```

## Components

### [status-core](https://github.com/indeedeng/status/tree/master/status-core)

Provides the core components for reporting the status of various application dependencies.
Can be used without the status-web package and does not requires a web server.

### [status-web](https://github.com/indeedeng/status/tree/master/status-web)

Provides components that can be used with web applications to provide a clean JSON dump of the current application status.
Requires the status-core package and does require a web server that supports servlets.

## License

[Apache License Version 2.0](https://github.com/indeedeng/status/blob/master/LICENSE)
