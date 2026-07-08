package ru.voidrp.authbridge.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ru.voidrp.authbridge.compat.Compat;

public record AuthStatusPayload(
        String status,
        String message
) implements CustomPacketPayload {

    public static final Type<AuthStatusPayload> TYPE =
            Compat.payloadType("auth_status");

    public static final StreamCodec<ByteBuf, AuthStatusPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    AuthStatusPayload::status,
                    ByteBufCodecs.STRING_UTF8,
                    AuthStatusPayload::message,
                    AuthStatusPayload::new
            );

    public static AuthStatusPayload pending(String message) {
        return new AuthStatusPayload("PENDING", message);
    }

    public static AuthStatusPayload accepted(String message) {
        return new AuthStatusPayload("ACCEPTED", message);
    }

    public static AuthStatusPayload rejected(String message) {
        return new AuthStatusPayload("REJECTED", message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
