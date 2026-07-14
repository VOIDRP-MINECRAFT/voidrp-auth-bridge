package ru.voidrp.authbridge.skin;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.voidrp.authbridge.VoidRpAuthBridge;

/**
 * Client side of the VoidRP skin system (26.2). Receives skin mappings from the
 * server, downloads the PNG via Minecraft's own {@link SkinTextureDownloader}
 * (handles legacy 64x32 conversion + texture registration), and overrides each
 * player's skin by replacing the private {@code PlayerInfo.skinLookup} supplier.
 * No mixin required — NeoForge production runs on official mappings.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSkinService {

    // lowercase player name -> resolved custom skin
    private static final Map<String, PlayerSkin> READY = new ConcurrentHashMap<>();
    // lowercase player name -> hash currently downloaded/applied (dedupe)
    private static final Map<String, String> APPLIED_HASH = new ConcurrentHashMap<>();

    private static SkinTextureDownloader downloader;
    private static Field skinLookupField;
    private static int tickCounter;

    private ClientSkinService() {
    }

    /** Payload handler registered via the client payload registrar. */
    public static void handle(SkinDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> accept(payload));
    }

    private static void accept(SkinDataPayload payload) {
        String key = payload.playerName().toLowerCase(Locale.ROOT);
        String url = payload.skinUrl();

        if (url == null || url.isEmpty()) {
            // Player has no custom skin — drop any override so the default shows.
            READY.remove(key);
            APPLIED_HASH.remove(key);
            return;
        }

        String hash = payload.hash() != null && !payload.hash().isEmpty()
                ? payload.hash()
                : Integer.toHexString(url.hashCode());

        if (hash.equals(APPLIED_HASH.get(key)) && READY.containsKey(key)) {
            // Same skin already resolved — just make sure it is applied.
            applyToPlayerInfo(key, READY.get(key));
            return;
        }
        APPLIED_HASH.put(key, hash);

        PlayerModelType model = payload.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        String safe = hash.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (safe.isEmpty()) {
            safe = Integer.toHexString(url.hashCode());
        }

        Minecraft mc = Minecraft.getInstance();
        Identifier textureId = Identifier.fromNamespaceAndPath(VoidRpAuthBridge.MODID, "skins/" + safe);
        Path cache = mc.gameDirectory.toPath().resolve("voidrp-skins").resolve(safe + ".png");

        try {
            downloader().downloadAndRegisterSkin(textureId, cache, url, true)
                    .thenAccept(texture -> mc.execute(() -> {
                        PlayerSkin skin = PlayerSkin.insecure(texture, null, null, model);
                        READY.put(key, skin);
                        applyToPlayerInfo(key, skin);
                    }))
                    .exceptionally(ex -> {
                        VoidRpAuthBridge.LOGGER.warn("Failed to load VoidRP skin for {}: {}", key, ex.getMessage());
                        return null;
                    });
        } catch (Exception ex) {
            VoidRpAuthBridge.LOGGER.warn("Skin download dispatch failed for {}: {}", key, ex.getMessage());
        }
    }

    /** Re-apply resolved skins periodically so newly-listed PlayerInfos pick them up. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (READY.isEmpty()) {
            return;
        }
        if (++tickCounter % 20 != 0) {
            return;
        }
        for (Map.Entry<String, PlayerSkin> entry : READY.entrySet()) {
            applyToPlayerInfo(entry.getKey(), entry.getValue());
        }
    }

    private static void applyToPlayerInfo(String lowerName, PlayerSkin skin) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            return;
        }
        PlayerInfo info = connection.getPlayerInfo(lowerName);
        if (info == null) {
            // getPlayerInfo(String) is exact-case; fall back to a scan.
            for (PlayerInfo candidate : connection.getOnlinePlayers()) {
                if (candidate.getProfile().name().toLowerCase(Locale.ROOT).equals(lowerName)) {
                    info = candidate;
                    break;
                }
            }
        }
        if (info == null) {
            return;
        }
        try {
            Field field = skinLookupField();
            final PlayerSkin resolved = skin;
            field.set(info, (Supplier<PlayerSkin>) () -> resolved);
        } catch (Exception ex) {
            VoidRpAuthBridge.LOGGER.warn("Could not override skinLookup for {}: {}", lowerName, ex.getMessage());
        }
    }

    private static SkinTextureDownloader downloader() {
        if (downloader == null) {
            Minecraft mc = Minecraft.getInstance();
            downloader = new SkinTextureDownloader(mc.getProxy(), mc.getTextureManager(), mc);
        }
        return downloader;
    }

    private static Field skinLookupField() throws NoSuchFieldException {
        if (skinLookupField == null) {
            Field f = PlayerInfo.class.getDeclaredField("skinLookup");
            f.setAccessible(true);
            skinLookupField = f;
        }
        return skinLookupField;
    }
}
