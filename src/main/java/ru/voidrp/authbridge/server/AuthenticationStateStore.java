package ru.voidrp.authbridge.server;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthenticationStateStore {

    private final Map<UUID, AuthenticatedPlayerRecord> records = new ConcurrentHashMap<>();
    private final Map<UUID, PendingPlayerRecord> pendingRecords = new ConcurrentHashMap<>();
    private final Map<UUID, PendingPlayerRecord> legacyPendingRecords = new ConcurrentHashMap<>();
    private final Map<UUID, ReconnectGrantRecord> reconnectGrantRecords = new ConcurrentHashMap<>();

    public void markAuthenticated(AuthenticatedPlayerRecord record) {
        records.put(record.playerUuid(), record);
        pendingRecords.remove(record.playerUuid());
        legacyPendingRecords.remove(record.playerUuid());
    }

    public void markPending(UUID playerUuid, PendingPlayerRecord pending) {
        pendingRecords.put(playerUuid, pending);
        legacyPendingRecords.remove(playerUuid);
    }

    public Optional<PendingPlayerRecord> findPending(UUID playerUuid) {
        return Optional.ofNullable(pendingRecords.get(playerUuid));
    }

    public Optional<PendingPlayerRecord> findLegacyPending(UUID playerUuid) {
        return Optional.ofNullable(legacyPendingRecords.get(playerUuid));
    }

    public Optional<PendingPlayerRecord> findRestriction(UUID playerUuid) {
        PendingPlayerRecord pending = pendingRecords.get(playerUuid);
        if (pending != null) {
            return Optional.of(pending);
        }

        return Optional.ofNullable(legacyPendingRecords.get(playerUuid));
    }

    public void markLegacyPending(UUID playerUuid) {
        PendingPlayerRecord pending = pendingRecords.remove(playerUuid);
        if (pending != null) {
            legacyPendingRecords.put(playerUuid, pending);
        }
    }

    public boolean isLegacyPending(UUID playerUuid) {
        return legacyPendingRecords.containsKey(playerUuid);
    }

    public void clear(UUID playerUuid) {
        records.remove(playerUuid);
        pendingRecords.remove(playerUuid);
        legacyPendingRecords.remove(playerUuid);
    }

    public boolean isAuthenticated(UUID playerUuid) {
        return records.containsKey(playerUuid);
    }

    public boolean isRestricted(UUID playerUuid) {
        return !records.containsKey(playerUuid)
                && (pendingRecords.containsKey(playerUuid) || legacyPendingRecords.containsKey(playerUuid));
    }

    public Optional<AuthenticatedPlayerRecord> find(UUID playerUuid) {
        return Optional.ofNullable(records.get(playerUuid));
    }

    public Map<UUID, PendingPlayerRecord> snapshotPending() {
        return Map.copyOf(pendingRecords);
    }

    public void rememberReconnectGrant(AuthenticatedPlayerRecord record, Instant expiresAtUtc) {
        reconnectGrantRecords.put(
                record.playerUuid(),
                new ReconnectGrantRecord(
                        record.playerUuid(),
                        record.userId(),
                        record.playerName(),
                        record.source(),
                        record.legacyAuthEnabled(),
                        record.authenticatedAt(),
                        expiresAtUtc
                )
        );
    }

    public Optional<ReconnectGrantRecord> findActiveReconnectGrant(UUID playerUuid, String playerName, Instant nowUtc) {
        ReconnectGrantRecord grant = reconnectGrantRecords.get(playerUuid);
        if (grant == null) {
            return Optional.empty();
        }

        if (grant.isExpired(nowUtc)) {
            reconnectGrantRecords.remove(playerUuid);
            return Optional.empty();
        }

        if (playerName == null || grant.playerName() == null) {
            reconnectGrantRecords.remove(playerUuid);
            return Optional.empty();
        }

        if (!grant.playerName().equalsIgnoreCase(playerName)) {
            return Optional.empty();
        }

        return Optional.of(grant);
    }

    public void removeReconnectGrant(UUID playerUuid) {
        reconnectGrantRecords.remove(playerUuid);
    }

    public void evictExpiredReconnectGrants(Instant nowUtc) {
        reconnectGrantRecords.entrySet().removeIf(e -> e.getValue().isExpired(nowUtc));
    }

    public record PendingPlayerRecord(
            Instant deadlineUtc,
            boolean legacyAuthEnabled,
            boolean mustUseLauncher,
            double anchorX,
            double anchorY,
            double anchorZ
    ) {
    }

    public record ReconnectGrantRecord(
            UUID playerUuid,
            UUID userId,
            String playerName,
            AuthSource source,
            boolean legacyAuthEnabled,
            Instant grantedAtUtc,
            Instant expiresAtUtc
    ) {
        

    public boolean isExpired(Instant nowUtc) {
        return expiresAtUtc == null || !expiresAtUtc.isAfter(nowUtc);
    }
}
}
