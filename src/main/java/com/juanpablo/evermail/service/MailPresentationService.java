package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.util.HtmlMail;

public final class MailPresentationService {
    public MailPresentation prepare(MailContent content, Deadline deadline) throws EvermailException {
        deadline.check();
        if (content.getHtml() == null) return new MailPresentation(content, null);
        try {
            var safe = HtmlMail.sanitize(content.getHtml(), cid -> null, deadline);
            deadline.check();
            return new MailPresentation(content, HtmlMail.document(safe.html()));
        } catch (EvermailException e) { throw e; }
        catch (Exception e) {
            throw new MailFetchException(ErrorCode.IMAP_FETCH_FAILED, "Cannot prepare message for display", e);
        }
    }
}
