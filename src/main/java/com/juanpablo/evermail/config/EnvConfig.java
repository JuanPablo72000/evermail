package com.juanpablo.evermail.config;

import com.juanpablo.evermail.exception.ConfigurationException;
import com.juanpablo.evermail.exception.ErrorCode;
import com.juanpablo.evermail.model.OAuthProvider;
import io.github.cdimascio.dotenv.Dotenv;
import java.util.function.Function;

public class EnvConfig {
    private final Function<String, String> values;

    public EnvConfig() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        values = dotenv::get;
    }

    public EnvConfig(Function<String, String> values) {
        this.values = values;
    }

    public ProviderConfig loadProvider(OAuthProvider provider) throws ConfigurationException {
        String id = values.apply(provider.name() + "_CLIENT_ID");
        String secret = provider.requiresClientSecret() ? values.apply(provider.name() + "_CLIENT_SECRET") : null;
        if (id == null || id.isBlank() || (provider.requiresClientSecret() && (secret == null || secret.isBlank()))) {
            throw new ConfigurationException(ErrorCode.CONFIG_INVALID, "Missing configuration for " + provider.name());
        }
        int port = provider == OAuthProvider.MICROSOFT ? 53682 : 0;
        String configured = values.apply(provider.name() + "_REDIRECT_PORT");
        if (configured != null && !configured.isBlank()) {
            try {
                port = Integer.parseInt(configured);
                if (port < 1024 || port > 65535) throw new NumberFormatException();
            } catch (NumberFormatException invalid) {
                throw new ConfigurationException(ErrorCode.CONFIG_INVALID, "Invalid local redirect port");
            }
        }
        return new ProviderConfig(provider, id, secret, port);
    }
}
