package com.juanpablo.evermail.exception;

import lombok.Getter;

/**
 * Base class for all checked exceptions in Evermail.
 * Represents failures originating outside the application's control
 * (network, database, OAuth, mail protocols, attachments), which the
 * application must explicitly anticipate and handle.
 */
@Getter
public abstract class EvermailException extends Exception {

    private final ErrorCode errorCode;

    protected EvermailException(ErrorCode errorCode) {
        super();
        this.errorCode = errorCode;
    }

    protected EvermailException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected EvermailException(ErrorCode errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }

    protected EvermailException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}