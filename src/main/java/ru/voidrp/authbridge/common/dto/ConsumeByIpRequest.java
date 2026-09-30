package ru.voidrp.authbridge.common.dto;

import com.google.gson.annotations.SerializedName;

/** The server's own ticket lookup at join: who connected and from which IP. */
public record ConsumeByIpRequest(
        @SerializedName("player_name")
        String playerName,

        @SerializedName("ip")
        String ip
) {
}
