package ru.voidrp.authbridge.client;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import ru.voidrp.authbridge.common.dto.PlayTicketFile;
import ru.voidrp.authbridge.common.json.GsonFactory;

public final class ClientTicketFileService {
    private final Gson gson = GsonFactory.create();

    public Optional<PlayTicketFile> readTicket(Path path) {
        if (path == null || Files.notExists(path)) {
            return Optional.empty();
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return Optional.ofNullable(gson.fromJson(json, PlayTicketFile.class));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }
}
