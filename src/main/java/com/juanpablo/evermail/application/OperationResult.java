package com.juanpablo.evermail.application;

/** A successful send still requires inspecting SendResult.state; success is not delivery confirmation. */
public record OperationResult<T>(T value, OperationError error) {
    public boolean succeeded() { return error == null; }
    public static <T> OperationResult<T> success(T value) { return new OperationResult<>(value, null); }
    public static <T> OperationResult<T> failure(OperationError error) { return new OperationResult<>(null, error); }
}
