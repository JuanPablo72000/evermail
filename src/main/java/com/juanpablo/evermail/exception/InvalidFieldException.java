package com.juanpablo.evermail.exception;

public class InvalidFieldException extends EvermailRuntimeException {

    public InvalidFieldException(String fieldName) {
        super(fieldName);
    }

    public InvalidFieldException(String fieldName, String message) {
        super(fieldName, message);
    }

    public InvalidFieldException(String fieldName, Throwable cause) {
        super(fieldName, cause);
    }

    public InvalidFieldException(String fieldName, String message, Throwable cause) {
        super(fieldName, message, cause);
    }
}