package ru.voidrp.authbridge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.voidrp.authbridge.VoidRpAuthBridge;
import ru.voidrp.authbridge.network.AuthStatusPayload;

public final class ClientAuthStatusHandler {

    private ClientAuthStatusHandler() {
    }

    public static void handle(AuthStatusPayload payload, IPayloadContext context) {
        VoidRpAuthBridge.LOGGER.info(
                "Auth status from server: status={} message={}",
                payload.status(),
                payload.message()
        );

        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(payload.message()));
            }

            if ("ACCEPTED".equals(payload.status()) || "REJECTED".equals(payload.status())) {
                ClientChatFilter.stopFiltering();
            }
        });
    }
}
