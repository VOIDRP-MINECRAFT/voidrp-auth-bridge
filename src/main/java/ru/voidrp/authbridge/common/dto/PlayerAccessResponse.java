package ru.voidrp.authbridge.common.dto;

import com.google.gson.annotations.SerializedName;

public record PlayerAccessResponse(
        @SerializedName("player_exists")
        boolean playerExists,

        @SerializedName("user_active")
        boolean userActive,

        @SerializedName("legacy_auth_enabled")
        boolean legacyAuthEnabled,

        @SerializedName("must_use_launcher")
        boolean mustUseLauncher,

        @SerializedName("minecraft_nickname")
        String minecraftNickname,

        @SerializedName("error")
        String error
) {
    public static PlayerAccessResponse failed(String error) {
        return new PlayerAccessResponse(false, false, false, true, null, error);
    }
}