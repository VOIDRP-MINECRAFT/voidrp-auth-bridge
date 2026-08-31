package ru.voidrp.authbridge.skin;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.voidrp.authbridge.VoidRpAuthBridge;
import ru.voidrp.authbridge.bootstrap.ModBootstrap;
import ru.voidrp.authbridge.common.dto.PlayerSkinResponse;
import ru.voidrp.authbridge.compat.Compat;

/**
 * Server side of the VoidRP skin system (1.21.1). On join we push every known
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
        refreshAndBroadcast(server, playerName);
    }

    /**
     * Re-fetch a player's skin from the backend and broadcast it to everyone online — used
     * for INSTANT skin changes (triggered from the WebGUI via a server command). The player
     * name is resolved to its exact online-profile casing when possible.
     */
    public static void refreshAndBroadcast(MinecraftServer server, String playerName) {
        if (server == null || playerName == null || playerName.isEmpty()) {
            return;
        }
        String resolved = playerName;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            String n = Compat.profileName(online.getGameProfile());
            if (n.equalsIgnoreCase(playerName)) {
                resolved = n;
                break;
            }
        }
        final String name = resolved;
        ModBootstrap.get().backendAuthClient().getPlayerSkinAsync(name)
                .thenAccept(skin -> server.execute(() -> applyAndBroadcast(server, name, skin)));
    }

    /** Console/OP command: {@code /voidrpskin refresh <player>} → instant skin re-broadcast. */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("voidrpskin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("refresh")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> {
                                            MinecraftServer server = ctx.getSource().getServer();
                                            String name = StringArgumentType.getString(ctx, "player");
                                            refreshAndBroadcast(server, name);
                                            return 1;
                                        }))));
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
