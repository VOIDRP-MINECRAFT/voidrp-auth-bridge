package ru.voidrp.authbridge.common.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.Instant;

public final class GsonFactory {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
            .disableHtmlEscaping()
            .create();

    private GsonFactory() {
    }

    public static Gson create() {
        return GSON;
    }
}
