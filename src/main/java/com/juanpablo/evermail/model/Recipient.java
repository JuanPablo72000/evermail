package com.juanpablo.evermail.model;

import lombok.Value;
import lombok.With;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;

@Value
@With
@ToString(onlyExplicitlyIncluded = true)
public class Recipient {
    String email;
    String addressKey;
    String displayName;
    RecipientType type;
}
