package ru.voidrp.authbridge.compat;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import ru.voidrp.authbridge.VoidRpAuthBridge;

/**
 * Версионный адаптер API (Minecraft 26.2 / NeoForge 26.2).
 * Общий код обращается только к этому классу; различия версий живут здесь.
 */
public final class Compat {
    private Compat() {}

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VoidRpAuthBridge.MODID, path));
    }

    public static String profileName(GameProfile profile) {
        return profile.name();
    }

    /** Сообщение над хотбаром (actionbar). */
    public static void sendOverlay(Player player, Component message) {
        player.sendOverlayMessage(message);
    }

}
