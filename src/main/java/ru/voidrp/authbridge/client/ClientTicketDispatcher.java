package ru.voidrp.authbridge.client;

import java.time.Instant;
import java.util.Optional;
import ru.voidrp.authbridge.common.dto.ConsumePlayTicketRequest;
import ru.voidrp.authbridge.common.dto.PlayTicketFile;
import ru.voidrp.authbridge.config.AuthBridgeProperties;

public final class ClientTicketDispatcher {
    private final AuthBridgeProperties properties;
    private final ClientTicketFileService ticketFileService;

    public ClientTicketDispatcher(AuthBridgeProperties properties) {
        this.properties = properties;
        this.ticketFileService = new ClientTicketFileService();
    }

    public Optional<PlayTicketFile> loadValidTicket(String currentPlayerName) {
        Optional<PlayTicketFile> ticket = ticketFileService.readTicket(properties.localTicketPath());
        if (ticket.isEmpty()) {
            return Optional.empty();
        }

        PlayTicketFile value = ticket.get();
        if (value.isExpired(Instant.now())) {
            return Optional.empty();
        }
        if (currentPlayerName == null || value.minecraftNickname() == null) {
            return Optional.empty();
        }
        if (!currentPlayerName.equalsIgnoreCase(value.minecraftNickname())) {
            return Optional.empty();
        }
        if (value.ticket() == null || value.ticket().isBlank()) {
            return Optional.empty();
        }

        return Optional.of(value);
    }

    public Optional<ConsumePlayTicketRequest> buildConsumeRequest(String currentPlayerName) {
        return loadValidTicket(currentPlayerName)
                .map(ticket -> new ConsumePlayTicketRequest(
                        ticket.ticket(),
                        currentPlayerName,
                        ticket.launcherProof() != null ? ticket.launcherProof() : ""
                ));
    }
}