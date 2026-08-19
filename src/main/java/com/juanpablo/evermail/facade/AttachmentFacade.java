package com.juanpablo.evermail.facade;

import com.juanpablo.evermail.model.Account;
import com.juanpablo.evermail.model.Attachment;
import com.juanpablo.evermail.service.AttachmentService;
import javafx.concurrent.Task;

import java.io.File;

/**
 * Exposure layer for on-demand attachment downloads.
 */
public class AttachmentFacade {

    private final AttachmentService attachmentService;

    public AttachmentFacade(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    public Task<File> downloadAttachmentTask(Attachment attachment, Account account) {
        return new Task<>() {
            @Override
            protected File call() throws Exception {
                return attachmentService.downloadAttachment(attachment, account);
            }
        };
    }
}