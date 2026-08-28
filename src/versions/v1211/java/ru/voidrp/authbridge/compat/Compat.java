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

    /** IP-адрес подключения игрока (без порта), либо null. */
    public static String remoteIp(net.minecraft.server.level.ServerPlayer player) {
        try {
            java.net.SocketAddress addr = player.connection.getConnection().getRemoteAddress();
            if (addr instanceof java.net.InetSocketAddress isa && isa.getAddress() != null) {
                return isa.getAddress().getHostAddress();
            }
            return addr != null ? addr.toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Сообщение над хотбаром (actionbar). */
    public static void sendOverlay(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    /**
     * Включает систему скинов VoidRP (1.21.1). Регистрирует payload и серверные
     * хуки всегда; клиентский обработчик — только на физическом клиенте, чтобы
     * client-only классы не грузились на dedicated-сервере. Заменяет старый путь
     * gamesync→SkinsRestorer (тот дёргал player-list refresh и ронял игроков
     * из таб-листа на гибриде Mohist/Youer).
     */
    public static void initSkins(net.neoforged.bus.api.IEventBus modBus) {
        modBus.register(ru.voidrp.authbridge.skin.SkinPayloadRegistrar.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(ru.voidrp.authbridge.skin.ServerSkinHooks.class);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(ru.voidrp.authbridge.skin.ClientSkinService.class);
        }
    }

}
