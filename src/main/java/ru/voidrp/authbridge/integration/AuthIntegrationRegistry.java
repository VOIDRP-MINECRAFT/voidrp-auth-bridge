package ru.voidrp.authbridge.integration;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthIntegrationRegistry {

    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();

    public void markAuthenticated(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }

        authenticatedPlayers.add(playerUuid);
    }

    public void clear(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }

        authenticatedPlayers.remove(playerUuid);
    }

    public boolean isAuthenticated(UUID playerUuid) {
        return playerUuid != null && authenticatedPlayers.contains(playerUuid);
    }

    public int authenticatedCount() {
        return authenticatedPlayers.size();
    }
}
