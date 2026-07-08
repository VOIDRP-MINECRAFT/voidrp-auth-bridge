package ru.voidrp.authbridge.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ru.voidrp.authbridge.compat.Compat;

public record ConsumePlayTicketPayload(
        String ticket,
        String playerName,
        String launcherProof
) implements CustomPacketPayload {

    public static final Type<ConsumePlayTicketPayload> TYPE =
            Compat.payloadType("consume_play_ticket");

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