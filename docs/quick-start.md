---
layout: default
title: Quick Start
permalink: /docs/quick-start/
---

## Get Status

### Pulling from maven repository

Add these dependencies to your pom.xml:

```xml
<dependencies>
    <dependency>
        <groupId>com.indeed</groupId>
        <artifactId>status-core</artifactId>
        <version>1.0.4</version>
    </dependency>
    <dependency>
        <groupId>com.indeed</groupId>
        <artifactId>status-web</artifactId>
        <version>1.0.4</version>
    </dependency>
</dependencies>
```

### Building from source (using maven)

Use git to clone https://github.com/indeedeng/status, and run `mvn install` to build.

### Using status in code

Create a new dependency manager for your app 

{% gist a86c6f7964/0e32de29eae24e81083c %}

Create a servlet that will return your dependency manager 

{% gist a86c6f7964/947bcef1ab7619517fbf %}

Create a simple dependency 

{% gist a86c6f7964/3e0c1f23fa611409cd5d %}

Using your favorite way to serve a servlet (such as Jetty), add the dependency to the manager and serve the servlet. 

{% gist a86c6f7964/ef76ab599a5352aaa692 %}

