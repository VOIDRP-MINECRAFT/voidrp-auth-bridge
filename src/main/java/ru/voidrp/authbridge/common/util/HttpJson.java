package ru.voidrp.authbridge.common.util;

import com.google.gson.Gson;
import java.net.http.HttpRequest;

public final class HttpJson {
    private HttpJson() {
    }

    public static HttpRequest.BodyPublisher body(Gson gson, Object value) {
        return HttpRequest.BodyPublishers.ofString(gson.toJson(value));
    }
}
