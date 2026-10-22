package com.juanpablo.evermail.config;

import com.juanpablo.evermail.exception.ConfigurationException;
import com.juanpablo.evermail.exception.ErrorCode;
import java.nio.file.Path;

public final class StorageConfig {
    private StorageConfig() {
    }

    public static Path databasePath() throws ConfigurationException {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            throw new ConfigurationException(ErrorCode.CONFIG_INVALID, "APPDATA is unavailable");
        }
        return Path.of(appData, "Evermail", "evermail.db").toAbsolutePath();
    }
}
