package com.juanpablo.evermail.service;

import com.juanpablo.evermail.config.*;
import com.juanpablo.evermail.exception.EvermailException;
import com.juanpablo.evermail.model.*;

public interface OAuthGateway {
    AuthorizationResult authorize(ProviderConfig config, CancellationToken cancel) throws EvermailException;
    OAuthCredentials refresh(ProviderConfig config, OAuthCredentials previous, Deadline deadline) throws EvermailException;
}
