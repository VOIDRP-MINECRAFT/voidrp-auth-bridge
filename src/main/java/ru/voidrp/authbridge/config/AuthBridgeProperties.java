package ru.voidrp.authbridge.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

public record AuthBridgeProperties(
        URI backendBaseUrl,
        Duration requestTimeout,
        long authGraceSeconds,
        Path localTicketPath,
        String consumeTicketPath,
        String legacyLoginPath,
        String gameAuthSecret
        ) {

    public static AuthBridgeProperties loadDefault() {
        String baseUrl = System.getProperty("voidrp.auth.backend", "https://api.void-rp.ru");
        String timeoutMs = System.getProperty("voidrp.auth.timeoutMs", "60000");
        String graceSecs = System.getProperty("voidrp.auth.graceSecs", "120");
        String ticketPath = System.getProperty("voidrp.auth.ticketPath", defaultTicketPath().toString());
        String gameSecret = System.getProperty("voidrp.auth.gameSecret", "");

        return new AuthBridgeProperties(
                URI.create(baseUrl),
                Duration.ofMillis(Long.parseLong(timeoutMs)),
                Long.parseLong(graceSecs),
                Path.of(ticketPath),
                "/api/v1/server/auth/consume-play-ticket",
                "/api/v1/server/auth/legacy-login",
                gameSecret
        );
    }

    private static Path defaultTicketPath() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "VoidRpLauncher", "state", "play-ticket.json");
        }

        String osName = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home", ".");
        if (osName.contains("mac")) {
            return Path.of(userHome, "Library", "Application Support", "VoidRpLauncher", "state", "play-ticket.json");
        }

        return Path.of(userHome, ".local", "share", "VoidRpLauncher", "state", "play-ticket.json");
    }
}
