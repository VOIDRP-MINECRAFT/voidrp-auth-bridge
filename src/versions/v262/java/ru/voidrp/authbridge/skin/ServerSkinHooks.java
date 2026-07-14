package ru.voidrp.authbridge.skin;

import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.voidrp.authbridge.VoidRpAuthBridge;
import ru.voidrp.authbridge.bootstrap.ModBootstrap;
import ru.voidrp.authbridge.compat.Compat;
import ru.voidrp.authbridge.common.dto.PlayerSkinResponse;

/**
 * Server side of the VoidRP skin system (26.2). On join we push every known
 * player's skin to the newcomer, then fetch the newcomer's skin from the backend
 * and broadcast it to everyone. All wire work happens on the main server thread.
 */
public final class ServerSkinHooks {

    // playerName (lowercase) -> last known skin payload
    private static final Map<String, SkinDataPayload> CACHE = new ConcurrentHashMap<>();

    private ServerSkinHooks() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        String playerName = Compat.profileName(player.getGameProfile());

        // 1. Send every already-known skin to the joining player.
        for (SkinDataPayload payload : CACHE.values()) {
            PacketDistributor.sendToPlayer(player, payload);
        }

        // 2. Fetch the joiner's own skin, then broadcast it to everyone.
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        ModBootstrap.get().backendAuthClient().getPlayerSkinAsync(playerName)
                .thenAccept(skin -> server.execute(() -> applyAndBroadcast(server, playerName, skin)));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CACHE.remove(Compat.profileName(player.getGameProfile()).toLowerCase(Locale.ROOT));
    }

    private static void applyAndBroadcast(MinecraftServer server, String playerName, PlayerSkinResponse skin) {
        String url = skin != null && skin.hasSkin() && skin.skinUrl() != null ? skin.skinUrl() : "";
        boolean slim = skin != null && skin.isSlim();
        String hash = skin != null && skin.sha256() != null ? skin.sha256() : "";

        SkinDataPayload payload = new SkinDataPayload(playerName, url, slim, hash);
        CACHE.put(playerName.toLowerCase(Locale.ROOT), payload);

        // Only send to players actually online (the joiner included).
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(online, payload);
        }

        VoidRpAuthBridge.LOGGER.info(
                "Skin broadcast: player={} hasSkin={} slim={}",
                playerName, !url.isEmpty(), slim
        );
    }
}
