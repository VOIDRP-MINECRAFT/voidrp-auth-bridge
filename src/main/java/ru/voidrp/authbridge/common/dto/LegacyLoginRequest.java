package ru.voidrp.authbridge.common.dto;

import com.google.gson.annotations.SerializedName;

public record LegacyLoginRequest(
        @SerializedName("player_name")
        String playerName,

        @SerializedName("password")
        String password
) {
}