package com.juanpablo.evermail.exception;

public class ValidationException extends EvermailException {
    public ValidationException(ErrorCode code, String message) {
        super(code, message);
    }

    public ValidationException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
