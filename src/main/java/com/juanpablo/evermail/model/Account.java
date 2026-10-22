package com.juanpablo.evermail.model;

import lombok.Value;
import lombok.With;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;

@Value
@With
@ToString(onlyExplicitlyIncluded = true)
public class Account {
    UUID id;
    OAuthProvider provider;
    String providerSubject;
    String email;
    String displayName;
    String keyRef;
    AccountStatus status;
}
