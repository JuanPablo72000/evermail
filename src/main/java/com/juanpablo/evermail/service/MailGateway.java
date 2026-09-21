package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.exception.EvermailException;
import java.util.UUID;

public interface MailGateway {
    InboxSession openInbox(UUID accountId, Deadline deadline) throws EvermailException;
    SmtpSession openSmtp(UUID accountId, Deadline deadline) throws EvermailException;
}
