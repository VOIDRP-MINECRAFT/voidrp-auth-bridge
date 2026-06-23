package ru.voidrp.authbridge.integration;

import java.util.UUID;

public interface AuthRestrictionBridge {

    void onPlayerAuthenticated(UUID playerUuid);

    default void onPlayerSessionEnded(UUID playerUuid) {
        // Optional lifecycle hook.
    }

    static AuthRestrictionBridge noop() {
        return new AuthRestrictionBridge() {
            @Override
            public void onPlayerAuthenticated(UUID playerUuid) {
                // Intentionally blank.
            }
        };
    }
}
