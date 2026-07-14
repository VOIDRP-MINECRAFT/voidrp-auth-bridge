package ru.voidrp.authbridge.skin;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ru.voidrp.authbridge.compat.Compat;

/**
 * Server → client: tells the modpack which VoidRP skin to apply for a player.
 * skinUrl is a public media PNG (empty = player has no custom skin → default).
 * 26.2-only feature (abyss); applied client-side, no Mojang signature needed.
 */
public record SkinDataPayload(
        String playerName,
        String skinUrl,
        boolean slim,
        String hash
) implements CustomPacketPayload {

    public static final Type<SkinDataPayload> TYPE = Compat.payloadType("skin_data");

    public static final StreamCodec<ByteBuf, SkinDataPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    SkinDataPayload::playerName,
                    ByteBufCodecs.STRING_UTF8,
                    SkinDataPayload::skinUrl,
                    ByteBufCodecs.BOOL,
                    SkinDataPayload::slim,
                    ByteBufCodecs.STRING_UTF8,
                    SkinDataPayload::hash,
                    SkinDataPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
