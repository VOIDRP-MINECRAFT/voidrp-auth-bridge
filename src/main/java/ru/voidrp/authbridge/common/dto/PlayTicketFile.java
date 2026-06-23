package ru.voidrp.authbridge.common.dto;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;

public record PlayTicketFile(
        @SerializedName("ticket")
        String ticket,

        @SerializedName("minecraftNickname")
        String minecraftNickname,

        @SerializedName("expiresAtUtc")
        Instant expiresAtUtc,

        @SerializedName("source")
        String source,

        @SerializedName("launcherProof")
        String launcherProof
) {
    public boolean isExpired(Instant now) {
        return expiresAtUtc == null || !expiresAtUtc.isAfter(now);
    }
}