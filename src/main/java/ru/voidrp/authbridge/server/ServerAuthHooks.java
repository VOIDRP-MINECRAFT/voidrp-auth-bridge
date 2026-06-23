package ru.voidrp.authbridge.server;

import java.time.Instant;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import ru.voidrp.authbridge.VoidRpAuthBridge;
import ru.voidrp.authbridge.bootstrap.ModBootstrap;
import ru.voidrp.authbridge.server.AuthCommandBridge;

public final class ServerAuthHooks {

    private static final long RECONNECT_GRANT_SECONDS = 300L;

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

            // Give a reconnect grant to all players who authenticated via a real launcher ticket
            // or via legacy login. A session that was itself restored from a reconnect grant does
            // NOT produce a new grant — this prevents indefinite chaining for launcher-only accounts.
            boolean isChainableSource = record.source() == AuthSource.LAUNCHER_TICKET
                    || record.source() == AuthSource.LEGACY_LOGIN;
            if (isChainableSource) {
                Instant expiresAtUtc = Instant.now().plusSeconds(RECONNECT_GRANT_SECONDS);
                stateStore.rememberReconnectGrant(record, expiresAtUtc);

                VoidRpAuthBridge.LOGGER.info(
                        "Saved reconnect grant for player={} uuid={} source={} until={}",
                        record.playerName(),
                        record.playerUuid(),
                        record.source(),
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
