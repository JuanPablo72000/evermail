package com.juanpablo.evermail.application;

import java.util.concurrent.CompletionStage;

public interface OperationHandle<T> {
    CompletionStage<OperationResult<T>> result();
    /** Check again when dispatching a result to a UI thread: the session may have changed in between. */
    long sessionVersion();
    /** Requests cancellation; completion follows when the worker stops using the operation's resources. */
    boolean cancel();
}
