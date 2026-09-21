package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.exception.EvermailException;
import com.juanpablo.evermail.model.*;

public interface InboxSession extends AutoCloseable {
    RemoteInboxPage fetchLatest(int size, Deadline deadline) throws EvermailException;
    RemoteInboxPage fetchBefore(InboxCursor cursor, int size, Deadline deadline) throws EvermailException;
    RemoteMailContent fetchContent(RemoteMailId id, Deadline deadline) throws EvermailException;
    @Override
    void close();
}
