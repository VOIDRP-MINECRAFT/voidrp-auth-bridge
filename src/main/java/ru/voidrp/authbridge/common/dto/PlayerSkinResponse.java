package ru.voidrp.authbridge.common.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Backend response for GET /api/v1/server/auth/player-skin/{name}.
 * Returns the raw skin PNG url (public media) plus the model variant — no Mojang
 * signature, so it is applied client-side by the modpack.
 */
public record PlayerSkinResponse(
        @SerializedName("has_skin")
        boolean hasSkin,

        @SerializedName("model_variant")
        String modelVariant,

        @SerializedName("skin_url")
        String skinUrl,

        @SerializedName("sha256")
        String sha256,

        @SerializedName("error")
        String error
) {
    public static PlayerSkinResponse failed(String error) {
        return new PlayerSkinResponse(false, "classic", null, null, error);
    }

    public boolean isSlim() {
        return "slim".equalsIgnoreCase(modelVariant);
    }
}
