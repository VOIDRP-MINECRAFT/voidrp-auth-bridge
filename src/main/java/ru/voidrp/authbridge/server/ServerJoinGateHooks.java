package ru.voidrp.authbridge.server;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.voidrp.authbridge.VoidRpAuthBridge;
import ru.voidrp.authbridge.bootstrap.ModBootstrap;
import ru.voidrp.authbridge.common.dto.PlayerAccessResponse;
import ru.voidrp.authbridge.network.AuthStatusPayload;

public final class ServerJoinGateHooks {

    private static final Component WAITING_FOR_LAUNCHER = Component.literal(
            "Ожидаем подтверждение входа через лаунчер VoidRP..."
    );
    private static final Component WAITING_FOR_TICKET_OR_LOGIN = Component.literal(
            "Проверяем вход. Если подтверждение из лаунчера не придёт, используй /login <password>."
    );
    private static final Component LEGACY_LOGIN_REQUIRED = Component.literal(
            "Для входа используйте /login <password>"
    );
    private static final Component LAUNCHER_ONLY_KICK = Component.literal(
            "Авторизация доступна только через лаунчер VoidRP."
    );
    private static final Component ACCOUNT_DENIED_KICK = Component.literal(
            "Вход в игру сейчас недоступен для этого аккаунта."
    );
    private static final Component ACCOUNT_NOT_FOUND_KICK = Component.literal(
            "Игровой аккаунт не найден. Зарегистрируй аккаунт VoidRP и укажи правильный ник Minecraft."
    );
    private static final Component AUTH_SERVICE_UNAVAILABLE_KICK = Component.literal(
            "Сервис авторизации временно недоступен. Попробуйте зайти позже."
    );
    private static final Component RECONNECT_ACCEPTED = Component.literal(
            "Повторный вход подтверждён. Можно продолжать игру."
    );

    // Pending async backend access checks: UUID -> future result
    private static final Map<UUID, CompletableFuture<PlayerAccessResponse>> pendingAccessChecks =
            new ConcurrentHashMap<>();
    // Timestamps for timeout enforcement (using configured requestTimeout)
    private static final Map<UUID, Instant> pendingAccessCheckStartTimes = new ConcurrentHashMap<>();

    private ServerJoinGateHooks() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUuid = player.getUUID();
        String playerName = player.getGameProfile().getName();
        var stateStore = ModBootstrap.get().stateStore();

        var reconnectGrant = stateStore.findActiveReconnectGrant(
                playerUuid,
                playerName,
                Instant.now()
        );

        if (reconnectGrant.isPresent()) {
            var grant = reconnectGrant.get();

            stateStore.markAuthenticated(new AuthenticatedPlayerRecord(
                    playerUuid,
                    grant.userId(),
                    playerName,
                    Instant.now(),
                    AuthSource.RECONNECT_GRANT,
                    grant.legacyAuthEnabled()
            ));

            player.sendSystemMessage(RECONNECT_ACCEPTED);

            VoidRpAuthBridge.LOGGER.info(
                    "Player restored from reconnect grant: player={} uuid={} expiresAtUtc={}",
                    playerName,
                    playerUuid,
                    grant.expiresAtUtc()
            );
            return;
        }

        // Start async backend check — never blocks the server tick
        CompletableFuture<PlayerAccessResponse> future =
                ModBootstrap.get().backendAuthClient().getPlayerAccessAsync(playerName);
        pendingAccessChecks.put(playerUuid, future);
        pendingAccessCheckStartTimes.put(playerUuid, Instant.now());

        VoidRpAuthBridge.LOGGER.info(
                "Player queued for async backend access check: player={} uuid={}",
                playerName, playerUuid
        );
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var stateStore = ModBootstrap.get().stateStore();
        stateStore.evictExpiredReconnectGrants(Instant.now());

        // Process completed async access checks
        if (!pendingAccessChecks.isEmpty()) {
            long timeoutMs = ModBootstrap.get().properties().requestTimeout().toMillis();
            Iterator<Map.Entry<UUID, CompletableFuture<PlayerAccessResponse>>> it =
                    pendingAccessChecks.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<UUID, CompletableFuture<PlayerAccessResponse>> entry = it.next();
                UUID playerUuid = entry.getKey();
                CompletableFuture<PlayerAccessResponse> future = entry.getValue();

                ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerUuid);

                // Player disconnected before check finished — cancel and clean up
                if (player == null) {
                    future.cancel(false);
                    it.remove();
                    pendingAccessCheckStartTimes.remove(playerUuid);
                    stateStore.clear(playerUuid);
                    continue;
                }

                String playerName = player.getGameProfile().getName();

                // Force-timeout if backend took longer than configured timeout
                Instant startTime = pendingAccessCheckStartTimes.get(playerUuid);
                if (startTime != null
                        && Instant.now().toEpochMilli() - startTime.toEpochMilli() > timeoutMs) {
                    future.cancel(false);
                    it.remove();
                    pendingAccessCheckStartTimes.remove(playerUuid);
                    VoidRpAuthBridge.LOGGER.warn(
                            "Kicking player because backend access check timed out: player={} uuid={} timeoutMs={}",
                            playerName, playerUuid, timeoutMs
                    );
                    disconnectAndClear(player, AUTH_SERVICE_UNAVAILABLE_KICK);
                    continue;
                }

