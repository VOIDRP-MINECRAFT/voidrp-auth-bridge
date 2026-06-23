package ru.voidrp.authbridge.common.dto;

import com.google.gson.annotations.SerializedName;

public record PlayerAccessRequest(
        @SerializedName("player_name")
        String playerName
) {
}