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

    /**
     * Where a server keeps its settings without JVM flags — the way a partner's server is set
     * up (the integration page hands this file out filled in). Relative to the server folder.
     */
    public static final Path CONFIG_FILE = Path.of("config", "voidrp-auth-bridge.properties");

    public static AuthBridgeProperties loadDefault() {
        // JVM flags win over the file, so the main server (configured by flags) is unaffected.
        java.util.Properties file = new java.util.Properties();
        if (java.nio.file.Files.isRegularFile(CONFIG_FILE)) {
            try (var reader = java.nio.file.Files.newBufferedReader(CONFIG_FILE, java.nio.charset.StandardCharsets.UTF_8)) {
                file.load(reader);
            } catch (java.io.IOException ignored) {
                // unreadable file: flags and defaults still apply
            }
        }
        String baseUrl = setting(file, "backend", "https://api.void-rp.ru");
        String timeoutMs = setting(file, "timeoutMs", "60000");
        String graceSecs = setting(file, "graceSecs", "120");
        String ticketPath = setting(file, "ticketPath", defaultTicketPath().toString());
        String gameSecret = setting(file, "gameSecret", "");

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

    private static String setting(java.util.Properties file, String key, String fallback) {
        String flag = System.getProperty("voidrp.auth." + key);
        if (flag != null && !flag.isBlank()) {
            return flag.trim();
        }
        String value = file.getProperty(key);
        return value != null && !value.isBlank() ? value.trim() : fallback;
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
