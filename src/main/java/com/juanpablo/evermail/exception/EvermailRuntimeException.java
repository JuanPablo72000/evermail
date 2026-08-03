package com.juanpablo.evermail.exception;

import lombok.Getter;

/**
 * Base class for all unchecked exceptions in Evermail.
 * Represents internal business-logic/validation failures (e.g. invalid
 * field values), which do not require callers to explicitly catch or
 * declare them.
 */
@Getter
public abstract class EvermailRuntimeException extends RuntimeException {

    private final String fieldName;

    protected EvermailRuntimeException(String fieldName) {
        super();
        this.fieldName = fieldName;
    }

    protected EvermailRuntimeException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    protected EvermailRuntimeException(String fieldName, Throwable cause) {
        super(cause);
        this.fieldName = fieldName;
    }

    protected EvermailRuntimeException(String fieldName, String message, Throwable cause) {
        super(message, cause);
        this.fieldName = fieldName;
    }
}