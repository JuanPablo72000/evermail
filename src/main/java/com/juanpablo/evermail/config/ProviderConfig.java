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
    int redirectPort;

    public ProviderConfig(OAuthProvider provider, String clientId, String clientSecret) {
        this(provider, clientId, clientSecret, provider == OAuthProvider.MICROSOFT ? 53682 : 0);
    }

    public ProviderConfig(OAuthProvider provider, String clientId, String clientSecret, int redirectPort) {
        if (redirectPort != 0 && (redirectPort < 1024 || redirectPort > 65535))
            throw new IllegalArgumentException("Invalid local redirect port");
        this.provider = provider; this.clientId = clientId; this.clientSecret = clientSecret; this.redirectPort = redirectPort;
    }
}
