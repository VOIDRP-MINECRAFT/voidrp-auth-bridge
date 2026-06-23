package ru.voidrp.authbridge.integration;

import java.util.UUID;
import net.neoforged.bus.api.Event;

public final class VoidRpPlayerAuthSessionEndedEvent extends Event {

    private final UUID playerUuid;

    public VoidRpPlayerAuthSessionEndedEvent(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID playerUuid() {
        return playerUuid;
    }
}
