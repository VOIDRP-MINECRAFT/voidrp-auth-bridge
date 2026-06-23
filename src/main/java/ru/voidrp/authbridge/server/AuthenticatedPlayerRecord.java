package ru.voidrp.authbridge.server;

import java.time.Instant;
import java.util.UUID;

public record AuthenticatedPlayerRecord(
        UUID playerUuid,
        UUID userId,
        String playerName,
        Instant authenticatedAt,
        AuthSource source,
        boolean legacyAuthEnabled
) {
}