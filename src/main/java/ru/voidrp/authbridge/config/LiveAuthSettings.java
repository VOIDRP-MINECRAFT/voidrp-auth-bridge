package ru.voidrp.authbridge.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.voidrp.authbridge.VoidRpAuthBridge;

/**
 * Login timeouts fetched from the backend and applied without a restart.
 *
 * <p>These used to be JVM flags in youer.service ({@code -Dvoidrp.auth.graceSecs},
 * {@code -Dvoidrp.auth.timeoutMs}, {@code -Dvoidrp.auth.reconnectGrantMinutes}),
 * so loosening a timeout during an incident meant editing a systemd unit and
 * restarting the whole server — the worst possible moment to do either. They now
 * live in {@code game_servers.auth_settings} and are polled from
 * {@code GET /api/v1/server/auth/settings}.
 *
 * <p>The JVM flags are still read: they seed the values before the first poll and
 * remain in force whenever the backend is unreachable, so a backend outage can
 * never change login behaviour on its own.
 *
 * <p>All reads are lock-free volatile reads — they happen on the server thread
 * every tick for every pending player.
 */
public final class LiveAuthSettings {

    /** Frequent enough that an admin change feels immediate, cheap enough to ignore. */
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(10);

    private final AuthBridgeProperties properties;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    private volatile long authGraceSeconds;
    private volatile long requestTimeoutMs;
    private volatile long reconnectGrantMinutes;
    /** True once a poll has succeeded — only used to make the logs honest. */
    private volatile boolean everFetched;

    public LiveAuthSettings(AuthBridgeProperties properties) {
        this.properties = properties;
        this.authGraceSeconds = properties.authGraceSeconds();
        this.requestTimeoutMs = properties.requestTimeout().toMillis();
        this.reconnectGrantMinutes = ServerReconnectDefaults.grantMinutesFromSystemProperty();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .build();
    }

    /**
     * Seconds a player may stay unauthenticated before being kicked.
     * {@code 0} means unlimited — callers must not kick at all.
     */
    public long authGraceSeconds() {
        return authGraceSeconds;
    }

    /** Whether the auth grace period is currently unlimited. */
    public boolean unlimitedLogin() {
        return authGraceSeconds <= 0L;
    }

    public Duration requestTimeout() {
        return Duration.ofMillis(requestTimeoutMs);
    }

    public long reconnectGrantSeconds() {
        return reconnectGrantMinutes * 60L;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "voidrp-auth-settings-poll");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(
                this::pollQuietly, 0L, POLL_INTERVAL.toSeconds(), TimeUnit.SECONDS);

        VoidRpAuthBridge.LOGGER.info(
                "Live auth settings poller started (every {}s). Until the first successful poll the "
                        + "JVM-flag values apply: grace={}s timeout={}ms reconnectGrant={}min",
                POLL_INTERVAL.toSeconds(), authGraceSeconds, requestTimeoutMs, reconnectGrantMinutes);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void pollQuietly() {
        try {
            poll();
        } catch (Exception ex) {
            // A backend hiccup must never be able to take the server thread or the
            // poller down; the previously known-good values simply stay in force.
            VoidRpAuthBridge.LOGGER.debug("Auth settings poll failed, keeping current values", ex);
        }
    }

    private void poll() throws Exception {
        URI uri = properties.backendBaseUrl().resolve("/api/v1/server/auth/settings");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("X-Game-Auth-Secret", properties.gameAuthSecret())
                .timeout(requestTimeout())
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            VoidRpAuthBridge.LOGGER.debug(
                    "Auth settings poll returned http {} — keeping current values", response.statusCode());
            return;
        }

        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        if (json == null) {
            return;
        }

        long newGrace = readLong(json, "auth_grace_seconds", authGraceSeconds);
        long newTimeout = readLong(json, "request_timeout_ms", requestTimeoutMs);
        long newGrant = readLong(json, "reconnect_grant_minutes", reconnectGrantMinutes);

        boolean changed = newGrace != authGraceSeconds
                || newTimeout != requestTimeoutMs
                || newGrant != reconnectGrantMinutes;

        authGraceSeconds = newGrace;
        requestTimeoutMs = newTimeout;
        reconnectGrantMinutes = newGrant;

        if (changed || !everFetched) {
            VoidRpAuthBridge.LOGGER.info(
                    "Auth settings applied from backend: grace={}s ({}) timeout={}ms reconnectGrant={}min",
                    newGrace,
                    newGrace <= 0L ? "unlimited login" : "kick after timeout",
                    newTimeout,
                    newGrant);
        }
        everFetched = true;
    }

    private long readLong(JsonObject json, String key, long fallback) {
        try {
            if (json.has(key) && !json.get(key).isJsonNull()) {
                return json.get(key).getAsLong();
            }
        } catch (RuntimeException ignored) {
            // Malformed field — keep what we had rather than guessing.
        }
        return fallback;
    }

    /** Reads the legacy reconnect-grant JVM flag, so it still seeds the value. */
    private static final class ServerReconnectDefaults {
        private static long grantMinutesFromSystemProperty() {
            try {
                String prop = System.getProperty("voidrp.auth.reconnectGrantMinutes");
                if (prop != null && !prop.isBlank()) {
                    return Math.max(1L, Long.parseLong(prop.trim()));
                }
            } catch (RuntimeException ignored) {
                // fall through to the default
            }
            return 30L;
        }
    }
}
