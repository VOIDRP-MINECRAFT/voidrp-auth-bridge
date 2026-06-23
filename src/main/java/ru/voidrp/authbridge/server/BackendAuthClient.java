package ru.voidrp.authbridge.server;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import ru.voidrp.authbridge.common.dto.ConsumePlayTicketRequest;
import ru.voidrp.authbridge.common.dto.ConsumePlayTicketResponse;
import ru.voidrp.authbridge.common.dto.LegacyLoginRequest;
import ru.voidrp.authbridge.common.dto.LegacyLoginResponse;
import ru.voidrp.authbridge.common.json.GsonFactory;
import ru.voidrp.authbridge.common.util.HttpJson;
import ru.voidrp.authbridge.config.AuthBridgeProperties;
import ru.voidrp.authbridge.common.dto.PlayerAccessRequest;
import ru.voidrp.authbridge.common.dto.PlayerAccessResponse;

public final class BackendAuthClient {
    private final AuthBridgeProperties properties;
    private final Gson gson;
    private final HttpClient httpClient;

    public BackendAuthClient(AuthBridgeProperties properties) {
        this.properties = properties;
        this.gson = GsonFactory.create();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .build();
    }

    /**
     * Async variant: DNS resolution and HTTP happen on the HttpClient thread pool,
     * so the server main thread is never blocked even if the backend is unreachable.
     */
    public CompletableFuture<PlayerAccessResponse> getPlayerAccessAsync(String playerName) {
        URI uri = properties.backendBaseUrl().resolve("/api/v1/server/auth/player-access");
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("X-Game-Auth-Secret", properties.gameAuthSecret())
                .timeout(properties.requestTimeout())
                .POST(HttpJson.body(gson, new PlayerAccessRequest(playerName)))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return gson.fromJson(response.body(), PlayerAccessResponse.class);
                    }
                    return PlayerAccessResponse.failed("http_" + response.statusCode() + ": " + response.body());
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    return PlayerAccessResponse.failed("io_error: " + cause.getMessage());
                });
    }

    public CompletableFuture<ConsumePlayTicketResponse> consumePlayTicketAsync(ConsumePlayTicketRequest request) {
        URI uri = properties.backendBaseUrl().resolve(properties.consumeTicketPath());
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("X-Game-Auth-Secret", properties.gameAuthSecret())
                .timeout(properties.requestTimeout())
                .POST(HttpJson.body(gson, request))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return gson.fromJson(response.body(), ConsumePlayTicketResponse.class);
                    }
                    return ConsumePlayTicketResponse.failed("http_" + response.statusCode() + ": " + response.body());
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    return ConsumePlayTicketResponse.failed("io_error: " + cause.getMessage());
                });
    }

    public CompletableFuture<LegacyLoginResponse> legacyLoginAsync(LegacyLoginRequest request) {
        URI uri = properties.backendBaseUrl().resolve(properties.legacyLoginPath());
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("X-Game-Auth-Secret", properties.gameAuthSecret())
                .timeout(properties.requestTimeout())
                .POST(HttpJson.body(gson, request))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return gson.fromJson(response.body(), LegacyLoginResponse.class);
                    }
                    return LegacyLoginResponse.failed("http_" + response.statusCode() + ": " + response.body());
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    return LegacyLoginResponse.failed("io_error: " + cause.getMessage());
                });
    }

    public PlayerAccessResponse getPlayerAccess(String playerName) {
        URI uri = properties.backendBaseUrl().resolve("/api/v1/server/auth/player-access");
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .header("X-Game-Auth-Secret", properties.gameAuthSecret())
                .timeout(properties.requestTimeout())
                .POST(HttpJson.body(gson, new PlayerAccessRequest(playerName)))
                .build();

        try {
            HttpResponse<String> response = httpClient
                    .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .get(properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return gson.fromJson(response.body(), PlayerAccessResponse.class);
            }
            return PlayerAccessResponse.failed("http_" + response.statusCode() + ": " + response.body());
        } catch (TimeoutException ex) {
            return PlayerAccessResponse.failed("io_error: request timeout");
        } catch (ExecutionException ex) {
            return PlayerAccessResponse.failed("io_error: " + ex.getCause().getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return PlayerAccessResponse.failed("interrupted: " + ex.getMessage());
        } catch (RuntimeException ex) {
            return PlayerAccessResponse.failed("client_error: " + ex.getMessage());
        }
    }
}
