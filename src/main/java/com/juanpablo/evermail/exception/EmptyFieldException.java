package com.juanpablo.evermail.exception;

public class EmptyFieldException extends InvalidFieldException {

    public EmptyFieldException(String fieldName) {
        super(fieldName);
    }

    public EmptyFieldException(String fieldName, String message) {
        super(fieldName, message);
    }

    public EmptyFieldException(String fieldName, Throwable cause) {
        super(fieldName, cause);
    }

    public EmptyFieldException(String fieldName, String message, Throwable cause) {
        super(fieldName, message, cause);
    }
}