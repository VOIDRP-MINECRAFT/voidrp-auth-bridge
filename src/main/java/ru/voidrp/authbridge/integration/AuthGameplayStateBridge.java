package ru.voidrp.authbridge.integration;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class AuthGameplayStateBridge implements AuthRestrictionBridge {

    public static final String AUTHENTICATED_TAG = "voidrp_authenticated";
    public static final String AUTH_PENDING_TAG = "voidrp_auth_pending";

    @Override
    public void onPlayerAuthenticated(UUID playerUuid) {
        ServerPlayer player = findPlayer(playerUuid);
        if (player == null) {
            return;
        }

        player.removeTag(AUTH_PENDING_TAG);
        player.addTag(AUTHENTICATED_TAG);
    }

    @Override
    public void onPlayerSessionEnded(UUID playerUuid) {
        ServerPlayer player = findPlayer(playerUuid);
        if (player == null) {
            return;
        }

        player.removeTag(AUTHENTICATED_TAG);
        player.addTag(AUTH_PENDING_TAG);
    }

    private ServerPlayer findPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }

        return server.getPlayerList().getPlayer(playerUuid);
    }
}
