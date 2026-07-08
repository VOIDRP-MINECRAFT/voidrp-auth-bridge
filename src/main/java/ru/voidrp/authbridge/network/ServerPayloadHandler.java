package ru.voidrp.authbridge.network;

import ru.voidrp.authbridge.compat.Compat;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.voidrp.authbridge.VoidRpAuthBridge;
import ru.voidrp.authbridge.bootstrap.ModBootstrap;
import ru.voidrp.authbridge.common.dto.ConsumePlayTicketRequest;
import ru.voidrp.authbridge.common.dto.ConsumePlayTicketResponse;

public final class ServerPayloadHandler {

    private ServerPayloadHandler() {
    }

    public static void handleConsumePlayTicket(final ConsumePlayTicketPayload payload, final IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            VoidRpAuthBridge.LOGGER.warn("Received consume-play-ticket payload from a non-server-player context.");
            return;
        }

        // Always use the server-verified game profile name, never trust the client-supplied name.
        String verifiedPlayerName = Compat.profileName(serverPlayer.getGameProfile());
        UUID playerUuid = serverPlayer.getUUID();
        MinecraftServer server = serverPlayer.level().getServer();

        VoidRpAuthBridge.LOGGER.info(
                "Received launcher ticket payload: player={} uuid={} requestedPlayerName={}",
                verifiedPlayerName,
                playerUuid,
                payload.playerName()
        );

        String ticket = payload.ticket() != null ? payload.ticket().strip() : "";
        String launcherProof = payload.launcherProof() != null ? payload.launcherProof().strip() : "";
        if (ticket.length() > 512) {
            ticket = ticket.substring(0, 512);
        }
        if (launcherProof.length() > 128) {
            launcherProof = launcherProof.substring(0, 128);
        }
        final String finalTicket = ticket;
        final String finalProof = launcherProof;

        // Fire-and-forget async HTTP call — never blocks the server tick.
        // When the future completes (on the HttpClient thread pool), we schedule
        // the result back to the main server thread via server.execute().
        ModBootstrap.get().playTicketConsumeService().authenticateAsync(
                playerUuid,
                verifiedPlayerName,
                new ConsumePlayTicketRequest(finalTicket, verifiedPlayerName, finalProof)
        ).thenAccept(response -> server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) {
                VoidRpAuthBridge.LOGGER.warn(
                        "Launcher auth result arrived but player {} ({}) has disconnected",
                        verifiedPlayerName, playerUuid);
                return;
            }
            applyAuthResult(player, response, verifiedPlayerName);
        }));
    }

    private static void applyAuthResult(ServerPlayer player, ConsumePlayTicketResponse response, String verifiedPlayerName) {
        if (response != null && response.accepted()) {
            ModBootstrap.get().authRestrictionBridge().onPlayerAuthenticated(player.getUUID());
            player.sendSystemMessage(Component.literal("Авторизация через лаунчер подтверждена."));
            PacketDistributor.sendToPlayer(player, AuthStatusPayload.accepted("Авторизация подтверждена"));

            VoidRpAuthBridge.LOGGER.info(
                    "Launcher auth accepted: player={} uuid={} userId={}",
                    verifiedPlayerName,
                    player.getUUID(),
                    response.userId()
            );
        } else {
            String reason = response != null && response.error() != null
                    ? response.error()
                    : "launcher ticket rejected";

            player.sendSystemMessage(Component.literal("Ticket авторизация не прошла: " + reason));
            PacketDistributor.sendToPlayer(player, AuthStatusPayload.rejected(reason));

            VoidRpAuthBridge.LOGGER.warn(
                    "Launcher auth rejected: player={} uuid={} reason={}",
                    verifiedPlayerName,
                    player.getUUID(),
                    reason
            );
        }
    }
}
