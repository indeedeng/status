package com.indeed.status.core;

/**
 * An interface for the enum class that describes which type the {@link Dependency} is.
 *
 * This interface is mainly for further clarification of dependency. When something came up,
 * and the description of system dependency was poor, we had to dig log files and spend time
 * for investigation. {@link DependencyType} provides a standard way to describe the type of
 * the dependency.
 *
 * By implementing this interface, the categorization and terminology of the dependencies is
 * intended to be shared within the system. As a result, the documentation of the dependencies
 * will exist in the health check code.
 *
 * An example implementation is {@link StandardDependencyTypes}. If your project needs its own
 * implementation of {@link DependencyType}, please create another.
 */
public interface DependencyType {
    String toString();

    /**
     * An implementation of the interface {@link DependencyType} for standard systems.
     *
     * In this class, dependency types are categorized as resource entities
     */
    enum StandardDependencyTypes implements DependencyType {
        // connection to database servers
        MYSQL("mysql"),
        MONGO("mongo"),
        CASSANDRA("cassandra"),
        OTHER_DATABASE("other database"),

        // availability of system resources
        MEMORY("memory"),
        DISK("disk"),

        // connection to services by its protocol/interface
        HTTP_SERVICE("http service"),
        OTHER_SERVICE("other service"),

        // connection to external 3rd party services
        THIRD_PARTY("3rd party"),

        // none of above
        OTHER("other");

        private final String name;

        StandardDependencyTypes(final String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }
    }
}
