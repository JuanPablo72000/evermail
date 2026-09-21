package com.juanpablo.evermail.facade;

import com.juanpablo.evermail.model.*;
import com.juanpablo.evermail.config.AppConstants;
import com.juanpablo.evermail.config.Deadline;
import com.juanpablo.evermail.service.StartupService;
import javafx.concurrent.Task;
import java.util.UUID;

/** Creates backend tasks without starting them or changing any screen. */
public class StartupFacade {
    private final StartupService startup;

    public StartupFacade(StartupService startup) {
        this.startup = startup;
    }

    public Task<StartupResult> startTask() {
        Deadline deadline = Deadline.after(AppConstants.STARTUP_BUDGET);
        return new Task<>() {
            @Override
            protected StartupResult call() throws Exception {
                deadline.check();
                return startup.start(deadline);
            }
        };
    }
}
