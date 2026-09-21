package com.juanpablo.evermail.service;

import com.juanpablo.evermail.exception.*;
import com.juanpablo.evermail.model.ComposeRequest;
import com.juanpablo.evermail.util.EmailValidator;

public class ComposeService {
    public ComposeRequest validate(ComposeRequest request) throws ValidationException {
        if (request == null || request.getAccountId() == null || request.getSubmissionId() == null) {
            throw new ValidationException(ErrorCode.INVALID_RECIPIENT, "Missing submission identity");
        }
        String subject = request.getSubject() == null ? "" : request.getSubject();
        if (subject.contains("\r") || subject.contains("\n")) {
            throw new ValidationException(ErrorCode.INVALID_RECIPIENT, "Subject cannot contain line breaks");
        }
        return new ComposeRequest(request.getSubmissionId(), request.getAccountId(), subject,
                request.getPlainText() == null ? "" : request.getPlainText(),
                EmailValidator.validateRecipients(request.getRecipients()));
    }
}
