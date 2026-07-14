package ru.voidrp.authbridge.server;

import java.time.Instant;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import ru.voidrp.authbridge.VoidRpAuthBridge;
import ru.voidrp.authbridge.bootstrap.ModBootstrap;
import ru.voidrp.authbridge.compat.Compat;
import ru.voidrp.authbridge.server.AuthCommandBridge;

public final class ServerAuthHooks {

    // Reconnect-grant lifetime. Default 30 min (was 5). Override with
    // -Dvoidrp.auth.reconnectGrantMinutes=<n>. Lets players who get disconnected
    // (e.g. by an HDD save-freeze timeout kick) rejoin without a full launcher re-auth.
    private static final long RECONNECT_GRANT_SECONDS = resolveGrantSeconds();

    private static long resolveGrantSeconds() {
        long minutes = 30L;
        try {
            String prop = System.getProperty("voidrp.auth.reconnectGrantMinutes");
            if (prop != null) {
                minutes = Long.parseLong(prop.trim());
            }
        } catch (Throwable ignored) {
            // keep default
        }
        return Math.max(1L, minutes) * 60L;
    }

    private ServerAuthHooks() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        var stateStore = ModBootstrap.get().stateStore();
        var player = event.getEntity();
        var playerUuid = player.getUUID();

        var authenticated = stateStore.find(playerUuid);
        if (authenticated.isPresent()) {
            var record = authenticated.get();

            // Give a reconnect grant to players who authenticated via a launcher ticket, legacy
            // login, OR a previous reconnect grant. Chaining RECONNECT_GRANT lets a player who is
            // repeatedly disconnected (HDD save-freeze kicks) rejoin unlimited times without a full
            // launcher re-auth. Note: vanilla bans and the anticheat still apply before this mod, so
            // only the backend account-access re-check is deferred within the grant window.
            boolean isChainableSource = record.source() == AuthSource.LAUNCHER_TICKET
                    || record.source() == AuthSource.LEGACY_LOGIN
                    || record.source() == AuthSource.RECONNECT_GRANT;
            if (isChainableSource) {
                Instant expiresAtUtc = Instant.now().plusSeconds(RECONNECT_GRANT_SECONDS);
                String ip = player instanceof ServerPlayer sp ? Compat.remoteIp(sp) : null;
                stateStore.rememberReconnectGrant(record, expiresAtUtc, ip);

                VoidRpAuthBridge.LOGGER.info(
                        "Saved reconnect grant for player={} uuid={} source={} ip={} until={}",
                        record.playerName(),
                        record.playerUuid(),
                        record.source(),
                        ip,
                        expiresAtUtc
                );
            } else {
                stateStore.removeReconnectGrant(playerUuid);
                VoidRpAuthBridge.LOGGER.info(
                        "No reconnect grant saved for player={} uuid={} source={} (reconnect chain not allowed)",
                        record.playerName(),
                        record.playerUuid(),
                        record.source()
                );
            }
        }

        AuthCommandBridge.clearCooldown(playerUuid);
        ModBootstrap.get().authRestrictionBridge().onPlayerSessionEnded(playerUuid);
        stateStore.clear(playerUuid);
    }
}
