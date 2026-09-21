package com.juanpablo.evermail.exception;

public class SessionException extends EvermailException {
    public SessionException(ErrorCode code, String message) {
        super(code, message);
    }

    public SessionException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
