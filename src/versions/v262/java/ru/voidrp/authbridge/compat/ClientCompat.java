package ru.voidrp.authbridge.compat;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Клиентская часть версионного адаптера — ссылается на client-only классы,
 * не должна грузиться на dedicated-сервере.
 */
public final class ClientCompat {
    private ClientCompat() {}

    /** Отправка пейлоада с клиента на сервер. */
    public static void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
