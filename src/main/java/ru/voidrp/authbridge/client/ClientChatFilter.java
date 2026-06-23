package ru.voidrp.authbridge.client;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import ru.voidrp.authbridge.VoidRpAuthBridge;

public final class ClientChatFilter {

    private static volatile boolean filteringActive = false;

    private ClientChatFilter() {
    }

    public static void startFiltering() {
        filteringActive = true;
        VoidRpAuthBridge.LOGGER.debug("Auth chat filter: ON");
    }

    public static void stopFiltering() {
        if (filteringActive) {
            filteringActive = false;
            VoidRpAuthBridge.LOGGER.debug("Auth chat filter: OFF");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChatReceived(ClientChatReceivedEvent event) {
        if (filteringActive) {
            event.setCanceled(true);
        }
    }
}
