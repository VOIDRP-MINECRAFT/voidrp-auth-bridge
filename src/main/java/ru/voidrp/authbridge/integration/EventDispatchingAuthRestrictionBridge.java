package ru.voidrp.authbridge.integration;

import java.util.UUID;
import net.neoforged.neoforge.common.NeoForge;
import ru.voidrp.authbridge.VoidRpAuthBridge;

public final class EventDispatchingAuthRestrictionBridge implements AuthRestrictionBridge {

    private final AuthIntegrationRegistry registry;

    public EventDispatchingAuthRestrictionBridge(AuthIntegrationRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }

        this.registry = registry;
    }

    @Override
    public void onPlayerAuthenticated(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }

        registry.markAuthenticated(playerUuid);
        NeoForge.EVENT_BUS.post(new VoidRpPlayerAuthenticatedEvent(playerUuid));

        VoidRpAuthBridge.LOGGER.info(
                "External auth bridge marked player as authenticated: uuid={}",
                playerUuid
        );
    }

    @Override
    public void onPlayerSessionEnded(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }

        boolean wasAuthenticated = registry.isAuthenticated(playerUuid);
        registry.clear(playerUuid);

        if (!wasAuthenticated) {
            return;
        }

        NeoForge.EVENT_BUS.post(new VoidRpPlayerAuthSessionEndedEvent(playerUuid));

        VoidRpAuthBridge.LOGGER.info(
                "External auth bridge cleared authenticated player session: uuid={}",
                playerUuid
        );
    }
}
