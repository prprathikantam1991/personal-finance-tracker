package com.pradeep.finance.importing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WatchedFolderImportService {
    private final StatementImportService importService;
    private final Path incomingDirectory;
    private final Path archiveDirectory;
    private final Map<String, InboxStatus> inboxStatuses = new ConcurrentHashMap<>();

    public WatchedFolderImportService(StatementImportService importService,
            @Value("${finance.automation.incoming-directory}") String incomingDirectory,
            @Value("${finance.automation.archive-directory}") String archiveDirectory) {
        this.importService = importService;
        this.incomingDirectory = Path.of(incomingDirectory);
        this.archiveDirectory = Path.of(archiveDirectory);
    }

    @Scheduled(fixedDelayString = "${finance.automation.scan-interval-ms:30000}")
    public void importAvailableStatements() {
        try {
            Files.createDirectories(incomingDirectory);
            Files.createDirectories(archiveDirectory);
            try (Stream<Path> files = Files.list(incomingDirectory)) {
                List<Path> incoming = files.filter(Files::isRegularFile).toList();
                inboxStatuses.keySet().retainAll(incoming.stream().map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
                incoming.forEach(path -> {
                    if (supported(path)) importOne(path);
                    else inboxStatuses.put(path.getFileName().toString(), new InboxStatus("UNSUPPORTED", "Use a PDF or CSV statement file."));
                });
            }
        } catch (IOException ignored) {
            // The next scan retries temporary filesystem issues without losing any input files.
        }
    }

    private boolean supported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || name.endsWith(".csv");
    }

    private void importOne(Path path) {
        try {
            StatementImportResponse response = importService.importStatement(new LocalStatementFile(path), ImportSource.WATCHED_FOLDER);
            if (response.transactions().isEmpty()) {
                inboxStatuses.put(path.getFileName().toString(), new InboxStatus("NEEDS_ATTENTION", "No transaction rows were found. Use a CSV export or add support for this statement layout."));
                return;
            }
            if (response.accountIdentification().status() == com.pradeep.finance.account.AccountIdentificationResponse.AccountMatchStatus.CONFIRMATION_REQUIRED) {
                inboxStatuses.put(path.getFileName().toString(), new InboxStatus("NEEDS_ATTENTION", "The account could not be identified confidently."));
                return;
            }
            importService.confirmAutomatically(response.importId());
            archive(path);
            inboxStatuses.remove(path.getFileName().toString());
        } catch (Exception exception) {
            inboxStatuses.put(path.getFileName().toString(), new InboxStatus("FAILED", "The file could not be read. It was left here for you to inspect or retry."));
        }
    }

    private void archive(Path path) throws IOException {
        String name = Instant.now().toEpochMilli() + "-" + path.getFileName();
        Files.move(path, archiveDirectory.resolve(name), StandardCopyOption.ATOMIC_MOVE);
    }

    public List<IncomingStatementItem> incomingStatements() {
        try {
            Files.createDirectories(incomingDirectory);
            try (Stream<Path> files = Files.list(incomingDirectory)) {
                return files.filter(Files::isRegularFile).map(this::toInboxItem)
                        .sorted(java.util.Comparator.comparing(IncomingStatementItem::modifiedAt).reversed()).toList();
            }
        } catch (IOException exception) {
            return List.of();
        }
    }

    private IncomingStatementItem toInboxItem(Path path) {
        try {
            InboxStatus status = inboxStatuses.getOrDefault(path.getFileName().toString(),
                    new InboxStatus(supported(path) ? "AWAITING_PROCESSING" : "UNSUPPORTED", supported(path) ? "Waiting for the next scan." : "Use a PDF or CSV statement file."));
            return new IncomingStatementItem(path.getFileName().toString(), Files.size(path), Files.getLastModifiedTime(path).toInstant(), status.status(), status.message());
        } catch (IOException exception) {
            return new IncomingStatementItem(path.getFileName().toString(), 0, Instant.EPOCH, "FAILED", "File details could not be read.");
        }
    }

    private record InboxStatus(String status, String message) {}
}
