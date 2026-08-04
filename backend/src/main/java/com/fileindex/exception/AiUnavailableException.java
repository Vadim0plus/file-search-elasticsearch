package com.fileindex.exception;

/** AI tagging was requested but is not configured, or the upstream model call failed. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
