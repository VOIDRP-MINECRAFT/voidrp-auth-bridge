package ru.voidrp.authbridge.skin;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.voidrp.authbridge.VoidRpAuthBridge;
import ru.voidrp.authbridge.compat.Compat;

/**
 * Client side of the VoidRP skin system (1.21.1). Receives skin mappings from the
 * server, downloads the PNG via Minecraft's own {@link HttpTexture} (handles
 * legacy 64x32 conversion + texture registration), and overrides each player's
 * skin by replacing the private {@code PlayerInfo.skinLookup} supplier. No mixin
 * required — NeoForge production runs on official mappings. Because the swap is
 * purely client-side, the server never removes/re-adds the player from the
 * player-list, so nobody flickers off the tab list.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSkinService {

    // lowercase player name -> resolved custom skin
    private static final Map<String, PlayerSkin> READY = new ConcurrentHashMap<>();
    // lowercase player name -> hash currently downloaded/applied (dedupe)
    private static final Map<String, String> APPLIED_HASH = new ConcurrentHashMap<>();

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

        PlayerSkin.Model model = payload.slim() ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
        String safe = hash.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (safe.isEmpty()) {
            safe = Integer.toHexString(url.hashCode());
        }

        Minecraft mc = Minecraft.getInstance();
        ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(VoidRpAuthBridge.MODID, "skins/" + safe);
        File cache = mc.gameDirectory.toPath().resolve("voidrp-skins").resolve(safe + ".png").toFile();

        try {
            // Same download+register path vanilla's SkinManager uses for URL skins:
            // HttpTexture downloads to the cache file, converts legacy 64x32, and
            // registers under our ResourceLocation; onDownloaded completes the future.
            CompletableFuture<ResourceLocation> future = new CompletableFuture<>();
            HttpTexture texture = new HttpTexture(
                    cache,
                    url,
                    DefaultPlayerSkin.getDefaultTexture(),
                    true,
                    () -> future.complete(textureId)
            );
            mc.getTextureManager().register(textureId, texture);
            future.thenAccept(id -> mc.execute(() -> {
                PlayerSkin skin = new PlayerSkin(id, url, null, null, model, false);
                READY.put(key, skin);
                applyToPlayerInfo(key, skin);
            })).exceptionally(ex -> {
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
        PlayerInfo info = null;
        for (PlayerInfo candidate : connection.getOnlinePlayers()) {
            if (Compat.profileName(candidate.getProfile()).toLowerCase(Locale.ROOT).equals(lowerName)) {
                info = candidate;
                break;
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

    private static Field skinLookupField() throws NoSuchFieldException {
        if (skinLookupField == null) {
            Field f = PlayerInfo.class.getDeclaredField("skinLookup");
            f.setAccessible(true);
            skinLookupField = f;
        }
        return skinLookupField;
    }
}
