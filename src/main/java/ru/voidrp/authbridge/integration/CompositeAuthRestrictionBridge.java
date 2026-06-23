package ru.voidrp.authbridge.integration;

import java.util.List;
import java.util.UUID;

public final class CompositeAuthRestrictionBridge implements AuthRestrictionBridge {

    private final List<AuthRestrictionBridge> delegates;

    public CompositeAuthRestrictionBridge(List<AuthRestrictionBridge> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public void onPlayerAuthenticated(UUID playerUuid) {
        for (AuthRestrictionBridge delegate : delegates) {
            delegate.onPlayerAuthenticated(playerUuid);
        }
    }

    @Override
    public void onPlayerSessionEnded(UUID playerUuid) {
        for (AuthRestrictionBridge delegate : delegates) {
            delegate.onPlayerSessionEnded(playerUuid);
        }
    }
}
