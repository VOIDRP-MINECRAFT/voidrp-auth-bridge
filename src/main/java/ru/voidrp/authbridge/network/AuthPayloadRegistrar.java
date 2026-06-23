package ru.voidrp.authbridge.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ru.voidrp.authbridge.client.ClientAuthStatusHandler;

public final class AuthPayloadRegistrar {
    private AuthPayloadRegistrar() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Client → server: launcher ticket submission.
        registrar.playToServer(
                ConsumePlayTicketPayload.TYPE,
                ConsumePlayTicketPayload.STREAM_CODEC,
                ServerPayloadHandler::handleConsumePlayTicket
        );

        // Server → client: auth status notifications.
        // Having this channel registered on both sides forces NeoForge to reject
        // any client that does not have this mod installed during login negotiation.
        registrar.playToClient(
                AuthStatusPayload.TYPE,
                AuthStatusPayload.STREAM_CODEC,
                ClientAuthStatusHandler::handle
        );
    }
}