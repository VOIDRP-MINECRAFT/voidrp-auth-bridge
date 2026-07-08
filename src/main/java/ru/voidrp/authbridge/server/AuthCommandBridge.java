package ru.voidrp.authbridge.server;

import ru.voidrp.authbridge.compat.Compat;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import ru.voidrp.authbridge.VoidRpAuthBridge;
import ru.voidrp.authbridge.bootstrap.ModBootstrap;

public final class AuthCommandBridge {

    private static final long LOGIN_COOLDOWN_MS = 3_000L;
    private static final Map<UUID, Long> lastLoginAttempt = new ConcurrentHashMap<>();

    private AuthCommandBridge() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("login")
                        .then(Commands.argument("password", StringArgumentType.greedyString())
                                .executes(context -> {
                                    var source = context.getSource();
                                    var player = source.getPlayerOrException();
                                    UUID playerUuid = player.getUUID();
                                    String password = StringArgumentType.getString(context, "password");

                                    if (!ModBootstrap.get().stateStore().isLegacyPending(playerUuid)) {
                                        player.sendSystemMessage(Component.literal("Для этого аккаунта legacy вход не разрешён."));
                                        return 0;
                                    }

                                    long now = System.currentTimeMillis();
                                    Long last = lastLoginAttempt.get(playerUuid);
                                    if (last != null && now - last < LOGIN_COOLDOWN_MS) {
                                        player.sendSystemMessage(Component.literal("Слишком частые попытки. Подождите немного."));
                                        return 0;
                                    }
                                    lastLoginAttempt.put(playerUuid, now);

                                    String playerName = Compat.profileName(player.getGameProfile());
                                    MinecraftServer server = player.level().getServer();

                                    // Fire-and-forget async HTTP — never blocks the server tick.
                                    ModBootstrap.get().legacyAuthService().loginAsync(playerUuid, playerName, password)
                                            .thenAccept(response -> server.execute(() -> {
                                                var playerNow = server.getPlayerList().getPlayer(playerUuid);
                                                if (playerNow == null) return;
                                                if (response != null && response.accepted()) {
                                                    lastLoginAttempt.remove(playerUuid);
                                                    ModBootstrap.get().authRestrictionBridge().onPlayerAuthenticated(playerUuid);
                                                    playerNow.sendSystemMessage(Component.literal("Legacy авторизация успешна."));
                                                    VoidRpAuthBridge.LOGGER.info("Legacy login accepted: player={} uuid={}", playerName, playerUuid);
                                                } else {
                                                    VoidRpAuthBridge.LOGGER.warn("Legacy login failed: player={} uuid={}", playerName, playerUuid);
                                                    playerNow.sendSystemMessage(Component.literal("Неверный пароль."));
                                                }
                                            }));
                                    return 1;
                                }))
        );
    }

    public static void clearCooldown(UUID playerUuid) {
        lastLoginAttempt.remove(playerUuid);
    }
}