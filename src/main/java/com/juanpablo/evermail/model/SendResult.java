package com.juanpablo.evermail.model;

import lombok.Value;
import lombok.With;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;

@Value
@With
@ToString(onlyExplicitlyIncluded = true)
public class SendResult {
    UUID submissionId;
    DeliveryState state;
    UUID sentMailId;
    com.juanpablo.evermail.exception.ErrorCode error;
}