                if (!future.isDone()) {
                    continue;
                }

                it.remove();
                pendingAccessCheckStartTimes.remove(playerUuid);

                PlayerAccessResponse access;
                try {
                    access = future.get();
                } catch (Exception ex) {
                    VoidRpAuthBridge.LOGGER.warn(
                            "Kicking player because backend access check threw: player={} uuid={} error={}",
                            playerName, playerUuid, ex.getMessage()
                    );
                    disconnectAndClear(player, AUTH_SERVICE_UNAVAILABLE_KICK);
                    continue;
                }

                if (isBackendLookupFailure(access)) {
                    VoidRpAuthBridge.LOGGER.warn(
                            "Kicking player because auth backend lookup failed: player={} uuid={} error={}",
                            playerName, playerUuid, access.error()
                    );
                    disconnectAndClear(player, AUTH_SERVICE_UNAVAILABLE_KICK);
                    continue;
                }

                if (!access.playerExists()) {
                    VoidRpAuthBridge.LOGGER.warn(
                            "Kicking player because player account was not found: player={} uuid={} backendError={}",
                            playerName, playerUuid, access.error()
                    );
                    disconnectAndClear(player, ACCOUNT_NOT_FOUND_KICK);
                    continue;
                }

                if (!access.userActive()) {
                    VoidRpAuthBridge.LOGGER.warn(
                            "Kicking player because account is disabled: player={} uuid={} backendError={}",
                            playerName, playerUuid, access.error()
                    );
                    disconnectAndClear(player, ACCOUNT_DENIED_KICK);
                    continue;
                }

                Instant deadline = Instant.now().plusSeconds(ModBootstrap.get().properties().authGraceSeconds());

                stateStore.markPending(
                        playerUuid,
                        new AuthenticationStateStore.PendingPlayerRecord(
                                deadline,
                                access.legacyAuthEnabled(),
                                access.mustUseLauncher(),
                                player.getX(),
                                player.getY(),
                                player.getZ()
                        )
                );

                VoidRpAuthBridge.LOGGER.info(
                        "Player entered auth gate: player={} uuid={} playerExists={} userActive={} legacyEnabled={} mustUseLauncher={} deadlineUtc={} backendError={}",
                        playerName,
                        playerUuid,
                        access.playerExists(),
                        access.userActive(),
                        access.legacyAuthEnabled(),
                        access.mustUseLauncher(),
                        deadline,
                        access.error()
                );

                if (access.legacyAuthEnabled()) {
                    player.sendSystemMessage(WAITING_FOR_TICKET_OR_LOGIN);
                    PacketDistributor.sendToPlayer(player, AuthStatusPayload.pending(
                            "Ожидаем тикет лаунчера или /login"
                    ));
                } else {
                    player.sendSystemMessage(WAITING_FOR_LAUNCHER);
                    PacketDistributor.sendToPlayer(player, AuthStatusPayload.pending(
                            "Ожидаем тикет лаунчера VoidRP"
                    ));
                }
            }
        }

        // Process auth grace period timeouts
        var pending = stateStore.snapshotPending();

        for (var pendingEntry : pending.entrySet()) {
            UUID playerUuid = pendingEntry.getKey();
            AuthenticationStateStore.PendingPlayerRecord record = pendingEntry.getValue();

            if (Instant.now().isBefore(record.deadlineUtc())) {
                continue;
            }

            if (stateStore.isAuthenticated(playerUuid)) {
                continue;
            }

            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerUuid);
            if (player == null) {
                stateStore.clear(playerUuid);
                continue;
            }

            if (record.legacyAuthEnabled()) {
                stateStore.markLegacyPending(playerUuid);
                player.sendSystemMessage(LEGACY_LOGIN_REQUIRED);

                VoidRpAuthBridge.LOGGER.info(
                        "Player moved to legacy pending: player={} uuid={}",
                        player.getGameProfile().getName(),
                        playerUuid
                );
                continue;
            }

            if (record.mustUseLauncher()) {
                VoidRpAuthBridge.LOGGER.warn(
                        "Kicking player because launcher auth did not arrive before deadline: player={} uuid={}",
                        player.getGameProfile().getName(),
                        playerUuid
                );
                player.connection.disconnect(LAUNCHER_ONLY_KICK);
            } else {
                VoidRpAuthBridge.LOGGER.warn(
                        "Kicking player because account access is denied after auth gate timeout: player={} uuid={}",
                        player.getGameProfile().getName(),
                        playerUuid
                );
                player.connection.disconnect(ACCOUNT_DENIED_KICK);
            }

            stateStore.clear(playerUuid);
        }
    }

    private static boolean isBackendLookupFailure(PlayerAccessResponse access) {
        if (access == null) {
            return true;
        }

        String error = access.error();
        if (error == null || error.isBlank()) {
            return false;
        }

        return error.startsWith("io_error:")
                || error.startsWith("interrupted:")
                || error.startsWith("client_error:")
                || error.startsWith("http_");
    }

    private static void disconnectAndClear(ServerPlayer player, Component reason) {
        ModBootstrap.get().stateStore().clear(player.getUUID());
        player.connection.disconnect(reason);
    }
}
