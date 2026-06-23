package ru.voidrp.authbridge.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.voidrp.authbridge.VoidRpAuthBridge;

public record ConsumePlayTicketPayload(
        String ticket,
        String playerName,
        String launcherProof
) implements CustomPacketPayload {

    public static final Type<ConsumePlayTicketPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VoidRpAuthBridge.MODID, "consume_play_ticket"));

    public static final StreamCodec<ByteBuf, ConsumePlayTicketPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    ConsumePlayTicketPayload::ticket,
                    ByteBufCodecs.STRING_UTF8,
                    ConsumePlayTicketPayload::playerName,
                    ByteBufCodecs.STRING_UTF8,
                    ConsumePlayTicketPayload::launcherProof,
                    ConsumePlayTicketPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}