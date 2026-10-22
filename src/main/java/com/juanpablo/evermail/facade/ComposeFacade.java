package com.juanpablo.evermail.facade;

import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.config.AppConstants;
import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.service.MailSendService;
import javafx.concurrent.Task;
import java.util.UUID;

/** Creates backend tasks without starting them or changing any screen. */
public class ComposeFacade {
    private final MailSendService sender;

    public ComposeFacade(MailSendService sender) {
        this.sender = sender;
    }

    public Task<SendResult> sendTask(ComposeRequest request) {
        return new Task<>() {
            @Override
            protected SendResult call() throws Exception {
                Deadline deadline = Deadline.after(AppConstants.SEND_BUDGET);
                deadline.check();
                return sender.send(request, deadline);
            }
        };
    }
}
