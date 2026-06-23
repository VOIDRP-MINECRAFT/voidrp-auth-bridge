package ru.voidrp.authbridge.common.dto;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;

public record LegacyLoginResponse(
        @SerializedName("accepted")
        boolean accepted,

        @SerializedName("user_id")
        UUID userId,

        @SerializedName("minecraft_nickname")
        String minecraftNickname,

        @SerializedName("legacy_auth_enabled")
        boolean legacyAuthEnabled,

        @SerializedName("email_verified")
        boolean emailVerified,

        String error
) {
    public static LegacyLoginResponse failed(String error) {
        return new LegacyLoginResponse(false, null, null, false, false, error);
    }
}