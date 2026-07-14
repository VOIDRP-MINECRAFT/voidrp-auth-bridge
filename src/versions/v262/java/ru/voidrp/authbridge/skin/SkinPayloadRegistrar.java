package ru.voidrp.authbridge.skin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the server → client skin channel (26.2 only). Optional channel so a
 * mismatched client is never rejected. The real client handler is only wired on
 * the physical client — on a dedicated server we register a no-op receiver so
 * the payload type is known and sendable, without loading client-only classes.
 */
public final class SkinPayloadRegistrar {

    private SkinPayloadRegistrar() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            registrar.playToClient(
                    SkinDataPayload.TYPE,
                    SkinDataPayload.STREAM_CODEC,
                    ClientSkinService::handle
            );
        } else {
            // Server only sends this payload; it never receives it.
            registrar.playToClient(
                    SkinDataPayload.TYPE,
                    SkinDataPayload.STREAM_CODEC,
                    (payload, context) -> {
                    }
            );
        }
    }
}
