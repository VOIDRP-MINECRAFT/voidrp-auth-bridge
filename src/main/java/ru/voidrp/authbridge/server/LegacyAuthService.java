package ru.voidrp.authbridge.server;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import ru.voidrp.authbridge.common.dto.LegacyLoginRequest;
import ru.voidrp.authbridge.common.dto.LegacyLoginResponse;

public final class LegacyAuthService {
    private final BackendAuthClient backendAuthClient;
    private final AuthenticationStateStore stateStore;

    public LegacyAuthService(BackendAuthClient backendAuthClient, AuthenticationStateStore stateStore) {
        this.backendAuthClient = backendAuthClient;
        this.stateStore = stateStore;
    }

    public CompletableFuture<LegacyLoginResponse> loginAsync(UUID playerUuid, String playerName, String password) {
        return backendAuthClient.legacyLoginAsync(new LegacyLoginRequest(playerName, password))
                .thenApply(response -> {
                    if (response != null && response.accepted() && response.userId() != null) {
                        stateStore.markAuthenticated(new AuthenticatedPlayerRecord(
                                playerUuid,
                                response.userId(),
                                playerName,
                                Instant.now(),
                                AuthSource.LEGACY_LOGIN,
                                true
                        ));
                    }
                    return response;
                });
    }
}