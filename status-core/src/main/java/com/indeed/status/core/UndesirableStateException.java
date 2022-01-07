package com.indeed.status.core;

/**
 * Just like an {@link IllegalStateException} but less scary in the logs.
 *
 * @author matts
 */
@SuppressWarnings({"UnusedDeclaration"}) // Public exception; used in other codebases
public class UndesirableStateException extends RuntimeException {
    public UndesirableStateException() {}

    public UndesirableStateException(Throwable cause) {
        super(cause);
    }

    public UndesirableStateException(String message) {
        super(message);
    }

    public UndesirableStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
