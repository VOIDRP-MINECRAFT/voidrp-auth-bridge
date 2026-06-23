package ru.voidrp.authbridge.common.dto;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.UUID;

public record ConsumePlayTicketResponse(
        @SerializedName("accepted")
        boolean accepted,

        @SerializedName("user_id")
        UUID userId,

        @SerializedName("minecraft_nickname")
        String minecraftNickname,

        @SerializedName("legacy_auth_enabled")
        boolean legacyAuthEnabled,

        @SerializedName("expires_at")
        Instant expiresAt,

        String error
) {
    public static ConsumePlayTicketResponse failed(String error) {
        return new ConsumePlayTicketResponse(false, null, null, false, null, error);
    }
}