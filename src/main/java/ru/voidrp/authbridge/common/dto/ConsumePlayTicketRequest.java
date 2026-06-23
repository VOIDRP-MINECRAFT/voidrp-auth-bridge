package ru.voidrp.authbridge.common.dto;

import com.google.gson.annotations.SerializedName;

public record ConsumePlayTicketRequest(
        @SerializedName("ticket")
        String ticket,

        @SerializedName("player_name")
        String playerName,

        @SerializedName("launcher_proof")
        String launcherProof
) {
}