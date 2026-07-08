package ru.voidrp.authbridge.compat;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import ru.voidrp.authbridge.VoidRpAuthBridge;

/**
 * Версионный адаптер API (Minecraft 1.21.1 / NeoForge 21.1).
 * Общий код обращается только к этому классу; различия версий живут здесь.
 */
public final class Compat {
    private Compat() {}

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VoidRpAuthBridge.MODID, path));
    }

    public static String profileName(GameProfile profile) {
        return profile.getName();
    }

    /** Сообщение над хотбаром (actionbar). */
    public static void sendOverlay(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

}
