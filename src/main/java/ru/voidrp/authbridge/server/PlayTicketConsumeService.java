package ru.voidrp.authbridge.server;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import ru.voidrp.authbridge.common.dto.ConsumePlayTicketRequest;
import ru.voidrp.authbridge.common.dto.ConsumePlayTicketResponse;

public final class PlayTicketConsumeService {
    private final BackendAuthClient backendAuthClient;
    private final AuthenticationStateStore stateStore;

    public PlayTicketConsumeService(BackendAuthClient backendAuthClient, AuthenticationStateStore stateStore) {
        this.backendAuthClient = backendAuthClient;
        this.stateStore = stateStore;
    }

    public CompletableFuture<ConsumePlayTicketResponse> authenticateAsync(UUID playerUuid, String playerName, ConsumePlayTicketRequest request) {
        return backendAuthClient.consumePlayTicketAsync(request)
                .thenApply(response -> {
                    if (response != null && response.accepted() && response.userId() != null) {
                        stateStore.markAuthenticated(new AuthenticatedPlayerRecord(
                                playerUuid,
                                response.userId(),
                                playerName,
                                Instant.now(),
                                AuthSource.LAUNCHER_TICKET,
                                response.legacyAuthEnabled()
                        ));
                    }
                    return response;
                });
    }
}