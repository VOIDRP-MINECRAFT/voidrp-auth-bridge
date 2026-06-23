package ru.voidrp.authbridge.client;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.voidrp.authbridge.VoidRpAuthBridge;
import ru.voidrp.authbridge.bootstrap.ModBootstrap;
import ru.voidrp.authbridge.common.dto.ConsumePlayTicketRequest;
import ru.voidrp.authbridge.network.ConsumePlayTicketPayload;

public final class ClientAuthHooks {

    private static final long DISPATCH_WINDOW_MS = 120_000L;

    private static boolean sentThisSession = false;
    private static boolean awaitingDispatch = false;
    private static long loginStartedAtMs = 0L;

    private ClientAuthHooks() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        sentThisSession = false;
        awaitingDispatch = true;
        loginStartedAtMs = System.currentTimeMillis();
        ClientChatFilter.startFiltering();

        VoidRpAuthBridge.LOGGER.info(
                "Client login detected, waiting for player instance before sending launcher ticket."
        );
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!awaitingDispatch || sentThisSession) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.getConnection() == null) {
            resetState();
            return;
        }

        long elapsedMs = System.currentTimeMillis() - loginStartedAtMs;
        if (elapsedMs > DISPATCH_WINDOW_MS) {
            awaitingDispatch = false;
            ClientChatFilter.stopFiltering();
            VoidRpAuthBridge.LOGGER.warn(
                    "Launcher ticket dispatch window expired before player instance became ready."
            );
            return;
        }

        if (minecraft.player == null) {
            return;
        }

        String playerName = minecraft.player.getGameProfile().getName();

        Optional<ConsumePlayTicketRequest> request
                = ModBootstrap.get().clientTicketDispatcher().buildConsumeRequest(playerName);

        if (request.isEmpty()) {
            awaitingDispatch = false;
            ClientChatFilter.stopFiltering();
            VoidRpAuthBridge.LOGGER.warn(
                    "No valid launcher play ticket found for player={} at path={}",
                    playerName,
                    ModBootstrap.get().properties().localTicketPath()
            );
            return;
        }

        ConsumePlayTicketRequest value = request.get();

        PacketDistributor.sendToServer(new ConsumePlayTicketPayload(
                value.ticket(),
                value.playerName(),
                value.launcherProof() != null ? value.launcherProof() : ""
        ));

        sentThisSession = true;
        awaitingDispatch = false;

        VoidRpAuthBridge.LOGGER.info(
                "Sent launcher play ticket payload for player={}",
                playerName
        );
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        resetState();
    }

    private static void resetState() {
        sentThisSession = false;
        awaitingDispatch = false;
        loginStartedAtMs = 0L;
        ClientChatFilter.stopFiltering();
    }
}
