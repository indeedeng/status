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

