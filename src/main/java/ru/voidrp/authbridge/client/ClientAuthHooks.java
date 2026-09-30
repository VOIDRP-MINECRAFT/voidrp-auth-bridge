package ru.voidrp.authbridge.client;

import ru.voidrp.authbridge.compat.ClientCompat;
import ru.voidrp.authbridge.compat.Compat;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.voidrp.authbridge.VoidRpAuthBridge;
import ru.voidrp.authbridge.bootstrap.ModBootstrap;
import ru.voidrp.authbridge.common.dto.ConsumePlayTicketRequest;
import ru.voidrp.authbridge.network.ConsumePlayTicketPayload;

public final class ClientAuthHooks {

    // How long the client keeps trying to send its ticket after login. A heavy pack keeps
    // the client thread busy for minutes on a slow PC; the old 120 s window expired first
    // and the ticket was never sent, leaving the player at "checking authorization".
    private static final long DISPATCH_WINDOW_MS = 30 * 60_000L;

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
                "Client login detected, sending launcher ticket as soon as the player instance exists."
        );
        // The player usually exists already here: send now instead of on the first client
        // tick, which a heavy pack delays by minutes while it loads recipes and tags.
        // Only when everything is in place: otherwise tryDispatch would reset the state and
        // the tick fallback would never run.
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().getConnection() != null) {
            tryDispatch();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!awaitingDispatch || sentThisSession) {
            return;
        }
        tryDispatch();
    }

    // No client class in any method signature here: the event bus reads every declared
    // method's signature when it registers this class, also on the dedicated server, where
    // a Minecraft parameter fails the whole mod with NoClassDefFoundError.
    private static void tryDispatch() {
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

        String playerName = Compat.profileName(minecraft.player.getGameProfile());

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

        ClientCompat.sendToServer(new ConsumePlayTicketPayload(
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
