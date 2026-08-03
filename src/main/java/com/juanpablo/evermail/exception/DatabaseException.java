package com.juanpablo.evermail.exception;

public class DatabaseException extends EvermailException {

    public DatabaseException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DatabaseException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DatabaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public DatabaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}