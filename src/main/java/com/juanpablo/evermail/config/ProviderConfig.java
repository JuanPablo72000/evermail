package com.juanpablo.evermail.config;

import com.juanpablo.evermail.model.OAuthProvider;
import lombok.Value;
import lombok.ToString;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class ProviderConfig {
    OAuthProvider provider;
    String clientId;
    String clientSecret;
}
